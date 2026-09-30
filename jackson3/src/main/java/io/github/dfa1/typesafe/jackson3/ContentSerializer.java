package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Content;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Content has no {@code type} discriminator on the wire — it's written as whichever raw JSON
 * shape (string, object, or array) the variant represents.
 */
final class ContentSerializer extends ValueSerializer<Content> {

    @Override
    public void serialize(Content value, JsonGenerator gen, SerializationContext ctxt) {
        switch (value) {
            case Content.Text text -> gen.writeString(text.value());
            case Content.Fields fields -> gen.writePOJO(fields.fields());
            case Content.Messages messages -> gen.writePOJO(messages.values());
        }
    }
}
