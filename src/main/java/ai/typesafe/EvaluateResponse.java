package ai.typesafe;

import java.util.Map;

public record EvaluateResponse(String model, Map<String, Answer> answers, Usage usage) {
}
