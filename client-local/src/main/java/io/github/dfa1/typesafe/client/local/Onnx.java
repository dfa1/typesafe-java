package io.github.dfa1.typesafe.client.local;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.github.dfa1.typesafe.core.JsonCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/** The few ONNX Runtime and tokenizer calls every engine shares. */
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

    /** typesafe-java's JsonCodec (jackson2 or jackson3), found the way the API client finds it. */
    static JsonCodec json() {
        return Codec.INSTANCE;
    }

    private static final class Codec {
        static final JsonCodec INSTANCE = ServiceLoader.load(JsonCodec.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No JsonCodec found on the classpath. Add typesafe-java-codec-jackson2 "
                        + "or typesafe-java-codec-jackson3 as a dependency."));
    }

    /**
     * The model file, wherever {@code hf download} put it: the only {@code .onnx} file in {@code dir/onnx} or
     * {@code dir} ({@code model.onnx} if there are several), so files keep their HuggingFace names
     * ({@code model_q4.onnx}, {@code model_fp16.onnx}, ...).
     */
    static Path model(Path dir) {
        for (Path d : List.of(dir.resolve("onnx"), dir)) {
            if (!Files.isDirectory(d)) {
                continue;
            }
            List<Path> models;
            try (Stream<Path> files = Files.list(d)) {
                models = files.filter(f -> f.getFileName().toString().endsWith(".onnx")).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (models.size() == 1) {
                return models.getFirst();
            }
            if (models.contains(d.resolve("model.onnx"))) {
                return d.resolve("model.onnx");
            }
            if (!models.isEmpty()) {
                throw new IllegalArgumentException("several .onnx files in " + d + ": " + models + "; keep one model per directory");
            }
        }
        throw new IllegalArgumentException("no .onnx file in " + dir + " or " + dir.resolve("onnx")
                + ": see https://github.com/dfa1/typesafe-java/blob/main/docs/how-to.md#run-without-the-api-on-a-local-model for the hf download commands");
    }

    static Path require(Path dir, String file) {
        Path path = dir.resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(path + " not found: see https://github.com/dfa1/typesafe-java/blob/main/docs/how-to.md#run-without-the-api-on-a-local-model for the hf download commands");
        }
        return path;
    }
}
