package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.ApiKey;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.TypeSafeClient;
import io.github.dfa1.typesafe.jackson2.Jackson2Codec;
import io.github.dfa1.typesafe.json.JsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How closely a local engine agrees with the real hosted JEV on the {@link JevCases}. JEV's
 * answers are cached under {@code src/test/resources/jev/} (first run calls the TypeSafe API,
 * needs {@code ~/.typesafe.apikey}); later runs only evaluate locally. Run:
 * {@code mvn test-compile exec:exec -Dexec.classpathScope=test -Dexec.executable=java
 * "-Dexec.args=-cp %classpath io.github.dfa1.typesafe.local.JevComparison laya"} (or qwen; append -gpu for WebGPU).
 */
public final class JevComparison {

    private static final Path CACHE = Path.of("src/test/resources/jev");

    private JevComparison() {
    }

    static final List<String> MESSAGES = List.of(
            "Help! My payouts have been failing for 3 days and I can't pay my staff.",
            "Thanks for the quick reply last week, everything works fine now.",
            "I was charged twice for my subscription this month.",
            "Could you tell me where to find the export button? Thanks.",
            "This is the THIRD time your app ate my data. I am DONE. Unacceptable!!!",
            "It would be nice if the dashboard had a dark mode.",
            "I can't log in, the password reset email never arrives. I have a demo in 20 minutes!",
            "The app crashes every time I open the settings page on Android 14.",
            "Please cancel my account and delete my data.",
            "Your invoice shows the wrong VAT number for our company.",
            "Hey, love the product! Any plans for a Slack integration?",
            "Why was my card declined? It works everywhere else. Fix this now.",
            "The CSV export puts dates in the wrong format, minor annoyance but please look at it.",
            "Our whole team is locked out after the SSO change, production is down.",
            "Can I change the email address on my account?",
            "I've been waiting two weeks for a refund. This is ridiculous.",
            "Search results load slowly since yesterday's update.",
            "Is there an API rate limit I should know about?",
            "You deleted my project without warning. I want an explanation and my data back immediately.",
            "Could you add support for exporting reports as PDF?",
            "My trial ended but I was charged for a full year instead of a month.",
            "Notifications stopped working on iOS after the latest update, not urgent.",
            "How do I add a second admin to our workspace?",
            "The billing page throws a 500 error when I try to update my card, and my plan expires tonight.");

    static EvaluateRequest request(String message) {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("billing", "payments, charges, invoices, refunds");
        kinds.put("bug", "the product crashes, errors or misbehaves");
        kinds.put("feature", "a request for something new");
        kinds.put("account", "login, access, account settings");
        return EvaluateRequest.builder().state(message)
                .noul("urgent", "Does this message require immediate attention?")
                .choice("kind", "What is this support message about?", kinds)
                .score("anger", "How angry is the customer?", List.of("calm", "annoyed", "angry", "furious"))
                .build();
    }

    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : "laya";
        JsonCodec codec = new Jackson2Codec();
        List<JevCases.Case> cases = JevCases.all();
        List<EvaluateResponse> jev = jevAnswers(codec, cases);

        List<EvaluateResponse> local = new ArrayList<>();
        long start;
        try (TypeSafeClient client = Engines.client(model)) {
            client.evaluate(cases.getFirst().request()); // warm-up, not timed
            start = System.nanoTime();
            for (JevCases.Case c : cases) {
                local.add(client.evaluate(c.request()));
            }
        }
        double secondsPerRequest = (System.nanoTime() - start) / 1e9 / cases.size();

        System.out.printf("%s vs %s, %d requests, %.2f s/request locally%n", model, jev.getFirst().model().name(),
                cases.size(), secondsPerRequest);
        Map<String, Stats> bySuite = new LinkedHashMap<>();
        Stats overall = new Stats();
        for (int i = 0; i < cases.size(); i++) {
            Stats suite = bySuite.computeIfAbsent(cases.get(i).suite(), k -> new Stats());
            suite.add(jev.get(i), local.get(i));
            overall.add(jev.get(i), local.get(i));
        }
        bySuite.forEach((suite, stats) -> System.out.println(stats.line(suite)));
        System.out.println(overall.line("ALL"));
    }

    /** Agreement over every answer of a set of requests, by question type; scores normalized to 0–1. */
    static final class Stats {
        final List<double[]> nouls = new ArrayList<>();   // {jev, local}
        final List<double[]> scores = new ArrayList<>();  // {jev, local}, each score / (levels - 1)
        int choices, choiceAgree;
        double choiceTv;

        void add(EvaluateResponse jev, EvaluateResponse local) {
            jev.answers().forEach((key, j) -> {
                Answer l = local.answers().get(key);
                switch (j) {
                    case Answer.Noul jn -> nouls.add(new double[]{jn.noul(), ((Answer.Noul) l).noul()});
                    case Answer.Choice jc -> {
                        Answer.Choice lc = (Answer.Choice) l;
                        choices++;
                        choiceAgree += jc.choice().equals(lc.choice()) ? 1 : 0;
                        double tv = 0;
                        for (String option : jc.probabilities().keySet()) {
                            tv += Math.abs(jc.probabilities().get(option) - lc.probabilities().getOrDefault(option, 0.0));
                        }
                        choiceTv += tv / 2;
                    }
                    case Answer.Score js -> {
                        double top = js.legend().size() - 1;
                        scores.add(new double[]{js.score() / top, ((Answer.Score) l).score() / top});
                    }
                }
            });
        }

        String line(String name) {
            double[] jn = col(nouls, 0), ln = col(nouls, 1), js = col(scores, 0), ls = col(scores, 1);
            int agree = 0;
            for (double[] pair : nouls) {
                agree += (pair[0] > 0.5) == (pair[1] > 0.5) ? 1 : 0;
            }
            return String.format("  %-10s noul agree %3d/%-3d (%3.0f%%) MAE %.2f r %5.2f | choice agree %3d/%-3d (%3.0f%%) TV %.2f"
                            + " | score MAE %.2f r %5.2f",
                    name, agree, nouls.size(), 100.0 * agree / nouls.size(), mae(jn, ln), pearson(jn, ln),
                    choiceAgree, choices, 100.0 * choiceAgree / choices, choiceTv / choices, mae(js, ls), pearson(js, ls));
        }

        private static double[] col(List<double[]> pairs, int i) {
            return pairs.stream().mapToDouble(p -> p[i]).toArray();
        }
    }

    /** JEV's answers, from the cache or (first run, per missing case) the real TypeSafe API. */
    private static List<EvaluateResponse> jevAnswers(JsonCodec codec, List<JevCases.Case> cases) throws IOException {
        Files.createDirectories(CACHE);
        List<EvaluateResponse> result = new ArrayList<>();
        TypeSafeClient api = null;
        try {
            for (JevCases.Case c : cases) {
                Path file = CACHE.resolve(c.file());
                if (!Files.exists(file)) {
                    if (api == null) {
                        api = TypeSafeClient.builder(ApiKey.fromDefaultFile()).jsonCodec(codec).build();
                    }
                    EvaluateResponse r = api.evaluate(c.request());
                    // metadata (request id, timing) is per call, not part of the answer
                    Files.writeString(file, codec.writeValueAsPrettyString(new EvaluateResponse(r.model(), r.answers(), r.usage(), null)));
                }
                result.add(codec.readValue(Files.readString(file), EvaluateResponse.class));
            }
        } finally {
            if (api != null) {
                api.close();
            }
        }
        return result;
    }

    static double mae(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum / a.length;
    }

    static double pearson(double[] a, double[] b) {
        double ma = 0, mb = 0;
        for (int i = 0; i < a.length; i++) {
            ma += a[i] / a.length;
            mb += b[i] / b.length;
        }
        double cov = 0, va = 0, vb = 0;
        for (int i = 0; i < a.length; i++) {
            cov += (a[i] - ma) * (b[i] - mb);
            va += (a[i] - ma) * (a[i] - ma);
            vb += (b[i] - mb) * (b[i] - mb);
        }
        return cov / Math.sqrt(va * vb);
    }
}
