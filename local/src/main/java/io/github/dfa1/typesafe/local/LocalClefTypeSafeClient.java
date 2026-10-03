package io.github.dfa1.typesafe.local;

import java.nio.file.Path;

/**
 * Clef-flash (Cloudflare/clef-flash, Apache-2.0), in-process on ONNX Runtime: Qwen3.5-9B with a joint schema head,
 * every question of a request decided in one forward pass. The closest to Jev, and by far the heaviest: convert it
 * to 4-bit weights first ({@code local/scripts/clef/quantize_q4.py}). On a Mac, the same model is faster through MLX
 * with the regular client: see the how-to.
 *
 * <p>Same request in, same response out as {@code api.typesafe.ai}, so everything built on typesafe-java
 * ({@code MappingTypeSafeClient}, deadlines, token counting, test doubles) works on top of it unchanged. No network,
 * no API key. Jev itself isn't available outside TypeSafe's API, so this is API parity, not model parity: see the
 * how-to for how closely each model agrees with Jev.
 */
public final class LocalClefTypeSafeClient extends LocalTypeSafeClient {

    private LocalClefTypeSafeClient(Engine engine) {
        super(engine);
    }

    /**
     * @param dir clef-flash's {@code tokenizer.json} and {@code *.safetensors} as {@code hf download} writes them, plus
     *            Ollaya's {@code flash/model-fp32.onnx} (or the {@code flash/model-q4.onnx} that
     *            {@code quantize_q4.py} writes); the first load adds {@code sha256-*} hard links next to the graph
     */
    public static LocalClefTypeSafeClient load(Path dir) {
        return new LocalClefTypeSafeClient(ClefEngine.load(dir));
    }
}
