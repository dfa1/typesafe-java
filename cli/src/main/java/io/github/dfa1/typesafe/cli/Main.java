package io.github.dfa1.typesafe.cli;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;
import io.github.dfa1.typesafe.jackson3.Jackson3Codec;

import java.nio.charset.StandardCharsets;
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
            + "[--verbose] [--timing] | --version "
            + "(name defaults to noul/choice/score, so name it explicitly if you use more than one)";

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

        TypesafeClient client = TypesafeClient.withDefaultToken();
        EvaluateResponse response = client.evaluate(EvaluateRequest.of(State.text(state), model, questions));

        if (verbose) {
            System.err.println("request-id: " + response.metadata().requestId());
        }
        if (timing) {
            System.err.println("time: " + response.metadata().upstreamServiceTime());
        }
        System.out.println(new String(new Jackson3Codec().writeValueAsBytes(response), StandardCharsets.UTF_8));
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
