package ai.typesafe;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("acceptance")
class TypesafeClientAcceptanceTest {

    @Test
    void evaluatesANoulQuestionAgainstTheLiveApi() throws Exception {
        TypesafeClient client = TypesafeClient.withDefaultToken();

        EvaluateRequest request = EvaluateRequest.of(
                "Help! My payouts have been failing for 3 days.",
                Map.of("is_urgent", Question.noul("Does this convey urgency?",
                        Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"))));

        EvaluateResponse response = client.evaluate(request);

        assertTrue(response.model().startsWith("jev-"));
        Answer answer = response.answers().get("is_urgent");
        assertInstanceOf(Answer.Noul.class, answer);
        double noul = ((Answer.Noul) answer).noul();
        System.out.println("is_urgent noul score = " + noul);
        assertTrue(noul >= 0.0 && noul <= 1.0);
        assertTrue(response.usage().inputTokens() > 0);
        assertTrue(response.metadata().requestId().value().startsWith("req_"));
        assertTrue(response.metadata().upstreamServiceTime().toMillis() > 0);
    }
}
