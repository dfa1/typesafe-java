package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateRequestTest {

    @Test
    void defaultsToTheLatestModel() {
        // When
        EvaluateRequest result = EvaluateRequest.of(State.text("state"), Map.of());

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
    }

    @Test
    void picksAnExplicitModel() {
        // When
        EvaluateRequest result = EvaluateRequest.of(State.text("state"), Model.PREVIEW, Map.of());

        // Then
        assertThat(result.model()).isEqualTo(Model.PREVIEW);
    }
}
