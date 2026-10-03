package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.jackson2.Jackson2Codec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure-Java tokenizer against HuggingFace tokenizers' ids (scripts/tokenizer/reference.py). */
@Tag("model")
class BpeTokenizerTest {

    @ParameterizedTest
    @CsvSource({"laya, LAYA", "qwen, QWEN"})
    @SuppressWarnings("unchecked")
    void matchesHuggingFaceTokenizersOnEveryString(String name, Engines engine) throws Exception {
        // Given
        BpeTokenizer sut = BpeTokenizer.load(engine.dir().resolve("tokenizer.json"));
        List<Object> expected = new Jackson2Codec().readValue(Files.readString(Path.of("src/test/resources/tokenizer/expected-" + name + ".json")), List.class);
        List<String> mismatches = new ArrayList<>();

        for (Object o : expected) {
            Map<String, Object> e = (Map<String, Object>) o;
            String text = (String) e.get("text");
            long[] want = ((List<Object>) e.get("ids")).stream().mapToLong(x -> ((Number) x).longValue()).toArray();

            // When
            long[] result = sut.encode(text);

            // Then
            if (!java.util.Arrays.equals(result, want)) {
                mismatches.add(text.length() > 60 ? text.substring(0, 60) + "…" : text);
            }
        }
        assertThat(mismatches).as(mismatches.size() + " of " + expected.size() + " strings differ").isEmpty();
    }
}
