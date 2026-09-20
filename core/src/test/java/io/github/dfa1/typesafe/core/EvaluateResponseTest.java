package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateResponseTest {

    private final EvaluateResponse sut = new EvaluateResponse(Model.LATEST, Map.of(
            "urgent", new Answer.Noul(0.5),
            "category", new Answer.Choice("billing", Map.of("billing", 1.0), 1.0),
            "severity", new Answer.Score(2.5, Map.of(), Map.of(), 1.0)),
            new Usage(0, 0), null);

    @Test
    void noulsHoldsOnlyTheNoulAnswers() {
        // When
        Map<String, Answer.Noul> result = sut.nouls();

        // Then
        assertThat(result).containsOnlyKeys("urgent");
        assertThat(result.get("urgent").noul()).isEqualTo(0.5);
    }

    @Test
    void choicesHoldsOnlyTheChoiceAnswers() {
        // When
        Map<String, Answer.Choice> result = sut.choices();

        // Then
        assertThat(result).containsOnlyKeys("category");
        assertThat(result.get("category").choice()).isEqualTo("billing");
    }

    @Test
    void scoresHoldsOnlyTheScoreAnswers() {
        // When
        Map<String, Answer.Score> result = sut.scores();

        // Then
        assertThat(result).containsOnlyKeys("severity");
        assertThat(result.get("severity").score()).isEqualTo(2.5);
    }
}
