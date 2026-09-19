package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("acceptance")
class TypesafeClientAcceptanceTest {

    @Test
    void evaluatesANoulQuestionAgainstTheLiveApi() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.withDefaultToken();
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Help! My payouts have been failing for 3 days."),
                Map.of("is_urgent", Question.noul("Does this convey urgency?",
                        Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"))));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.model()).startsWith("jev-");
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class, answer -> {
            System.out.println("is_urgent noul score = " + answer.noul());
            assertThat(answer.noul()).isBetween(0.0, 1.0);
        });
        assertThat(result.usage().inputTokens()).isPositive();
        assertThat(result.metadata().requestId().value()).startsWith("req_");
        assertThat(result.metadata().upstreamServiceTime().toMillis()).isPositive();
    }

    @Test
    void evaluatesAsyncAgainstTheLiveApi() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.withDefaultToken();
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Help! My payouts have been failing for 3 days."),
                Map.of("is_urgent", Question.noul("Does this convey urgency?",
                        Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"))));

        // When
        EvaluateResponse result = sut.evaluateAsync(request).get();

        // Then
        assertThat(result.answers().get("is_urgent")).isInstanceOfSatisfying(Answer.Noul.class,
                answer -> assertThat(answer.noul()).isBetween(0.0, 1.0));
    }
}
