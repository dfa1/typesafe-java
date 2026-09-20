package io.github.dfa1.typesafe.cli;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.RequestModel;
import io.github.dfa1.typesafe.core.TypesafeClient;
import io.github.dfa1.typesafe.core.Usage;
import io.github.dfa1.typesafe.jackson3.Jackson3Codec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MainTest {

    @Mock
    private TypesafeClient client;

    private final Jackson3Codec codec = new Jackson3Codec();
    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outBuffer);
    private final PrintStream err = new PrintStream(errBuffer);

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
        assertThat(result.model()).isEqualTo(RequestModel.Alias.PREVIEW);
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
    void requestModelResolvesKnownAliases() {
        // When / Then
        assertThat(Main.requestModel("jev-latest")).isEqualTo(RequestModel.Alias.LATEST);
        assertThat(Main.requestModel("jev-preview")).isEqualTo(RequestModel.Alias.PREVIEW);
    }

    @Test
    void requestModelPinsAnyOtherIdAsAModel() {
        // When
        RequestModel result = Main.requestModel("jev-1.13.0");

        // Then
        assertThat(result).isEqualTo(new RequestModel.Pinned("jev-1.13.0", null, null));
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

    @Test
    void runPrintsTheFullResponseAsJsonWhenNoPrintNamesAreGiven() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.5))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of(), List.of(), false, false);

        // When
        int result = Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(result).isZero();
        assertThat(outBuffer.toString()).contains("\"noul\":0.5");
    }

    @Test
    void runPrintsOnlyTheRequestedAnswersWhenPrintNamesAreGiven() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.5))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of(), List.of("urgent"), false, false);

        // When
        int result = Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(result).isZero();
        assertThat(outBuffer.toString()).isEqualToNormalizingNewlines("0.5\n");
    }

    @Test
    void runReturns1AndReportsMinFailuresOnStderr() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.2))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of("urgent=0.5"), List.of(), false, false);

        // When
        int result = Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(result).isEqualTo(1);
        assertThat(errBuffer.toString()).contains("--min failed: urgent=0.2 < 0.5");
    }

    @Test
    void runPrintsRequestAndResponseToStderrWhenVerbose() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.5))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of(), List.of(), true, false);

        // When
        Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(errBuffer.toString()).contains("request:").contains("response:").contains("request-id:");
    }

    @Test
    void runPrintsTimingToStderrWhenTiming() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.5))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of(), List.of(), false, true);

        // When
        Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(errBuffer.toString()).contains("time:");
    }

    @Test
    void runReturns1AndPrintsUsageWhenAPrintNameIsUnknown() throws Exception {
        // Given
        given(client.evaluate(any())).willReturn(response(Map.of("urgent", new Answer.Noul(0.5))));
        Main.ParsedArgs parsed = new Main.ParsedArgs("hi", RequestModel.Alias.LATEST,
                Map.of("urgent", Question.noul("Is this urgent?")), List.of(), List.of("bogus"), false, false);

        // When
        int result = Main.run(client, codec, parsed, out, err);

        // Then
        assertThat(result).isEqualTo(1);
        assertThat(errBuffer.toString()).contains("No such answer: bogus").contains("Usage: typesafe");
    }

    @Test
    void runFromArgsPrintsVersionAndReturns0() throws Exception {
        // When
        int result = Main.run(new String[] {"--version"}, out, err);

        // Then
        assertThat(result).isZero();
    }

    @Test
    void runFromArgsReturns1AndPrintsUsageWhenArgsAreInvalid() throws Exception {
        // When
        int result = Main.run(new String[] {"--bogus"}, out, err);

        // Then
        assertThat(result).isEqualTo(1);
        assertThat(errBuffer.toString()).contains("Unknown flag: --bogus").contains("Usage: typesafe");
    }

    private static EvaluateResponse response(Map<String, Answer> answers) {
        return new EvaluateResponse(new RequestModel.Pinned("jev-latest", null, null), answers, new Usage(0, 0),
                new EvaluateResponse.Metadata(null, null));
    }
}
