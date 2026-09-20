package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.RequestId;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** RequestId has no {@code type} discriminator on the wire — it's written as its bare {@code value}. */
final class RequestIdSerializer extends ValueSerializer<RequestId> {

    @Override
    public void serialize(RequestId value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.value());
    }
}
