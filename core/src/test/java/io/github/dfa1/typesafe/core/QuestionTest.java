package io.github.dfa1.typesafe.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTest {

    @Test
    void noulDefaultsCriteriaToNullWhenOmitted() {
        // When
        Question.Noul result = Question.noul("Is this urgent?");

        // Then
        assertThat(result.criteria()).isNull();
    }
}
