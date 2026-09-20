package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Model;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Model has no {@code type} discriminator on the wire — it's written as its bare {@code name}. */
final class ModelSerializer extends ValueSerializer<Model> {

    @Override
    public void serialize(Model value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.name());
    }
}
