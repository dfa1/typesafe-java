package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeExceptionTest {

    @Test
    void exposesStatusCodeAndBodyAndBuildsAMessageFromThem() {
        // When
        TypeSafeException result = new TypeSafeException(500, "server error");

        // Then
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(result.body()).isEqualTo("server error");
        assertThat(result.getMessage()).isEqualTo("TypeSafe API error 500: server error");
    }

    @Test
    void rateLimitExposesTheRetryAfterHeaderWhenTheServerSentOne() {
        // When
        TypeSafeException.RateLimit result = new TypeSafeException.RateLimit("slow down", Duration.ofSeconds(2));

        // Then
        assertThat(result.statusCode()).isEqualTo(429);
        assertThat(result.retryAfter()).contains(Duration.ofSeconds(2));
    }

    @Test
    void rateLimitRetryAfterIsEmptyWhenTheServerDidNotSendOne() {
        // When
        TypeSafeException.RateLimit result = new TypeSafeException.RateLimit("slow down", null);

        // Then
        assertThat(result.retryAfter()).isEmpty();
    }

    @Test
    void responseDecodingWrapsTheCodecFailureAsItsCause() {
        // Given
        RuntimeException codecFailure = new RuntimeException("unexpected token");

        // When
        TypeSafeException.ResponseDecoding result = new TypeSafeException.ResponseDecoding("not json", codecFailure);

        // Then
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("not json");
        assertThat(result.getCause()).isSameAs(codecFailure);
    }

    @Test
    void connectionHasNoStatusCodeOrBodySinceThereWasNoHttpResponse() {
        // Given
        IOException transportFailure = new IOException("connection reset");

        // When
        TypeSafeException.Connection result = new TypeSafeException.Connection(transportFailure);

        // Then
        assertThat(result.statusCode()).isEqualTo(-1);
        assertThat(result.body()).isNull();
        assertThat(result.getCause()).isSameAs(transportFailure);
    }

    @Test
    void timeoutIsAConnectionFailure() {
        // Given
        IOException transportFailure = new HttpTimeoutException("timed out");

        // When
        TypeSafeException.Timeout result = new TypeSafeException.Timeout(transportFailure);

        // Then
        assertThat(result).isInstanceOf(TypeSafeException.Connection.class);
        assertThat(result.getCause()).isSameAs(transportFailure);
    }

    @Test
    void interruptedWrapsTheInterruptedExceptionAsItsCause() {
        // Given
        InterruptedException interruption = new InterruptedException();

        // When
        TypeSafeException.Interrupted result = new TypeSafeException.Interrupted(interruption);

        // Then
        assertThat(result.statusCode()).isEqualTo(-1);
        assertThat(result.body()).isNull();
        assertThat(result.getCause()).isSameAs(interruption);
    }
}
