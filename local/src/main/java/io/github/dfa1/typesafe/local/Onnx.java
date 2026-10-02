package io.github.dfa1.typesafe.local;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** The few ONNX Runtime and tokenizer calls both engines share. */
final class Onnx {

    static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();

    private Onnx() {
    }

    /** All cores: one session on every core beat every split we measured (experiments/). With {@code gpu}, the
     *  WebGPU execution provider (Metal on macOS, via Dawn) takes what it supports and the CPU the rest. */
    static OrtSession session(Path model, boolean gpu) throws OrtException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
            if (gpu) {
                options.addWebGPU(java.util.Map.of());
            }
            return ENV.createSession(model.toString(), options);
        }
    }

    /** HuggingFace tokenizer.json, tokenized exactly as the model was trained; no special tokens added implicitly. */
    static BpeTokenizer tokenizer(Path json) throws IOException {
        return BpeTokenizer.load(json);
    }

    static OnnxTensor longs(long[] data, long... shape) throws OrtException {
        return OnnxTensor.createTensor(ENV, LongBuffer.wrap(data), shape);
    }

    static Path require(Path dir, String file) {
        Path path = dir.resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(path + " not found: see the README for how to prepare a model directory");
        }
        return path;
    }
}
