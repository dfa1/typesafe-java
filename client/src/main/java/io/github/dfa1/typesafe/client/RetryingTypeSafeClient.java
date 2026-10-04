package io.github.dfa1.typesafe.client;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.ModelDetails;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Opt-in {@link TypeSafeClient} decorator that retries a {@code 408}/{@code 429}/any {@code 5xx}
 * {@link TypeSafeException}, or a {@link TypeSafeException.Connection} (including
 * {@link TypeSafeException.Timeout}), up to {@code maxRetries} times. Waits the server's
 * {@code retry-after}/{@code retry-after-ms} when the exception carries one
 * ({@link TypeSafeException.RateLimit#retryAfter()}/{@link TypeSafeException.InternalServer#retryAfter()}),
 * exponential backoff from {@code initialBackoff} otherwise. Anything else propagates unchanged.
 *
 * <p>Add one via {@code DefaultTypeSafeClient.Builder.decorateWith(...)}, e.g.
 * {@code builder().apiKey(apiKey).decorateWith(RetryingTypeSafeClient::decorate).build()}. Don't stack two: the
 * attempts multiply, and {@code build()} rejects it.
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

    /** Wraps {@code delegate} with 5 retries, backoff starting at 500ms (500ms, 1s, 2s, 4s, 8s).
     *  Pass it to {@code DefaultTypeSafeClient.Builder.decorateWith(...)} as
     *  {@code decorateWith(RetryingTypeSafeClient::decorate)}. */
    public static RetryingTypeSafeClient decorate(TypeSafeClient delegate) {
        return decorate(delegate, 5, Duration.ofMillis(500));
    }

    /** Wraps {@code delegate} with {@code maxRetries} retries, backoff starting at
     *  {@code initialBackoff}, e.g. {@code decorateWith(c -> RetryingTypeSafeClient.decorate(c, 3, Duration.ofMillis(200)))}. */
    public static RetryingTypeSafeClient decorate(TypeSafeClient delegate, int maxRetries, Duration initialBackoff) {
        return new RetryingTypeSafeClient(delegate, maxRetries, initialBackoff);
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
