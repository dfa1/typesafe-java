package io.github.dfa1.typesafe.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProbabilitiesTest {

    @Test
    void softmaxSurvivesHugeLogits() {
        // When
        double[] result = Probabilities.softmax(1000f, 1000f);

        // Then
        assertThat(result).containsExactly(0.5, 0.5);
    }

    @Test
    void confidenceIsOneWhenCertain() {
        // When
        double result = Probabilities.confidence(new double[]{1, 0, 0});

        // Then
        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void confidenceIsZeroWhenUniform() {
        // When
        double result = Probabilities.confidence(new double[]{0.25, 0.25, 0.25, 0.25});

        // Then
        assertThat(result).isCloseTo(0.0, within(1e-9));
    }
}
