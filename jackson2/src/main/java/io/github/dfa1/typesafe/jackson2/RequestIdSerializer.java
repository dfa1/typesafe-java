package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.RequestId;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/** RequestId has no {@code type} discriminator on the wire — it's written as its bare {@code value}. */
final class RequestIdSerializer extends JsonSerializer<RequestId> {

    @Override
    public void serialize(RequestId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.value());
    }
}
