package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Model;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Model has no {@code type} discriminator on the wire — it's read from its bare {@code name}. */
final class ModelDeserializer extends ValueDeserializer<Model> {

    @Override
    public Model deserialize(JsonParser p, DeserializationContext ctxt) {
        return new Model(p.getString());
    }
}
