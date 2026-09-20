package io.github.dfa1.typesafe.jdk;

import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JdkHttpTransportTest {

    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/evaluate");

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Test
    void postSendsAHeadedJsonPostAndMapsTheResponse() throws Exception {
        // Given
        JdkHttpTransport sut = new JdkHttpTransport(httpClient);
        given(httpResponse.statusCode()).willReturn(200);
        given(httpResponse.headers()).willReturn(
                HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (name, value) -> true));
        given(httpResponse.body()).willReturn("{\"ok\":true}");
        given(httpClient.<String>send(any(), any())).willReturn(httpResponse);

        // When
        HttpTransportResponse result = sut.post(ENDPOINT, Map.of("Authorization", "Bearer secret"), "{\"a\":1}");

        // Then
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.headers()).containsEntry("Content-Type", "application/json");
        assertThat(result.body()).isEqualTo("{\"ok\":true}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        then(httpClient).should().send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertThat(sent.uri()).isEqualTo(ENDPOINT);
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer secret");
    }

    @Test
    void postAsyncSendsAHeadedJsonPostAndMapsTheResponse() throws ExecutionException, InterruptedException {
        // Given
        JdkHttpTransport sut = new JdkHttpTransport(httpClient);
        given(httpResponse.statusCode()).willReturn(500);
        given(httpResponse.headers()).willReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        given(httpResponse.body()).willReturn("{\"error\":true}");
        given(httpClient.<String>sendAsync(any(), any())).willReturn(CompletableFuture.completedFuture(httpResponse));

        // When
        CompletableFuture<HttpTransportResponse> result =
                sut.postAsync(ENDPOINT, Map.of(), "{\"a\":1}");

        // Then
        assertThat(result.get().statusCode()).isEqualTo(500);
        assertThat(result.get().body()).isEqualTo("{\"error\":true}");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        then(httpClient).should().sendAsync(captor.capture(), any());
        assertThat(captor.getValue().uri()).isEqualTo(ENDPOINT);
        assertThat(captor.getValue().method()).isEqualTo("POST");
    }
}
