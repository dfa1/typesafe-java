package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void latestIsTheWireValue() {
        // Then
        assertThat(Model.LATEST.name()).isEqualTo("jev-latest");
    }

    @Test
    void previewIsTheWireValue() {
        // Then
        assertThat(Model.PREVIEW.name()).isEqualTo("jev-preview");
    }

    @Test
    void pinsAnyIdDirectly() {
        // When
        Model result = new Model("jev-1.13.0");

        // Then
        assertThat(result.name()).isEqualTo("jev-1.13.0");
    }
}
