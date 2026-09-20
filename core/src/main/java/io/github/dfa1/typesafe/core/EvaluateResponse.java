package io.github.dfa1.typesafe.core;

import java.time.Duration;
import java.util.Map;

/**
 * The TypeSafe evaluate endpoint's response. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 *
 * @param model    the versioned model that actually processed the request (e.g.
 *                 {@code "jev-1.13.0"}); only {@link Model#id()} is populated from the wire —
 *                 {@code description}/{@code releaseDate} are {@code null} unless separately
 *                 looked up via {@link TypesafeClient#listModels()}
 * @param answers  one {@link Answer} per question, keyed identically to the request's
 *                 {@link EvaluateRequest#questions()}
 * @param usage    token accounting for the request
 * @param metadata not part of the response body itself — populated by {@link TypesafeClient}
 *                 from response headers
 */
public record EvaluateResponse(Model model, Map<String, Answer> answers, Usage usage, Metadata metadata) {

    /**
     * @param requestId           from the {@code x-typesafe-request-id} response header, or
     *                            {@code null} if absent
     * @param upstreamServiceTime from the {@code x-envoy-upstream-service-time} response
     *                            header, or {@code null} if absent
     */
    public record Metadata(RequestId requestId, Duration upstreamServiceTime) {
    }
}
