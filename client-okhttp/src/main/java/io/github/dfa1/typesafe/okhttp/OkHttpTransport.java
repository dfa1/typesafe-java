package io.github.dfa1.typesafe.okhttp;

import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** An alternative to {@code JdkHttpTransport} for environments {@code java.net.http} doesn't
 *  cover, e.g. Android, or for a consumer who already depends on OkHttp. Response header names
 *  in the returned {@link HttpTransportResponse#headers()} are lowercase (OkHttp normalizes
 *  them internally) rather than preserving the wire casing the way {@code JdkHttpTransport}
 *  does; use {@link HttpTransportResponse#header(String)} for a case-insensitive lookup either
 *  way. */
public final class OkHttpTransport implements HttpTransport {

    /** Applied as the overall call timeout unless overridden via the {@code (OkHttpClient, Duration)}
     *  constructor. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final OkHttpClient http;

    public OkHttpTransport() {
        this(new OkHttpClient(), DEFAULT_TIMEOUT);
    }

    public OkHttpTransport(OkHttpClient http) {
        this(http, DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout applied as {@code http}'s overall call timeout via
     *                {@link OkHttpClient.Builder#callTimeout(Duration)}, or {@code null} to use
     *                {@code http} exactly as given.
     */
    public OkHttpTransport(OkHttpClient http, Duration timeout) {
        this.http = timeout != null ? http.newBuilder().callTimeout(timeout).build() : http;
    }

    @Override
    public CompletableFuture<HttpTransportResponse> post(URI uri, Map<String, String> headers, String body) {
        Request request = request(uri, headers)
                .post(RequestBody.create(body, null))
                .build();
        return execute(request);
    }

    @Override
    public CompletableFuture<HttpTransportResponse> get(URI uri, Map<String, String> headers) {
        Request request = request(uri, headers).get().build();
        return execute(request);
    }

    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        if (http.cache() != null) {
            try {
                http.cache().close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private Request.Builder request(URI uri, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(uri.toString());
        headers.forEach(builder::header);
        return builder;
    }

    private CompletableFuture<HttpTransportResponse> execute(Request request) {
        CompletableFuture<HttpTransportResponse> future = new CompletableFuture<>();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    Map<String, String> responseHeaders = new LinkedHashMap<>();
                    response.headers().toMultimap().forEach((name, values) -> responseHeaders.put(name, values.get(0)));
                    String responseBody = response.body() != null
                            ? response.body().string()
                            : "";
                    future.complete(new HttpTransportResponse(response.code(), responseHeaders, responseBody));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
