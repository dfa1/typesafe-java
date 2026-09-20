package io.github.dfa1.typesafe.transport;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTransportResponseTest {

    @Test
    void equalsAndHashCodeConsiderBodyContentNotArrayIdentity() {
        // Given
        HttpTransportResponse sut = new HttpTransportResponse(
                200, Map.of("x", "y"), "body".getBytes(StandardCharsets.UTF_8));
        HttpTransportResponse other = new HttpTransportResponse(
                200, Map.of("x", "y"), "body".getBytes(StandardCharsets.UTF_8));

        // When / Then
        assertThat(sut).isEqualTo(other);
        assertThat(sut).hasSameHashCodeAs(other);
    }

    @Test
    void notEqualWhenBodyContentDiffers() {
        // Given
        HttpTransportResponse sut = new HttpTransportResponse(
                200, Map.of(), "one".getBytes(StandardCharsets.UTF_8));
        HttpTransportResponse other = new HttpTransportResponse(
                200, Map.of(), "two".getBytes(StandardCharsets.UTF_8));

        // When / Then
        assertThat(sut).isNotEqualTo(other);
    }

    @Test
    void toStringDoesNotDumpRawArrayReference() {
        // Given
        HttpTransportResponse sut = new HttpTransportResponse(
                200, Map.of(), "body".getBytes(StandardCharsets.UTF_8));

        // When
        String result = sut.toString();

        // Then
        assertThat(result).contains("4 bytes").doesNotContain("[B@");
    }
}
