package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

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
}
