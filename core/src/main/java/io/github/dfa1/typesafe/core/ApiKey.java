package io.github.dfa1.typesafe.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record ApiKey(String value) {

    private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".typesafe.apikey");
    private static final String ENV_VAR = "TYPESAFE_API_KEY";

    public ApiKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("API key must not be blank");
        }
    }

    public static ApiKey fromFile(Path path) throws IOException {
        return new ApiKey(Files.readString(path).strip());
    }

    public static ApiKey fromDefaultFile() throws IOException {
        return fromFile(DEFAULT_PATH);
    }

    public static ApiKey fromEnv() {
        String value = System.getenv(ENV_VAR);
        if (value == null) {
            throw new IllegalStateException(ENV_VAR + " is not set");
        }
        return new ApiKey(value);
    }

    public String toHttpHeaderValue() {
        return "Bearer " + value;
    }

    @Override
    public String toString() {
        return "ApiKey(****)";
    }
}
