package io.github.dfa1.typesafe.transport;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends the HTTP calls TypeSafeClient needs (a JSON POST for evaluate, a GET for listing
 * models) without tying it to a particular HTTP library. Every call is asynchronous;
 * {@code TypeSafeClient}'s synchronous methods block on the returned future. Implementations
 * are discovered via {@link java.util.ServiceLoader} (see typesafe-java-client-jdk) or
 * wired explicitly via {@code TypeSafeClient.builder(...).httpTransport(...)}.
 */
public interface HttpTransport extends AutoCloseable {

    CompletableFuture<HttpTransportResponse> post(URI uri, Map<String, String> headers, String body);

    CompletableFuture<HttpTransportResponse> get(URI uri, Map<String, String> headers);

    /** Releases any resources held by this transport. */
    @Override
    void close();
}
