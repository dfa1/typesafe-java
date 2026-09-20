package io.github.dfa1.typesafe.transport;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTransportResponseTest {

    @Test
    void headerLooksUpCaseInsensitively() {
        // Given
        HttpTransportResponse sut = new HttpTransportResponse(200, Map.of("X-Request-Id", "abc"), "body");

        // When
        var result = sut.header("x-request-id");

        // Then
        assertThat(result).contains("abc");
    }

    @Test
    void copiesHeadersDefensivelySoLaterMutationIsNotVisible() {
        // Given
        Map<String, String> headers = new HashMap<>(Map.of("X-A", "1"));
        HttpTransportResponse sut = new HttpTransportResponse(200, headers, "body");

        // When
        headers.put("X-A", "mutated");

        // Then
        assertThat(sut.headers()).containsEntry("X-A", "1");
    }

    @Test
    void headersAreImmutable() {
        // Given
        HttpTransportResponse sut = new HttpTransportResponse(200, Map.of("X-A", "1"), "body");

        // When / Then
        assertThatThrownBy(() -> sut.headers().put("X-B", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
