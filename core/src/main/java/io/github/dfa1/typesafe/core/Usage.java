package io.github.dfa1.typesafe.core;

/**
 * Token accounting for an {@link EvaluateRequest}/{@link EvaluateResponse} pair. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 *
 * @param inputTokens  tokens consumed by the request
 * @param outputTokens tokens consumed by the response
 */
public record Usage(int inputTokens, int outputTokens) {
}
