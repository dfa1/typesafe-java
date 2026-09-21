package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.TypesafeClient;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link TypesafeClient} test double for unit-testing code that calls it, at the
 * {@code EvaluateRequest}/{@code EvaluateResponse} level. Every {@code evaluate()} call is
 * appended to {@link #evaluateRequests()} in order; {@link #enqueueEvaluate} queues the
 * response to whichever {@code evaluate()}/{@code evaluateAsync()} call comes next, and
 * {@link #enqueueModels} does the same for {@code listModels()}. A call with nothing left
 * queued throws (or, for {@code evaluateAsync}, fails its future with) an {@link AssertionError}.
 */
public final class RecordingTypesafeClient implements TypesafeClient {

    private final List<EvaluateRequest> evaluateRequests = new CopyOnWriteArrayList<>();
    private final Queue<EvaluateResponse> evaluateResponses = new ConcurrentLinkedQueue<>();
    private final Queue<List<ModelDetails>> modelsResponses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger listModelsCalls = new AtomicInteger();

    public RecordingTypesafeClient enqueueEvaluate(EvaluateResponse response) {
        evaluateResponses.add(response);
        return this;
    }

    public RecordingTypesafeClient enqueueModels(List<ModelDetails> models) {
        modelsResponses.add(models);
        return this;
    }

    public List<EvaluateRequest> evaluateRequests() {
        return List.copyOf(evaluateRequests);
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        evaluateRequests.add(request);
        EvaluateResponse response = evaluateResponses.poll();
        if (response == null) {
            throw new AssertionError("No response queued for " + request);
        }
        return response;
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        try {
            return CompletableFuture.completedFuture(evaluate(request));
        } catch (AssertionError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public List<ModelDetails> listModels() {
        int call = listModelsCalls.incrementAndGet();
        List<ModelDetails> response = modelsResponses.poll();
        if (response == null) {
            throw new AssertionError("No response queued for listModels() call #" + call);
        }
        return response;
    }

    @Override
    public void close() {
        // no resources to release
    }
}
