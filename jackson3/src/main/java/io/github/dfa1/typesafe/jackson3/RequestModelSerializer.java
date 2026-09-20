package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.RequestModel;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * RequestModel has no {@code type} discriminator on the wire — a request's {@code model} field
 * is just its {@link RequestModel#id()} string, whichever variant it is.
 */
final class RequestModelSerializer extends ValueSerializer<RequestModel> {

    @Override
    public void serialize(RequestModel value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(value.id());
    }
}
