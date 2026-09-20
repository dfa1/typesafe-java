package io.github.dfa1.typesafe.cli;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.ApiKey;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;
import io.github.dfa1.typesafe.jackson3.Jackson3Codec;
import io.github.dfa1.typesafe.jdk.JdkHttpTransport;
import io.github.dfa1.typesafe.json.JsonCodec;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point, built as an uber-jar. Evaluates a single string {@code --state}
 * against any number of {@code --noul}/{@code --choice}/{@code --score} questions. Prints
 * nothing to stdout by default — pass {@code --print <name>} for specific answers, or
 * {@code --verbose} for the full {@link EvaluateResponse} as JSON.
 */
@SuppressWarnings("java:S106") // System.out/err are this CLI's actual output, not application logging
public final class Main {

    private static final String USAGE = "Usage: typesafe --state <text> [--model <id>] "
            + "[--noul [<name>=]<instructions>]... "
            + "[--choice [<name>=]<instructions>|<option1,option2,...>]... "
            + "[--score [<name>=]<instructions>|<level1,level2,...>]... "
            + "[--min <name>=<threshold>]... [--print <name>]... "
            + "[--verbose] [--timing] | --version "
            + "(name defaults to noul/choice/score, so name it explicitly if you use more than one; "
            + "--min compares a noul/score answer's value, exits 1 if any is below its threshold; "
            + "--print prints just that answer's value; without --print, stdout is silent unless "
            + "--verbose, which prints the full response as JSON)";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args, System.out, System.err));
    }

    /** Parses {@code args} and, on success, builds the real client and evaluates. */
    static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        if (args.length == 1 && "--version".equals(args[0])) {
            out.println(Main.class.getPackage().getImplementationVersion());
            return 0;
        }

        ParsedArgs parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException e) {
            return fail(err, e.getMessage());
        }

        Jackson3Codec codec = new Jackson3Codec();
        try (TypesafeClient client = TypesafeClient.builder(ApiKey.fromDefaultFile())
                .jsonCodec(codec)
                .httpTransport(new JdkHttpTransport())
                .build()) {
            return run(client, codec, parsed, out, err);
        }
    }

    /** The evaluate-and-print flow, taking an already-built client so it's testable without a
     *  network call. */
    static int run(TypesafeClient client, JsonCodec codec, ParsedArgs parsed, PrintStream out, PrintStream err)
            throws Exception {
        EvaluateRequest request = EvaluateRequest.of(State.text(parsed.state()), parsed.model(), parsed.questions());

        if (parsed.verbose()) {
            err.println("request: " + codec.writeValueAsString(request));
        }

        EvaluateResponse response = client.evaluate(request);

        if (parsed.verbose()) {
            err.println("response: " + codec.writeValueAsString(response));
            err.println("request-id: " + response.metadata().requestId());
        }
        if (parsed.timing()) {
            err.println("time: " + response.metadata().upstreamServiceTime());
        }
        try {
            if (!parsed.printNames().isEmpty()) {
                parsed.printNames().forEach(name -> out.println(answerValue(response, name)));
            } else if (parsed.verbose()) {
                out.println(codec.writeValueAsString(response));
            }

            List<String> failures = minFailures(response, parsed.minSpecs());
            if (!failures.isEmpty()) {
                failures.forEach(f -> err.println("--min failed: " + f));
                return 1;
            }
            return 0;
        } catch (RuntimeException e) {
            return fail(err, e.getMessage());
        }
    }

    record ParsedArgs(String state, Model model, Map<String, Question> questions, List<String> minSpecs,
            List<String> printNames, boolean verbose, boolean timing) {
    }

    static ParsedArgs parse(String[] args) {
        String state = null;
        Model model = Model.LATEST;
        Map<String, Question> questions = new LinkedHashMap<>();
        List<String> minSpecs = new ArrayList<>();
        List<String> printNames = new ArrayList<>();
        boolean verbose = false;
        boolean timing = false;

        try {
            for (int i = 0; i < args.length; i++) {
                String flag = args[i];

                switch (flag) {
                    case "--verbose" -> verbose = true;
                    case "--timing" -> timing = true;
                    case "--state" -> state = args[++i];
                    case "--model" -> model = new Model(args[++i]);
                    case "--min" -> minSpecs.add(args[++i]);
                    case "--print" -> printNames.add(args[++i]);
                    case "--noul", "--choice", "--score" -> {
                        String value = args[++i];
                        int eq = value.indexOf('=');
                        String name = eq >= 0 ? value.substring(0, eq) : flag.substring(2);
                        String rest = eq >= 0 ? value.substring(eq + 1) : value;
                        questions.put(name, question(flag, rest));
                    }
                    default -> throw new IllegalArgumentException("Unknown flag: " + flag);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Missing value for " + args[args.length - 1]);
        }

        if (state == null) {
            throw new IllegalArgumentException("Missing required --state");
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("At least one --noul/--choice/--score question is required");
        }

        return new ParsedArgs(state, model, questions, minSpecs, printNames, verbose, timing);
    }

    static String answerValue(EvaluateResponse response, String name) {
        return switch (response.answers().get(name)) {
            case Answer.Noul n -> String.valueOf(n.noul());
            case Answer.Choice c -> c.choice();
            case Answer.Score s -> String.valueOf(s.score());
            case null -> throw new IllegalArgumentException("No such answer: " + name);
        };
    }

    static List<String> minFailures(EvaluateResponse response, List<String> minSpecs) {
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

    static Question question(String flag, String rest) {
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

    private static int fail(PrintStream err, String message) {
        err.println(message);
        err.println(USAGE);
        return 1;
    }
}
