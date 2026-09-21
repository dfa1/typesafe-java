package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.transport.HttpTransport;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An interface, not a final class, so a caller can wrap one behind a decorator (caching,
 * metrics, a circuit breaker, ...) that's itself substitutable anywhere a {@code TypeSafeClient}
 * is expected. {@link #builder} is the only supported way to obtain a real one, backed by
 * {@link DefaultTypeSafeClient}, the sole implementation this library ships.
 */
public interface TypeSafeClient extends AutoCloseable {

    static DefaultTypeSafeClient.Builder builder(ApiKey apiKey) {
        return DefaultTypeSafeClient.builder(apiKey);
    }

    /**
     * Evaluates {@code request} synchronously, blocking until a response arrives or the retry
     * budget is exhausted. Retries a {@code 408}/{@code 429}/any {@code 5xx} status, or a
     * connection failure, up to {@code maxRetries} times (exponential backoff, honoring a
     * {@code retry-after}/{@code retry-after-ms} response header when present); any other
     * non-{@code 200} status throws {@link TypeSafeException}.
     *
     * @throws IOException a connection failure or timeout, once retries are exhausted
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    EvaluateResponse evaluate(EvaluateRequest request) throws IOException, InterruptedException;

    /**
     * Asynchronous form of {@link #evaluate}: same retry policy, but returns immediately with a
     * {@link CompletableFuture} that completes with the response, or completes exceptionally
     * with {@link TypeSafeException} (a non-retryable or retries-exhausted status) or an
     * {@link IOException} (a connection failure or timeout, once retries are exhausted).
     */
    CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request);

    /** Lists the models available to the account. */
    List<ModelDetails> listModels() throws IOException, InterruptedException;

    /** Closes the underlying {@link HttpTransport}, releasing any resources it holds. */
    @Override
    void close();
}
