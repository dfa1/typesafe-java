package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ContentTest {

    /** Hash codes unrelated to this order: Map.copyOf iterates in hash-table order (or its reverse, per JVM), so for
     *  sequential keys like "k0".."k19" it can return insertion order by chance; for these it practically never does. */
    static final List<String> SHUFFLED_KEYS = List.of("kilo", "alpha", "tango", "echo", "zulu", "mike", "bravo", "quebec", "hotel", "xray", "delta", "oscar", "golf", "victor", "juliet", "papa", "lima", "sierra", "charlie", "india");

    @Test
    void messagesCopiesTheGivenValues() {
        // Given
        List<String> values = new ArrayList<>(List.of("hi", "there"));

        // When
        Content.Messages result = Content.messages(values);
        values.add("mutated after the call");

        // Then
        assertThat(result.values()).containsExactly("hi", "there");
    }

    @Test
    void fieldsKeepsTheCallersKeyOrder() {
        // Given
        Map<String, Object> fields = new LinkedHashMap<>();
        SHUFFLED_KEYS.forEach(key -> fields.put(key, key.length()));

        // When
        Content.Fields result = Content.fields(fields);

        // Then
        assertThat(result.fields().keySet()).containsExactlyElementsOf(fields.keySet());
    }

    @Test
    void fieldsCopiesTheGivenMap() {
        // Given
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("order_id", "A-104"));

        // When
        Content.Fields result = Content.fields(fields);
        fields.put("mutated", "after the call");

        // Then
        assertThat(result.fields()).containsOnlyKeys("order_id");
    }

    @Test
    void fieldsRejectsANullValue() {
        // Given
        Map<String, Object> fields = new HashMap<>();
        fields.put("order_id", null);

        // When
        Throwable result = catchThrowable(() -> Content.fields(fields));

        // Then
        assertThat(result).isInstanceOf(NullPointerException.class);
    }
}
