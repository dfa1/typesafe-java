package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.TypeSafeClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A {@link TypeSafeClient} decorator for unit-testing a caller's handling of intermittent
 * failures: every {@code failEvery}-th call (counting {@code evaluate()}, {@code evaluateAsync()},
 * and {@code listModels()} together) throws {@code failure.get()} instead of reaching
 * {@code delegate}; every other call passes straight through. Wrap any {@link TypeSafeClient},
 * e.g. a {@link RecordingTypeSafeClient}, to get both recording and periodic failure at once.
 */
public final class FailingTypeSafeClient implements TypeSafeClient {

    private final TypeSafeClient delegate;
    private final int failEvery;
    private final Supplier<? extends RuntimeException> failure;
    private final AtomicInteger calls = new AtomicInteger();

    public FailingTypeSafeClient(TypeSafeClient delegate, int failEvery, Supplier<? extends RuntimeException> failure) {
        if (failEvery <= 0) {
            throw new IllegalArgumentException("failEvery must be positive, got " + failEvery);
        }
        this.delegate = delegate;
        this.failEvery = failEvery;
        this.failure = failure;
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        maybeFail();
        return delegate.evaluate(request);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        try {
            maybeFail();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return delegate.evaluateAsync(request);
    }

    @Override
    public List<ModelDetails> listModels() {
        maybeFail();
        return delegate.listModels();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void maybeFail() {
        if (calls.incrementAndGet() % failEvery == 0) {
            throw failure.get();
        }
    }
}
