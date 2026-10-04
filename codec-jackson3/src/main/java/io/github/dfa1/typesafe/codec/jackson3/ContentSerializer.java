package io.github.dfa1.typesafe.codec.jackson3;

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
            case Content.Text(String text) -> gen.writeString(text);
            case Content.Fields(var fields) -> gen.writePOJO(fields);
            case Content.Messages(var messages) -> gen.writePOJO(messages);
        }
    }
}
