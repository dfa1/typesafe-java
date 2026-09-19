package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson2CodecTest {

    private final Jackson2Codec sut = new Jackson2Codec();

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
        String result = new String(sut.writeValueAsBytes(request), StandardCharsets.UTF_8);

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
        assertThat(new String(sut.writeValueAsBytes(State.text("hi")), StandardCharsets.UTF_8))
                .isEqualTo("\"hi\"");
        assertThat(new String(sut.writeValueAsBytes(State.fields(Map.of("order_id", "A-104"))), StandardCharsets.UTF_8))
                .isEqualTo("{\"order_id\":\"A-104\"}");
        assertThat(new String(sut.writeValueAsBytes(State.messages(List.of("hi", "there"))), StandardCharsets.UTF_8))
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
        EvaluateResponse result = sut.readValue(json.getBytes(StandardCharsets.UTF_8), EvaluateResponse.class);

        // Then
        assertThat(result.usage().inputTokens()).isEqualTo(312);
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class,
                noul -> assertThat(noul.noul()).isEqualTo(0.92));
        assertThat(result.answers().get("department")).isInstanceOfSatisfying(Answer.Choice.class,
                choice -> assertThat(choice.choice()).isEqualTo("technical"));
        assertThat(result.answers().get("frustration")).isInstanceOfSatisfying(Answer.Score.class,
                score -> assertThat(score.score()).isEqualTo(1.6));
    }
}
