package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void idReturnsTheName() {
        // Given
        Model sut = new Model("jev-1.13.0", "TypeSafe's flagship System One model.", "2026-01-01");

        // When
        String result = sut.id();

        // Then
        assertThat(result).isEqualTo("jev-1.13.0");
    }
}
