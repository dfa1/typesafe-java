package io.github.dfa1.typesafe.cli;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.Usage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MainTest {

    @Test
    void parseReadsStateModelAndAllFlagTypes() {
        // When
        Main.ParsedArgs result = Main.parse(new String[] {
                "--state", "My card was charged twice.",
                "--model", "jev-preview",
                "--noul", "urgent=Is this urgent?",
                "--choice", "category=Pick one|a,b,c",
                "--score", "severity=Rate it|low,mid,high",
                "--min", "urgent=0.5",
                "--print", "urgent",
                "--verbose",
                "--timing"
        });

        // Then
        assertThat(result.state()).isEqualTo("My card was charged twice.");
        assertThat(result.model()).isEqualTo(Model.PREVIEW);
        assertThat(result.questions()).containsOnlyKeys("urgent", "category", "severity");
        assertThat(result.minSpecs()).containsExactly("urgent=0.5");
        assertThat(result.printNames()).containsExactly("urgent");
        assertThat(result.verbose()).isTrue();
        assertThat(result.timing()).isTrue();
    }

    @Test
    void parseDefaultsQuestionNameToTheFlagWhenNoNameIsGiven() {
        // When
        Main.ParsedArgs result = Main.parse(new String[] {"--state", "hi", "--noul", "Is this urgent?"});

        // Then
        assertThat(result.questions()).containsOnlyKeys("noul");
    }

    @Test
    void parseRejectsUnknownFlag() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.parse(new String[] {"--bogus"}))
                .withMessageContaining("--bogus");
    }

    @Test
    void parseRejectsAFlagMissingItsValue() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.parse(new String[] {"--state"}))
                .withMessageContaining("--state");
    }

    @Test
    void parseRejectsMissingState() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.parse(new String[] {"--noul", "Is this urgent?"}))
                .withMessageContaining("--state");
    }

    @Test
    void parseRejectsWhenNoQuestionIsGiven() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.parse(new String[] {"--state", "hi"}))
                .withMessageContaining("question");
    }

    @Test
    void modelByIdResolvesKnownId() {
        // When
        Model result = Main.modelById("jev-preview");

        // Then
        assertThat(result).isEqualTo(Model.PREVIEW);
    }

    @Test
    void modelByIdRejectsUnknownId() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.modelById("bogus"))
                .withMessageContaining("bogus");
    }

    @Test
    void questionBuildsNoulWithoutOptions() {
        // When
        Question result = Main.question("--noul", "Is this urgent?");

        // Then
        assertThat(result).isEqualTo(Question.noul("Is this urgent?", Map.of()));
    }

    @Test
    void questionBuildsChoiceWithOptions() {
        // When
        Question result = Main.question("--choice", "Pick one|a,b,c");

        // Then
        assertThat(result).isEqualTo(Question.choice("Pick one", Map.of("a", "", "b", "", "c", "")));
    }

    @Test
    void questionBuildsScoreWithOrderedLevels() {
        // When
        Question result = Main.question("--score", "Rate it|low,mid,high");

        // Then
        assertThat(result).isEqualTo(Question.score("Rate it", List.of("low", "mid", "high")));
    }

    @Test
    void answerValueReturnsNoulProbability() {
        // Given
        EvaluateResponse sut = response(Map.of("urgent", new Answer.Noul(0.5)));

        // When
        String result = Main.answerValue(sut, "urgent");

        // Then
        assertThat(result).isEqualTo("0.5");
    }

    @Test
    void answerValueReturnsChoicePick() {
        // Given
        EvaluateResponse sut = response(Map.of(
                "category", new Answer.Choice("billing", Map.of("billing", 1.0), 1.0)));

        // When
        String result = Main.answerValue(sut, "category");

        // Then
        assertThat(result).isEqualTo("billing");
    }

    @Test
    void answerValueReturnsScoreValue() {
        // Given
        EvaluateResponse sut = response(Map.of(
                "severity", new Answer.Score(2.5, Map.of(), Map.of(), 1.0)));

        // When
        String result = Main.answerValue(sut, "severity");

        // Then
        assertThat(result).isEqualTo("2.5");
    }

    @Test
    void answerValueRejectsUnknownName() {
        // Given
        EvaluateResponse sut = response(Map.of("urgent", new Answer.Noul(0.5)));

        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.answerValue(sut, "bogus"))
                .withMessageContaining("bogus");
    }

    @Test
    void minFailuresIsEmptyWhenAboveThreshold() {
        // Given
        EvaluateResponse sut = response(Map.of("urgent", new Answer.Noul(0.6)));

        // When
        List<String> result = Main.minFailures(sut, List.of("urgent=0.5"));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void minFailuresComparesAScoreAnswerToo() {
        // Given
        EvaluateResponse sut = response(Map.of(
                "severity", new Answer.Score(2.5, Map.of(), Map.of(), 1.0)));

        // When
        List<String> result = Main.minFailures(sut, List.of("severity=3.0"));

        // Then
        assertThat(result).containsExactly("severity=2.5 < 3.0");
    }

    @Test
    void minFailuresReportsWhenBelowThreshold() {
        // Given
        EvaluateResponse sut = response(Map.of("urgent", new Answer.Noul(0.6)));

        // When
        List<String> result = Main.minFailures(sut, List.of("urgent=0.9"));

        // Then
        assertThat(result).containsExactly("urgent=0.6 < 0.9");
    }

    @Test
    void minFailuresRejectsUnknownName() {
        // Given
        EvaluateResponse sut = response(Map.of("urgent", new Answer.Noul(0.6)));

        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.minFailures(sut, List.of("bogus=0.5")))
                .withMessageContaining("bogus");
    }

    @Test
    void minFailuresRejectsChoiceAnswers() {
        // Given
        EvaluateResponse sut = response(Map.of(
                "category", new Answer.Choice("billing", Map.of("billing", 1.0), 1.0)));

        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Main.minFailures(sut, List.of("category=0.5")))
                .withMessageContaining("category");
    }

    private static EvaluateResponse response(Map<String, Answer> answers) {
        return new EvaluateResponse("jev-latest", answers, new Usage(0, 0),
                new EvaluateResponse.Metadata(null, null));
    }
}
