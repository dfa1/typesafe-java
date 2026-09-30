package io.github.dfa1.typesafe.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * {@link TypeSafeClient} decorator that bounds how long one {@link #evaluate}/{@link #evaluateAsync}
 * call may take in total, failing it with {@link TypeSafeException.Timeout} once {@code deadline}
 * elapses. Placed outside a {@link RetryingTypeSafeClient} — e.g.
 * {@code builder(apiKey).decorateWith(RetryingTypeSafeClient::decorate).decorateWith(c -> DeadlineTypeSafeClient.decorate(c, deadline))} — that total
 * includes every retry and backoff, and no further retry is started once it's hit. An attempt
 * already in flight isn't aborted; its response is just ignored.
 *
 * <p>Placed <em>inside</em> a {@link RetryingTypeSafeClient} instead, it's a per-attempt timeout:
 * {@link TypeSafeException.Timeout} is retryable. {@link #listModels} has no async form to time out,
 * so it passes through unbounded.
 */
public final class DeadlineTypeSafeClient implements TypeSafeClient {

    private final TypeSafeClient delegate;
    private final Duration deadline;

    DeadlineTypeSafeClient(TypeSafeClient delegate, Duration deadline) {
        this.delegate = delegate;
        this.deadline = deadline;
    }

    /** Wraps {@code delegate} with {@code deadline}. Pass it to
     *  {@link DefaultTypeSafeClient.Builder#decorateWith(Function)}, e.g.
     *  {@code decorateWith(c -> DeadlineTypeSafeClient.decorate(c, Duration.ofSeconds(20)))}. */
    public static DeadlineTypeSafeClient decorate(TypeSafeClient delegate, Duration deadline) {
        return new DeadlineTypeSafeClient(delegate, deadline);
    }

    /** Runs through {@link #evaluateAsync}, so the deadline holds without a watchdog thread. */
    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        return DefaultTypeSafeClient.await(evaluateAsync(request));
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        // orTimeout on the delegate's own future, not a copy: that's what lets a
        // RetryingTypeSafeClient underneath see it's done and stop retrying.
        return delegate.evaluateAsync(request)
                .orTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionallyCompose(error -> CompletableFuture.failedFuture(error instanceof TimeoutException
                        ? new TypeSafeException.Timeout(new TimeoutException("deadline of " + deadline + " exceeded"))
                        : error));
    }

    @Override
    public List<ModelDetails> listModels() {
        return delegate.listModels();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
