package io.github.dfa1.typesafe.model;

import java.util.Map;

public record EvaluateRequest(Object state, String model, Map<String, Question> questions) {

    public static EvaluateRequest of(Object state, Map<String, Question> questions) {
        return new EvaluateRequest(state, "jev-latest", questions);
    }
}
