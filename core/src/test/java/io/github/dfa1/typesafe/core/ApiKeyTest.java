package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void fromDefaultFileReadsTheHomeDirectoryToken() throws IOException {
        // Given
        Path defaultPath = Path.of(System.getProperty("user.home"), ".typesafe.apitoken");

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
    void fromEnvReadsTheEnvironmentVariableWhenSet() throws IOException, InterruptedException {
        // Given: fromEnv() reads the real process environment, which this JVM can't safely
        // mutate, so exercise the success path in a child process with the var set instead.
        // Carries over this JVM's own -javaagent (JaCoCo, when the coverage profile is
        // active) so the child's execution counts toward coverage too.
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:") && arg.contains("jacoco"))
                .forEach(command::add);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ApiKeyFromEnvProbe.class.getName());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("TYPESAFE_API_KEY", "apikey_dummy_test_value");
        processBuilder.redirectErrorStream(true);

        // When
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        // Then
        assertThat(exitCode).isZero();
        assertThat(output).isEqualTo("apikey_dummy_test_value");
    }
}
