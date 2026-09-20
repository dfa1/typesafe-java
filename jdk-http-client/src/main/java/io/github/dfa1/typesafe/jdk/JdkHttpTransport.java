package io.github.dfa1.typesafe.jdk;

import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class JdkHttpTransport implements HttpTransport {

    /** Applied to every request unless overridden via the {@code (HttpClient, Duration)} constructor. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final Duration timeout;

    public JdkHttpTransport() {
        this(HttpClient.newHttpClient(), DEFAULT_TIMEOUT);
    }

    public JdkHttpTransport(HttpClient http) {
        this(http, DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout applied to every request via {@link HttpRequest.Builder#timeout}, or
     *                {@code null} for no timeout.
     */
    public JdkHttpTransport(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    @Override
    public HttpTransportResponse post(URI uri, Map<String, String> headers, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(request(builder, headers), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toTransportResponse(response);
    }

    @Override
    public CompletableFuture<HttpTransportResponse> postAsync(URI uri, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return http.sendAsync(request(builder, headers), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(JdkHttpTransport::toTransportResponse);
    }

    @Override
    public HttpTransportResponse get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
        HttpResponse<String> response = http.send(request(builder, headers), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return toTransportResponse(response);
    }

    @Override
    public void close() {
        http.close();
    }

    private HttpRequest request(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach(builder::header);
        if (timeout != null) {
            builder.timeout(timeout);
        }
        return builder.build();
    }

    private static HttpTransportResponse toTransportResponse(HttpResponse<String> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> headers.put(name, values.get(0)));
        return new HttpTransportResponse(response.statusCode(), headers, response.body());
    }
}
