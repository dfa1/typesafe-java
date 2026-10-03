package io.github.dfa1.typesafe.local;

import ai.onnxruntime.OrtException;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.TypeSafeClient;
import io.github.dfa1.typesafe.core.TypeSafeException;
import io.github.dfa1.typesafe.core.Usage;

import java.time.Duration;
import java.util.List;
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

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

    /** {@link #evaluate} on a virtual thread. Concurrent calls are safe; they share the CPU, so they
     *  don't add throughput (one session already uses every core). */
    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        return CompletableFuture.supplyAsync(() -> evaluate(request), executor);
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
