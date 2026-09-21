package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeExceptionTest {

    @Test
    void exposesStatusCodeAndBodyAndBuildsAMessageFromThem() {
        // When
        TypeSafeException result = new TypeSafeException(500, "server error");

        // Then
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.body()).isEqualTo("server error");
        assertThat(result.getMessage()).isEqualTo("TypeSafe API error 500: server error");
    }
}
