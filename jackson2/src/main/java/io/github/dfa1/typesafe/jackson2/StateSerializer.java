package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.State;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * State has no {@code type} discriminator on the wire — it's written as whichever raw JSON
 * shape (string, object, or array) the variant represents.
 */
final class StateSerializer extends JsonSerializer<State> {

    @Override
    public void serialize(State value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        switch (value) {
            case State.Text text -> gen.writeString(text.value());
            case State.Fields fields -> gen.writeObject(fields.fields());
            case State.Messages messages -> gen.writeObject(messages.values());
        }
    }
}
