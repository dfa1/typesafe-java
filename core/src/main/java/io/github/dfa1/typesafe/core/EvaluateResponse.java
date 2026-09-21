package io.github.dfa1.typesafe.core;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The TypeSafe evaluate endpoint's response. See
 * <a href="https://docs.typesafe.ai/api">docs.typesafe.ai/api</a>.
 *
 * @param model    the versioned model that actually processed the request (e.g.
 *                 {@code "jev-1.13.0"}) — see {@link TypeSafeClient#listModels()} for its
 *                 description and release date
 * @param answers  one {@link Answer} per question, keyed identically to the request's
 *                 {@link EvaluateRequest#questions()}
 * @param usage    token accounting for the request
 * @param metadata not part of the response body itself — populated by {@link TypeSafeClient}
 *                 from response headers
 */
public record EvaluateResponse(Model model, Map<String, Answer> answers, Usage usage, Metadata metadata) {

    /** {@link #answers()}, narrowed to just the {@link Answer.Noul} entries. */
    public Map<String, Answer.Noul> nouls() {
        return answersOfType(Answer.Noul.class);
    }

    /** {@link #answers()}, narrowed to just the {@link Answer.Choice} entries. */
    public Map<String, Answer.Choice> choices() {
        return answersOfType(Answer.Choice.class);
    }

    /** {@link #answers()}, narrowed to just the {@link Answer.Score} entries. */
    public Map<String, Answer.Score> scores() {
        return answersOfType(Answer.Score.class);
    }

    private <T extends Answer> Map<String, T> answersOfType(Class<T> type) {
        return answers.entrySet().stream()
                .filter(entry -> type.isInstance(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> type.cast(entry.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * @param requestId           from the {@code x-typesafe-request-id} response header, or
     *                            {@code null} if absent
     * @param upstreamServiceTime from the {@code x-envoy-upstream-service-time} response
     *                            header, or {@code null} if absent
     */
    public record Metadata(RequestId requestId, Duration upstreamServiceTime) {
    }
}
