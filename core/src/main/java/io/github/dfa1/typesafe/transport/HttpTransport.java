package io.github.dfa1.typesafe.transport;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends the single HTTP call TypesafeClient needs (a JSON POST) without tying it to a
 * particular HTTP library. Implementations are discovered via {@link java.util.ServiceLoader}
 * (see typesafe-java-jdk-http-client) or wired explicitly via
 * {@code TypesafeClient.Builder#httpTransport}.
 */
public interface HttpTransport {

    HttpTransportResponse post(URI uri, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException;

    CompletableFuture<HttpTransportResponse> postAsync(URI uri, Map<String, String> headers, byte[] body);
}
