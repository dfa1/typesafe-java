package io.github.dfa1.typesafe.local;

import ai.onnxruntime.OrtException;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.TypeSafeClient;
import io.github.dfa1.typesafe.core.TypeSafeException;
import io.github.dfa1.typesafe.core.Usage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A {@link TypeSafeClient} that evaluates in-process on ONNX Runtime instead of calling
 * {@code api.typesafe.ai}: same request in, same response out, so everything built on
 * typesafe-java ({@code MappingTypeSafeClient}, deadlines, token counting, test doubles) works on
 * top of it unchanged. No network, no API key; the model is read from a local directory.
 *
 * <p>Jev itself isn't available outside TypeSafe's API, so this is API parity, not model parity:
 * see the README for how closely each engine agrees with Jev.
 */
public final class LocalTypeSafeClient implements TypeSafeClient {

    private final Engine engine;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    LocalTypeSafeClient(Engine engine) {
        this.engine = engine;
    }

    /**
     * Laya (convaiinnovations/laya-typed-decisions, Apache-2.0): a 421M ModernBERT encoder with a
     * decision head trained for Jev-style questions. The recommended engine: about ten times faster than
     * {@link #qwen}, closer to Jev on yes/no and scores.
     *
     * @param dir onnx-community's export as {@code hf download} writes it: {@code onnx/model.onnx} (or
     *            {@code model_fp16.onnx}) with its weights file, {@code tokenizer.json}, {@code config.json}
     */
    public static LocalTypeSafeClient laya(Path dir) {
        return new LocalTypeSafeClient(LayaEngine.load(dir));
    }

    /**
     * Qwen2.5-1.5B-Instruct (Apache-2.0), answering from its next-token logits. Slower than
     * {@link #laya}, somewhat better on choices.
     *
     * @param dir {@code tokenizer.json} and one {@code .onnx} model (e.g. onnx-community's {@code onnx/model_q4.onnx})
     */
    public static LocalTypeSafeClient qwen(Path dir) {
        return new LocalTypeSafeClient(QwenEngine.load(dir));
    }

    /**
     * Clef-flash (Cloudflare/clef-flash, Apache-2.0): Qwen3.5-9B with a joint schema head, every question of a
     * request decided in one forward pass. About 19 GB of bf16 weights: meant for a machine with the memory for it.
     *
     * @param dir clef-flash's {@code tokenizer.json} and {@code *.safetensors} as {@code hf download} writes them, plus
     *            Ollaya's {@code flash/model-fp32.onnx}; the first load adds {@code sha256-*} links next to the graph
     */
    public static LocalTypeSafeClient clef(Path dir) {
        return new LocalTypeSafeClient(ClefEngine.load(dir));
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
