package io.github.dfa1.typesafe.client;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.Usage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

/**
 * Running total of the tokens TypeSafe reports in {@link EvaluateResponse#usage()}. Create one,
 * then add its decorator to a client:
 *
 * <pre>{@code
 * TokenCounter tokens = new TokenCounter();
 * TypeSafeClient client = DefaultTypeSafeClient.builder().apiKey(apiKey)
 *         .decorateWith(tokens::decorate)
 *         .build();
 * // ...
 * long spent = tokens.totalTokens();
 * }</pre>
 *
 * <p>The counter lives outside the client, so it stays readable after the decorator disappears
 * into a stack, and one counter can be shared by several clients. Only a successful response
 * carries usage, so failed calls and retried attempts add nothing, wherever the decorator sits.
 * Thread-safe; each getter is a {@link LongAdder#sum()}, so a read concurrent with calls may miss
 * the ones still completing.
 */
public final class TokenCounter {

    private final LongAdder inputTokens = new LongAdder();
    private final LongAdder outputTokens = new LongAdder();

    /** Wraps {@code delegate} so every successful {@code evaluate}/{@code evaluateAsync} adds its
     *  usage to this counter. Pass it to {@code DefaultTypeSafeClient.Builder.decorateWith(...)} as
     *  {@code tokens::decorate}. */
    public TypeSafeClient decorate(TypeSafeClient delegate) {
        return new Counting(delegate);
    }

    public long inputTokens() {
        return inputTokens.sum();
    }

    public long outputTokens() {
        return outputTokens.sum();
    }

    public long totalTokens() {
        return inputTokens() + outputTokens();
    }

    private EvaluateResponse record(EvaluateResponse response) {
        Usage usage = response.usage();
        if (usage != null) {
            inputTokens.add(usage.inputTokens());
            outputTokens.add(usage.outputTokens());
        }
        return response;
    }

    private final class Counting implements TypeSafeClient {
        private final TypeSafeClient delegate;

        Counting(TypeSafeClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public EvaluateResponse evaluate(EvaluateRequest request) {
            return record(delegate.evaluate(request));
        }

        @Override
        public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
            return delegate.evaluateAsync(request).thenApply(TokenCounter.this::record);
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
}
