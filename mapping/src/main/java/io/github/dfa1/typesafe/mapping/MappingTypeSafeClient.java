package io.github.dfa1.typesafe.mapping;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypeSafeClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A {@link TypeSafeClient} decorator that reflects over a record's {@link Noul @Noul}/
 * {@link Choice @Choice}/{@link Score @Score}-annotated components to build the
 * {@link EvaluateRequest}'s questions, then maps {@link EvaluateResponse#answers()} back into a
 * new instance of that same record — so a caller works with a typed record instead of
 * {@code Map<String, Answer>} and a manual {@code (Answer.Noul)}-style cast. Each component must
 * carry exactly one of the three annotations, matching its type: {@code double} for
 * {@code @Noul}/{@code @Score}, {@code String} for {@code @Choice}. A record type's reflection
 * metadata (its components' questions and its canonical constructor) is computed once and cached
 * for the lifetime of this instance, so repeated calls for the same record type don't re-walk it.
 *
 * <p>A {@link io.github.dfa1.typesafe.core.TypeSafeException} the delegate throws (already final
 * — its own retries, if any, are already exhausted) propagates unchanged; this class never wraps
 * or reclassifies it. The exceptions this class raises itself —
 * {@link IllegalArgumentException} for a record whose annotations don't validate, and
 * {@link IllegalStateException} for a response that doesn't match its record — are deliberately
 * a different, non-{@code TypeSafeException} family: both are always non-transient (a
 * misconfigured record fails the same way every time; a successful response that doesn't match
 * its record is a client-side data-shape bug, not a server hiccup), so retrying either is never
 * useful.
 */
public final class MappingTypeSafeClient implements TypeSafeClient {

    private final TypeSafeClient delegate;
    private final Map<Class<?>, RecordMapping<?>> cache = new ConcurrentHashMap<>();

    private MappingTypeSafeClient(TypeSafeClient delegate) {
        this.delegate = delegate;
    }

    /** Wraps {@code delegate} in a {@link MappingTypeSafeClient}. Pass this as the {@code decorate}
     *  function to {@link io.github.dfa1.typesafe.core.DefaultTypeSafeClient.Builder#build(java.util.function.Function)},
     *  e.g.
     *  {@code builder(apiKey).build(MappingTypeSafeClient::decorate)}. */
    public static MappingTypeSafeClient decorate(TypeSafeClient delegate) {
        return new MappingTypeSafeClient(delegate);
    }

    /** {@link #evaluateTyped(State, Model, Class)} against {@link Model#LATEST}. */
    public <T extends Record> T evaluateTyped(State state, Class<T> type) {
        return evaluateTyped(state, Model.LATEST, type);
    }

    /** Evaluates {@code state} against {@code type}'s annotated components, blocking until a
     *  typed result arrives or the retry budget is exhausted (see {@link #evaluate}). */
    public <T extends Record> T evaluateTyped(State state, Model model, Class<T> type) {
        RecordMapping<T> mapping = mappingFor(type);
        EvaluateResponse response = delegate.evaluate(EvaluateRequest.of(state, model, questionsFor(mapping)));
        return toRecord(response, mapping);
    }

    /** {@link #evaluateTypedAsync(State, Model, Class)} against {@link Model#LATEST}. */
    public <T extends Record> CompletableFuture<T> evaluateTypedAsync(State state, Class<T> type) {
        return evaluateTypedAsync(state, Model.LATEST, type);
    }

    /** Asynchronous form of {@link #evaluateTyped(State, Model, Class)}. */
    public <T extends Record> CompletableFuture<T> evaluateTypedAsync(State state, Model model, Class<T> type) {
        RecordMapping<T> mapping = mappingFor(type);
        return delegate.evaluateAsync(EvaluateRequest.of(state, model, questionsFor(mapping)))
                .thenApply(response -> toRecord(response, mapping));
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        return delegate.evaluate(request);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        return delegate.evaluateAsync(request);
    }

    @Override
    public List<ModelDetails> listModels() {
        return delegate.listModels();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** One record component's precomputed mapping: the question key/shape to ask, and how to
     *  pull its value back out of the matching {@link Answer}. */
    private record ComponentMapping(String name, Question question, Function<Answer, Object> answerValue) {
    }

    /** {@code type}'s full mapping, computed once: every component's {@link ComponentMapping},
     *  plus the canonical constructor {@link #toRecord} builds a new instance through. */
    private record RecordMapping<T extends Record>(List<ComponentMapping> components, Constructor<T> constructor) {
    }

    @SuppressWarnings("unchecked")
    private <T extends Record> RecordMapping<T> mappingFor(Class<T> type) {
        return (RecordMapping<T>) cache.computeIfAbsent(type, ignored -> computeMapping(type));
    }

    private static <T extends Record> RecordMapping<T> computeMapping(Class<T> type) {
        RecordComponent[] recordComponents = type.getRecordComponents();
        List<ComponentMapping> components = new ArrayList<>(recordComponents.length);
        Class<?>[] paramTypes = new Class<?>[recordComponents.length];
        for (int i = 0; i < recordComponents.length; i++) {
            components.add(componentMappingFor(recordComponents[i]));
            paramTypes[i] = recordComponents[i].getType();
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return new RecordMapping<>(components, constructor);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No canonical constructor found for " + type.getSimpleName(), e);
        }
    }

    private static ComponentMapping componentMappingFor(RecordComponent component) {
        Noul noul = component.getAnnotation(Noul.class);
        Choice choice = component.getAnnotation(Choice.class);
        Score score = component.getAnnotation(Score.class);
        int annotationCount = (noul != null ? 1 : 0) + (choice != null ? 1 : 0) + (score != null ? 1 : 0);
        if (annotationCount != 1) {
            throw new IllegalArgumentException(
                    component + " must carry exactly one of @Noul/@Choice/@Score, found " + annotationCount);
        }
        if (noul != null) {
            requireType(component, double.class);
            return new ComponentMapping(component.getName(), Question.noul(noul.value()),
                    answer -> ((Answer.Noul) answer).noul());
        }
        if (choice != null) {
            requireType(component, String.class);
            return new ComponentMapping(component.getName(), Question.choice(choice.value(), criteriaFor(choice, component)),
                    answer -> ((Answer.Choice) answer).choice());
        }
        requireType(component, double.class);
        return new ComponentMapping(component.getName(), Question.score(score.value(), List.of(score.levels())),
                answer -> ((Answer.Score) answer).score());
    }

    private static Map<String, String> criteriaFor(Choice choice, RecordComponent component) {
        Map<String, String> criteria = new LinkedHashMap<>();
        for (Option option : choice.options()) {
            if (criteria.putIfAbsent(option.value(), option.description()) != null) {
                throw new IllegalArgumentException("Duplicate @Option(\"" + option.value() + "\") on " + component);
            }
        }
        return criteria;
    }

    private static void requireType(RecordComponent component, Class<?> expected) {
        if (component.getType() != expected) {
            throw new IllegalArgumentException(
                    component + " must be " + expected.getSimpleName() + ", got " + component.getType().getSimpleName());
        }
    }

    private static Map<String, Question> questionsFor(RecordMapping<?> mapping) {
        Map<String, Question> questions = new LinkedHashMap<>();
        for (ComponentMapping component : mapping.components()) {
            questions.put(component.name(), component.question());
        }
        return questions;
    }

    private static <T extends Record> T toRecord(EvaluateResponse response, RecordMapping<T> mapping) {
        List<ComponentMapping> components = mapping.components();
        Object[] args = new Object[components.size()];
        for (int i = 0; i < components.size(); i++) {
            ComponentMapping component = components.get(i);
            Answer answer = response.answers().get(component.name());
            if (answer == null) {
                throw new IllegalStateException(
                        "No answer for \"" + component.name() + "\" in the response for " + mapping.constructor().getDeclaringClass().getSimpleName());
            }
            try {
                args[i] = component.answerValue().apply(answer);
            } catch (ClassCastException e) {
                throw new IllegalStateException("Answer for \"" + component.name() + "\" was a "
                        + answer.getClass().getSimpleName() + ", not what its annotation expected", e);
            }
        }
        try {
            return mapping.constructor().newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to construct " + mapping.constructor().getDeclaringClass().getSimpleName() + " from the response", e);
        }
    }
}
