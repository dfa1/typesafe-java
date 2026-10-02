package io.github.dfa1.typesafe.local;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiFunction;

/** The engines the model tests, the comparison and the benchmark run, with their model directories (local/scripts).
 *  Public only because JMH's generated benchmark code (a sub-package) takes it as a @Param. */
public enum Engines {
    LAYA("laya-fp32", false, LayaEngine::load),
    LAYA_GPU("laya-fp32", true, LayaEngine::load),
    LAYA_FP16("laya-fp16", false, LayaEngine::load),
    LAYA_FP16_GPU("laya-fp16", true, LayaEngine::load),
    QWEN("qwen2.5-1.5b", false, QwenEngine::load),
    QWEN_GPU("qwen2.5-1.5b", true, QwenEngine::load);

    private final String dirName;
    private final boolean gpu;
    private final BiFunction<Path, Boolean, Engine> load;

    Engines(String dirName, boolean gpu, BiFunction<Path, Boolean, Engine> load) {
        this.dirName = dirName;
        this.gpu = gpu;
        this.load = load;
    }

    /** {@code laya-gpu} or {@code LAYA_GPU}. */
    static Engines of(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    Path dir() {
        return Path.of(System.getProperty("typesafe.local.models", System.getProperty("user.home") + "/.cache/typesafe-local"))
                .resolve(dirName);
    }

    LocalTypeSafeClient client() {
        return new LocalTypeSafeClient(load.apply(dir(), gpu));
    }
}
