package io.github.dfa1.typesafe.client.local;

import ai.onnxruntime.OrtException;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.TypeSafeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The TypeSafe contract around any {@link Engine}. */
class LocalTypeSafeClientTest {

    private static final Model FAKE = new Model("local/fake");
    private static final EvaluateRequest REQUEST = EvaluateRequest.of(Content.text("x"), Map.of("q", Question.noul("?")));

    private boolean closed;
    private boolean fail;

    private final Engine engine = new Engine() {
        @Override
        public Answers answer(Content state, Map<String, Question> questions) throws OrtException {
            if (fail) {
                throw new OrtException("boom");
            }
            return new Answers(Map.of("q", new Answer.Noul(0.7)), 3);
        }

        @Override
        public Model model() {
            return FAKE;
        }

        @Override
        public String description() {
            return "fake";
        }

        @Override
        public void close() {
            closed = true;
        }
    };

    private final LocalTypeSafeClient sut = new LocalTypeSafeClient(engine);

    @Test
    void answersWithTheEnginesModelUsageAndTiming() {
        // When
        EvaluateResponse result = sut.evaluate(REQUEST);

        // Then
        assertThat(result.model()).isEqualTo(FAKE);
        assertThat(result.nouls().get("q").noul()).isEqualTo(0.7);
        assertThat(result.usage().inputTokens()).isEqualTo(3);
        assertThat(result.metadata().upstreamServiceTime()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jev-latest", "jev-preview", "local/fake"})
    void acceptsTheSymbolicModelsAndItsOwn(String model) {
        // When
        EvaluateResponse result = sut.evaluate(EvaluateRequest.of(Content.text("x"), new Model(model), REQUEST.questions()));

        // Then
        assertThat(result.model()).isEqualTo(FAKE);
    }

    @Test
    void rejectsAnotherModelAsNotFound() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(Content.text("x"), new Model("jev-1.13.0"), REQUEST.questions());

        // When
        Throwable result = catchThrowable(() -> sut.evaluate(request));

        // Then
        assertThat(result).isInstanceOf(TypeSafeException.NotFound.class);
    }

    @Test
    void rejectsNoQuestionsAsBadRequest() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(Content.text("x"), Map.of());

        // When
        Throwable result = catchThrowable(() -> sut.evaluate(request));

        // Then
        assertThat(result).isInstanceOf(TypeSafeException.BadRequest.class);
    }

    @Test
    void rejectsAChoiceWithoutCriteriaAsBadRequest() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(Content.text("x"), Map.of("q", Question.choice("?", Map.of())));

        // When
        Throwable result = catchThrowable(() -> sut.evaluate(request));

        // Then
        assertThat(result).isInstanceOf(TypeSafeException.BadRequest.class).hasMessageContaining("q:");
    }

    @Test
    void inferenceFailureIsAnInternalServerError() {
        // Given
        fail = true;

        // When
        Throwable result = catchThrowable(() -> sut.evaluate(REQUEST));

        // Then
        assertThat(result).isInstanceOf(TypeSafeException.InternalServer.class);
    }

    @Test
    void evaluateAsyncCompletesWithTheSameAnswer() {
        // When
        EvaluateResponse result = sut.evaluateAsync(REQUEST).join();

        // Then
        assertThat(result.nouls().get("q").noul()).isEqualTo(0.7);
    }

    @Test
    void listsItsOneModel() {
        // When
        List<ModelDetails> result = sut.listModels();

        // Then
        assertThat(result).extracting(ModelDetails::name).containsExactly("local/fake");
    }

    @Test
    void closeClosesTheEngine() {
        // When
        sut.close();

        // Then
        assertThat(closed).isTrue();
    }
}
