package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.RequestModel;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * RequestModel has no {@code type} discriminator on the wire — a request's {@code model} field
 * is just its {@link RequestModel#id()} string, whichever variant it is.
 */
final class RequestModelSerializer extends JsonSerializer<RequestModel> {

    @Override
    public void serialize(RequestModel value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.id());
    }
}
