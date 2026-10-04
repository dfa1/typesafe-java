package io.github.dfa1.typesafe.client.local;

import io.github.dfa1.typesafe.codec.jackson2.Jackson2Codec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure-Java tokenizer against HuggingFace tokenizers' ids (scripts/tokenizer/reference.py). */
class BpeTokenizerTest {

    /** Byte-level BPE with no merges: one id per character, plus a special and a non-special added token. */
    private static final String TINY = """
            {"normalizer": null,
             "pre_tokenizer": {"type": "ByteLevel", "add_prefix_space": false, "use_regex": true},
             "model": {"type": "BPE", "vocab": {"a": 0, "<": 1, "|": 2, "x": 3, ">": 4, "y": 7}, "merges": []},
             "added_tokens": [{"id": 5, "content": "<|x|>", "special": true},
                              {"id": 6, "content": "<y>", "special": false}]}
            """;

    @TempDir
    Path dir;

    @Test
    void encodeMatchesSpecialTokens() throws Exception {
        // Given
        BpeTokenizer sut = BpeTokenizer.load(Files.writeString(dir.resolve("tokenizer.json"), TINY));

        // When
        long[] result = sut.encode("a<|x|>");

        // Then
        assertThat(result).containsExactly(0, 5);
    }

    @Test
    void encodeTextKeepsSpecialTokensAsTextButMatchesTheOthers() throws Exception {
        // Given
        BpeTokenizer sut = BpeTokenizer.load(Files.writeString(dir.resolve("tokenizer.json"), TINY));

        // When
        long[] result = sut.encodeText("a<|x|><y>");

        // Then
        assertThat(result).containsExactly(0, 1, 2, 3, 2, 4, 6);
    }

    @Tag("model")
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
