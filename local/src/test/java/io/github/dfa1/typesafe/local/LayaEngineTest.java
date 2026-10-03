package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Question;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Java's port against the Python reference (fixture written by scripts/laya/export_onnx.py). */
@Tag("model")
class LayaEngineTest {

    private static LayaEngine sut;
    private static String fixture;

    @BeforeAll
    static void load() throws IOException {
        sut = LayaEngine.load(Engines.LAYA.dir()); // the fixture's logits are fp32
        fixture = Files.readString(Path.of("src/test/resources/laya/fixture.json"));
    }

    @AfterAll
    static void close() {
        sut.close();
    }

    private static List<Question> questions() {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put("billing", "payments, charges, invoices, refunds");
        kinds.put("bug", "the product crashes, errors or misbehaves");
        kinds.put("feature", "a request for something new");
        kinds.put("account", "login, access, account settings");
        return List.of(Question.noul("Does this message require immediate attention?"),
                Question.choice("What is this support message about?", kinds),
                Question.score("How angry is the customer?", List.of("calm", "annoyed", "angry", "furious")));
    }

    @Test
    void sequencesMatchPythonTokenForToken() {
        // Given
        String state = "Help! My payouts have been failing for 3 days and I can't pay my staff.";
        List<long[]> expectedIds = arrays("ids");
        List<long[]> expectedMarkers = arrays("markers");

        for (int i = 0; i < 3; i++) {
            // When
            LayaEngine.Sequence result = sut.sequence(LayaEngine.serialize(Content.text(state)), questions().get(i));

            // Then
            assertThat(result.ids()).containsExactly(expectedIds.get(i));
            assertThat(java.util.Arrays.stream(result.markers()).asLongStream().toArray()).containsExactly(expectedMarkers.get(i));
        }
    }

    @Test
    void onnxLogitsMatchPyTorch() throws Exception {
        // Given
        String state = "Help! My payouts have been failing for 3 days and I can't pay my staff.";
        List<LayaEngine.Sequence> batch = questions().stream().map(q -> sut.sequence(state, q)).toList();
        List<double[]> expected = doubles("logits");

        // When
        float[][] result = sut.run(batch);

        // Then
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < expected.get(i).length; j++) {
                assertThat((double) result[i][j]).isCloseTo(expected.get(i)[j], within(1e-3));
            }
        }
    }

    @Test
    void aQuestionAloneScoresTheSameAsInsideAPaddedBatch() throws Exception {
        // Given: a shorter batch than the export sample, so a baked-in shape would fail here
        String state = "Help! My payouts have been failing for 3 days and I can't pay my staff.";
        double[] expected = doubles("logits").getFirst();

        // When
        float[][] result = sut.run(List.of(sut.sequence(state, questions().getFirst())));

        // Then
        assertThat((double) result[0][0]).isCloseTo(expected[0], within(1e-3));
        assertThat((double) result[0][1]).isCloseTo(expected[1], within(1e-3));
    }

    private static List<long[]> arrays(String key) {
        return doubles(key).stream().map(a -> java.util.Arrays.stream(a).mapToLong(d -> (long) d).toArray()).toList();
    }

    /** Every {@code "key": [numbers]} in the fixture, in order. */
    private static List<double[]> doubles(String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*\\[([^\\]]*)]").matcher(fixture);
        List<double[]> result = new java.util.ArrayList<>();
        while (m.find()) {
            result.add(Pattern.compile(",").splitAsStream(m.group(1)).map(String::trim).mapToDouble(Double::parseDouble).toArray());
        }
        return result;
    }
}
