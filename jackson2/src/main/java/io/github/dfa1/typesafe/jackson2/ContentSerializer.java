package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Content;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Content has no {@code type} discriminator on the wire — it's written as whichever raw JSON
 * shape (string, object, or array) the variant represents.
 */
final class ContentSerializer extends JsonSerializer<Content> {

    @Override
    public void serialize(Content value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        switch (value) {
            case Content.Text(String text) -> gen.writeString(text);
            case Content.Fields(var fields) -> gen.writeObject(fields);
            case Content.Messages(var messages) -> gen.writeObject(messages);
        }
    }
}
