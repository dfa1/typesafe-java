package io.github.dfa1.typesafe.core;

import java.util.Map;

/**
 * The answer to one {@link Question}, keyed identically to it in
 * {@link EvaluateResponse#answers()}. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 */
public sealed interface Answer permits Answer.Noul, Answer.Choice, Answer.Score {

    /**
     * Answer to a {@link Question.Noul}.
     *
     * @param noul the probability the answer is affirmative, from 0 (no) to 1 (yes)
     */
    record Noul(double noul) implements Answer {
    }

    /**
     * Answer to a {@link Question.Choice}.
     *
     * @param choice        the highest-probability option
     * @param probabilities every option mapped to its probability (sums to 1)
     * @param confidence    certainty derived from the probability distribution
     */
    record Choice(String choice, Map<String, Double> probabilities, double confidence) implements Answer {
    }

    /**
     * Answer to a {@link Question.Score}.
     *
     * @param score         probability-weighted value across the question's levels; may land
     *                      between two levels
     * @param legend        each level's index (as a string) mapped to its description
     * @param probabilities each level's index (as a string) mapped to its probability
     * @param confidence    certainty derived from the probability distribution
     */
    record Score(double score, Map<String, String> legend, Map<String, Double> probabilities, double confidence)
            implements Answer {
    }
}
