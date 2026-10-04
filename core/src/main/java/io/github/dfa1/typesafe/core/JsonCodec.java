package io.github.dfa1.typesafe.core;

/**
 * JSON serialization plugged in by a codec module (typesafe-java-codec-jackson2 or -jackson3), as
 * UTF-8 bytes: what goes over the wire, with no {@code String} in between. Implementations are
 * discovered via {@link java.util.ServiceLoader}.
 */
public interface JsonCodec {

    byte[] writeValueAsBytes(Object value);

    /** Same as {@link #writeValueAsBytes(Object)}, indented for human reading. */
    byte[] writeValueAsPrettyBytes(Object value);

    <T> T readValue(byte[] content, Class<T> type);
}
