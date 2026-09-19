package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo: turn a human-language instrument request into a GraphQL query by
 * using TypeSafe Choice questions to fill the query's filter slots.
 */
@Tag("acceptance")
class GraphqlSlotFillingDemoTest {

    @Test
    void fillsGraphqlFilterSlotsFromHumanText() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.withDefaultToken();
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Give me all instruments on US market of type bond"),
                Map.of(
                        "market", Question.choice("Which market is the request about?",
                                Map.of("US", "United States market", "EU", "European market", "ASIA", "Asian markets")),
                        "instrument_type", Question.choice("Which instrument type is the request about?",
                                Map.of("bond", "Bonds", "equity", "Equities", "fx", "FX instruments"))));

        for (int i = 1; i <= 10; i++) {
            // When
            Instant start = Instant.now();
            EvaluateResponse result = sut.evaluate(request);
            Duration endToEnd = Duration.between(start, Instant.now());

            // Then
            Answer.Choice market = (Answer.Choice) result.answers().get("market");
            Answer.Choice instrumentType = (Answer.Choice) result.answers().get("instrument_type");

            System.out.printf("run %2d: model = %4d ms, end-to-end = %4d ms%n",
                    i, result.metadata().upstreamServiceTime().toMillis(), endToEnd.toMillis());

            assertThat(market.choice()).isEqualTo("US");
            assertThat(instrumentType.choice()).isEqualTo("bond");
        }
    }
}
