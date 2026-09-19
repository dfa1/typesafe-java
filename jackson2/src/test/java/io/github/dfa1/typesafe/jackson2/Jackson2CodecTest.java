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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson2CodecTest {

    private final Jackson2Codec codec = new Jackson2Codec();

    @Test
    void serializesEachQuestionTypeWithItsDiscriminator() {
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

        String json = new String(codec.writeValueAsBytes(request), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"type\":\"noul\""));
        assertTrue(json.contains("\"type\":\"choice\""));
        assertTrue(json.contains("\"type\":\"score\""));
        assertTrue(json.contains("\"model\":\"jev-latest\""));
        assertTrue(json.contains("\"state\":\"Help! My payouts have been failing for 3 days.\""));
    }

    @Test
    void serializesEachStateShapeAsItsRawJsonType() {
        assertEquals("\"hi\"",
                new String(codec.writeValueAsBytes(State.text("hi")), StandardCharsets.UTF_8));
        assertEquals("{\"order_id\":\"A-104\"}",
                new String(codec.writeValueAsBytes(State.fields(Map.of("order_id", "A-104"))), StandardCharsets.UTF_8));
        assertEquals("[\"hi\",\"there\"]",
                new String(codec.writeValueAsBytes(State.messages(List.of("hi", "there"))), StandardCharsets.UTF_8));
    }

    @Test
    void deserializesEachAnswerTypeFromItsDiscriminator() {
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

        EvaluateResponse response = codec.readValue(json.getBytes(StandardCharsets.UTF_8), EvaluateResponse.class);

        assertEquals(312, response.usage().inputTokens());
        assertInstanceOf(Answer.Noul.class, response.answers().get("is_urgent"));
        assertEquals(0.92, ((Answer.Noul) response.answers().get("is_urgent")).noul());
        assertInstanceOf(Answer.Choice.class, response.answers().get("department"));
        assertEquals("technical", ((Answer.Choice) response.answers().get("department")).choice());
        assertInstanceOf(Answer.Score.class, response.answers().get("frustration"));
        assertEquals(1.6, ((Answer.Score) response.answers().get("frustration")).score());
    }
}
