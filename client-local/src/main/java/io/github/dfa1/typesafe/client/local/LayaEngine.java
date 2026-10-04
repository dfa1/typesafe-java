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
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laya ({@code convaiinnovations/laya-typed-decisions}, Apache-2.0), a 421M ModernBERT encoder with
 * a decision head trained for Jev-style questions. No generation and no prompt engineering: each
 * question is one sequence
 * {@code [CLS] <type> question: <instructions> [SEP] [MASK] opt0 [MASK] opt1 ... [SEP] state [SEP]},
 * the head scores each option's {@code [MASK]}, and all of a request's questions run as one batch.
 * A port of Laya's {@code rl_common.build_sequence}/{@code rl_agent_api.system_one}; the ONNX file
 * is onnx-community's export.
 */
final class LayaEngine implements Engine {

    private static final int CHOICE = 0, SCORE = 1, NOUL = 2; // rl_common.QTYPES
    private static final int OPTION_TOKENS = 48;

    private final OrtSession session;
    private final Model model;
    private final BpeTokenizer tokenizer;
    private final long cls, sep, pad, mask;
    private final int maxLen, headMaxLen;
    private final double[] temperature;
    private final Map<String, Double> temperatureByOptions;

    private LayaEngine(OrtSession session, BpeTokenizer tokenizer, Map<String, Object> config, Model model) throws OrtException {
        this.session = session;
        this.model = model;
        this.tokenizer = tokenizer;
        this.cls = special("[CLS]");
        this.sep = special("[SEP]");
        this.pad = special("[PAD]");
        this.mask = special("[MASK]");
        this.maxLen = ((Number) config.get("max_len")).intValue();
        this.headMaxLen = ((Number) config.get("head_max_len")).intValue();
        @SuppressWarnings("unchecked")
        List<Object> t = (List<Object>) config.getOrDefault("temperature", List.of(1.0, 1.0, 1.0));
        this.temperature = t.stream().mapToDouble(x -> ((Number) x).doubleValue()).toArray();
        this.temperatureByOptions = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> byOptions = (Map<String, Object>) config.getOrDefault("temperature_by_options", Map.of());
        byOptions.forEach((k, v) -> temperatureByOptions.put(k, ((Number) v).doubleValue()));
    }

    static LayaEngine load(Path dir) {
        return load(dir, false);
    }

    static LayaEngine load(Path dir, boolean gpu) {
        Path modelFile = Onnx.model(dir);
        Path tokenizerFile = Onnx.require(dir, "tokenizer.json");
        try {
            return new LayaEngine(Onnx.session(modelFile, gpu), Onnx.tokenizer(tokenizerFile), config(dir),
                    new Model("local/" + dir.getFileName()));
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("cannot load " + dir, e);
        }
    }

    /** One model input: token ids plus each option's [MASK] position. */
    record Sequence(long[] ids, int[] markers, int qtype) {
    }

    /** Sequence and calibration settings: the "laya" section of onnx-community's config.json (Laya's rl_agent_config.json values). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> config(Path dir) throws IOException {
        Map<String, Object> config = Onnx.json().readValue(Files.readAllBytes(Onnx.require(dir, "config.json")), Map.class);
        Object laya = config.get("laya");
        if (!(laya instanceof Map)) {
            throw new IllegalArgumentException(dir.resolve("config.json") + " has no \"laya\" section");
        }
        return (Map<String, Object>) laya;
    }

    @Override
    public Answers answer(Content state, Map<String, Question> questions) throws OrtException {
        String serialized = serialize(state);
        List<Map.Entry<String, Question>> entries = new ArrayList<>(questions.entrySet());
        List<Sequence> batch = new ArrayList<>();
        for (Map.Entry<String, Question> q : entries) {
            Sequence s = sequence(serialized, q.getValue());
            if (s.markers().length != options(q.getValue()).size()) {
                throw new TypeSafeException.BadRequest(q.getKey() + ": options do not fit in " + headMaxLen + " tokens");
            }
            batch.add(s);
        }
        float[][] logits = run(batch);
        Map<String, Answer> answers = new LinkedHashMap<>();
        int tokens = 0;
        for (int r = 0; r < batch.size(); r++) {
            Sequence s = batch.get(r);
            tokens += s.ids().length;
            double[] p = probabilities(logits[r], s.markers().length, s.qtype());
            answers.put(entries.get(r).getKey(), switch (entries.get(r).getValue()) {
                case Question.Noul ignored -> new Answer.Noul(p[1]);
                case Question.Choice(Content ignored, Map<String, String> criteria) -> Probabilities.choice(criteria, p);
                case Question.Score(Content ignored, List<String> criteria) -> Probabilities.score(criteria, p);
            });
        }
        return new Answers(answers, tokens);
    }

    /** rl_common.build_sequence, without the training-only option shuffling and left truncation. */
    Sequence sequence(String state, Question question) {
        String type = switch (question) {
            case Question.Choice ignored -> "choice";
            case Question.Score ignored -> "score";
            case Question.Noul ignored -> "noul";
        };
        Content instructions = switch (question) {
            case Question.Choice c -> c.instructions();
            case Question.Score s -> s.instructions();
            case Question.Noul n -> n.instructions();
        };
        long[] head = encode(type + " question: " + serialize(instructions).replace("[MASK]", " "));
        List<long[]> opts = new ArrayList<>();
        for (String option : options(question)) {
            long[] text = encode(" " + option.replace("[MASK]", " "));
            long[] withMarker = new long[1 + Math.min(OPTION_TOKENS, text.length)];
            withMarker[0] = mask;
            System.arraycopy(text, 0, withMarker, 1, withMarker.length - 1);
            opts.add(withMarker);
        }
        int budget = headMaxLen - opts.stream().mapToInt(o -> o.length).sum();
        if (budget < 16) { // too many / too long options: shrink every option text evenly
            int per = Math.max(4, (headMaxLen - 16) / Math.max(1, opts.size()));
            opts.replaceAll(o -> Arrays.copyOf(o, Math.min(o.length, per)));
            budget = headMaxLen - opts.stream().mapToInt(o -> o.length).sum();
        }
        List<Long> ids = new ArrayList<>();
        ids.add(cls);
        for (int i = 0; i < Math.min(head.length, Math.max(8, budget)); i++) {
            ids.add(head[i]);
        }
        ids.add(sep);
        List<Integer> markers = new ArrayList<>();
        for (long[] o : opts) {
            markers.add(ids.size());
            for (long id : o) {
                ids.add(id);
            }
        }
        ids.add(sep);
        int room = Math.max(0, maxLen - ids.size() - 1);
        long[] st = encode(state.replace("[MASK]", " "));
        for (int i = 0; i < Math.min(room, st.length); i++) {
            ids.add(st[i]);
        }
        ids.add(sep);
        long[] result = ids.stream().limit(maxLen).mapToLong(Long::longValue).toArray();
        int[] inRange = markers.stream().filter(m -> m < maxLen).mapToInt(Integer::intValue).toArray();
        int qtype = switch (question) {
            case Question.Choice ignored -> CHOICE;
            case Question.Score ignored -> SCORE;
            case Question.Noul ignored -> NOUL;
        };
        return new Sequence(result, inRange, qtype);
    }

    /** rl_common.render_options: option texts in label order; a noul is always [false, true]. */
    static List<String> options(Question question) {
        return switch (question) {
            case Question.Choice(Content ignored, Map<String, String> criteria) -> criteria.entrySet().stream()
                    .map(e -> e.getValue() == null || e.getValue().isEmpty() ? e.getKey() : e.getKey() + ": " + e.getValue())
                    .toList();
            case Question.Score(Content ignored, List<String> criteria) -> {
                List<String> levels = new ArrayList<>();
                for (int i = 0; i < criteria.size(); i++) {
                    levels.add("level " + i + ": " + criteria.get(i));
                }
                yield levels;
            }
            case Question.Noul(Content ignored, Map<String, String> criteria) -> {
                Map<String, String> c = criteria == null ? Map.of() : criteria;
                yield List.of("false: " + orDefault(c.get("false"), "no, the statement does not hold"),
                        "true: " + orDefault(c.get("true"), "yes, the statement holds"));
            }
        };
    }

    /** All questions in one padded batch: one forward pass per request. */
    float[][] run(List<Sequence> batch) throws OrtException {
        int n = batch.size();
        int len = batch.stream().mapToInt(s -> s.ids().length).max().orElse(0);
        int k = batch.stream().mapToInt(s -> s.markers().length).max().orElse(0);
        long[] ids = new long[n * len], att = new long[n * len], pos = new long[n * k], posMask = new long[n * k], qtype = new long[n];
        Arrays.fill(ids, pad);
        for (int r = 0; r < n; r++) {
            Sequence s = batch.get(r);
            for (int i = 0; i < s.ids().length; i++) {
                ids[r * len + i] = s.ids()[i];
                att[r * len + i] = 1;
            }
            for (int i = 0; i < s.markers().length; i++) {
                pos[r * k + i] = s.markers()[i];
                posMask[r * k + i] = 1;
            }
            qtype[r] = s.qtype();
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", Onnx.longs(ids, n, len));
            inputs.put("attention_mask", Onnx.longs(att, n, len));
            inputs.put("marker_pos", Onnx.longs(pos, n, k));
            inputs.put("marker_mask", bools(posMask, n, k));
            inputs.put("qtype", Onnx.longs(qtype, n));
            try (OrtSession.Result result = session.run(inputs, Set.of("logits"))) {
                FloatBuffer flat = ((OnnxTensor) result.get("logits").orElseThrow()).getFloatBuffer();
                float[][] logits = new float[n][k];
                for (float[] row : logits) {
                    flat.get(row);
                }
                return logits;
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private static OnnxTensor bools(long[] mask, int n, int k) throws OrtException {
        boolean[][] b = new boolean[n][k];
        for (int i = 0; i < mask.length; i++) {
            b[i / k][i % k] = mask[i] != 0;
        }
        return OnnxTensor.createTensor(Onnx.ENV, b);
    }

    /** rl_agent_api: logits / temperature for this (type, option count) bucket, softmaxed over the real options. */
    private double[] probabilities(float[] logits, int k, int qtype) {
        String size = k <= 2 ? "2" : k <= 5 ? "3-5" : k <= 10 ? "6-10" : "11+";
        String bucket = List.of("choice", "score", "noul").get(qtype) + ":" + size;
        double t = temperatureByOptions.getOrDefault(bucket, temperature[qtype]);
        float[] z = new float[k];
        for (int i = 0; i < k; i++) {
            z[i] = (float) (logits[i] / t);
        }
        return Probabilities.softmax(z);
    }

    /** Everything but the markers is caller text, so [CLS]/[SEP] spelled there stay text. */
    private long[] encode(String text) {
        return tokenizer.encodeText(text);
    }

    private long special(String token) {
        return tokenizer.encode(token)[0];
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** rl_common.serialize_state: text as is, anything else as JSON. Laya was trained on Python's json.dumps spacing;
     *  Codec's compact form measured no different against Jev (104 requests), so it isn't reproduced. */
    static String serialize(Content content) {
        return switch (content) {
            case Content.Text(String value) -> value;
            case Content.Fields(Map<String, Object> fields) -> Onnx.toJson(fields);
            case Content.Messages(List<String> values) -> Onnx.toJson(values);
        };
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public String description() {
        return "Laya typed-decisions: ModernBERT encoder + decision head (local ONNX)";
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
