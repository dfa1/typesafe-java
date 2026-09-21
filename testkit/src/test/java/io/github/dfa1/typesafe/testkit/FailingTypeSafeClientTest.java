package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypeSafeException;
import io.github.dfa1.typesafe.core.Usage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("resource")
class FailingTypeSafeClientTest {

    private static final EvaluateRequest REQUEST = EvaluateRequest.of(State.text("hi"), Map.of());

    @Test
    void everyNthCallThrowsInsteadOfReachingTheDelegate() {
        // Given
        EvaluateResponse response = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient()
                .enqueueEvaluate(response)
                .enqueueEvaluate(response)
                .enqueueEvaluate(response);
        FailingTypeSafeClient sut = new FailingTypeSafeClient(delegate, 3, () -> new TypeSafeException(503, "overloaded"));

        // When / Then
        assertThat(sut.evaluate(REQUEST)).isSameAs(response);
        assertThat(sut.evaluate(REQUEST)).isSameAs(response);
        assertThatThrownBy(() -> sut.evaluate(REQUEST))
                .isInstanceOf(TypeSafeException.class)
                .satisfies(e -> assertThat(((TypeSafeException) e).statusCode()).isEqualTo(503));
        assertThat(sut.evaluate(REQUEST)).isSameAs(response);
    }

    @Test
    void evaluateAsyncFailsTheFutureOnTheNthCall() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient();
        FailingTypeSafeClient sut = new FailingTypeSafeClient(delegate, 1, () -> new TypeSafeException(503, "overloaded"));

        // When
        var future = sut.evaluateAsync(REQUEST);

        // Then
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(TypeSafeException.class);
    }

    @Test
    void listModelsIsCountedTowardTheSameFailEveryCounterAsEvaluate() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient()
                .enqueueEvaluate(new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null));
        FailingTypeSafeClient sut = new FailingTypeSafeClient(delegate, 2, () -> new TypeSafeException(503, "overloaded"));

        // When / Then
        assertThat(sut.evaluate(REQUEST)).isNotNull();
        assertThatThrownBy(sut::listModels).isInstanceOf(TypeSafeException.class);
    }

    @Test
    void simulatesASpecificTypeSafeExceptionSubclassForCallersToCatch() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient();
        FailingTypeSafeClient sut = new FailingTypeSafeClient(
                delegate, 1, () -> new TypeSafeException.RateLimit("slow down", Duration.ofSeconds(2)));

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(REQUEST))
                .isInstanceOf(TypeSafeException.RateLimit.class)
                .satisfies(e -> assertThat(((TypeSafeException.RateLimit) e).retryAfter()).contains(Duration.ofSeconds(2)));
    }

    @Test
    void constructorRejectsANonPositiveFailEvery() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient();

        // When / Then
        assertThatThrownBy(() -> new FailingTypeSafeClient(delegate, 0, RuntimeException::new))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
