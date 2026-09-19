package io.github.dfa1.typesafe.cli;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;
import io.github.dfa1.typesafe.jackson3.Jackson3Codec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point, built as an uber-jar. Evaluates a single string {@code --state}
 * against any number of {@code --noul}/{@code --choice}/{@code --score} questions and prints
 * the {@link EvaluateResponse} as JSON.
 */
public final class Main {

    private static final String USAGE = "Usage: typesafe --state <text> [--model <id>] "
            + "[--noul [<name>=]<instructions>]... "
            + "[--choice [<name>=]<instructions>|<option1,option2,...>]... "
            + "[--score [<name>=]<instructions>|<level1,level2,...>]... "
            + "[--min <name>=<threshold>]... "
            + "[--verbose] [--timing] | --version "
            + "(name defaults to noul/choice/score, so name it explicitly if you use more than one; "
            + "--min compares a noul/score answer's value, exits 1 if any is below its threshold)";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println(Main.class.getPackage().getImplementationVersion());
            return;
        }

        String state = null;
        Model model = Model.LATEST;
        Map<String, Question> questions = new LinkedHashMap<>();
        List<String> minSpecs = new ArrayList<>();
        boolean verbose = false;
        boolean timing = false;

        try {
            for (int i = 0; i < args.length; i++) {
                String flag = args[i];

                switch (flag) {
                    case "--verbose" -> verbose = true;
                    case "--timing" -> timing = true;
                    case "--state" -> state = args[++i];
                    case "--model" -> model = modelById(args[++i]);
                    case "--min" -> minSpecs.add(args[++i]);
                    case "--noul", "--choice", "--score" -> {
                        String value = args[++i];
                        int eq = value.indexOf('=');
                        String name = eq >= 0 ? value.substring(0, eq) : flag.substring(2);
                        String rest = eq >= 0 ? value.substring(eq + 1) : value;
                        questions.put(name, question(flag, rest));
                    }
                    default -> fail("Unknown flag: " + flag);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            fail("Missing value for " + args[args.length - 1]);
        }

        if (state == null) {
            fail("Missing required --state");
        }
        if (questions.isEmpty()) {
            fail("At least one --noul/--choice/--score question is required");
        }

        Jackson3Codec codec = new Jackson3Codec();
        EvaluateRequest request = EvaluateRequest.of(State.text(state), model, questions);

        if (verbose) {
            System.err.println("request: " + new String(codec.writeValueAsBytes(request), StandardCharsets.UTF_8));
        }

        TypesafeClient client = TypesafeClient.withDefaultToken();
        EvaluateResponse response = client.evaluate(request);

        if (verbose) {
            System.err.println("request-id: " + response.metadata().requestId());
        }
        if (timing) {
            System.err.println("time: " + response.metadata().upstreamServiceTime());
        }
        System.out.println(new String(codec.writeValueAsBytes(response), StandardCharsets.UTF_8));

        try {
            List<String> failures = minFailures(response, minSpecs);
            if (!failures.isEmpty()) {
                failures.forEach(f -> System.err.println("--min failed: " + f));
                System.exit(1);
            }
        } catch (RuntimeException e) {
            fail(e.getMessage());
        }
    }

    private static List<String> minFailures(EvaluateResponse response, List<String> minSpecs) {
        List<String> failures = new ArrayList<>();
        for (String spec : minSpecs) {
            int eq = spec.indexOf('=');
            String name = spec.substring(0, eq);
            double min = Double.parseDouble(spec.substring(eq + 1));
            double value = switch (response.answers().get(name)) {
                case Answer.Noul n -> n.noul();
                case Answer.Score s -> s.score();
                case null -> throw new IllegalArgumentException("No such answer: " + name);
                default -> throw new IllegalArgumentException(
                        "--min " + name + " only applies to noul/score answers");
            };
            if (value < min) {
                failures.add(name + "=" + value + " < " + min);
            }
        }
        return failures;
    }

    private static Question question(String flag, String rest) {
        int bar = rest.indexOf('|');
        String instructions = bar >= 0 ? rest.substring(0, bar) : rest;
        List<String> options = bar >= 0 ? Arrays.asList(rest.substring(bar + 1).split(",")) : List.of();

        return switch (flag) {
            case "--noul" -> Question.noul(instructions, Map.of());
            case "--choice" -> {
                Map<String, String> criteria = new LinkedHashMap<>();
                options.forEach(option -> criteria.put(option, ""));
                yield Question.choice(instructions, criteria);
            }
            case "--score" -> Question.score(instructions, options);
            default -> throw new IllegalStateException(flag);
        };
    }

    private static Model modelById(String id) {
        for (Model model : Model.values()) {
            if (model.id().equals(id)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown model: " + id);
    }

    private static void fail(String message) {
        System.err.println(message);
        System.err.println(USAGE);
        System.exit(1);
    }
}
