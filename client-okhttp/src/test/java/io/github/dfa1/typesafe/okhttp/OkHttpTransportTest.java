package io.github.dfa1.typesafe.okhttp;

import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OkHttpTransportTest {

    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/evaluate");

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    @Test
    void postSendsAHeadedJsonPostAndMapsTheResponse() throws Exception {
        // Given
        OkHttpTransport sut = new OkHttpTransport(httpClient, null);
        given(httpClient.newCall(any())).willReturn(call);

        // When
        CompletableFuture<HttpTransportResponse> future =
                sut.post(ENDPOINT, Map.of("Authorization", "Bearer secret"), "{\"a\":1}");

        // Then
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        then(httpClient).should().newCall(requestCaptor.capture());
        Request sent = requestCaptor.getValue();
        assertThat(sent.url().toString()).isEqualTo(ENDPOINT.toString());
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer secret");

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        then(call).should().enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(call, responseTo(sent, 200, "application/json", "{\"ok\":true}"));

        HttpTransportResponse result = future.get();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.header("Content-Type")).contains("application/json");
        assertThat(result.body()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void getSendsAHeadedGetAndMapsTheResponse() throws Exception {
        // Given
        OkHttpTransport sut = new OkHttpTransport(httpClient, null);
        given(httpClient.newCall(any())).willReturn(call);

        // When
        CompletableFuture<HttpTransportResponse> future = sut.get(ENDPOINT, Map.of("Authorization", "Bearer secret"));

        // Then
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        then(httpClient).should().newCall(requestCaptor.capture());
        Request sent = requestCaptor.getValue();
        assertThat(sent.url().toString()).isEqualTo(ENDPOINT.toString());
        assertThat(sent.method()).isEqualTo("GET");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer secret");

        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        then(call).should().enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onResponse(call, responseTo(sent, 200, null, "{\"models\":[]}"));

        HttpTransportResponse result = future.get();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isEqualTo("{\"models\":[]}");
    }

    @Test
    void aTransportFailureFailsTheFuture() {
        // Given
        OkHttpTransport sut = new OkHttpTransport(httpClient, null);
        given(httpClient.newCall(any())).willReturn(call);
        IOException connectionFailure = new IOException("connection reset");

        // When
        CompletableFuture<HttpTransportResponse> future = sut.get(ENDPOINT, Map.of());

        // Then
        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        then(call).should().enqueue(callbackCaptor.capture());
        callbackCaptor.getValue().onFailure(call, connectionFailure);

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause().isSameAs(connectionFailure);
    }

    @Test
    void aNonNullTimeoutRebuildsTheClientWithAnOverallCallTimeout() {
        // Given
        given(httpClient.newBuilder()).willReturn(new OkHttpClient.Builder());

        // When
        new OkHttpTransport(httpClient, Duration.ofSeconds(3));

        // Then
        then(httpClient).should().newBuilder();
    }

    @Test
    void aNullTimeoutLeavesTheGivenClientUntouched() {
        // Given / When
        new OkHttpTransport(httpClient, null);

        // Then
        then(httpClient).should(never()).newBuilder();
    }

    private static Response responseTo(Request request, int code, String contentType, String body) {
        Response.Builder builder = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(ResponseBody.create(body, null));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return builder.build();
    }
}
