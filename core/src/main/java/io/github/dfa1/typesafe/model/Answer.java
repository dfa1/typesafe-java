package io.github.dfa1.typesafe.model;

import java.util.Map;

public sealed interface Answer permits Answer.Noul, Answer.Choice, Answer.Score {

    record Noul(double noul) implements Answer {
    }

    record Choice(String choice, Map<String, Double> probabilities, double confidence) implements Answer {
    }

    record Score(double score, Map<String, String> legend, Map<String, Double> probabilities, double confidence)
            implements Answer {
    }
}
