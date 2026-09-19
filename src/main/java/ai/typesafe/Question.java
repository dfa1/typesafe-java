package ai.typesafe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Question.Noul.class, name = "noul"),
        @JsonSubTypes.Type(value = Question.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = Question.Score.class, name = "score")
})
public sealed interface Question permits Question.Noul, Question.Choice, Question.Score {

    static Noul noul(String instructions, Map<String, String> criteria) {
        return new Noul(instructions, criteria);
    }

    static Choice choice(String instructions, Map<String, String> criteria) {
        return new Choice(instructions, criteria);
    }

    static Score score(String instructions, List<String> criteria) {
        return new Score(instructions, criteria);
    }

    record Noul(String instructions, Map<String, String> criteria) implements Question {
    }

    record Choice(String instructions, Map<String, String> criteria) implements Question {
    }

    record Score(String instructions, List<String> criteria) implements Question {
    }
}
