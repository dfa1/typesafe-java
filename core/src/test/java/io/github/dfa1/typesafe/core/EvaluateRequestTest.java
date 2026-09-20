package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EvaluateRequestTest {

    @Test
    void defaultsToTheLatestModel() {
        // When
        EvaluateRequest result = EvaluateRequest.of(State.text("state"), Map.of());

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
    }

    @Test
    void picksAnExplicitModel() {
        // When
        EvaluateRequest result = EvaluateRequest.of(State.text("state"), Model.PREVIEW, Map.of());

        // Then
        assertThat(result.model()).isEqualTo(Model.PREVIEW);
    }

    @Test
    void builderDefaultsToTheLatestModelAndTextState() {
        // When
        EvaluateRequest result = EvaluateRequest.builder()
                .state("Help! My payouts have been failing for 3 days.")
                .noul("is_urgent", "Does this convey urgency?")
                .build();

        // Then
        assertThat(result.model()).isEqualTo(Model.LATEST);
        assertThat(result.state()).isEqualTo(State.text("Help! My payouts have been failing for 3 days."));
        assertThat(result.questions()).containsEntry("is_urgent", Question.noul("Does this convey urgency?"));
    }

    @Test
    void builderAcceptsAStructuredStateAndAnExplicitModel() {
        // When
        EvaluateRequest result = EvaluateRequest.builder()
                .state(State.fields(Map.of("order_id", "o-1")))
                .model(Model.PREVIEW)
                .choice("category", "Pick one", Map.of("billing", ""))
                .score("severity", "Rate it", List.of("low", "high"))
                .build();

        // Then
        assertThat(result.model()).isEqualTo(Model.PREVIEW);
        assertThat(result.state()).isEqualTo(State.fields(Map.of("order_id", "o-1")));
        assertThat(result.questions()).containsKeys("category", "severity");
    }

    @Test
    void builderAcceptsANoulQuestionWithExplicitCriteria() {
        // When
        EvaluateRequest result = EvaluateRequest.builder()
                .state("hi")
                .noul("urgent", "Is this urgent?", Map.of("true", "Yes", "false", "No"))
                .build();

        // Then
        assertThat(result.questions()).containsEntry("urgent",
                Question.noul("Is this urgent?", Map.of("true", "Yes", "false", "No")));
    }

    @Test
    void builderRejectsADuplicateQuestionName() {
        // Given
        EvaluateRequest.Builder sut = EvaluateRequest.builder()
                .state("hi")
                .noul("urgent", "Is this urgent?");

        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sut.choice("urgent", "Pick one", Map.of("a", "")))
                .withMessageContaining("urgent");
    }
}
