package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestModelTest {

    @Test
    void aliasLatestIdIsTheWireValue() {
        // When
        String result = RequestModel.Alias.LATEST.id();

        // Then
        assertThat(result).isEqualTo("jev-latest");
    }

    @Test
    void aliasPreviewIdIsTheWireValue() {
        // When
        String result = RequestModel.Alias.PREVIEW.id();

        // Then
        assertThat(result).isEqualTo("jev-preview");
    }
}
