package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.jackson2.Jackson2Codec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The Java port of encode_record against Clef's own (fixture written by scripts/clef/reference.py). Needs only
 *  clef-flash's tokenizer.json, not the weights. */
class ClefEngineTest {

    // sha256 of "weights"
    private static final String WEIGHTS_SHA256 = "9a129038d9a00aed0cf6a7ea059ca50a813449061ab87848cf1a13eafdf33b2c";

    @TempDir
    Path dir;

    @Test
    void verifyAcceptsTheExpectedHash() throws Exception {
        // Given
        Path file = Files.writeString(dir.resolve("w.safetensors"), "weights");

        // When
        Throwable result = catchThrowable(() -> ClefEngine.verify(file, WEIGHTS_SHA256));

        // Then
        assertThat(result).isNull();
    }

    @Test
    void verifyRejectsAnotherRevision() throws Exception {
        // Given
        Path file = Files.writeString(dir.resolve("w.safetensors"), "other weights");

        // When
        Throwable result = catchThrowable(() -> ClefEngine.verify(file, WEIGHTS_SHA256));

        // Then
        assertThat(result).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("download it again");
    }

    @Tag("model")
    @Test
    @SuppressWarnings("unchecked")
    void sequencesMatchPythonTokenForToken() throws Exception {
        // Given
        Jackson2Codec codec = new Jackson2Codec();
        ClefEngine sut = new ClefEngine(null, BpeTokenizer.load(Engines.CLEF.dir().resolve("tokenizer.json")), null);
        Map<String, Object> fixture = codec.readValue(Files.readString(Path.of("src/test/resources/clef/expected.json")), Map.class);
        List<Object> requests = (List<Object>) fixture.get("requests");
        List<Map<String, Object>> expected = (List<Map<String, Object>>) fixture.get("expected");

        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> request = (Map<String, Object>) requests.get(i);
            Map<String, Question> questions = new LinkedHashMap<>();
            ((Map<String, Map<String, Object>>) request.get("questions")).forEach((k, q) -> questions.put(k, question(q)));
            Map<String, Object> want = expected.get(i);

            // When
            ClefEngine.Sequence result = sut.sequence(content(request.get("state")), questions);

            // Then
            assertThat(result.ids()).as("request %d ids", i).containsExactly(longs(want.get("input_ids")));
            assertThat(Arrays.stream(result.questionSpans()).map(s -> List.of(s[0], s[1])).toList()).as("request %d question spans", i)
                    .isEqualTo(pairs(want.get("question_spans")));
            assertThat(result.questionTypes()).as("request %d types", i).containsExactly(longs(want.get("question_types")));
            assertThat(Arrays.stream(result.optionSpans()).map(s -> List.of(s[0], s[1])).toList()).as("request %d option spans", i)
                    .isEqualTo(pairs(want.get("option_spans")));
            assertThat(result.optionIds()).as("request %d option ids", i).isEqualTo(want.get("option_ids"));
        }
    }

    /** The wire shapes the codec writes but doesn't read. */
    @SuppressWarnings("unchecked")
    private static Content content(Object json) {
        return switch (json) {
            case String text -> new Content.Text(text);
            case Map<?, ?> fields -> new Content.Fields((Map<String, Object>) fields);
            default -> new Content.Messages((List<String>) json);
        };
    }

    @SuppressWarnings("unchecked")
    private static Question question(Map<String, Object> q) {
        Content instructions = content(q.get("instructions"));
        return switch ((String) q.get("type")) {
            case "noul" -> new Question.Noul(instructions, (Map<String, String>) q.get("criteria"));
            case "choice" -> new Question.Choice(instructions, (Map<String, String>) q.get("criteria"));
            default -> new Question.Score(instructions, (List<String>) q.get("criteria"));
        };
    }

    private static long[] longs(Object list) {
        return ((List<?>) list).stream().mapToLong(x -> ((Number) x).longValue()).toArray();
    }

    private static List<List<Long>> pairs(Object list) {
        return ((List<?>) list).stream().map(p -> Arrays.stream(longs(p)).boxed().toList()).toList();
    }
}
