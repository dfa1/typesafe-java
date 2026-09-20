package io.github.dfa1.typesafe.core;

import java.util.List;
import java.util.Map;

/**
 * One question to evaluate a {@link State} against. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 */
public sealed interface Question permits Question.Noul, Question.Choice, Question.Score {

    static Noul noul(String instructions, Map<String, String> criteria) {
        return new Noul(instructions, criteria);
    }

    /** {@code criteria}-less variant, for a yes/no question that needs no elaboration. */
    static Noul noul(String instructions) {
        return new Noul(instructions, null);
    }

    static Choice choice(String instructions, Map<String, String> criteria) {
        return new Choice(instructions, criteria);
    }

    static Score score(String instructions, List<String> criteria) {
        return new Score(instructions, criteria);
    }

    /**
     * A yes/no question. The matching {@link Answer.Noul} carries the probability the answer
     * is affirmative, on a 0–1 scale.
     *
     * @param instructions the yes/no question itself
     * @param criteria     what {@code "true"}/{@code "false"} mean for this question, keyed by
     *                     those literal strings; optional, may be {@code null}
     */
    record Noul(String instructions, Map<String, String> criteria) implements Question {
    }

    /**
     * A single-select question over a fixed set of options. The matching {@link Answer.Choice}
     * carries the selected option plus a probability distribution over all options.
     *
     * @param instructions the decision the model should make
     * @param criteria     option keys mapped to a description of each (a description may be
     *                     omitted when the key needs none)
     */
    record Choice(String instructions, Map<String, String> criteria) implements Question {
    }

    /**
     * A rubric rating along an ordered scale. The matching {@link Answer.Score} carries a
     * probability-weighted value across the levels — it may land between two levels.
     *
     * @param instructions what to evaluate
     * @param criteria     ordered level descriptions, lowest to highest (at least two)
     */
    record Score(String instructions, List<String> criteria) implements Question {
    }
}
