package io.github.dfa1.typesafe.client.local;

import ai.onnxruntime.OrtException;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.TypeSafeClient;
import io.github.dfa1.typesafe.core.TypeSafeException;
import io.github.dfa1.typesafe.core.Usage;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What every local client shares: a {@link TypeSafeClient} that evaluates in-process on ONNX Runtime instead of
 * calling {@code api.typesafe.ai}, through one {@link Engine}. Package-private; callers use one public class per
 * model ({@link LocalLayaTypeSafeClient}, {@link LocalQwenTypeSafeClient}, {@link LocalClefTypeSafeClient}), and
 * the test harness builds this directly for engine variants (GPU, other model directories).
 */
class LocalTypeSafeClient implements TypeSafeClient {

    private final Engine engine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("typesafe-local").daemon().factory()); // daemon: an unclosed client can't block JVM exit

    LocalTypeSafeClient(Engine engine) {
        this.engine = engine;
    }

    /**
     * Evaluates {@code request} on the local model. Throws {@link TypeSafeException.BadRequest} for a
     * request the engine can't express, {@link TypeSafeException.NotFound} for a {@code model} other
     * than {@link Model#LATEST}, {@link Model#PREVIEW} or this client's own, and
     * {@link TypeSafeException.InternalServer} if inference fails — the same classes the API's
     * statuses map to, so {@code RetryingTypeSafeClient} classifies them correctly.
     */
    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        Model requested = request.model();
        if (requested != null && !requested.equals(Model.LATEST) && !requested.equals(Model.PREVIEW)
                && !requested.equals(engine.model())) {
            throw new TypeSafeException.NotFound("unknown model " + requested.name() + "; this client runs " + engine.model().name());
        }
        if (request.state() == null) {
            throw new TypeSafeException.BadRequest("state is required");
        }
        validate(request.questions());
        long start = System.nanoTime();
        Engine.Answers answers;
        try {
            answers = engine.answer(request.state(), request.questions());
        } catch (OrtException e) {
            throw new TypeSafeException.InternalServer(500, "local inference failed: " + e.getMessage());
        }
        return new EvaluateResponse(engine.model(), answers.answers(), new Usage(answers.inputTokens(), 0),
                new EvaluateResponse.Metadata(null, Duration.ofNanos(System.nanoTime() - start)));
    }

    /** {@link #evaluate} on one platform thread, one call at a time: concurrency adds no throughput (one session
     *  already uses every core), and a virtual thread would stay pinned to its carrier through the native call. */
    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        return CompletableFuture.supplyAsync(() -> evaluate(request), executor);
    }

    /** What no engine can answer: no questions, or a choice/score without criteria. */
    private static void validate(Map<String, Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new TypeSafeException.BadRequest("questions are required");
        }
        questions.forEach((name, question) -> {
            boolean empty = switch (question) {
                case Question.Noul ignored -> false;
                case Question.Choice c -> c.criteria() == null || c.criteria().isEmpty();
                case Question.Score s -> s.criteria() == null || s.criteria().isEmpty();
            };
            if (empty) {
                throw new TypeSafeException.BadRequest(name + ": criteria must not be empty");
            }
        });
    }

    /** The one model this client runs. */
    @Override
    public List<ModelDetails> listModels() {
        return List.of(new ModelDetails(engine.model().name(), engine.description(), null));
    }

    @Override
    public void close() {
        executor.close();
        engine.close();
    }
}
