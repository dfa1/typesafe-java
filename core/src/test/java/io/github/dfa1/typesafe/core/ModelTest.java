package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void idReturnsTheWireValue() {
        // When
        String result = Model.PREVIEW.id();

        // Then
        assertThat(result).isEqualTo("jev-preview");
    }

    @Test
    void toStringReturnsTheWireValue() {
        // When
        String result = Model.PREVIEW.toString();

        // Then
        assertThat(result).isEqualTo("jev-preview");
    }
}
