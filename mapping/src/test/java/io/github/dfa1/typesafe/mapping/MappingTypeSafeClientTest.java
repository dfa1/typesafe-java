package io.github.dfa1.typesafe.mapping;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.Usage;
import io.github.dfa1.typesafe.testkit.RecordingTypeSafeClient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingTypeSafeClientTest {

    record TicketUrgency(
            @Noul("Does this convey urgency?") double isUrgent,
            @Choice(value = "Who's at fault?", options = {
                    @Option("wrong_toppings"), @Option(value = "late_delivery", description = "Arrived late") })
            String culprit,
            @Score(value = "How spicy?", levels = { "Mild", "Medium", "Hot" }) double spiciness) {
    }

    record NotAnnotated(String value) {
    }

    record WrongComponentType(@Noul("yes/no?") String notADouble) {
    }

    record DoublyAnnotated(@Noul("yes/no?") @Score(value = "how much?", levels = { "low", "high" }) double both) {
    }

    record DuplicateOption(
            @Choice(value = "which?", options = { @Option("a"), @Option(value = "a", description = "again") })
            String choice) {
    }

    record SingleNoul(@Noul("Does this convey urgency?") double isUrgent) {
    }

    @Test
    void evaluateTypedBuildsTheRequestFromAnnotatedComponentsAndMapsTheAnswersBack() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueEvaluate(new EvaluateResponse(
                Model.LATEST,
                Map.of(
                        "isUrgent", new Answer.Noul(0.9),
                        "culprit", new Answer.Choice("late_delivery", Map.of("late_delivery", 1.0), 1.0),
                        "spiciness", new Answer.Score(2.0, Map.of(), Map.of(), 1.0)),
                new Usage(1, 1), null));
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);
        State state = State.text("Help! My payouts have been failing for 3 days.");

        // When
        TicketUrgency result = sut.evaluateTyped(state, TicketUrgency.class);

        // Then
        assertThat(result).isEqualTo(new TicketUrgency(0.9, "late_delivery", 2.0));
        Question.Noul noul = (Question.Noul) delegate.evaluateRequests().get(0).questions().get("isUrgent");
        assertThat(noul.instructions()).isEqualTo("Does this convey urgency?");
        Question.Choice choice = (Question.Choice) delegate.evaluateRequests().get(0).questions().get("culprit");
        assertThat(choice.criteria()).containsEntry("late_delivery", "Arrived late").containsEntry("wrong_toppings", "");
        Question.Score score = (Question.Score) delegate.evaluateRequests().get(0).questions().get("spiciness");
        assertThat(score.criteria()).containsExactly("Mild", "Medium", "Hot");
    }

    @Test
    void evaluateTypedAsyncMapsTheAnswersBackToo() throws Exception {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueEvaluate(new EvaluateResponse(
                Model.LATEST,
                Map.of(
                        "isUrgent", new Answer.Noul(0.1),
                        "culprit", new Answer.Choice("wrong_toppings", Map.of("wrong_toppings", 1.0), 1.0),
                        "spiciness", new Answer.Score(0.5, Map.of(), Map.of(), 1.0)),
                new Usage(1, 1), null));
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When
        TicketUrgency result = sut.evaluateTypedAsync(State.text("hi"), TicketUrgency.class).get();

        // Then
        assertThat(result).isEqualTo(new TicketUrgency(0.1, "wrong_toppings", 0.5));
    }

    @Test
    void evaluateTypedRejectsARecordWithAnUnannotatedComponent() {
        // Given
        MappingTypeSafeClient sut = new MappingTypeSafeClient(new RecordingTypeSafeClient());

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), NotAnnotated.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Noul/@Choice/@Score");
    }

    @Test
    void evaluateTypedRejectsAComponentTypeMismatchedWithItsAnnotation() {
        // Given
        MappingTypeSafeClient sut = new MappingTypeSafeClient(new RecordingTypeSafeClient());

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), WrongComponentType.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be double");
    }

    @Test
    void evaluateTypedRejectsAComponentCarryingMoreThanOneAnnotation() {
        // Given
        MappingTypeSafeClient sut = new MappingTypeSafeClient(new RecordingTypeSafeClient());

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), DoublyAnnotated.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of @Noul/@Choice/@Score");
    }

    @Test
    void evaluateTypedRejectsADuplicateOptionKey() {
        // Given
        MappingTypeSafeClient sut = new MappingTypeSafeClient(new RecordingTypeSafeClient());

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), DuplicateOption.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate @Option(\"a\")");
    }

    @Test
    void evaluateTypedThrowsAClearErrorWhenTheResponseIsMissingAnAnswer() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient()
                .enqueueEvaluate(new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null));
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), SingleNoul.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No answer for \"isUrgent\"");
    }

    @Test
    void evaluateTypedThrowsAClearErrorWhenTheAnswerShapeDoesNotMatchItsAnnotation() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueEvaluate(new EvaluateResponse(
                Model.LATEST,
                Map.of("isUrgent", new Answer.Choice("x", Map.of("x", 1.0), 1.0)),
                new Usage(1, 1), null));
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When / Then
        assertThatThrownBy(() -> sut.evaluateTyped(State.text("hi"), SingleNoul.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("isUrgent")
                .hasMessageContaining("Choice")
                .cause().isInstanceOf(ClassCastException.class);
    }

    @Test
    void evaluateTypedPinsTheRequestedModel() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueEvaluate(new EvaluateResponse(
                Model.LATEST, Map.of("isUrgent", new Answer.Noul(0.5)), new Usage(1, 1), null));
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When
        sut.evaluateTyped(State.text("hi"), Model.PREVIEW, SingleNoul.class);

        // Then
        assertThat(delegate.evaluateRequests().get(0).model()).isEqualTo(Model.PREVIEW);
    }

    @Test
    void evaluateTypedReusesTheCachedMappingAcrossRepeatedCallsForTheSameRecordType() {
        // Given
        EvaluateResponse response = new EvaluateResponse(
                Model.LATEST, Map.of("isUrgent", new Answer.Noul(0.5)), new Usage(1, 1), null);
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient()
                .enqueueEvaluate(response).enqueueEvaluate(response);
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When
        SingleNoul first = sut.evaluateTyped(State.text("hi"), SingleNoul.class);
        SingleNoul second = sut.evaluateTyped(State.text("hi"), SingleNoul.class);

        // Then
        assertThat(first).isEqualTo(second).isEqualTo(new SingleNoul(0.5));
    }

    @Test
    void evaluateDelegatesStraightThroughUnchanged() {
        // Given
        EvaluateResponse response = new EvaluateResponse(Model.LATEST, Map.of(), new Usage(1, 1), null);
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueEvaluate(response);
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);
        EvaluateRequest request = EvaluateRequest.of(State.text("hi"), Map.of());

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result).isSameAs(response);
        assertThat(delegate.evaluateRequests()).containsExactly(request);
    }

    @Test
    void listModelsDelegatesStraightThroughUnchanged() {
        // Given
        RecordingTypeSafeClient delegate = new RecordingTypeSafeClient().enqueueModels(List.of());
        MappingTypeSafeClient sut = new MappingTypeSafeClient(delegate);

        // When / Then
        assertThat(sut.listModels()).isEmpty();
    }
}
