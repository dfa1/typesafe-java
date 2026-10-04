package io.github.dfa1.typesafe.client.local;

import java.nio.file.Path;

/**
 * Qwen2.5-1.5B-Instruct (Apache-2.0), in-process on ONNX Runtime, answering from its next-token logits: a baseline
 * showing what prompting a general LLM to imitate Jev buys. About ten times slower than {@link LocalLayaTypeSafeClient}
 * and less like Jev on yes/no and scores; {@link LocalClefTypeSafeClient}, trained for these decisions, beats both.
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
