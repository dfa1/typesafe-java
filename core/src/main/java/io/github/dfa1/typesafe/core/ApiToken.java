package io.github.dfa1.typesafe.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record ApiToken(String value) {

    private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".typesafe.apitoken");
    private static final String ENV_VAR = "TYPESAFE_API_TOKEN";

    public ApiToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("API token must not be blank");
        }
    }

    public static ApiToken fromFile(Path path) throws IOException {
        return new ApiToken(Files.readString(path).strip());
    }

    public static ApiToken fromDefaultFile() throws IOException {
        return fromFile(DEFAULT_PATH);
    }

    public static ApiToken fromEnv() {
        String value = System.getenv(ENV_VAR);
        if (value == null) {
            throw new IllegalStateException(ENV_VAR + " is not set");
        }
        return new ApiToken(value);
    }

    public String toHttpHeaderValue() {
        return "Bearer " + value;
    }

    @Override
    public String toString() {
        return "ApiToken(****)";
    }
}
