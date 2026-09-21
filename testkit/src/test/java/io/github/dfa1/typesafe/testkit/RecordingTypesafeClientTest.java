package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.Usage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingTypesafeClientTest {

    @Test
    void evaluateReturnsTheNextQueuedResponseAndRecordsTheRequest() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse response = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
        RecordingTypesafeClient sut = new RecordingTypesafeClient().enqueueEvaluate(response);

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result).isSameAs(response);
        assertThat(sut.evaluateRequests()).containsExactly(request);
    }

    @Test
    void queuedResponsesAreConsumedInOrder() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse first = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
        EvaluateResponse second = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(2, 2), null);
        RecordingTypesafeClient sut = new RecordingTypesafeClient()
                .enqueueEvaluate(first)
                .enqueueEvaluate(second);

        // When / Then
        assertThat(sut.evaluate(request)).isSameAs(first);
        assertThat(sut.evaluate(request)).isSameAs(second);
    }

    @Test
    void evaluateThrowsAnAssertionErrorWhenNothingIsQueued() {
        // Given
        RecordingTypesafeClient sut = new RecordingTypesafeClient();
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(request))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No response queued");
    }

    @Test
    void evaluateAsyncFailsTheFutureWhenNothingIsQueued() {
        // Given
        RecordingTypesafeClient sut = new RecordingTypesafeClient();
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        // When
        var future = sut.evaluateAsync(request);

        // Then
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void listModelsReturnsTheNextQueuedResponse() {
        // Given
        List<ModelDetails> models = List.of(new ModelDetails("jev-latest", "Most recent stable release.", "2026-01-01"));
        RecordingTypesafeClient sut = new RecordingTypesafeClient().enqueueModels(models);

        // When
        List<ModelDetails> result = sut.listModels();

        // Then
        assertThat(result).isSameAs(models);
    }
}
