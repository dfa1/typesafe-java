package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ApiKeyTest {

    @Test
    void loadsAndTrimsKeyFromFile(@TempDir Path dir) throws IOException {
        // Given
        Path file = dir.resolve(".typesafe.apitoken");
        Files.writeString(file, "apikey_dummy_test_value\n");

        // When
        ApiKey result = ApiKey.fromFile(file);

        // Then
        assertThat(result.value()).isEqualTo("apikey_dummy_test_value");
        assertThat(result.toHttpHeaderValue()).isEqualTo("Bearer apikey_dummy_test_value");
    }

    @Test
    void toStringNeverLeaksTheValue() {
        // Given
        ApiKey sut = new ApiKey("apikey_dummy_test_value");

        // When
        String result = sut.toString();

        // Then
        assertThat(result).doesNotContain("apikey_dummy_test_value");
    }

    @Test
    void rejectsBlankKey() {
        // When / Then
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiKey("  "));
    }

    @Test
    void failsWhenEnvVarNotSet() {
        // When / Then
        assertThatIllegalStateException()
                .isThrownBy(ApiKey::fromEnv)
                .withMessageContaining("TYPESAFE_API_KEY");
    }
}
