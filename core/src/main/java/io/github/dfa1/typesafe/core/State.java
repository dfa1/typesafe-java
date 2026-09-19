package io.github.dfa1.typesafe.core;

import java.util.List;
import java.util.Map;

/**
 * The context an {@link EvaluateRequest} evaluates its questions against. Exactly the three
 * shapes documented at <a href="https://docs.typesafe.ai/concepts/state">docs.typesafe.ai/concepts/state</a>:
 * a string, a JSON object, or an array of text values. Serialized as that raw shape, with no
 * {@code type} discriminator — see the codec modules for how each writes it.
 */
public sealed interface State permits State.Text, State.Fields, State.Messages {

    static Text text(String value) {
        return new Text(value);
    }

    static Fields fields(Map<String, Object> fields) {
        return new Fields(Map.copyOf(fields));
    }

    static Messages messages(List<String> values) {
        return new Messages(List.copyOf(values));
    }

    /** A plain string, e.g. {@code "My card was charged twice."}. */
    record Text(String value) implements State {
    }

    /** Named fields, related records, or application state, e.g. order id alongside a message. */
    record Fields(Map<String, Object> fields) implements State {
    }

    /** A sequence of text values, e.g. a conversation's messages in order. */
    record Messages(List<String> values) implements State {
    }
}
