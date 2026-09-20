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

    @Test
    void pinnedIdIsTheName() {
        // Given
        RequestModel.Pinned sut =
                new RequestModel.Pinned("jev-1.13.0", "TypeSafe's flagship System One model.", "2026-01-01");

        // When
        String result = sut.id();

        // Then
        assertThat(result).isEqualTo("jev-1.13.0");
    }
}
