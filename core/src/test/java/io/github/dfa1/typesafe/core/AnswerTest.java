package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerTest {

    @Test
    void noulCarriesItsProbability() {
        // When
        Answer.Noul result = new Answer.Noul(0.92);

        // Then
        assertThat(result.noul()).isEqualTo(0.92);
    }

    @Test
    void choiceCarriesItsPickProbabilitiesAndConfidence() {
        // When
        Answer.Choice result = new Answer.Choice("billing", Map.of("billing", 1.0), 1.0);

        // Then
        assertThat(result.choice()).isEqualTo("billing");
        assertThat(result.probabilities()).containsEntry("billing", 1.0);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void scoreCarriesItsValueLegendProbabilitiesAndConfidence() {
        // When
        Answer.Score result = new Answer.Score(1.6, Map.of("0", "Calm"), Map.of("0", 1.0), 0.78);

        // Then
        assertThat(result.score()).isEqualTo(1.6);
        assertThat(result.legend()).containsEntry("0", "Calm");
        assertThat(result.probabilities()).containsEntry("0", 1.0);
        assertThat(result.confidence()).isEqualTo(0.78);
    }
}
