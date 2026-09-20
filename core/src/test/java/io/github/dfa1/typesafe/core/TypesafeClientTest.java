package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TypesafeClientTest {

    private static final URI ENDPOINT = URI.create("https://example.test/systemone");
    private static final Duration NO_BACKOFF = Duration.ZERO;

    @Mock
    private HttpTransport httpTransport;

    @Mock
    private JsonCodec jsonCodec;

    @Test
    void evaluateDelegatesToTheConfiguredHttpTransportAndJsonCodec() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.builder(new ApiKey("secret"))
                .endpoint(ENDPOINT)
                .httpTransport(httpTransport)
                .jsonCodec(jsonCodec)
                .build();

        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        Map<String, String> expectedHeaders = Map.of(
                "Authorization", "Bearer secret",
                "Content-Type", "application/json");
        String requestBody = "{\"request\":true}";
        String responseBody = "{\"response\":true}";
        EvaluateResponse decodedResponse = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(10, 5), null);

        given(jsonCodec.writeValueAsString(request)).willReturn(requestBody);
        given(httpTransport.post(ENDPOINT, expectedHeaders, requestBody))
                .willReturn(new HttpTransportResponse(200, Map.of(), responseBody));
        given(jsonCodec.readValue(responseBody, EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        then(jsonCodec).should().writeValueAsString(request);
        then(httpTransport).should().post(ENDPOINT, expectedHeaders, requestBody);
        then(jsonCodec).should().readValue(responseBody, EvaluateResponse.class);
        assertThat(result.model()).isEqualTo(Model.LATEST);
        assertThat(result.usage().inputTokens()).isEqualTo(10);
    }

    @Test
    void listModelsDelegatesToTheConfiguredHttpTransportAndJsonCodec() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.builder(new ApiKey("secret"))
                .endpoint(ENDPOINT)
                .httpTransport(httpTransport)
                .jsonCodec(jsonCodec)
                .build();

        URI modelsEndpoint = URI.create("https://example.test/v1/models");
        Map<String, String> expectedHeaders = Map.of("Authorization", "Bearer secret");
        List<ModelDetails> models = List.of(new ModelDetails("jev-latest", "Most recent stable release.", "2026-01-01"));

        given(httpTransport.get(modelsEndpoint, expectedHeaders))
                .willReturn(new HttpTransportResponse(200, Map.of(), "models-json"));
        given(jsonCodec.readValue("models-json", TypesafeClient.ModelsResponse.class))
                .willReturn(new TypesafeClient.ModelsResponse(models));

        // When
        List<ModelDetails> result = sut.listModels();

        // Then
        then(httpTransport).should().get(modelsEndpoint, expectedHeaders);
        assertThat(result).isEqualTo(models);
    }

    @Test
    void closeClosesTheUnderlyingHttpTransport() {
        // Given
        TypesafeClient sut = TypesafeClient.builder(new ApiKey("secret"))
                .httpTransport(httpTransport)
                .jsonCodec(jsonCodec)
                .build();

        // When
        sut.close();

        // Then
        then(httpTransport).should().close();
    }

    @Test
    void evaluateThrowsOnANonRetryableErrorStatus() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.post(any(), any(), any()))
                .willReturn(new HttpTransportResponse(400, Map.of(), "bad request"));

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(request))
                .isInstanceOf(TypesafeException.class)
                .satisfies(e -> {
                    TypesafeException ex = (TypesafeException) e;
                    assertThat(ex.statusCode()).isEqualTo(400);
                    assertThat(ex.body()).isEqualTo("bad request");
                });
    }

    @Test
    void evaluateRetriesOnRateLimitThenSucceeds() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse decodedResponse = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.post(any(), any(), any()))
                .willReturn(new HttpTransportResponse(429, Map.of(), "slow down"))
                .willReturn(new HttpTransportResponse(200, Map.of(), "ok"));
        given(jsonCodec.readValue("ok", EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
        then(httpTransport).should(times(2)).post(any(), any(), any());
    }

    @Test
    void evaluateThrowsAfterExhaustingRetries() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF, 1);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.post(any(), any(), any()))
                .willReturn(new HttpTransportResponse(529, Map.of(), "overloaded"));

        // When / Then
        assertThatThrownBy(() -> sut.evaluate(request))
                .isInstanceOf(TypesafeException.class)
                .satisfies(e -> assertThat(((TypesafeException) e).statusCode()).isEqualTo(529));
        then(httpTransport).should(times(2)).post(any(), any(), any());
    }

    @Test
    void evaluatePopulatesMetadataFromResponseHeaders() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse decodedResponse = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
        Map<String, String> responseHeaders = Map.of(
                "x-typesafe-request-id", "req_123",
                "x-envoy-upstream-service-time", "42");

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.post(any(), any(), any()))
                .willReturn(new HttpTransportResponse(200, responseHeaders, "ok"));
        given(jsonCodec.readValue("ok", EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.metadata().requestId()).isEqualTo(new RequestId("req_123"));
        assertThat(result.metadata().upstreamServiceTime()).isEqualTo(Duration.ofMillis(42));
    }

    @Test
    void evaluateAsyncSucceeds() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse decodedResponse = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.postAsync(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(
                        new HttpTransportResponse(200, Map.of(), "ok")));
        given(jsonCodec.readValue("ok", EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluateAsync(request).get();

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
    }

    @Test
    void evaluateAsyncRetriesOnRateLimitThenSucceeds() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        EvaluateResponse decodedResponse = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.postAsync(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(
                        new HttpTransportResponse(429, Map.of(), "slow down")))
                .willReturn(CompletableFuture.completedFuture(
                        new HttpTransportResponse(200, Map.of(), "ok")));
        given(jsonCodec.readValue("ok", EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluateAsync(request).get();

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
        then(httpTransport).should(times(2)).postAsync(any(), any(), any());
    }

    @Test
    void evaluateAsyncFailsAfterExhaustingRetries() throws Exception {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF, 1);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.postAsync(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(
                        new HttpTransportResponse(529, Map.of(), "overloaded")));

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(request).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(TypesafeException.class);
    }

    @Test
    void evaluateAsyncFailsFastWhenEncodingTheRequestThrows() {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        RuntimeException encodingFailure = new RuntimeException("boom");

        given(jsonCodec.writeValueAsString(request)).willThrow(encodingFailure);

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(request).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(encodingFailure);
    }

    @Test
    void evaluateAsyncFailsFastWhenDecodingTheResponseThrows() {
        // Given
        TypesafeClient sut = clientWith(NO_BACKOFF);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        RuntimeException decodingFailure = new RuntimeException("boom");

        given(jsonCodec.writeValueAsString(request)).willReturn("{}");
        given(httpTransport.postAsync(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(
                        new HttpTransportResponse(200, Map.of(), "not json")));
        given(jsonCodec.readValue("not json", EvaluateResponse.class)).willThrow(decodingFailure);

        // When / Then
        assertThatThrownBy(() -> sut.evaluateAsync(request).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(decodingFailure);
    }

    @Test
    void builderThrowsWhenNoHttpTransportIsConfiguredOrDiscoverable() {
        // Given
        TypesafeClient.Builder sut = TypesafeClient.builder(new ApiKey("secret")).jsonCodec(jsonCodec);

        // When / Then
        assertThatThrownBy(sut::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HttpTransport");
    }

    @Test
    void builderThrowsWhenNoJsonCodecIsConfiguredOrDiscoverable() {
        // Given
        TypesafeClient.Builder sut = TypesafeClient.builder(new ApiKey("secret")).httpTransport(httpTransport);

        // When / Then
        assertThatThrownBy(sut::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JsonCodec");
    }

    private TypesafeClient clientWith(Duration backoff) {
        return clientWith(backoff, 5);
    }

    private TypesafeClient clientWith(Duration backoff, int maxRetries) {
        return TypesafeClient.builder(new ApiKey("secret"))
                .endpoint(ENDPOINT)
                .httpTransport(httpTransport)
                .jsonCodec(jsonCodec)
                .initialBackoff(backoff)
                .maxRetries(maxRetries)
                .build();
    }
}
