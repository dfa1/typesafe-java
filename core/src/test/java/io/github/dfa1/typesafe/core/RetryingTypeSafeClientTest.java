package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RetryingTypeSafeClientTest {

    private static final EvaluateRequest REQUEST = EvaluateRequest.of(Content.text("hi"), Map.of());
    private static final EvaluateResponse RESPONSE = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);

    @Mock
    private TypeSafeClient delegate;

    @Test
    void evaluateRetriesARetryableExceptionThenSucceeds() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);
        given(delegate.evaluate(REQUEST))
                .willThrow(new TypeSafeException.RateLimit("slow down", null))
                .willThrow(new TypeSafeException.Connection(new IOException("reset")))
                .willReturn(RESPONSE);

        // When
        EvaluateResponse result = sut.evaluate(REQUEST);

        // Then
        assertThat(result).isSameAs(RESPONSE);
        then(delegate).should(times(3)).evaluate(REQUEST);
    }

    @Test
    void evaluateDoesNotRetryANonRetryableException() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);
        TypeSafeException badRequest = new TypeSafeException.BadRequest("nope");
        given(delegate.evaluate(REQUEST)).willThrow(badRequest);

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(REQUEST)).isSameAs(badRequest);
        then(delegate).should(times(1)).evaluate(REQUEST);
    }

    @Test
    void evaluateThrowsTheLastExceptionAfterExhaustingRetries() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 2, Duration.ZERO);
        TypeSafeException overloaded = new TypeSafeException.InternalServer(529, "overloaded");
        given(delegate.evaluate(REQUEST)).willThrow(overloaded);

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(REQUEST)).isSameAs(overloaded);
        then(delegate).should(times(3)).evaluate(REQUEST);
    }

    @Test
    void evaluateAsyncRetriesARetryableFailureThenSucceeds() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);
        given(delegate.evaluateAsync(REQUEST))
                .willReturn(CompletableFuture.failedFuture(new TypeSafeException.InternalServer(503, "unavailable")))
                .willReturn(CompletableFuture.completedFuture(RESPONSE));

        // When
        EvaluateResponse result = sut.evaluateAsync(REQUEST).join();

        // Then
        assertThat(result).isSameAs(RESPONSE);
        then(delegate).should(times(2)).evaluateAsync(REQUEST);
    }

    @Test
    void evaluateAsyncFailsWithoutRetryingANonTypeSafeException() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);
        RuntimeException encodingFailure = new RuntimeException("boom");
        given(delegate.evaluateAsync(REQUEST)).willReturn(CompletableFuture.failedFuture(encodingFailure));

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(REQUEST).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(encodingFailure);
        then(delegate).should(times(1)).evaluateAsync(REQUEST);
    }

    @Test
    void evaluateAsyncFailsWithTheLastExceptionAfterExhaustingRetries() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 1, Duration.ZERO);
        TypeSafeException timeout = new TypeSafeException.Timeout(new IOException("timed out"));
        given(delegate.evaluateAsync(REQUEST)).willReturn(CompletableFuture.failedFuture(timeout));

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(REQUEST).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(timeout);
        then(delegate).should(times(2)).evaluateAsync(REQUEST);
    }

    @Test
    void listModelsRetriesARetryableExceptionThenSucceeds() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);
        List<ModelDetails> models = List.of();
        given(delegate.listModels())
                .willThrow(new TypeSafeException(408, "request timeout"))
                .willReturn(models);

        // When
        List<ModelDetails> result = sut.listModels();

        // Then
        assertThat(result).isSameAs(models);
    }

    @Test
    void closeClosesTheDelegate() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ZERO);

        // When
        sut.close();

        // Then
        then(delegate).should().close();
    }

    @Test
    void isRetryableAcceptsConnectionFailuresRequestTimeoutRateLimitAndAnyServerError() {
        // When / Then
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.Connection(new IOException()))).isTrue();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.Timeout(new IOException()))).isTrue();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException(408, ""))).isTrue();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.RateLimit("", null))).isTrue();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.InternalServer(500, ""))).isTrue();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.InternalServer(599, ""))).isTrue();
    }

    @Test
    void isRetryableRejectsClientErrorsDecodingFailuresAndInterruption() {
        // When / Then
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.BadRequest(""))).isFalse();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.NotFound(""))).isFalse();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.ResponseDecoding("", new RuntimeException()))).isFalse();
        assertThat(RetryingTypeSafeClient.isRetryable(new TypeSafeException.Interrupted(new InterruptedException()))).isFalse();
    }

    @Test
    void backoffForUsesRetryAfterFromARateLimit() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ofSeconds(10));

        // When
        Duration result = sut.backoffFor(new TypeSafeException.RateLimit("", Duration.ofSeconds(1)), 0);

        // Then
        assertThat(result).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void backoffForUsesRetryAfterFromAnInternalServerError() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ofSeconds(10));

        // When
        Duration result = sut.backoffFor(new TypeSafeException.InternalServer(503, "", Duration.ofSeconds(2)), 0);

        // Then
        assertThat(result).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void backoffForFallsBackToExponentialWhenNoRetryAfterIsPresent() {
        // Given
        RetryingTypeSafeClient sut = new RetryingTypeSafeClient(delegate, 5, Duration.ofMillis(100));

        // When
        Duration result = sut.backoffFor(new TypeSafeException.RateLimit("", null), 2);

        // Then
        assertThat(result).isEqualTo(Duration.ofMillis(400));
    }
}
