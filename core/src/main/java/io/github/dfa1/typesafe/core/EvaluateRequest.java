package io.github.dfa1.typesafe.core;

import java.util.Map;

public record EvaluateRequest(Object state, String model, Map<String, Question> questions) {

    public static EvaluateRequest of(Object state, Map<String, Question> questions) {
        return of(state, Model.LATEST, questions);
    }

    public static EvaluateRequest of(Object state, Model model, Map<String, Question> questions) {
        return new EvaluateRequest(state, model.id(), questions);
    }
}
