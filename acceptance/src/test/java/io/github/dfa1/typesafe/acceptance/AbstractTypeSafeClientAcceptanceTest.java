package io.github.dfa1.typesafe.acceptance;

import io.github.dfa1.typesafe.core.ApiKey;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypeSafeClient;
import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.mapping.Choice;
import io.github.dfa1.typesafe.mapping.MappingTypeSafeClient;
import io.github.dfa1.typesafe.mapping.Noul;
import io.github.dfa1.typesafe.mapping.Option;
import io.github.dfa1.typesafe.mapping.Score;
import io.github.dfa1.typesafe.transport.HttpTransport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Acceptance tests against the live TypeSafe API, run once per {@link HttpTransport}/
 * {@link JsonCodec} combination by a concrete subclass (e.g.
 * {@code JdkHttpClientWithJackson2AcceptanceTest}). The model's judgment can change over time,
 * so assertions on {@code Choice}/{@code Score} answers only check the response is structurally
 * well-formed (values in range, probabilities summing to ~1, keys matching what was asked)
 * rather than pinning down a specific answer.
 */
@SuppressWarnings("JavaPrintToLogpoint")
@Tag("acceptance")
abstract class AbstractTypeSafeClientAcceptanceTest {

    private TypeSafeClient sut;

    protected abstract HttpTransport httpTransport();

    protected abstract JsonCodec jsonCodec();

    @BeforeEach
    void setUp() throws Exception {
        sut = TypeSafeClient.builder(ApiKey.fromDefaultFile())
                .httpTransport(httpTransport())
                .jsonCodec(jsonCodec())
                .build();
    }

    @AfterEach
    void tearDown() {
        sut.close();
    }

    @Test
    void evaluatesANoulQuestionAgainstTheLiveApi() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Help! My payouts have been failing for 3 days."),
                Map.of("is_urgent", Question.noul("Does this convey urgency?",
                        Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"))));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.model().name()).startsWith("jev-");
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

    @Test
    void noulRatesHowGruntledACustomerReallyIs() {
        // Given
        EvaluateRequest request = EvaluateRequest.of(
                State.text("""
                        I've been a customer for six years and this is, hands down, the single
                        worst support interaction I have ever had with any company, ever.
                        """),
                Map.of("would_recommend", Question.noul("Would this customer recommend the company to a friend?",
                        Map.of("true", "Would recommend", "false", "Would not recommend"))));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.answers().get("would_recommend")).isInstanceOfSatisfying(Answer.Noul.class, answer -> {
            System.out.println("would_recommend noul = " + answer.noul());
            assertThat(answer.noul()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void choicePicksTheCulpritBehindAPizzaOrderGoneWrong() {
        // Given
        Map<String, String> suspects = Map.of(
                "wrong_toppings", "The pizza arrived with the wrong toppings entirely",
                "missing_item", "A side item was missing from the order",
                "late_delivery", "The order arrived very late",
                "cold_food", "The food arrived cold");
        EvaluateRequest request = EvaluateRequest.of(
                State.text("""
                        I ordered a pepperoni and mushroom pizza with a side of garlic bread.
                        Ninety minutes later a plain cheese pizza showed up, ice cold, with no
                        garlic bread in sight.
                        """),
                Map.of("root_cause", Question.choice(
                        "What is the single biggest problem with this order?", suspects)));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.answers().get("root_cause")).isInstanceOfSatisfying(Answer.Choice.class, answer -> {
            System.out.println("root_cause choice = " + answer.choice()
                    + " (confidence " + answer.confidence() + ")");
            System.out.println("root_cause probabilities = " + answer.probabilities());

            assertThat(answer.choice()).isIn(suspects.keySet());
            assertThat(answer.confidence()).isBetween(0.0, 1.0);
            assertThat(answer.probabilities()).containsOnlyKeys(suspects.keySet());
            assertThat(answer.probabilities().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(0.02));
        });
    }

    @Test
    void scoreRatesTheSpicinessOfAChiliDescription() {
        // Given
        List<String> heatLevels = List.of("Mild", "Medium", "Hot", "Face-melting");
        EvaluateRequest request = EvaluateRequest.of(
                State.text("""
                        This chili is built around three varieties of ghost pepper, a splash of
                        pure capsaicin extract, and a garnish of raw habaneros for crunch.
                        """),
                Map.of("heat_level", Question.score("How spicy does this dish sound?", heatLevels)));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.answers().get("heat_level")).isInstanceOfSatisfying(Answer.Score.class, answer -> {
            System.out.println("heat_level score = " + answer.score()
                    + " (confidence " + answer.confidence() + ")");
            System.out.println("heat_level legend = " + answer.legend());
            System.out.println("heat_level probabilities = " + answer.probabilities());

            List<String> expectedIndices = IntStream.range(0, heatLevels.size())
                    .mapToObj(String::valueOf)
                    .toList();

            assertThat(answer.score()).isBetween(0.0, (double) (heatLevels.size() - 1));
            assertThat(answer.confidence()).isBetween(0.0, 1.0);
            assertThat(answer.legend()).containsOnlyKeys(expectedIndices);
            assertThat(answer.probabilities()).containsOnlyKeys(expectedIndices);
            assertThat(answer.probabilities().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(0.02));
        });
    }

    @Test
    void fillsGraphqlFilterSlotsFromHumanText() {
        // Given
        Map<String, String> markets = Map.of("US", "United States market", "EU", "European market",
                "ASIA", "Asian markets");
        Map<String, String> instrumentTypes = Map.of("bond", "Bonds", "equity", "Equities", "fx", "FX instruments");
        EvaluateRequest request = EvaluateRequest.of(
                State.text("Give me all instruments on US market of type bond"),
                Map.of(
                        "market", Question.choice("Which market is the request about?", markets),
                        "instrument_type", Question.choice("Which instrument type is the request about?",
                                instrumentTypes)));

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

            assertThat(market.choice()).isIn(markets.keySet());
            assertThat(instrumentType.choice()).isIn(instrumentTypes.keySet());
        }
    }

    @Test
    void diagnosesWhyMarketDataIsMissing() {
        // Given
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
        Map<String, String> rootCauses = Map.of(
                "market_license_missing", "The requested market isn't in the client's licensed markets",
                "instrument_class_not_entitled", "The instrument class itself isn't entitled",
                "redistribution_restricted", "Blocked by redistribution rights, not market access",
                "technical_delivery_fault", "Feed/config/connectivity problem, entitlements are fine",
                "billing_hold", "Account or billing issue is blocking delivery");

        EvaluateRequest request = EvaluateRequest.of(State.text(state), Map.of(
                "root_cause", Question.choice(
                        "Given the contract, account status, and delivery log, "
                                + "what is the most likely reason this client isn't receiving XSWX data?",
                        rootCauses),
                "needs_specialist_escalation", Question.choice(
                        "Should this be escalated to the entitlement team, or is it self-service "
                                + "(e.g. support can just point the client at adding the market to their contract)?",
                        Map.of(
                                "yes", "Needs entitlement specialist review",
                                "no", "Support can resolve directly with the client"))));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.answers().get("root_cause")).isInstanceOfSatisfying(Answer.Choice.class, rootCause -> {
            System.out.println("root cause: " + rootCause.choice() + " (confidence " + rootCause.confidence() + ")");
            System.out.println("probabilities: " + rootCause.probabilities());
            assertThat(rootCause.choice()).isIn(rootCauses.keySet());
        });
        assertThat(result.answers().get("needs_specialist_escalation")).isInstanceOfSatisfying(Answer.Choice.class,
                escalation -> {
                    System.out.println("needs escalation: " + escalation.choice()
                            + " (confidence " + escalation.confidence() + ")");
                    assertThat(escalation.choice()).isIn("yes", "no");
                });
    }

    @Test
    void triagesASupportTicketByUrgencyCategoryAndFrustrationInOneCall() {
        // Given
        Map<String, String> categories = Map.of(
                "billing", "Payments, charges, refunds",
                "technical", "Outages, bugs, errors",
                "shipping", "Delivery and tracking",
                "feedback", "Praise or general comments",
                "other", "Anything else");
        List<String> frustrationLevels = List.of("Calm", "Annoyed", "Frustrated", "Furious");
        EvaluateRequest request = EvaluateRequest.of(
                State.text("My card was charged twice for the same order. This is the third "
                        + "time this has happened and I'm about done with this company."),
                Map.of(
                        "urgent", Question.noul("Is this urgent?"),
                        "category", Question.choice("What kind of issue is this?", categories),
                        "frustration", Question.score("How frustrated is the customer?", frustrationLevels)));

        // When
        EvaluateResponse result = sut.evaluate(request);

        // Then
        assertThat(result.answers().get("urgent")).isInstanceOfSatisfying(Answer.Noul.class, urgent -> {
            System.out.println("urgent noul = " + urgent.noul());
            assertThat(urgent.noul()).isBetween(0.0, 1.0);
        });
        assertThat(result.answers().get("category")).isInstanceOfSatisfying(Answer.Choice.class, category -> {
            System.out.println("category = " + category.choice() + " (confidence " + category.confidence() + ")");
            assertThat(category.choice()).isIn(categories.keySet());
        });
        assertThat(result.answers().get("frustration")).isInstanceOfSatisfying(Answer.Score.class, frustration -> {
            System.out.println("frustration score = " + frustration.score());
            assertThat(frustration.score()).isBetween(0.0, (double) (frustrationLevels.size() - 1));
        });
    }

    /** {@code levels()} on {@link ItalianFoodVerdict#nonnaOutrage} below, mirrored here as a
     *  plain list since an annotation attribute can't reference a shared constant array — only
     *  an inline literal. Used to print the matching level name and bound the score assertion. */
    private static final List<String> NONNA_OUTRAGE_LEVELS =
            List.of("Mild disapproval", "Visible disappointment", "Loud protest", "Disowned from the family");

    /** A {@code Noul}, a {@code Choice} among three real Italian dining faux pas, and a
     *  {@code Score} across an outrage scale, all in one record — for
     *  {@link #judgesATouristsDiningChoicesLikeATraditionalItalianNonnaWould}. */
    record ItalianFoodVerdict(
            @Noul("Would a traditional Italian consider these dining choices a violation of culinary etiquette?")
            double isFoodHeresy,
            @Choice(value = "Which single choice described is the most egregious Italian food faux pas?", options = {
                    @Option(value = "pineapple_pizza", description = "Pizza topped with pineapple (ananas)"),
                    @Option(value = "cappuccino_after_dinner",
                            description = "Ordering a cappuccino after 9pm or after a meal, instead of an espresso"),
                    @Option(value = "parmesan_on_seafood", description = "Adding grated parmesan to a seafood pasta dish") })
            String worstOffense,
            @Score(value = "How strongly would a traditional Italian nonna react to these choices, from mild "
                    + "disapproval to disowning the tourist from the family?", levels = {
                            "Mild disapproval", "Visible disappointment", "Loud protest", "Disowned from the family" })
            double nonnaOutrage) {
    }

    @Test
    void judgesATouristsDiningChoicesLikeATraditionalItalianNonnaWould() {
        // Given
        MappingTypeSafeClient typedClient = MappingTypeSafeClient.decorate(sut);
        State state = State.text("""
                A tourist visiting Rome sits down at a trattoria at 9:30pm for dinner. They order
                a seafood spaghetti and ask the waiter to grate parmesan cheese generously over
                it. To finish the meal, they order a large cappuccino. Earlier that day, for
                lunch, they'd ordered a margherita pizza but asked the chef to add pineapple
                chunks on top.
                """);

        // When
        ItalianFoodVerdict verdict = typedClient.evaluateTyped(state, ItalianFoodVerdict.class);

        // Then
        System.out.println("isFoodHeresy = " + verdict.isFoodHeresy());
        System.out.println("worstOffense = " + verdict.worstOffense());
        System.out.println("nonnaOutrage = " + verdict.nonnaOutrage()
                + " (" + NONNA_OUTRAGE_LEVELS.get((int) Math.round(verdict.nonnaOutrage())) + ")");

        assertThat(verdict.isFoodHeresy()).isBetween(0.0, 1.0);
        assertThat(verdict.worstOffense()).isIn("pineapple_pizza", "cappuccino_after_dinner", "parmesan_on_seafood");
        assertThat(verdict.nonnaOutrage()).isBetween(0.0, (double) (NONNA_OUTRAGE_LEVELS.size() - 1));
    }

    @Test
    void judgesATouristsDiningChoicesAsynchronouslyToo() throws Exception {
        // Given
        MappingTypeSafeClient typedClient = MappingTypeSafeClient.decorate(sut);
        State state = State.text("""
                A tourist visits a historic pizzeria in Naples — the birthplace of pizza — and
                orders a hawaiian pizza, insisting the chef pile on extra pineapple chunks.
                After finishing dinner around 10pm, they order a cappuccino to end the night.
                """);

        // When
        ItalianFoodVerdict verdict = typedClient.evaluateTypedAsync(state, ItalianFoodVerdict.class).get();

        // Then
        System.out.println("isFoodHeresy = " + verdict.isFoodHeresy());
        System.out.println("worstOffense = " + verdict.worstOffense());
        System.out.println("nonnaOutrage = " + verdict.nonnaOutrage()
                + " (" + NONNA_OUTRAGE_LEVELS.get((int) Math.round(verdict.nonnaOutrage())) + ")");

        assertThat(verdict.isFoodHeresy()).isBetween(0.0, 1.0);
        assertThat(verdict.worstOffense()).isIn("pineapple_pizza", "cappuccino_after_dinner", "parmesan_on_seafood");
        assertThat(verdict.nonnaOutrage()).isBetween(0.0, (double) (NONNA_OUTRAGE_LEVELS.size() - 1));
    }
}
