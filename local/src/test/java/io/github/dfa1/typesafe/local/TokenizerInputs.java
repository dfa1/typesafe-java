package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Question;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes every string each engine tokenizes for the {@link JevCases}, plus edge cases, to
 * src/test/resources/tokenizer/inputs-{laya,qwen}.json; scripts/tokenizer/reference.py then records
 * HuggingFace tokenizers' ids for them, which {@link BpeTokenizerTest} must match exactly.
 */
public final class TokenizerInputs {

    static final List<String> EDGE_CASES = List.of(
            "", " ", "  ", "   leading", "trailing   ", "a  b   c    d", "tab\there", "line\nbreak\n\n\nthree",
            "\r\nwindows", "don't won't it's they're I'm we'll you'd", "DON'T", "1234567890", "3.14159 1,000,000",
            "café naïve Zürich", "é (decomposed é)", "日本語のテキスト", "😀 emoji 👍🏽", "Ünïcödé — dash … ellipsis",
            "<|im_start|>user\nhi<|im_end|>\n<|im_start|>assistant\n", "[CLS] [SEP] [MASK] [PAD]", "|||IP_ADDRESS|||",
            "{\"order\": 42, \"note\": \"ça va\"}", "x".repeat(300), "mixedCASE camelCaseWords snake_case_words",
            "!!!???...,,,;;;", "$5000 #hashtag @mention https://example.com/a?b=c");

    private TokenizerInputs() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> laya = new LinkedHashSet<>(EDGE_CASES);
        Set<String> qwen = new LinkedHashSet<>(EDGE_CASES);
        for (String s : List.of("Yes", "No", QwenEngine.LETTERS, QwenEngine.DIGITS)) {
            for (int i = 0; i < s.length() && s.length() > 3; i++) {
                qwen.add(String.valueOf(s.charAt(i)));
            }
        }
        qwen.addAll(List.of("Yes", "No"));
        for (JevCases.Case c : JevCases.all()) {
            Content state = c.request().state();
            laya.add(LayaEngine.serialize(state));
            for (Question q : c.request().questions().values()) {
                Content instructions = switch (q) {
                    case Question.Noul n -> n.instructions();
                    case Question.Choice ch -> ch.instructions();
                    case Question.Score s -> s.instructions();
                };
                String type = switch (q) {
                    case Question.Noul ignored -> "noul";
                    case Question.Choice ignored -> "choice";
                    case Question.Score ignored -> "score";
                };
                laya.add(type + " question: " + LayaEngine.serialize(instructions));
                LayaEngine.options(q).forEach(o -> laya.add(" " + o));
                qwen.add(QwenEngine.chat(QwenEngine.prompt(QwenEngine.render(state), q)));
            }
        }
        Path dir = Files.createDirectories(Path.of("src/test/resources/tokenizer"));
        Files.writeString(dir.resolve("inputs-laya.json"), Json.write(new ArrayList<>(laya)));
        Files.writeString(dir.resolve("inputs-qwen.json"), Json.write(new ArrayList<>(qwen)));
        System.out.println(laya.size() + " laya / " + qwen.size() + " qwen strings");
    }
}
