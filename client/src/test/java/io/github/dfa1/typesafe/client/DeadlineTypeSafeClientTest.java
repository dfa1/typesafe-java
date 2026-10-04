package io.github.dfa1.typesafe.client;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class DeadlineTypeSafeClientTest {

    private static final EvaluateRequest REQUEST = EvaluateRequest.of(Content.text("hi"), Map.of());
    private static final EvaluateResponse RESPONSE = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
    private static final Duration SHORT_DEADLINE = Duration.ofMillis(50);

    @Mock
    private TypeSafeClient delegate;

    @Test
    void evaluateReturnsTheDelegatesResponseWithinTheDeadline() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, Duration.ofSeconds(5));
        given(delegate.evaluateAsync(REQUEST)).willReturn(CompletableFuture.completedFuture(RESPONSE));

        // When
        EvaluateResponse result = sut.evaluate(REQUEST);

        // Then
        assertThat(result).isSameAs(RESPONSE);
    }

    @Test
    void evaluateThrowsTimeoutOnceTheDeadlineElapses() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, SHORT_DEADLINE);
        given(delegate.evaluateAsync(REQUEST)).willReturn(new CompletableFuture<>());

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(REQUEST))
                .isInstanceOf(TypeSafeException.Timeout.class)
                .hasMessageContaining("deadline of PT0.05S exceeded");
    }

    @Test
    void evaluateAsyncFailsWithTimeoutOnceTheDeadlineElapses() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, SHORT_DEADLINE);
        given(delegate.evaluateAsync(REQUEST)).willReturn(new CompletableFuture<>());

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(REQUEST).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(TypeSafeException.Timeout.class);
    }

    @Test
    void evaluateAsyncPassesOtherFailuresThroughUnchanged() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, Duration.ofSeconds(5));
        TypeSafeException badRequest = new TypeSafeException.BadRequest("nope");
        given(delegate.evaluateAsync(REQUEST)).willReturn(CompletableFuture.failedFuture(badRequest));

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(REQUEST)).isSameAs(badRequest);
    }

    @Test
    void evaluateAsyncStopsARetryingDelegateOnceTheDeadlineElapses() throws Exception {
        // Given
        given(delegate.evaluateAsync(REQUEST))
                .willReturn(CompletableFuture.failedFuture(new TypeSafeException.InternalServer(503, "unavailable")));
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(
                new RetryingTypeSafeClient(delegate, 5, Duration.ofMillis(100)), SHORT_DEADLINE);

        // When
        assertThatThrownBy(() -> sut.evaluate(REQUEST)).isInstanceOf(TypeSafeException.Timeout.class);
        Thread.sleep(300);

        // Then
        then(delegate).should(times(1)).evaluateAsync(REQUEST);
    }

    @Test
    void listModelsPassesThrough() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, SHORT_DEADLINE);
        List<ModelDetails> models = List.of();
        given(delegate.listModels()).willReturn(models);

        // When
        List<ModelDetails> result = sut.listModels();

        // Then
        assertThat(result).isSameAs(models);
    }

    @Test
    void closeClosesTheDelegate() {
        // Given
        DeadlineTypeSafeClient sut = new DeadlineTypeSafeClient(delegate, SHORT_DEADLINE);

        // When
        sut.close();

        // Then
        then(delegate).should().close();
    }
}
