package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson3CodecTest {

    private final Jackson3Codec sut = new Jackson3Codec();

    @Test
    void serializesEachQuestionTypeWithItsDiscriminator() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Help! My payouts have been failing for 3 days."),
                Map.of(
                        "is_urgent", Question.noul("Does this convey urgency?",
                                Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed")),
                        "department", Question.choice("Which team should handle this?",
                                Map.of("billing", "Payments, invoicing, refunds")),
                        "frustration", Question.score("How frustrated is the customer?",
                                List.of("Calm", "Frustrated", "Very angry"))
                ));

        // When
        String result = sut.writeValueAsString(request);

        // Then
        assertThat(result)
                .contains("\"type\":\"noul\"")
                .contains("\"type\":\"choice\"")
                .contains("\"type\":\"score\"")
                .contains("\"model\":\"jev-latest\"")
                .contains("\"state\":\"Help! My payouts have been failing for 3 days.\"");
    }

    @Test
    void serializesEachStateShapeAsItsRawJsonType() {
        // When / Then
        assertThat(sut.writeValueAsString(State.text("hi"))).isEqualTo("\"hi\"");
        assertThat(sut.writeValueAsString(State.fields(Map.of("order_id", "A-104"))))
                .isEqualTo("{\"order_id\":\"A-104\"}");
        assertThat(sut.writeValueAsString(State.messages(List.of("hi", "there"))))
                .isEqualTo("[\"hi\",\"there\"]");
    }

    @Test
    void deserializesEachAnswerTypeFromItsDiscriminator() {
        // Given
        String json = """
                {
                  "model": "jev-latest",
                  "answers": {
                    "is_urgent": { "type": "noul", "noul": 0.92 },
                    "department": { "type": "choice", "choice": "technical",
                      "probabilities": { "billing": 0.08, "technical": 0.85, "sales": 0.07 }, "confidence": 0.82 },
                    "frustration": { "type": "score", "score": 1.6,
                      "legend": { "0": "Calm", "1": "Frustrated", "2": "Very angry" },
                      "probabilities": { "0": 0.05, "1": 0.3, "2": 0.65 }, "confidence": 0.78 }
                  },
                  "usage": { "input_tokens": 312, "output_tokens": 48 }
                }
                """;

        // When
        EvaluateResponse result = sut.readValue(json, EvaluateResponse.class);

        // Then
        assertThat(result.model()).isEqualTo(new Model("jev-latest", null, null));
        assertThat(result.usage().inputTokens()).isEqualTo(312);
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class,
                noul -> assertThat(noul.noul()).isEqualTo(0.92));
        assertThat(result.answers().get("department")).isInstanceOfSatisfying(Answer.Choice.class,
                choice -> assertThat(choice.choice()).isEqualTo("technical"));
        assertThat(result.answers().get("frustration")).isInstanceOfSatisfying(Answer.Score.class,
                score -> assertThat(score.score()).isEqualTo(1.6));
    }

    @Test
    void deserializesAModelObjectFromTheModelsListingShape() {
        // Given
        String json = """
                {"name": "jev-1.13.0", "description": "System One model.", "release_date": "2026-01-01"}
                """;

        // When
        Model result = sut.readValue(json, Model.class);

        // Then
        assertThat(result).isEqualTo(new Model("jev-1.13.0", "System One model.", "2026-01-01"));
    }
}
