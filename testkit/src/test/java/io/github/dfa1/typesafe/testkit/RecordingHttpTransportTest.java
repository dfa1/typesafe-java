package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RecordingHttpTransportTest {

    private static final URI EVALUATE_URI = URI.create("https://example.test/v1/systemone");
    private static final URI MODELS_URI = URI.create("https://example.test/v1/models");

    @Test
    void recordsEveryCallInOrder() throws Exception {
        // Given
        RecordingHttpTransport sut = new RecordingHttpTransport()
                .respond(new HttpTransportResponse(200, Map.of(), "first"))
                .respond(new HttpTransportResponse(200, Map.of(), "second"));

        // When
        sut.post(EVALUATE_URI, Map.of(), "req1").get();
        sut.get(MODELS_URI, Map.of()).get();

        // Then
        assertThat(sut.requests())
                .extracting(RecordedRequest::method, RecordedRequest::uri)
                .containsExactly(
                        tuple("POST", EVALUATE_URI),
                        tuple("GET", MODELS_URI));
    }

    @Test
    void respondToMatchesAResponseToOnlyTheRequestsSatisfyingThePredicate() throws Exception {
        // Given
        RecordingHttpTransport sut = new RecordingHttpTransport()
                .respondTo(request -> request.uri().equals(MODELS_URI), new HttpTransportResponse(200, Map.of(), "models"))
                .respondTo(request -> request.uri().equals(EVALUATE_URI), new HttpTransportResponse(200, Map.of(), "evaluate"));

        // When
        HttpTransportResponse modelsResponse = sut.get(MODELS_URI, Map.of()).get();
        HttpTransportResponse evaluateResponse = sut.post(EVALUATE_URI, Map.of(), "req").get();

        // Then
        assertThat(modelsResponse.body()).isEqualTo("models");
        assertThat(evaluateResponse.body()).isEqualTo("evaluate");
    }

    @Test
    void aMatchingStubIsConsumedByTheFirstRequestThatSatisfiesItSoRepeatedCallsGetSuccessiveResponses() throws Exception {
        // Given
        RecordingHttpTransport sut = new RecordingHttpTransport()
                .respond(new HttpTransportResponse(500, Map.of(), "retry me"))
                .respond(new HttpTransportResponse(200, Map.of(), "ok"));

        // When
        HttpTransportResponse first = sut.post(EVALUATE_URI, Map.of(), "req").get();
        HttpTransportResponse second = sut.post(EVALUATE_URI, Map.of(), "req").get();

        // Then
        assertThat(first.statusCode()).isEqualTo(500);
        assertThat(second.statusCode()).isEqualTo(200);
    }

    @Test
    void aRequestWithNoMatchingStubFailsWithAnAssertionError() {
        // Given
        RecordingHttpTransport sut = new RecordingHttpTransport();

        // When
        var future = sut.post(EVALUATE_URI, Map.of(), "req");

        // Then
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No stubbed response");
    }
}
