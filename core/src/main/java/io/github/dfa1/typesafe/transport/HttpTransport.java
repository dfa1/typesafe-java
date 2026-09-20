package io.github.dfa1.typesafe.transport;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends the HTTP calls TypesafeClient needs (a JSON POST for evaluate, a GET for listing
 * models) without tying it to a particular HTTP library. Implementations are discovered via
 * {@link java.util.ServiceLoader} (see typesafe-java-jdk-http-client) or wired explicitly via
 * {@code TypesafeClient.Builder#httpTransport}.
 */
public interface HttpTransport extends AutoCloseable {

    HttpTransportResponse post(URI uri, Map<String, String> headers, String body)
            throws IOException, InterruptedException;

    CompletableFuture<HttpTransportResponse> postAsync(URI uri, Map<String, String> headers, String body);

    HttpTransportResponse get(URI uri, Map<String, String> headers) throws IOException, InterruptedException;

    /** Releases any resources held by this transport. A no-op unless overridden. */
    @Override
    default void close() {
    }
}
