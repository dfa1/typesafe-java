package io.github.dfa1.typesafe.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A string, a JSON object, or an array of text values — the shape shared by an
 * {@link EvaluateRequest}'s {@code state} ("a plain string for text, or structured data
 * (object/array) for things like chat logs, records, or the current state of your application",
 * <a href="https://docs.typesafe.ai/concepts/state">docs.typesafe.ai/concepts/state</a>) and a
 * {@link Question}'s {@code instructions} ("an object can hold the question in one field and
 * data it refers to in others", <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>).
 * Serialized as that raw shape, with no {@code type} discriminator — see the codec modules for
 * how each writes it.
 */
public sealed interface Content permits Content.Text, Content.Fields, Content.Messages {

    /** @param value plain text, e.g. {@code "My card was charged twice."} */
    static Text text(String value) {
        return new Text(value);
    }

    /**
     * @param fields named fields, e.g. a message alongside an order id; copied in the caller's iteration order (a
     *               {@code LinkedHashMap} stays ordered), so the same request serializes the same way on every run —
     *               {@code Map.copyOf} would reshuffle keys per JVM
     * @throws NullPointerException for a {@code null} key or value
     */
    static Fields fields(Map<String, Object> fields) {
        Map<String, Object> copy = new LinkedHashMap<>(fields);
        if (copy.containsKey(null) || copy.containsValue(null)) {
            throw new NullPointerException("fields must not contain null keys or values");
        }
        return new Fields(Collections.unmodifiableMap(copy));
    }

    /** @param values a sequence of text values, e.g. a conversation's messages in order */
    static Messages messages(List<String> values) {
        return new Messages(List.copyOf(values));
    }

    /** A plain string. */
    record Text(String value) implements Content {
    }

    /** Named fields, related records, or application state. */
    record Fields(Map<String, Object> fields) implements Content {
    }

    /** A sequence of text values. */
    record Messages(List<String> values) implements Content {
    }
}
