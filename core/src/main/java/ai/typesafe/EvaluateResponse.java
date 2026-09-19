package ai.typesafe;

import java.time.Duration;
import java.util.Map;

public record EvaluateResponse(String model, Map<String, Answer> answers, Usage usage, Metadata metadata) {

    public record Metadata(RequestId requestId, Duration upstreamServiceTime) {
    }
}
