package io.github.dfa1.typesafe.core;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link TypeSafeClient} decorator that retries a {@code 408}/{@code 429}/any {@code 5xx}
 * {@link TypeSafeException}, or a {@link TypeSafeException.Connection} (including
 * {@link TypeSafeException.Timeout}), up to {@code maxRetries} times. Waits the server's
 * {@code retry-after}/{@code retry-after-ms} when the exception carries one
 * ({@link TypeSafeException.RateLimit#retryAfter()}/{@link TypeSafeException.InternalServer#retryAfter()}),
 * exponential backoff from {@code initialBackoff} otherwise. Anything else propagates unchanged.
 *
 * <p>{@link DefaultTypeSafeClient.Builder#build()} already wraps the client in one of these
 * (configured via {@code maxRetries}/{@code initialBackoff}). To wrap your own instead, set
 * {@code maxRetries(0)} on the builder first — stacking two multiplies the attempts.
 */
public final class RetryingTypeSafeClient implements TypeSafeClient {

    private final TypeSafeClient delegate;
    private final int maxRetries;
    private final Duration initialBackoff;

    RetryingTypeSafeClient(TypeSafeClient delegate, int maxRetries, Duration initialBackoff) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
    }

    /** A decorator wrapping its delegate in a {@link RetryingTypeSafeClient}. Pass it as the
     *  {@code decorate} argument to {@link DefaultTypeSafeClient.Builder#build(Function)}, e.g.
     *  {@code builder(apiKey).maxRetries(0).build(RetryingTypeSafeClient.decorate(3, Duration.ofMillis(200)))}. */
    public static Function<TypeSafeClient, RetryingTypeSafeClient> decorate(int maxRetries, Duration initialBackoff) {
        return delegate -> new RetryingTypeSafeClient(delegate, maxRetries, initialBackoff);
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        return retrying(() -> delegate.evaluate(request));
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        return retryingAsync(() -> delegate.evaluateAsync(request));
    }

    @Override
    public List<ModelDetails> listModels() {
        return retrying(delegate::listModels);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private <T> T retrying(Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (TypeSafeException e) {
                if (!shouldRetry(e, attempt)) {
                    throw e;
                }
                sleep(backoffFor(e, attempt));
            }
        }
    }

    private <T> CompletableFuture<T> retryingAsync(Supplier<CompletableFuture<T>> call) {
        CompletableFuture<T> result = new CompletableFuture<>();
        attemptAsync(call, 0, result);
        return result;
    }

    /** Stops scheduling attempts once {@code result} is done — including when a caller completes
     *  it first (e.g. {@link DeadlineTypeSafeClient}'s {@code orTimeout}, or {@code cancel}), so an
     *  abandoned call doesn't keep hitting the API in the background. */
    private <T> void attemptAsync(Supplier<CompletableFuture<T>> call, int attempt, CompletableFuture<T> result) {
        CompletableFuture<T> future;
        try {
            future = call.get();
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return;
        }
        future.whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof TypeSafeException e && shouldRetry(e, attempt) && !result.isDone()) {
                CompletableFuture.delayedExecutor(backoffFor(e, attempt).toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
                    if (!result.isDone()) {
                        attemptAsync(call, attempt + 1, result);
                    }
                });
            } else {
                result.completeExceptionally(cause);
            }
        });
    }

    private boolean shouldRetry(TypeSafeException e, int attempt) {
        return attempt < maxRetries && isRetryable(e);
    }

    /** A connection failure/timeout, or {@code 408}/{@code 429}/any {@code 5xx}: a client- or
     *  server-side hiccup worth retrying, as opposed to a request TypeSafe rejected outright
     *  (e.g. {@code 400}, {@code 401}). */
    static boolean isRetryable(TypeSafeException e) {
        int status = e.statusCode();
        return e instanceof TypeSafeException.Connection || status == 408 || status == 429 || (status >= 500 && status < 600);
    }

    Duration backoffFor(TypeSafeException e, int attempt) {
        Optional<Duration> retryAfter = switch (e) {
            case TypeSafeException.RateLimit r -> r.retryAfter();
            case TypeSafeException.InternalServer s -> s.retryAfter();
            default -> Optional.empty();
        };
        return retryAfter.orElseGet(() -> initialBackoff.multipliedBy(1L << attempt));
    }

    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeException.Interrupted(e);
        }
    }
}
