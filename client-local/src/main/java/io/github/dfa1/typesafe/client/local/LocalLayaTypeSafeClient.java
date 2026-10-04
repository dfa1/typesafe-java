package io.github.dfa1.typesafe.client.local;

import java.nio.file.Path;

/**
 * Laya (convaiinnovations/laya-typed-decisions, Apache-2.0), in-process on ONNX Runtime: a 421M ModernBERT encoder
 * with a decision head trained for Jev-style questions. The fastest local model: about ten times faster than
 * {@link LocalQwenTypeSafeClient}, closer to Jev on yes/no and scores.
 *
 * <p>Same request in, same response out as {@code api.typesafe.ai}, so everything built on typesafe-java
 * ({@code MappingTypeSafeClient}, deadlines, token counting, test doubles) works on top of it unchanged. No network,
 * no API key. Jev itself isn't available outside TypeSafe's API, so this is API parity, not model parity: see the
 * how-to for how closely each model agrees with Jev.
 */
public final class LocalLayaTypeSafeClient extends LocalTypeSafeClient {

    private LocalLayaTypeSafeClient(Engine engine) {
        super(engine);
    }

    /**
     * @param dir onnx-community's export as {@code hf download} writes it: {@code onnx/model.onnx} (or
     *            {@code model_fp16.onnx}) with its weights file, {@code tokenizer.json}, {@code config.json}
     */
    public static LocalLayaTypeSafeClient load(Path dir) {
        return new LocalLayaTypeSafeClient(LayaEngine.load(dir));
    }
}
