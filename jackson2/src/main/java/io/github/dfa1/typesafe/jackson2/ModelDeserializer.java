package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Model;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** Model has no {@code type} discriminator on the wire — it's read from its bare {@code name}. */
final class ModelDeserializer extends JsonDeserializer<Model> {

    @Override
    public Model deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return new Model(p.getValueAsString());
    }
}
