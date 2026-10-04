package io.github.dfa1.typesafe.client.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A pure-Java byte-level BPE tokenizer for HuggingFace {@code tokenizer.json} files, enough for
 * Laya (ModernBERT) and Qwen2.5: NFC normalization, added tokens matched first (longest wins), a
 * regex pre-tokenizer, GPT-2's byte-to-unicode mapping, then BPE merges by rank. Any option outside
 * that subset is rejected at load time rather than tokenized differently. Never adds special tokens.
 * {@link #encodeText} is for untrusted text: a special token's spelling there stays plain text, so a state
 * containing {@code <|im_end|>} can't close the prompt's turn (HF's {@code split_special_tokens=True}).
 */
final class BpeTokenizer {

    /** GPT-2's pattern, which HF's ByteLevel pre-tokenizer applies when {@code use_regex} is true. */
    private static final String GPT2 = "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+";

    private final Map<String, Integer> vocab;
    private final Map<String, Integer> ranks;
    private final Map<String, Integer> added;
    private final Pattern addedSplit;
    private final Pattern addedSplitText;
    private final Pattern pre;
    private final String[] byteChar = byteToUnicode();
    private final Map<String, int[]> cache = new ConcurrentHashMap<>();

    /** An added token, whether it's special (a control token) and whether it swallows whitespace on its left/right
     *  (e.g. ModernBERT's [MASK]). */
    record Added(String content, int id, boolean special, boolean lstrip, boolean rstrip) {
        String regex() {
            return (lstrip ? "\\s*" : "") + Pattern.quote(content) + (rstrip ? "\\s*" : "");
        }
    }

    private BpeTokenizer(Map<String, Integer> vocab, Map<String, Integer> ranks, List<Added> added, String preRegex) {
        this.vocab = vocab;
        this.ranks = ranks;
        this.added = new HashMap<>();
        added.forEach(a -> this.added.put(a.content(), a.id()));
        this.addedSplit = split(added);
        this.addedSplitText = split(added.stream().filter(a -> !a.special()).toList());
        // Rust's \s and \p{..} are Unicode-aware; Java's \s is ASCII unless asked
        this.pre = Pattern.compile(preRegex, Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static Pattern split(List<Added> added) {
        return added.isEmpty() ? null : Pattern.compile(added.stream()
                .sorted(Comparator.comparingInt((Added a) -> a.content().length()).reversed())
                .map(Added::regex)
                .collect(Collectors.joining("|")), Pattern.UNICODE_CHARACTER_CLASS);
    }

    @SuppressWarnings("unchecked")
    static BpeTokenizer load(Path tokenizerJson) throws IOException {
        Map<String, Object> root = Onnx.json().readValue(Files.readAllBytes(tokenizerJson), Map.class);
        Map<String, Object> normalizer = (Map<String, Object>) root.get("normalizer");
        require(normalizer == null || "NFC".equals(normalizer.get("type")), "normalizer " + normalizer);

        Map<String, Object> model = (Map<String, Object>) root.get("model");
        require("BPE".equals(model.get("type")), "model " + model.get("type"));
        require(!Boolean.TRUE.equals(model.get("byte_fallback")) && !Boolean.TRUE.equals(model.get("ignore_merges"))
                && isEmpty(model.get("continuing_subword_prefix")) && isEmpty(model.get("end_of_word_suffix"))
                && model.get("dropout") == null, "BPE options");
        Map<String, Integer> vocab = new HashMap<>();
        ((Map<String, Object>) model.get("vocab")).forEach((k, v) -> vocab.put(k, ((Number) v).intValue()));
        Map<String, Integer> ranks = new HashMap<>();
        List<Object> merges = (List<Object>) model.get("merges");
        for (int r = 0; r < merges.size(); r++) {
            Object m = merges.get(r);
            String key = m instanceof List<?> pair ? pair.get(0) + " " + pair.get(1) : (String) m; // both formats
            ranks.putIfAbsent(key, r);
        }

        List<Added> added = new ArrayList<>();
        for (Object o : (List<Object>) root.get("added_tokens")) {
            Map<String, Object> t = (Map<String, Object>) o;
            require(!Boolean.TRUE.equals(t.get("single_word")), "single_word added token " + t.get("content"));
            added.add(new Added((String) t.get("content"), ((Number) t.get("id")).intValue(),
                    Boolean.TRUE.equals(t.get("special")), Boolean.TRUE.equals(t.get("lstrip")), Boolean.TRUE.equals(t.get("rstrip"))));
        }
        return new BpeTokenizer(vocab, ranks, added, preTokenizerRegex((Map<String, Object>) root.get("pre_tokenizer")));
    }

    /** ByteLevel(use_regex) alone, or Split(Regex, Isolated) followed by ByteLevel(no regex). */
    @SuppressWarnings("unchecked")
    private static String preTokenizerRegex(Map<String, Object> pt) {
        String type = (String) pt.get("type");
        if ("ByteLevel".equals(type)) {
            require(!Boolean.TRUE.equals(pt.get("add_prefix_space")) && !Boolean.FALSE.equals(pt.get("use_regex")), "ByteLevel " + pt);
            return GPT2;
        }
        require("Sequence".equals(type), "pre_tokenizer " + type);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) pt.get("pretokenizers");
        require(steps.size() == 2, "pre_tokenizer sequence " + steps);
        Map<String, Object> split = steps.get(0);
        Map<String, Object> byteLevel = steps.get(1);
        require("Split".equals(split.get("type")) && "Isolated".equals(split.get("behavior"))
                && !Boolean.TRUE.equals(split.get("invert")), "split " + split);
        require("ByteLevel".equals(byteLevel.get("type")) && Boolean.FALSE.equals(byteLevel.get("use_regex"))
                && !Boolean.TRUE.equals(byteLevel.get("add_prefix_space")), "byte level " + byteLevel);
        return (String) ((Map<String, Object>) split.get("pattern")).get("Regex");
    }

    /** Trusted text (a prompt template): special tokens' spellings become their ids. */
    long[] encode(String text) {
        return encode(text, addedSplit);
    }

    /** Untrusted text: only non-special added tokens are matched; a special token's spelling is tokenized as text. */
    long[] encodeText(String text) {
        return encode(text, addedSplitText);
    }

    private long[] encode(String text, Pattern split) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<Integer> ids = new ArrayList<>();
        if (split == null) {
            plain(normalized, ids);
        } else {
            Matcher m = split.matcher(normalized);
            int last = 0;
            while (m.find()) {
                plain(normalized.substring(last, m.start()), ids);
                Integer id = added.get(m.group()); // exact, or a token that swallowed surrounding whitespace
                ids.add(id != null ? id : added.get(m.group().strip()));
                last = m.end();
            }
            plain(normalized.substring(last), ids);
        }
        return ids.stream().mapToLong(Integer::longValue).toArray();
    }

    private void plain(String text, List<Integer> ids) {
        Matcher m = pre.matcher(text);
        if (cache.size() > 100_000) {
            cache.clear(); // ponytail: bounds a long-running client's memory; an LRU if the refill ever shows in profiles
        }
        while (m.find()) {
            for (int id : cache.computeIfAbsent(m.group(), this::bpe)) {
                ids.add(id);
            }
        }
    }

    /** GPT-2 BPE: repeatedly merge every occurrence of the lowest-ranked adjacent pair. */
    private int[] bpe(String piece) {
        List<String> word = new ArrayList<>();
        for (byte b : piece.getBytes(StandardCharsets.UTF_8)) {
            word.add(byteChar[b & 0xFF]);
        }
        while (word.size() > 1) {
            int best = Integer.MAX_VALUE;
            String first = null;
            String second = null;
            for (int i = 0; i < word.size() - 1; i++) {
                Integer r = ranks.get(word.get(i) + " " + word.get(i + 1));
                if (r != null && r < best) {
                    best = r;
                    first = word.get(i);
                    second = word.get(i + 1);
                }
            }
            if (first == null) {
                break;
            }
            List<String> merged = new ArrayList<>(word.size());
            for (int i = 0; i < word.size(); i++) {
                if (i < word.size() - 1 && word.get(i).equals(first) && word.get(i + 1).equals(second)) {
                    merged.add(first + second);
                    i++;
                } else {
                    merged.add(word.get(i));
                }
            }
            word = merged;
        }
        int[] result = new int[word.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = Objects.requireNonNull(vocab.get(word.get(i)), () -> "no vocab entry for a BPE symbol of " + piece);
        }
        return result;
    }

    /** GPT-2's reversible byte-to-printable-character table. */
    private static String[] byteToUnicode() {
        String[] table = new String[256];
        int n = 0;
        for (int b = 0; b < 256; b++) {
            boolean printable = (b >= '!' && b <= '~') || (b >= 0xA1 && b <= 0xAC) || (b >= 0xAE && b <= 0xFF);
            table[b] = String.valueOf((char) (printable ? b : 256 + n++));
        }
        return table;
    }

    private static boolean isEmpty(Object value) {
        return value == null || "".equals(value);
    }

    private static void require(boolean supported, String what) {
        if (!supported) {
            throw new IllegalArgumentException("unsupported tokenizer.json: " + what);
        }
    }
}
