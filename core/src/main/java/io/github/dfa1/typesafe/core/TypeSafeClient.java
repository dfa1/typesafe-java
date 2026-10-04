package io.github.dfa1.typesafe.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The TypeSafe contract: a request in, a response out. An interface, so a caller can wrap one
 * behind a decorator (caching, metrics, a circuit breaker, ...) that's itself substitutable
 * anywhere a {@code TypeSafeClient} is expected. Implemented by {@code DefaultTypeSafeClient}
 * (typesafe-java-client-http, the TypeSafe API over HTTP) and the in-process clients of
 * typesafe-java-client-local.
 */
public interface TypeSafeClient extends AutoCloseable {

    /**
     * Evaluates {@code request} synchronously, blocking until a response arrives. A client from
     * {@code DefaultTypeSafeClient} makes exactly one attempt; wrap it in {@link RetryingTypeSafeClient} to
     * retry. All of the
     * following throw {@link TypeSafeException}: any other non-{@code 200} status; a connection
     * failure/timeout ({@link TypeSafeException.Connection}/
     * {@link TypeSafeException.Timeout}); the calling thread being interrupted while waiting
     * ({@link TypeSafeException.Interrupted}, which restores the thread's interrupt status
     * before throwing).
     */
    EvaluateResponse evaluate(EvaluateRequest request);

    /**
     * Asynchronous form of {@link #evaluate}: returns immediately with a
     * {@link CompletableFuture} that completes with the response, or completes exceptionally
     * with {@link TypeSafeException}.
     */
    CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request);

    /** Lists the models available to the account. */
    List<ModelDetails> listModels();

    /** Releases whatever the client holds (an HTTP transport, a model session, ...). */
    @Override
    void close();
}
