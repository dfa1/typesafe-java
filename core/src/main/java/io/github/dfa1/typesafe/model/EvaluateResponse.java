package io.github.dfa1.typesafe.model;

import java.time.Duration;
import java.util.Map;

public record EvaluateResponse(String model, Map<String, Answer> answers, Usage usage, Metadata metadata) {

    public record Metadata(RequestId requestId, Duration upstreamServiceTime) {
    }
}
