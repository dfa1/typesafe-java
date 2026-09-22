package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyTest {

    @Test
    void loadsAndTrimsKeyFromFile(@TempDir Path dir) throws IOException {
        // Given
        Path file = dir.resolve(".typesafe.apikey");
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

    @Test
    void fromDefaultFileReadsTheHomeDirectoryToken() throws IOException {
        // Given
        Path defaultPath = Path.of(System.getProperty("user.home"), ".typesafe.apikey");

        if (Files.exists(defaultPath)) {
            // When
            ApiKey result = ApiKey.fromDefaultFile();

            // Then
            assertThat(result.value()).isEqualTo(Files.readString(defaultPath).strip());
        } else {
            // When / Then
            assertThatThrownBy(ApiKey::fromDefaultFile).isInstanceOf(IOException.class);
        }
    }

    @Test
    @SetEnvironmentVariable(key = "TYPESAFE_API_KEY", value = "apikey_dummy_test_value")
    void fromEnvReadsTheEnvironmentVariableWhenSet() {
        // When
        ApiKey result = ApiKey.fromEnv();

        // Then
        assertThat(result.value()).isEqualTo("apikey_dummy_test_value");
    }
}
