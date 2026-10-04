package io.github.dfa1.typesafe.client.local;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Every engine end to end on a tiny stand-in model (src/test/resources/tiny, from scripts/tiny/make_fixtures.py): the
 * real input/output names and shapes, deterministic logits, a few KB. Covers the Java plumbing in every build; the
 * answers themselves are the {@code @Tag("model")} tests' job.
 */
class TinyModelTest {

    private static final Path TINY = Path.of("src/test/resources/tiny");

    /** One of each question type, in a fixed order. */
    private static final EvaluateRequest REQUEST = EvaluateRequest.of(
            Content.fields(Map.of("message", "Help! My payouts have been failing for 3 days.")),
            ordered(Map.entry("urgent", Question.noul("Is this urgent?")),
                    Map.entry("kind", Question.choice("What is this about?",
                            ordered(Map.entry("billing", "payments"), Map.entry("bug", "crashes"), Map.entry("other", "")))),
                    Map.entry("anger", Question.score("How angry is the customer?", List.of("calm", "annoyed", "angry")))));

    @Test
    void layaAnswersEveryQuestionType() {
        // Given
        try (LocalLayaTypeSafeClient sut = LocalLayaTypeSafeClient.load(TINY.resolve("laya"))) {

            // When
            EvaluateResponse result = sut.evaluate(REQUEST);

            // Then
            assertWellFormed(result);
            assertThat(result.model()).isEqualTo(new Model("local/laya"));
        }
    }

    @Test
    void layaScoresAQuestionAloneTheSameAsInsideAPaddedBatch() {
        // Given
        try (LocalLayaTypeSafeClient sut = LocalLayaTypeSafeClient.load(TINY.resolve("laya"))) {
            Question kind = REQUEST.questions().get("kind");

            // When
            Answer.Choice alone = sut.evaluate(EvaluateRequest.of(REQUEST.state(), Map.of("kind", kind))).choices().get("kind");
            Answer.Choice result = sut.evaluate(EvaluateRequest.of(REQUEST.state(), ordered(Map.entry("kind", kind),
                    Map.entry("long", Question.noul("a much longer question that pads the other row of the batch")))))
                    .choices().get("kind");

            // Then
            assertThat(result.probabilities()).isEqualTo(alone.probabilities());
        }
    }

    @Test
    void qwenAnswersEveryQuestionTypeOnePrefillEach() {
        // Given
        try (LocalQwenTypeSafeClient sut = LocalQwenTypeSafeClient.load(TINY.resolve("qwen"))) {

            // When
            EvaluateResponse result = sut.evaluate(REQUEST);

            // Then
            assertWellFormed(result);
            assertThat(result.model()).isEqualTo(new Model("local/qwen"));
        }
    }

    @Test
    void clefAnswersEveryQuestionTypeInOnePass() throws Exception {
        // Given
        Path dir = TINY.resolve("clef");
        ClefEngine engine = new ClefEngine(Onnx.session(Onnx.model(dir.resolve("flash")), false),
                BpeTokenizer.load(dir.resolve("tokenizer.json")), new Model("local/clef"));
        try (LocalTypeSafeClient sut = new LocalTypeSafeClient(engine)) {

            // When
            EvaluateResponse result = sut.evaluate(REQUEST);

            // Then — options are scored in code-point order but come back in the caller's order
            assertWellFormed(result);
        }
    }

    @Test
    void clefLoadNeedsCloudflaresWeights() {
        // When
        Throwable result = catchThrowable(() -> LocalClefTypeSafeClient.load(TINY.resolve("clef")));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".safetensors not found");
    }

    /** What every engine must return for {@link #REQUEST}, whatever the model thinks. */
    private static void assertWellFormed(EvaluateResponse result) {
        assertThat(result.nouls().get("urgent").noul()).isBetween(0.0, 1.0);
        Answer.Choice kind = result.choices().get("kind");
        assertThat(kind.probabilities().keySet()).containsExactly("billing", "bug", "other");
        assertThat(kind.probabilities().values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-6));
        assertThat(kind.probabilities().get(kind.choice())).isEqualTo(kind.probabilities().values().stream()
                .mapToDouble(Double::doubleValue).max().orElseThrow());
        Answer.Score anger = result.scores().get("anger");
        assertThat(anger.score()).isBetween(0.0, 2.0);
        assertThat(anger.legend()).containsExactly(Map.entry("0", "calm"), Map.entry("1", "annoyed"), Map.entry("2", "angry"));
        assertThat(result.usage().inputTokens()).isPositive();
    }

    @SafeVarargs
    private static <V> Map<String, V> ordered(Map.Entry<String, V>... entries) {
        Map<String, V> map = new LinkedHashMap<>();
        for (Map.Entry<String, V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }
}
