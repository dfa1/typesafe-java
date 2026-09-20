package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.RequestModel;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * A model comes back from the wire two ways: a bare string (the {@code model} field of an
 * evaluate response, e.g. {@code "jev-latest"}) or a full object (an entry in {@code /v1/models}'
 * listing, with {@code description}/{@code release_date}).
 */
final class PinnedDeserializer extends JsonDeserializer<RequestModel.Pinned> {

    @Override
    public RequestModel.Pinned deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            return new RequestModel.Pinned(node.asText(), null, null);
        }
        return new RequestModel.Pinned(
                node.path("name").asText(null),
                node.path("description").asText(null),
                node.path("release_date").asText(null));
    }
}
