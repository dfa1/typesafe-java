package io.github.dfa1.typesafe.local;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class JsonTest {

    @Test
    void parsesEveryValueKind() {
        // When
        Object result = Json.parse(" {\"a\": [1, 2.5, -3e2, true, false, null], \"s\": \"q\\\"\\\\\\n\\u0120\\ud83d\\ude00\", \"o\": {}} ");

        // Then
        assertThat(result).isEqualTo(Map.of("a", java.util.Arrays.asList(1L, 2.5, -300.0, true, false, null),
                "s", "q\"\\\nĠ😀", "o", Map.of()));
    }

    @Test
    void writesLikePythonJsonDumps() {
        // Given
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("from", "anna@acme.com");
        value.put("n", 42);
        value.put("tags", List.of("a", "ç"));
        value.put("quote", "say \"hi\"\n");

        // When
        String result = Json.write(value);

        // Then
        assertThat(result).isEqualTo("{\"from\": \"anna@acme.com\", \"n\": 42, \"tags\": [\"a\", \"ç\"], \"quote\": \"say \\\"hi\\\"\\n\"}");
    }

    @Test
    void rejectsATrailingComma() {
        // When
        Throwable result = catchThrowable(() -> Json.parse("{\"a\": 1,}"));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrailingContent() {
        // When
        Throwable result = catchThrowable(() -> Json.parse("[1] 2"));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class);
    }
}
