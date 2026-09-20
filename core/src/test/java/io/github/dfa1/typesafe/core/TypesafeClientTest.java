package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TypesafeClientTest {

    private static final URI ENDPOINT = URI.create("https://example.test/systemone");

    @Mock
    private HttpTransport httpTransport;

    @Mock
    private JsonCodec jsonCodec;

    @Test
    void evaluateDelegatesToTheConfiguredHttpTransportAndJsonCodec() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.builder(new ApiToken("secret"))
                .endpoint(ENDPOINT)
                .httpTransport(httpTransport)
                .jsonCodec(jsonCodec)
                .build();

        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());
        Map<String, String> expectedHeaders = Map.of(
                "Authorization", "Bearer secret",
                "Content-Type", "application/json");
        byte[] requestBytes = "{\"request\":true}".getBytes(StandardCharsets.UTF_8);
        String requestBody = new String(requestBytes, StandardCharsets.UTF_8);
        String responseBody = "{\"response\":true}";
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        EvaluateResponse decodedResponse = new EvaluateResponse("jev-latest", Map.of(), new Usage(10, 5), null);

        given(jsonCodec.writeValueAsBytes(request)).willReturn(requestBytes);
        given(httpTransport.post(ENDPOINT, expectedHeaders, requestBody))
                .willReturn(new HttpTransportResponse(200, Map.of(), responseBody));
        given(jsonCodec.readValue(responseBytes, EvaluateResponse.class)).willReturn(decodedResponse);

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        then(jsonCodec).should().writeValueAsBytes(request);
        then(httpTransport).should().post(ENDPOINT, expectedHeaders, requestBody);
        then(jsonCodec).should().readValue(responseBytes, EvaluateResponse.class);
        assertThat(result.model()).isEqualTo("jev-latest");
        assertThat(result.usage().inputTokens()).isEqualTo(10);
    }
}
