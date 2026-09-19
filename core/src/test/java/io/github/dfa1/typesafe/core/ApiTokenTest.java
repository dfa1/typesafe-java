package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiTokenTest {

    @Test
    void loadsAndTrimsTokenFromFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(".typesafe.apitoken");
        Files.writeString(file, "apikey_dummy_test_value\n");

        ApiToken token = ApiToken.fromFile(file);

        assertEquals("apikey_dummy_test_value", token.value());
        assertEquals("Bearer apikey_dummy_test_value", token.toHttpHeaderValue());
    }

    @Test
    void toStringNeverLeaksTheValue() {
        ApiToken token = new ApiToken("apikey_dummy_test_value");

        assertFalse(token.toString().contains("apikey_dummy_test_value"));
    }

    @Test
    void rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new ApiToken("  "));
    }
}
