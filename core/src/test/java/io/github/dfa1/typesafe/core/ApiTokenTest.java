package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ApiTokenTest {

    @Test
    void loadsAndTrimsTokenFromFile(@TempDir Path dir) throws IOException {
        // Given
        Path file = dir.resolve(".typesafe.apitoken");
        Files.writeString(file, "apikey_dummy_test_value\n");

        // When
        ApiToken result = ApiToken.fromFile(file);

        // Then
        assertThat(result.value()).isEqualTo("apikey_dummy_test_value");
        assertThat(result.toHttpHeaderValue()).isEqualTo("Bearer apikey_dummy_test_value");
    }

    @Test
    void toStringNeverLeaksTheValue() {
        // Given
        ApiToken sut = new ApiToken("apikey_dummy_test_value");

        // When
        String result = sut.toString();

        // Then
        assertThat(result).doesNotContain("apikey_dummy_test_value");
    }

    @Test
    void rejectsBlankToken() {
        // When / Then
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiToken("  "));
    }

    @Test
    void failsWhenEnvVarNotSet() {
        // When / Then
        assertThatIllegalStateException()
                .isThrownBy(ApiToken::fromEnv)
                .withMessageContaining("TYPESAFE_API_TOKEN");
    }
}
