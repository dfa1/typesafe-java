package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StateTest {

    @Test
    void messagesCopiesTheGivenValues() {
        // Given
        List<String> values = new ArrayList<>(List.of("hi", "there"));

        // When
        State.Messages result = State.messages(values);
        values.add("mutated after the call");

        // Then
        assertThat(result.values()).containsExactly("hi", "there");
    }
}
