package io.github.dfa1.typesafe.codec.jackson2;

import java.nio.charset.StandardCharsets;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.RequestId;
import io.github.dfa1.typesafe.core.Content;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class Jackson2CodecTest {

    private final Jackson2Codec sut = new Jackson2Codec();

    @Test
    void serializesEachQuestionTypeWithItsDiscriminator() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(
                Content.text("Help! My payouts have been failing for 3 days."),
                Map.of(
                        "is_urgent", Question.noul("Does this convey urgency?",
                                Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed")),
                        "department", Question.choice("Which team should handle this?",
                                Map.of("billing", "Payments, invoicing, refunds")),
                        "frustration", Question.score("How frustrated is the customer?",
                                List.of("Calm", "Frustrated", "Very angry"))
                ));

        // When
        String result = json(sut.writeValueAsBytes(request));

        // Then
        assertThat(result)
                .contains("\"type\":\"noul\"")
                .contains("\"type\":\"choice\"")
                .contains("\"type\":\"score\"")
                .contains("\"model\":\"jev-latest\"")
                .contains("\"state\":\"Help! My payouts have been failing for 3 days.\"");
    }

    @Test
    void serializesStructuredInstructionsAsARawJsonObject() {
        // Given
        Question.Noul question = Question.noul(Content.fields(Map.of(
                "potential_duplicate", Map.of("name", "John Smith"),
                "question", "Is the resume for the same person as `potential_duplicate`?")));

        // When
        String result = json(sut.writeValueAsBytes(question));

        // Then
        assertThat(result)
                .contains("\"instructions\":{")
                .contains("\"potential_duplicate\":{\"name\":\"John Smith\"}")
                .doesNotContain("\"instructions\":\"");
    }

    @Test
    void serializesEachStateShapeAsItsRawJsonType() {
        // When / Then
        assertThat(json(sut.writeValueAsBytes(Content.text("hi")))).isEqualTo("\"hi\"");
        assertThat(json(sut.writeValueAsBytes(Content.fields(Map.of("order_id", "A-104")))))
                .isEqualTo("{\"order_id\":\"A-104\"}");
        assertThat(json(sut.writeValueAsBytes(Content.messages(List.of("hi", "there")))))
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
        EvaluateResponse result = sut.readValue(bytes(json), EvaluateResponse.class);

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
        assertThat(result.usage().inputTokens()).isEqualTo(312);
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class,
                noul -> assertThat(noul.noul()).isEqualTo(0.92));
        assertThat(result.answers().get("department")).isInstanceOfSatisfying(Answer.Choice.class,
                choice -> assertThat(choice.choice()).isEqualTo("technical"));
        assertThat(result.answers().get("frustration")).isInstanceOfSatisfying(Answer.Score.class,
                score -> assertThat(score.score()).isEqualTo(1.6));
    }

    @Test
    void deserializesModelDetailsFromTheModelsListingShape() {
        // Given
        String json = """
                {"name": "jev-1.13.0", "description": "System One model.", "release_date": "2026-01-01"}
                """;

        // When
        ModelDetails result = sut.readValue(bytes(json), ModelDetails.class);

        // Then
        assertThat(result).isEqualTo(new ModelDetails("jev-1.13.0", "System One model.", "2026-01-01"));
    }

    @Test
    void ignoresFieldsItDoesNotKnow() {
        // Given
        String json = """
                {
                  "model": "jev-latest",
                  "answers": { "is_urgent": { "type": "noul", "noul": 0.92, "rationale": "new field" } },
                  "usage": { "input_tokens": 312, "output_tokens": 0, "latency_ms": 540 },
                  "region": "eu"
                }
                """;

        // When
        EvaluateResponse result = sut.readValue(bytes(json), EvaluateResponse.class);

        // Then
        assertThat(result.usage().inputTokens()).isEqualTo(312);
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class,
                noul -> assertThat(noul.noul()).isEqualTo(0.92));
    }

    @Test
    void serializesAndDeserializesRequestIdAsItsBareValue() {
        // Given
        RequestId requestId = new RequestId("req_01a0c08d990e7e44ba9a80416308258a");

        // When
        String result = json(sut.writeValueAsBytes(requestId));

        // Then
        assertThat(result).isEqualTo("\"req_01a0c08d990e7e44ba9a80416308258a\"");
        assertThat(sut.readValue(bytes(result), RequestId.class)).isEqualTo(requestId);
    }

    @Test
    void writeValueAsStringWrapsAJsonProcessingExceptionInAnUncheckedIOException() {
        // Given
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getValue() {
                throw new RuntimeException("boom");
            }
        };

        // When / Then
        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> json(sut.writeValueAsBytes(unserializable)));
    }

    @Test
    void readValueWrapsAJsonProcessingExceptionInAnUncheckedIOException() {
        // When / Then
        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> sut.readValue(bytes("not json"), EvaluateResponse.class));
    }

    @Test
    void writesCharactersOutsideTheBmpAsTheyAreNotAsEscapedSurrogates() {
        // When
        String result = json(sut.writeValueAsBytes(Content.text("refund now \uD83D\uDE21")));

        // Then — a local model tokenizes this text, so the emoji must survive as one UTF-8 character
        assertThat(result).isEqualTo("\"refund now \uD83D\uDE21\"");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String json(byte[] utf8) {
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
