package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.Question;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real model from ~/.cache/typesafe-local (see local/scripts). Opt in: {@code -DexcludedGroups=acceptance};
 *  pick the engine with {@code -Dengine=laya|laya-int8|qwen} (append {@code -gpu} for WebGPU). */
@Tag("model")
class LocalTypeSafeClientModelTest {

    private static LocalTypeSafeClient sut;

    @BeforeAll
    static void load() {
        sut = Engines.client(System.getProperty("engine", "laya"));
    }

    @AfterAll
    static void close() {
        sut.close();
    }

    @Test
    void urgentMessageIsUrgent() {
        // When
        double result = urgency("Help! My payouts have been failing for 3 days and I can't pay my staff.");

        // Then
        assertThat(result).isGreaterThan(0.5);
    }

    @Test
    void thankYouNoteIsNotUrgent() {
        // When
        double result = urgency("Thanks for the quick reply last week, everything works fine now.");

        // Then
        assertThat(result).isLessThan(0.5);
    }

    @Test
    void choicePicksTheObviousCategory() {
        // When
        Answer.Choice result = sut.evaluate(EvaluateRequest.of(Content.text("I was charged twice for my subscription this month."),
                Map.of("kind", Question.choice("What is this support ticket about?",
                        Map.of("billing", "payments, charges, invoices", "bug", "the app crashes or misbehaves", "feature", "a request for something new")))))
                .choices().get("kind");

        // Then
        System.out.println("choice=" + result);
        assertThat(result.choice()).isEqualTo("billing");
    }

    @Test
    void scoreRanksAngerAboveCalm() {
        // When
        double furious = anger("This is the THIRD time your app ate my data. I am DONE. Unacceptable!!!");
        double calm = anger("Could you tell me where to find the export button? Thanks.");

        // Then
        // the ranking is the point: int8 Laya compresses the scale differently per CPU
        // (M5 2.02 vs 1.31, M1 runner 2.02 vs 1.53; fp32 2.37 vs 1.03)
        assertThat(furious).isGreaterThan(calm);
    }

    private static double anger(String state) {
        Answer.Score result = sut.evaluate(EvaluateRequest.of(Content.text(state),
                Map.of("anger", Question.score("How angry is the customer?", List.of("calm", "annoyed", "angry", "furious")))))
                .scores().get("anger");
        System.out.println("score=" + result + "  " + state);
        return result.score();
    }

    private static double urgency(String state) {
        double result = sut.evaluate(EvaluateRequest.of(Content.text(state),
                Map.of("urgent", Question.noul("Does this message convey urgency?")))).nouls().get("urgent").noul();
        System.out.printf("urgency=%.3f  %s%n", result, state);
        return result;
    }
}
