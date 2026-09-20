package io.github.dfa1.typesafe.core;

import java.util.LinkedHashMap;
import java.util.List;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private State state;
        private Model model = Model.LATEST;
        private final Map<String, Question> questions = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder state(String value) {
            this.state = State.text(value);
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder noul(String name, String instructions) {
            return question(name, Question.noul(instructions));
        }

        public Builder noul(String name, String instructions, Map<String, String> criteria) {
            return question(name, Question.noul(instructions, criteria));
        }

        public Builder choice(String name, String instructions, Map<String, String> criteria) {
            return question(name, Question.choice(instructions, criteria));
        }

        public Builder score(String name, String instructions, List<String> criteria) {
            return question(name, Question.score(instructions, criteria));
        }

        private Builder question(String name, Question question) {
            if (questions.putIfAbsent(name, question) != null) {
                throw new IllegalArgumentException("Duplicate question name: " + name);
            }
            return this;
        }

        public EvaluateRequest build() {
            return new EvaluateRequest(state, model, Map.copyOf(questions));
        }
    }
}
