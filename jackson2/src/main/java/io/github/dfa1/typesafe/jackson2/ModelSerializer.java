package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Model;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/** Model has no {@code type} discriminator on the wire — it's written as its bare {@code name}. */
final class ModelSerializer extends JsonSerializer<Model> {

    @Override
    public void serialize(Model value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.name());
    }
}
