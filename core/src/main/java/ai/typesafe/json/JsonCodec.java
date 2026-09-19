package ai.typesafe.json;

/**
 * JSON serialization plugged in by a codec module (typesafe-jackson2 or typesafe-jackson3).
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface JsonCodec {

    byte[] writeValueAsBytes(Object value);

    <T> T readValue(byte[] content, Class<T> type);
}
