package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.RequestId;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** RequestId has no {@code type} discriminator on the wire — it's read from its bare {@code value}. */
final class RequestIdDeserializer extends JsonDeserializer<RequestId> {

    @Override
    public RequestId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return new RequestId(p.getValueAsString());
    }
}
