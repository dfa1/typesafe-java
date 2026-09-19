package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.State;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * State has no {@code type} discriminator on the wire — it's written as whichever raw JSON
 * shape (string, object, or array) the variant represents.
 */
final class StateSerializer extends ValueSerializer<State> {

    @Override
    public void serialize(State value, JsonGenerator gen, SerializationContext ctxt) {
        switch (value) {
            case State.Text text -> gen.writeString(text.value());
            case State.Fields fields -> gen.writePOJO(fields.fields());
            case State.Messages messages -> gen.writePOJO(messages.values());
        }
    }
}
