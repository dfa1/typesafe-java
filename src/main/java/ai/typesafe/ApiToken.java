package ai.typesafe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record ApiToken(String value) {

    private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".typesafe.apitoken");

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

    public String toHttpHeaderValue() {
        return "Bearer " + value;
    }

    @Override
    public String toString() {
        return "ApiToken(****)";
    }
}
