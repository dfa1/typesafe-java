package io.github.dfa1.typesafe.local;

import java.nio.file.Path;

/** Where the tests, the comparison and the benchmark find model directories (prepared by scripts/). */
final class Engines {

    private Engines() {
    }

    static Path dir(String engine) {
        String name = switch (engine.replace("-gpu", "")) {
            case "laya" -> "laya-int8";
            case "laya-fp32" -> "laya-fp32";
            case "qwen" -> "qwen2.5-1.5b";
            default -> throw new IllegalArgumentException("unknown engine " + engine + "; use laya, laya-fp32 or qwen");
        };
        return Path.of(System.getProperty("typesafe.local.models", System.getProperty("user.home") + "/.cache/typesafe-local")).resolve(name);
    }

    /** {@code -gpu} suffix: WebGPU execution provider (experimental). */
    static LocalTypeSafeClient client(String engine) {
        boolean gpu = engine.endsWith("-gpu");
        return new LocalTypeSafeClient(engine.startsWith("qwen") ? QwenEngine.load(dir(engine), gpu) : LayaEngine.load(dir(engine), gpu));
    }
}
