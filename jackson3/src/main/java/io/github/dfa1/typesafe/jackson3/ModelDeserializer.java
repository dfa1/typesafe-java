package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Model;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * A model comes back from the wire two ways: a bare string (the {@code model} field of an
 * evaluate response, e.g. {@code "jev-latest"}) or a full object (an entry in {@code /v1/models}'
 * listing, with {@code description}/{@code release_date}).
 */
final class ModelDeserializer extends ValueDeserializer<Model> {

    @Override
    public Model deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);
        if (node.isString()) {
            return new Model(node.asString(), null, null);
        }
        return new Model(
                node.path("name").asString(null),
                node.path("description").asString(null),
                node.path("release_date").asString(null));
    }
}
