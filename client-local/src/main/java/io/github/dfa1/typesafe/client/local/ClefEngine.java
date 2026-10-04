package io.github.dfa1.typesafe.client.local;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.client.TypeSafeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Clef-flash ({@code Cloudflare/clef-flash}, Apache-2.0): Qwen3.5-9B with a joint schema head. The whole request is
 * one causal sequence (state, then every question and its options), and the head gives one logit per option, so a
 * request is one forward pass. A port of the model repository's {@code joint_schema_model.encode_record}/{@code systemone};
 * the ONNX graph is Ollaya's ({@code ollaya-dev/clef}, {@code flash/model-fp32.onnx}), which reads the upstream
 * bf16 safetensors in place by byte offset under {@code sha256-<hash>} names. Text only: the graph has no vision inputs.
 */
final class ClefEngine implements Engine {

    /** The weight files Ollaya's graph references, pinned to Cloudflare/clef-flash@17f0b0a. */
    private static final Map<String, String> WEIGHTS = Map.of(
            "model-00001-of-00004.safetensors", "8b45a8e968141cdcc58fb71c9adfc258e2c77b5f062bc636c1fd5bc5d916b565",
            "model-00002-of-00004.safetensors", "7590856c713eed844a2dcf48e6c43c4de165b788bc3f80e328311183cdbc7db8",
            "model-00003-of-00004.safetensors", "e6eac2467952c33361ed7dcb3c7959d1086bbe57201cd3749c3d769fdc17fe63",
            "model-00004-of-00004.safetensors", "9fcecc6556b39171238373a465f409794b7f821fb4cd1e6459e3a9c0fe317af7",
            "joint_head.safetensors", "19cdcec8c81dc9212be320fff47462ab342fbc1278be4368fb3da71241cf5ba0");
    private static final String PREFIX = "<|im_start|>system\nRead the complete state and schema. Decide every field jointly. "
            + "Each answer must be exactly one of that field's allowed options.<|im_end|>\n<|im_start|>user\nSTATE:\n";
    private static final String SUFFIX = "\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\nJOINT SCHEMA DECISIONS:";
    private static final int NOUL = 0, CHOICE = 1, SCORE = 2;
    private static final int MAX_TOKENS = 4096, SEQ_MULTIPLE = 64; // Ollaya's decision.json
    private static final long PAD = 248044;

    private final OrtSession session;
    private final BpeTokenizer tokenizer;
    private final Model model;

    ClefEngine(OrtSession session, BpeTokenizer tokenizer, Model model) {
        this.session = session;
        this.tokenizer = tokenizer;
        this.model = model;
    }

    static ClefEngine load(Path dir) {
        return load(dir, false);
    }

    /** @param dir clef-flash's {@code tokenizer.json} and safetensors, plus Ollaya's {@code flash/model-fp32.onnx} */
    static ClefEngine load(Path dir, boolean gpu) {
        Path modelFile = Onnx.model(dir.resolve("flash"));
        try {
            link(dir, modelFile.getParent());
            return new ClefEngine(Onnx.session(modelFile, gpu), Onnx.tokenizer(Onnx.require(dir, "tokenizer.json")),
                    new Model("local/" + dir.getFileName()));
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("cannot load " + dir, e);
        }
    }

    /** Hard-links each weight file next to the graph as {@code sha256-<hash>}, the name the graph's external data uses
     *  (no copy; ONNX Runtime rejects a symlink that resolves outside the graph's directory). The graph reads the
     *  weights by byte offset, so a file from another revision gives garbage, not an error: each is hashed before its
     *  link is made (once, about a minute for all 19 GB), and a link's name is then its proof. */
    private static void link(Path dir, Path graphDir) throws IOException {
        for (Map.Entry<String, String> w : WEIGHTS.entrySet()) {
            Path link = graphDir.resolve("sha256-" + w.getValue());
            if (!Files.exists(link)) {
                Path weights = Onnx.require(dir, w.getKey());
                verify(weights, w.getValue());
                Files.createLink(link, weights);
            }
        }
    }

    static void verify(Path file, String sha256) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[1 << 20];
        try (InputStream in = Files.newInputStream(file)) {
            for (int n; (n = in.read(buffer)) > 0;) {
                digest.update(buffer, 0, n);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(sha256)) {
            throw new IllegalArgumentException(file + " has sha256 " + actual + ", not clef-flash@17f0b0a's " + sha256
                    + ": download it again with the hf download command at https://github.com/dfa1/typesafe-java/blob/main/docs/how-to.md#run-without-the-api-on-a-local-model");
        }
    }

    /** One request as the graph takes it: token ids, then each question's instruction span and its options' spans. */
    @SuppressWarnings("java:S6218") // a one-shot model input, never compared or printed
    record Sequence(long[] ids, long[][] questionSpans, long[] questionTypes, long[][] optionSpans, long[] optionQuestion,
                    List<List<String>> optionIds) {
    }

    @Override
    public Answers answer(Content state, Map<String, Question> questions) throws OrtException {
        Sequence s = sequence(state, questions);
        float[] logits = run(s);
        Map<String, Answer> answers = new LinkedHashMap<>();
        int o = 0, q = 0;
        for (Map.Entry<String, Question> entry : questions.entrySet()) {
            List<String> ids = s.optionIds().get(q++);
            double[] p = Probabilities.softmax(Arrays.copyOfRange(logits, o, o + ids.size()));
            o += ids.size();
            answers.put(entry.getKey(), switch (entry.getValue()) {
                case Question.Noul ignored -> new Answer.Noul(p[0]); // [true, false]
                case Question.Choice(Content ignored, Map<String, String> criteria) -> {
                    double[] inCriteriaOrder = criteria.keySet().stream().mapToDouble(k -> p[ids.indexOf(k)]).toArray();
                    yield Probabilities.choice(criteria, inCriteriaOrder);
                }
                case Question.Score(Content ignored, List<String> criteria) -> Probabilities.score(criteria, p);
            });
        }
        return new Answers(answers, s.ids().length);
    }

    /** encode_record: prefix + state + schema + suffix, each piece tokenized on its own; the state truncated to fit. */
    Sequence sequence(Content state, Map<String, Question> questions) {
        List<Long> schema = new ArrayList<>(encode("\n\nSCHEMA FIELDS:\n"));
        List<long[]> questionSpans = new ArrayList<>(), optionSpans = new ArrayList<>();
        List<Long> questionTypes = new ArrayList<>(), optionQuestion = new ArrayList<>();
        List<List<String>> optionIds = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, Question> entry : questions.entrySet()) {
            Question question = entry.getValue();
            Map<String, String> options = options(question);
            int type = switch (question) {
                case Question.Noul ignored -> NOUL;
                case Question.Choice ignored -> CHOICE;
                case Question.Score ignored -> SCORE;
            };
            schema.addAll(encode("\nFIELD " + (n + 1) + "\nID: " + entry.getKey() + "\nTYPE: " + List.of("noul", "choice", "score").get(type)
                    + "\nINSTRUCTION: "));
            int start = schema.size();
            String instructions = render(instructions(question));
            schema.addAll(encode(instructions.isEmpty() ? entry.getKey() : instructions));
            questionSpans.add(new long[]{start, schema.size()});
            questionTypes.add((long) type);
            schema.addAll(encode("\nALLOWED OPTIONS:\n"));
            int i = 0;
            for (Map.Entry<String, String> option : options.entrySet()) {
                schema.addAll(encode("OPTION " + ++i + ": "));
                Map<String, Object> semantics = new TreeMap<>();
                semantics.put("option_id", option.getKey());
                if (option.getValue() != null) {
                    semantics.put("description", option.getValue());
                }
                int optionStart = schema.size();
                schema.addAll(encode(render(new Content.Fields(semantics))));
                optionSpans.add(new long[]{optionStart, schema.size()});
                optionQuestion.add((long) n);
                schema.addAll(encode("\n"));
            }
            schema.addAll(encode("END FIELD\n"));
            optionIds.add(List.copyOf(options.keySet()));
            n++;
        }
        List<Long> prefix = boxed(tokenizer.encode(PREFIX)), suffix = boxed(tokenizer.encode(SUFFIX));
        int fixed = prefix.size() + schema.size() + suffix.size();
        if (fixed > MAX_TOKENS) {
            throw new TypeSafeException.BadRequest("questions take " + fixed + " tokens; the maximum is " + MAX_TOKENS);
        }
        List<Long> stateIds = encode(render(state));
        List<Long> ids = new ArrayList<>(prefix);
        ids.addAll(stateIds.subList(0, Math.min(stateIds.size(), MAX_TOKENS - fixed)));
        int offset = ids.size();
        ids.addAll(schema);
        ids.addAll(suffix);
        questionSpans.forEach(span -> shift(span, offset));
        optionSpans.forEach(span -> shift(span, offset));
        return new Sequence(ids.stream().mapToLong(Long::longValue).toArray(), questionSpans.toArray(long[][]::new),
                questionTypes.stream().mapToLong(Long::longValue).toArray(), optionSpans.toArray(long[][]::new),
                optionQuestion.stream().mapToLong(Long::longValue).toArray(), optionIds);
    }

    /** question_options: option id to description; noul [true, false], choice labels by code point, score levels in order. */
    private static Map<String, String> options(Question question) {
        Map<String, String> options = new LinkedHashMap<>();
        switch (question) {
            case Question.Noul(Content ignored, Map<String, String> criteria) -> {
                Map<String, String> c = criteria == null ? Map.of() : criteria;
                options.put("true", c.getOrDefault("true", "The proposition is true or the answer is yes."));
                options.put("false", c.getOrDefault("false", "The proposition is false or the answer is no."));
            }
            case Question.Choice(Content ignored, Map<String, String> criteria) -> {
                criteria.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.comparing(k -> k.codePoints().toArray(), Arrays::compare)))
                        .forEach(e -> options.put(e.getKey(), e.getValue()));
            }
            case Question.Score(Content ignored, List<String> criteria) -> {
                for (int i = 0; i < criteria.size(); i++) {
                    options.put(String.valueOf(i), criteria.get(i));
                }
            }
        }
        return options;
    }

    private static Content instructions(Question question) {
        Content c = switch (question) {
            case Question.Noul q -> q.instructions();
            case Question.Choice q -> q.instructions();
            case Question.Score q -> q.instructions();
        };
        return c == null ? new Content.Text("") : c;
    }

    /** render: text as is, anything else as Python's {@code json.dumps(sort_keys=True, separators=(",", ":"))}. */
    static String render(Content content) {
        return switch (content) {
            case Content.Text(String value) -> value;
            case Content.Fields(Map<String, Object> fields) -> Onnx.toJson(sorted(fields));
            case Content.Messages(List<String> values) -> Onnx.toJson(values);
        };
    }

    private static Object sorted(Object value) {
        return switch (value) {
            case Map<?, ?> m -> {
                Map<String, Object> s = new TreeMap<>(Comparator.comparing(k -> k.codePoints().toArray(), Arrays::compare));
                m.forEach((k, v) -> s.put(String.valueOf(k), sorted(v)));
                yield s;
            }
            case List<?> l -> l.stream().map(ClefEngine::sorted).toList();
            case null, default -> value;
        };
    }

    private static void shift(long[] span, int offset) {
        span[0] += offset;
        span[1] += offset;
    }

    /** input_ids right-padded to a multiple of 64; causal attention, so the padding can't change the real tokens. */
    float[] run(Sequence s) throws OrtException {
        int len = s.ids().length;
        int padded = (len + SEQ_MULTIPLE - 1) / SEQ_MULTIPLE * SEQ_MULTIPLE;
        long[] ids = Arrays.copyOf(s.ids(), padded);
        Arrays.fill(ids, len, padded, PAD);
        long[] positions = new long[padded];
        Arrays.setAll(positions, i -> i);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", Onnx.longs(ids, 1, padded));
            inputs.put("token_positions", Onnx.longs(positions, padded));
            inputs.put("question_spans", Onnx.longs(flat(s.questionSpans()), s.questionSpans().length, 2));
            inputs.put("question_types", Onnx.longs(s.questionTypes(), s.questionTypes().length));
            inputs.put("option_spans", Onnx.longs(flat(s.optionSpans()), s.optionSpans().length, 2));
            inputs.put("option_question", Onnx.longs(s.optionQuestion(), s.optionQuestion().length));
            try (OrtSession.Result result = session.run(inputs, Set.of("logits"))) {
                float[] logits = new float[s.optionQuestion().length];
                ((OnnxTensor) result.get("logits").orElseThrow()).getFloatBuffer().get(logits);
                return logits;
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private static long[] flat(long[][] spans) {
        return Arrays.stream(spans).flatMapToLong(Arrays::stream).toArray();
    }

    /** Everything but PREFIX/SUFFIX carries caller text, so a special token spelled there stays text. */
    private List<Long> encode(String text) {
        return boxed(tokenizer.encodeText(text));
    }

    private static List<Long> boxed(long[] ids) {
        return Arrays.stream(ids).boxed().toList();
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public String description() {
        return "Clef-flash: Qwen3.5-9B + joint schema head (local ONNX)";
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException(e);
        }
    }
}
