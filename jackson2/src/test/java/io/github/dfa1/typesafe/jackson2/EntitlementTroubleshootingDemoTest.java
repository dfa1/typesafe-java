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

/**
 * Demo: a support agent gets "why am I not receiving data from a market?"
 * and has to manually check contract, entitlements, and delivery logs
 * across several systems. Here those facts are pulled into one blob and
 * TypeSafe Choice/Score questions rank the likely root cause with
 * confidence, instead of a human eyeballing every layer by hand.
 */
@Tag("acceptance")
class EntitlementTroubleshootingDemoTest {

    @Test
    void diagnosesWhyMarketDataIsMissing() throws Exception {
        // Given
        TypesafeClient sut = TypesafeClient.withDefaultToken();

        String state = """
                CLIENT COMPLAINT:
                "We are not receiving intraday data for SIX Swiss Exchange (XSWX) \
                equities since Monday."

                CONTRACT:
                Tier: Real-time Level 1, Europe bundle
                Market licenses: LSE, XETRA
                Redistribution rights: internal use only

                ACCOUNT STATUS:
                Active, no billing hold, no recent changes

                DELIVERY LOG (last 7 days):
                API feed connection: up, no errors
                Last successful message: today, other subscribed markets fine
                """;

        EvaluateRequest request = EvaluateRequest.of(State.text(state), Map.of(
                "root_cause", Question.choice(
                        "Given the contract, account status, and delivery log, "
                                + "what is the most likely reason this client isn't receiving XSWX data?",
                        Map.of(
                                "market_license_missing", "The requested market isn't in the client's licensed markets",
                                "instrument_class_not_entitled", "The instrument class itself isn't entitled",
                                "redistribution_restricted", "Blocked by redistribution rights, not market access",
                                "technical_delivery_fault", "Feed/config/connectivity problem, entitlements are fine",
                                "billing_hold", "Account or billing issue is blocking delivery")),
                "needs_specialist_escalation", Question.choice(
                        "Should this be escalated to the entitlement team, or is it self-service "
                                + "(e.g. support can just point the client at adding the market to their contract)?",
                        Map.of(
                                "yes", "Needs entitlement specialist review",
                                "no", "Support can resolve directly with the client"))));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        Answer.Choice rootCause = (Answer.Choice) result.answers().get("root_cause");
        Answer.Choice escalation = (Answer.Choice) result.answers().get("needs_specialist_escalation");

        System.out.println("root cause: " + rootCause.choice()
                + " (confidence " + rootCause.confidence() + ")");
        System.out.println("probabilities: " + rootCause.probabilities());
        System.out.println("needs escalation: " + escalation.choice()
                + " (confidence " + escalation.confidence() + ")");

        assertThat(rootCause.choice()).isEqualTo("market_license_missing");
    }
}
