package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.TypeSafeException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

class QwenEngineTest {

    private static final int YES = 1;
    private static final int NO = 2;

    /** Token id of a single letter/digit label, so tests can set its logit. */
    private static int label(char c) {
        return 10 + c;
    }

    private final float[] logits = new float[128];
    private final List<long[]> prompts = new ArrayList<>();
    private final LocalTypeSafeClient sut = new LocalTypeSafeClient(new QwenEngine(QwenEngineTest::encode, QwenEngineTest::encode,
            ids -> {
                prompts.add(ids);
                return logits.clone();
            }, () -> {
            }, new Model("local/fake")));

    /** "Yes"/"No" and single letters/digits are single tokens; any other text is one token per char. */
    private static long[] encode(String text) {
        return switch (text) {
            case "Yes" -> new long[]{YES};
            case "No" -> new long[]{NO};
            case String t when t.length() == 1 && Character.isLetterOrDigit(t.charAt(0)) -> new long[]{label(t.charAt(0))};
            default -> new long[text.length()];
        };
    }

    @Test
    void noulIsTheSoftmaxOfYesOverNo() {
        // Given
        logits[YES] = 2f;
        logits[NO] = 0f;

        // When
        EvaluateResponse result = sut.evaluate(EvaluateRequest.of(
                Content.text("My card was charged twice."), Map.of("urgent", Question.noul("Is this urgent?"))));

        // Then
        assertThat(result.nouls().get("urgent").noul()).isCloseTo(1 / (1 + Math.exp(-2)), within(1e-6));
        assertThat(result.model()).isEqualTo(new Model("local/fake"));
    }

    @Test
    void eachQuestionGetsItsOwnPrefillAndCountsItsTokens() {
        // When
        EvaluateResponse result = sut.evaluate(EvaluateRequest.builder().state("abc")
                .noul("a", "Q1?").noul("b", "Q2?").build());

        // Then
        assertThat(prompts).hasSize(2);
        assertThat(result.usage().inputTokens()).isEqualTo(prompts.stream().mapToInt(p -> p.length).sum());
        assertThat(result.usage().outputTokens()).isZero();
    }

    @Test
    void choiceMapsLetterProbabilitiesBackToOptionKeys() {
        // Given
        logits[label('A')] = 0f;
        logits[label('B')] = 3f;
        Map<String, String> options = new LinkedHashMap<>();
        options.put("billing", "money issues");
        options.put("bug", null);

        // When
        Answer.Choice result = sut.evaluate(EvaluateRequest.of(Content.text("x"),
                Map.of("kind", Question.choice("What kind?", options)))).choices().get("kind");

        // Then
        assertThat(result.choice()).isEqualTo("bug");
        assertThat(result.probabilities()).containsOnlyKeys("billing", "bug");
        assertThat(result.probabilities().get("bug")).isCloseTo(1 / (1 + Math.exp(-3)), within(1e-6));
    }

    @Test
    void scoreIsTheProbabilityWeightedLevel() {
        // Given: levels 1 and 2 equally likely, level 0 negligible
        logits[label('0')] = -100f;
        logits[label('1')] = 5f;
        logits[label('2')] = 5f;

        // When
        Answer.Score result = sut.evaluate(EvaluateRequest.of(Content.text("x"),
                Map.of("anger", Question.score("How angry?", List.of("calm", "annoyed", "furious"))))).scores().get("anger");

        // Then
        assertThat(result.score()).isCloseTo(1.5, within(1e-6));
        assertThat(result.legend()).containsExactly(entry("0", "calm"), entry("1", "annoyed"), entry("2", "furious"));
    }

    @Test
    void rejectsWhatSingleTokenLabelsCannotExpress() {
        // Given
        EvaluateRequest oneLevel = EvaluateRequest.of(Content.text("x"), Map.of("s", Question.score("?", List.of("only"))));
        EvaluateRequest noOptions = EvaluateRequest.of(Content.text("x"), Map.of("c", Question.choice("?", Map.of())));

        // When
        Throwable oneLevelResult = catchThrowable(() -> sut.evaluate(oneLevel));
        Throwable noOptionsResult = catchThrowable(() -> sut.evaluate(noOptions));

        // Then
        assertThat(oneLevelResult).isInstanceOf(TypeSafeException.BadRequest.class);
        assertThat(noOptionsResult).isInstanceOf(TypeSafeException.BadRequest.class);
    }

    @Test
    void noulPromptListsCriteriaAndAsksForYesOrNo() {
        // When
        String result = QwenEngine.prompt("s", Question.noul("Urgent?", Map.of("true", "act today")));

        // Then
        assertThat(result).contains("Question: Urgent?", "\"true\" means: act today").endsWith("Answer with Yes or No.");
    }

    @Test
    void choicePromptLabelsOptionsWithLetters() {
        // When
        String result = QwenEngine.prompt("s", Question.choice("Kind?", new LinkedHashMap<>(Map.of("bug", ""))));

        // Then
        assertThat(result).contains("A) bug\n").endsWith("Answer with the letter of one option.");
    }

    @Test
    void scorePromptNumbersLevelsFromZero() {
        // When
        String result = QwenEngine.prompt("s", Question.score("Angry?", List.of("calm", "furious")));

        // Then
        assertThat(result).contains("0) calm\n1) furious\n").endsWith("Answer with the number of one level.");
    }

    @Test
    void rendersFieldsAsKeyValueLines() {
        // When
        String result = QwenEngine.render(Content.fields(Map.of("order", 42)));

        // Then
        assertThat(result).isEqualTo("order: 42");
    }

    @Test
    void rendersMessagesOnePerLine() {
        // When
        String result = QwenEngine.render(Content.messages(List.of("a", "b")));

        // Then
        assertThat(result).isEqualTo("a\nb");
    }
}
