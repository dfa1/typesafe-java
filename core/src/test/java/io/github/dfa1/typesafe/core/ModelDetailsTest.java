package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDetailsTest {

    @Test
    void modelReturnsAPinnableModelById() {
        // Given
        ModelDetails sut = new ModelDetails("jev-1.13.0", "TypeSafe's flagship System One model.", "2026-01-01");

        // When
        Model result = sut.model();

        // Then
        assertThat(result).isEqualTo(new Model("jev-1.13.0"));
    }
}
