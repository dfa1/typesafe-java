package ai.typesafe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Answer.Noul.class, name = "noul"),
        @JsonSubTypes.Type(value = Answer.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = Answer.Score.class, name = "score")
})
public sealed interface Answer permits Answer.Noul, Answer.Choice, Answer.Score {

    record Noul(double noul) implements Answer {
    }

    record Choice(String choice, Map<String, Double> probabilities, double confidence) implements Answer {
    }

    record Score(double score, Map<String, String> legend, Map<String, Double> probabilities, double confidence)
            implements Answer {
    }
}
