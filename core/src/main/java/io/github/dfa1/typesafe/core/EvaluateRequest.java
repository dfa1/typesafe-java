package io.github.dfa1.typesafe.core;

import java.util.Map;

/**
 * A request to the TypeSafe evaluate endpoint. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 *
 * @param state     the content to evaluate the questions against
 * @param model     which model should process the request
 * @param questions the questions to evaluate, keyed by caller-chosen names; matching answers
 *                  come back under the same keys in {@link EvaluateResponse#answers()}
 */
public record EvaluateRequest(State state, Model model, Map<String, Question> questions) {

    /** Builds a request against {@link Model#LATEST}. */
    public static EvaluateRequest of(State state, Map<String, Question> questions) {
        return of(state, Model.LATEST, questions);
    }

    public static EvaluateRequest of(State state, Model model, Map<String, Question> questions) {
        return new EvaluateRequest(state, model, questions);
    }
}
