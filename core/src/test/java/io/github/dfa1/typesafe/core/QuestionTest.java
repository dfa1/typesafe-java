package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTest {

    @Test
    void noulDefaultsCriteriaToNullWhenOmitted() {
        // When
        Question.Noul result = Question.noul("Is this urgent?");

        // Then
        assertThat(result.criteria()).isNull();
    }

    @Test
    void noulCarriesExplicitCriteria() {
        // When
        Question.Noul result = Question.noul("Is this urgent?",
                Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"));

        // Then
        assertThat(result.criteria()).containsEntry("true", "Explicitly time-sensitive");
    }

    @Test
    void stringInstructionsAreSugarForContentText() {
        // When
        Question.Noul noul = Question.noul("Is this urgent?");
        Question.Choice choice = Question.choice("Which team?", Map.of("billing", ""));
        Question.Score score = Question.score("How frustrated?", List.of("Calm", "Angry"));

        // Then
        assertThat(noul.instructions()).isEqualTo(Content.text("Is this urgent?"));
        assertThat(choice.instructions()).isEqualTo(Content.text("Which team?"));
        assertThat(score.instructions()).isEqualTo(Content.text("How frustrated?"));
    }

    @Test
    void acceptsStructuredContentInstructions() {
        // Given
        Content instructions = Content.fields(Map.of(
                "potential_duplicate", Map.of("name", "John Smith"),
                "question", "Is the resume for the same person as `potential_duplicate`?"));

        // When
        Question.Noul result = Question.noul(instructions);

        // Then
        assertThat(result.instructions()).isEqualTo(instructions);
        assertThat(result.criteria()).isNull();
    }
}
