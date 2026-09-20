package io.github.dfa1.typesafe.demo;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.ApiToken;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.core.TypesafeClient;
import io.github.dfa1.typesafe.jackson3.Jackson3Codec;
import io.github.dfa1.typesafe.jdk.JdkHttpTransport;

import java.util.List;
import java.util.Map;

/**
 * Runnable showcase: a handful of sample support tickets triaged by urgency ({@code Noul}),
 * category ({@code Choice}), and frustration ({@code Score}) in a single API call each.
 */
public final class SupportTicketTriage {

    private static final List<String> TICKETS = List.of(
            "My card was charged twice for the same order. This is the third time this has "
                    + "happened and I'm about done with this company.",
            "Quick question -- does the app support dark mode yet?",
            "URGENT: our production servers are down and we're losing money every minute. "
                    + "Please call me immediately!",
            "Just wanted to say thanks, the new update fixed the export bug perfectly!",
            "My package was supposed to arrive yesterday but tracking hasn't updated in 3 days."
    );

    private static final Map<String, String> CATEGORIES = Map.of(
            "billing", "Payments, charges, refunds",
            "technical", "Outages, bugs, errors",
            "shipping", "Delivery and tracking",
            "feedback", "Praise or general comments",
            "other", "Anything else");

    private static final List<String> FRUSTRATION_LEVELS = List.of("Calm", "Annoyed", "Frustrated", "Furious");

    private SupportTicketTriage() {
    }

    public static void main(String[] args) throws Exception {
        TypesafeClient client = TypesafeClient.builder(ApiToken.fromDefaultFile())
                .jsonCodec(new Jackson3Codec())
                .httpTransport(new JdkHttpTransport())
                .build();

        for (int i = 0; i < TICKETS.size(); i++) {
            String ticket = TICKETS.get(i);
            EvaluateRequest request = EvaluateRequest.of(State.text(ticket), Map.of(
                    "urgent", Question.noul("Is this urgent?"),
                    "category", Question.choice("What kind of issue is this?", CATEGORIES),
                    "frustration", Question.score("How frustrated is the customer?", FRUSTRATION_LEVELS)));

            EvaluateResponse response = client.evaluate(request);
            Answer.Noul urgent = (Answer.Noul) response.answers().get("urgent");
            Answer.Choice category = (Answer.Choice) response.answers().get("category");
            Answer.Score frustration = (Answer.Score) response.answers().get("frustration");

            System.out.printf("%n%d. %s%n", i + 1, excerpt(ticket));
            System.out.printf("   Urgent:      %.0f%%%n", urgent.noul() * 100);
            System.out.printf("   Category:    %s (%.0f%% confidence)%n",
                    category.choice(), category.confidence() * 100);
            System.out.printf("   Frustration: %s (%.1f/%d)%n",
                    nearestLevel(frustration), frustration.score(), FRUSTRATION_LEVELS.size() - 1);
        }
    }

    private static String excerpt(String ticket) {
        return ticket.length() <= 70 ? ticket : ticket.substring(0, 67) + "...";
    }

    private static String nearestLevel(Answer.Score score) {
        int index = (int) Math.round(score.score());
        return score.legend().get(String.valueOf(index));
    }
}
