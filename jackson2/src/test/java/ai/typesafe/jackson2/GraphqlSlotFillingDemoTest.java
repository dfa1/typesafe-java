package ai.typesafe.jackson2;

import ai.typesafe.Answer;
import ai.typesafe.EvaluateRequest;
import ai.typesafe.EvaluateResponse;
import ai.typesafe.Question;
import ai.typesafe.TypesafeClient;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demo: turn a human-language instrument request into a GraphQL query by
 * using TypeSafe Choice questions to fill the query's filter slots.
 */
@Tag("acceptance")
class GraphqlSlotFillingDemoTest {

    @Test
    void fillsGraphqlFilterSlotsFromHumanText() throws Exception {
        TypesafeClient client = TypesafeClient.withDefaultToken();

        String text = "Give me all instruments on US market of type bond";

        EvaluateRequest request = EvaluateRequest.of(text, Map.of(
                "market", Question.choice("Which market is the request about?",
                        Map.of("US", "United States market", "EU", "European market", "ASIA", "Asian markets")),
                "instrument_type", Question.choice("Which instrument type is the request about?",
                        Map.of("bond", "Bonds", "equity", "Equities", "fx", "FX instruments"))));

        for (int i = 1; i <= 10; i++) {
            Instant start = Instant.now();
            EvaluateResponse response = client.evaluate(request);
            Duration endToEnd = Duration.between(start, Instant.now());

            Answer.Choice market = (Answer.Choice) response.answers().get("market");
            Answer.Choice instrumentType = (Answer.Choice) response.answers().get("instrument_type");

            System.out.printf("run %2d: model = %4d ms, end-to-end = %4d ms%n",
                    i, response.metadata().upstreamServiceTime().toMillis(), endToEnd.toMillis());

            assertTrue(market.choice().equals("US"));
            assertTrue(instrumentType.choice().equals("bond"));
        }
    }
}
