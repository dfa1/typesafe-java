package io.github.dfa1.typesafe.json;

/**
 * JSON serialization plugged in by a codec module (typesafe-jackson2 or typesafe-jackson3).
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface JsonCodec {

    String writeValueAsString(Object value);

    /** Same as {@link #writeValueAsString(Object)}, indented for human reading. */
    String writeValueAsPrettyString(Object value);

    <T> T readValue(String content, Class<T> type);
}
