package io.github.dfa1.typesafe.jdk;

import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient http;

    public JdkHttpTransport() {
        this(HttpClient.newHttpClient());
    }

    public JdkHttpTransport(HttpClient http) {
        this.http = http;
    }

    @Override
    public HttpTransportResponse post(URI uri, Map<String, String> headers, String body)
            throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(toRequest(uri, headers, body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toTransportResponse(response);
    }

    @Override
    public CompletableFuture<HttpTransportResponse> postAsync(URI uri, Map<String, String> headers, String body) {
        return http.sendAsync(toRequest(uri, headers, body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(JdkHttpTransport::toTransportResponse);
    }

    private static HttpRequest toRequest(URI uri, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return builder.build();
    }

    private static HttpTransportResponse toTransportResponse(HttpResponse<String> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> headers.put(name, values.get(0)));
        return new HttpTransportResponse(response.statusCode(), headers, response.body());
    }
}
