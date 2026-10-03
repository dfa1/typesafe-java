package io.github.dfa1.typesafe.local;

import java.nio.file.Path;

/**
 * Qwen2.5-1.5B-Instruct (Apache-2.0), in-process on ONNX Runtime, answering from its next-token logits. Slower than
 * {@link LocalLayaTypeSafeClient}, somewhat better on choices.
 *
 * <p>Same request in, same response out as {@code api.typesafe.ai}, so everything built on typesafe-java
 * ({@code MappingTypeSafeClient}, deadlines, token counting, test doubles) works on top of it unchanged. No network,
 * no API key. Jev itself isn't available outside TypeSafe's API, so this is API parity, not model parity: see the
 * how-to for how closely each model agrees with Jev.
 */
public final class LocalQwenTypeSafeClient extends LocalTypeSafeClient {

    private LocalQwenTypeSafeClient(Engine engine) {
        super(engine);
    }

    /**
     * @param dir {@code tokenizer.json} and one {@code .onnx} model (e.g. onnx-community's {@code onnx/model_q4.onnx})
     */
    public static LocalQwenTypeSafeClient load(Path dir) {
        return new LocalQwenTypeSafeClient(QwenEngine.load(dir));
    }
}
