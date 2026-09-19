package io.github.dfa1.typesafe.core;

import java.util.List;
import java.util.Map;

/**
 * The content an {@link EvaluateRequest} evaluates its questions against — "a plain string for
 * text, or structured data (object/array) for things like chat logs, records, or the current
 * state of your application" (<a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>).
 * Exactly the three shapes documented at
 * <a href="https://docs.typesafe.ai/concepts/state">docs.typesafe.ai/concepts/state</a>: a
 * string, a JSON object, or an array of text values. Serialized as that raw shape, with no
 * {@code type} discriminator — see the codec modules for how each writes it.
 */
public sealed interface State permits State.Text, State.Fields, State.Messages {

    /** @param value the content, e.g. {@code "My card was charged twice."} */
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
    record Text(String value) implements State {
    }

    /** Named fields, related records, or application state. */
    record Fields(Map<String, Object> fields) implements State {
    }

    /** A sequence of text values. */
    record Messages(List<String> values) implements State {
    }
}
