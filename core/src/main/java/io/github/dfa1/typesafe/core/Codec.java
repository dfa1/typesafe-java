package io.github.dfa1.typesafe.core;

/**
 * Serialization of the model to and from bytes, plugged in by a codec module, with no {@code String}
 * in between. The ones shipped (typesafe-java-codec-jackson2, -jackson3) write UTF-8 JSON, which is
 * what typesafe-java-client-http sends, since the TypeSafe API speaks JSON. Implementations are
 * discovered via {@link java.util.ServiceLoader}.
 */
public interface Codec {

    byte[] writeValueAsBytes(Object value);

    <T> T readValue(byte[] content, Class<T> type);
}
