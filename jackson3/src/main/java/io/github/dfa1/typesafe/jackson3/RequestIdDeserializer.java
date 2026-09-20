package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.RequestId;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** RequestId has no {@code type} discriminator on the wire — it's read from its bare {@code value}. */
final class RequestIdDeserializer extends ValueDeserializer<RequestId> {

    @Override
    public RequestId deserialize(JsonParser p, DeserializationContext ctxt) {
        return new RequestId(p.getString());
    }
}
