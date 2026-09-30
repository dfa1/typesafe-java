package io.github.dfa1.typesafe.core;

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

    /** @param fields named fields, e.g. a message alongside an order id */
    static Fields fields(Map<String, Object> fields) {
        return new Fields(Map.copyOf(fields));
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
