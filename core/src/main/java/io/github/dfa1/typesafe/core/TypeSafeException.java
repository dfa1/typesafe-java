package io.github.dfa1.typesafe.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Base type for every error {@link TypeSafeClient} throws; also the catch-all for a status code
 * with no dedicated subclass below.
 */
public sealed class TypeSafeException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public TypeSafeException(int statusCode, String body) {
        this(statusCode, body, "TypeSafe API error " + statusCode + ": " + body);
    }

    private TypeSafeException(int statusCode, String body, String message) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    public static final class BadRequest extends TypeSafeException {
        BadRequest(String body) {
            super(400, body);
        }
    }

    public static final class Authentication extends TypeSafeException {
        Authentication(String body) {
            super(401, body);
        }
    }

    public static final class PermissionDenied extends TypeSafeException {
        PermissionDenied(String body) {
            super(403, body);
        }
    }

    public static final class NotFound extends TypeSafeException {
        NotFound(String body) {
            super(404, body);
        }
    }

    public static final class UnprocessableEntity extends TypeSafeException {
        UnprocessableEntity(String body) {
            super(422, body);
        }
    }

    /** {@code 429}, with the server's requested backoff when it sent one. */
    public static final class RateLimit extends TypeSafeException {
        private final Duration retryAfter;

        RateLimit(String body, Duration retryAfter) {
            super(429, body);
            this.retryAfter = retryAfter;
        }

        public Optional<Duration> retryAfter() {
            return Optional.ofNullable(retryAfter);
        }
    }

    /** Any {@code 5xx}. */
    public static final class InternalServer extends TypeSafeException {
        InternalServer(int statusCode, String body) {
            super(statusCode, body);
        }
    }

    /** {@code 200}, but the configured {@link io.github.dfa1.typesafe.json.JsonCodec} couldn't
     *  decode the response body. */
    public static final class ResponseDecoding extends TypeSafeException {
        ResponseDecoding(String body, Throwable cause) {
            super(200, body, "TypeSafe API returned a 200 response that couldn't be decoded: " + cause.getMessage());
            initCause(cause);
        }
    }

    /** No HTTP response at all — the transport couldn't reach TypeSafe (refused connection, DNS
     *  failure, reset, ...) even after retries. {@code statusCode()} is {@code -1} and
     *  {@code body()} is {@code null}: there's no response to carry either. {@link Timeout} is
     *  thrown instead when the failure was specifically a timeout. */
    public static non-sealed class Connection extends TypeSafeException {
        Connection(String message, Throwable cause) {
            super(-1, null, message);
            initCause(cause);
        }

        Connection(Throwable cause) {
            this("TypeSafe API connection failed: " + cause.getMessage(), cause);
        }
    }

    /** The request timed out waiting for TypeSafe to respond, after retries were exhausted. */
    public static final class Timeout extends Connection {
        Timeout(Throwable cause) {
            super("TypeSafe API request timed out: " + cause.getMessage(), cause);
        }
    }

    /** The calling thread was interrupted while waiting for a response. The thread's interrupt
     *  status is restored before this is thrown, so code further up the call stack still sees
     *  it. {@code statusCode()} is {@code -1} and {@code body()} is {@code null}: there's no
     *  response to carry either. */
    public static final class Interrupted extends TypeSafeException {
        Interrupted(InterruptedException cause) {
            super(-1, null, "Interrupted while waiting for a TypeSafe API response");
            initCause(cause);
        }
    }
}
