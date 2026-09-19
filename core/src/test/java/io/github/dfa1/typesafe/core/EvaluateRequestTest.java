package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluateRequestTest {

    @Test
    void defaultsToTheLatestModel() {
        EvaluateRequest request = EvaluateRequest.of(State.text("state"), Map.of());

        assertEquals("jev-latest", request.model());
    }

    @Test
    void picksAnExplicitModel() {
        EvaluateRequest request = EvaluateRequest.of(State.text("state"), Model.PREVIEW, Map.of());

        assertEquals("jev-preview", request.model());
    }
}
