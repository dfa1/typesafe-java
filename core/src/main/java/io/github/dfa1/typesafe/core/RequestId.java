package io.github.dfa1.typesafe.core;

/**
 * The {@code x-typesafe-request-id} response header value, useful when reporting an issue to
 * TypeSafe about a specific request.
 *
 * @param value the raw header value, e.g. {@code "req_..."}
 */
public record RequestId(String value) {
}
