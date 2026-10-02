package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requests/s per engine and questions per request; questions/s = score x {@code questions}. Run:
 * {@code mvn test-compile exec:exec -Dexec.classpathScope=test -Dexec.executable=java
 * "-Dexec.args=-cp %classpath org.openjdk.jmh.Main LocalTypeSafeClientBenchmark"} (narrow with
 * e.g. {@code -p engine=laya -p questions=3}).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 10)
@Fork(1)
public class LocalTypeSafeClientBenchmark {

    @Param({"laya", "laya-gpu", "qwen", "qwen-gpu"})
    public String engine;

    @Param({"1", "3", "10"})
    public int questions;

    private EvaluateRequest request;
    private LocalTypeSafeClient sut;

    @Setup
    public void load() {
        sut = Engines.client(engine);
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("billing", "payments, charges, invoices, refunds");
        kinds.put("bug", "the product crashes, errors or misbehaves");
        kinds.put("feature", "a request for something new");
        kinds.put("account", "login, access, account settings");
        List<Question> mix = List.of(Question.noul("Does this message require immediate attention?"),
                Question.choice("What is this support message about?", kinds),
                Question.score("How angry is the customer?", List.of("calm", "annoyed", "angry", "furious")));
        Map<String, Question> qs = new LinkedHashMap<>();
        for (int i = 0; i < questions; i++) {
            qs.put("q" + i, mix.get(i % mix.size()));
        }
        request = EvaluateRequest.of(Content.text("Help! My payouts have been failing for 3 days and I can't pay my staff."), qs);
    }

    @TearDown
    public void close() {
        sut.close();
    }

    @Benchmark
    public EvaluateResponse evaluate() {
        return sut.evaluate(request);
    }
}
