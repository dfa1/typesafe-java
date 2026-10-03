package io.github.dfa1.typesafe.local;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.TypeSafeException;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Answers from a chat LLM's next-token logits, never from generated text: one prefill per
 * question, ending where the answer token goes, then a softmax over just the candidate tokens
 * ({@code Yes}/{@code No}, option letters, level digits). Deterministic, nothing to parse.
 * Prompted with Qwen2.5's ChatML template.
 */
final class QwenEngine implements Engine {

    /** Choice options are labelled A, B, ...; Score levels 0, 1, ... — single tokens, so one prefill reads them all. */
    static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String DIGITS = "0123456789";
    /** ChatML before and after {@code "user\n" + message}. */
    static final String CHAT_HEAD = "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n<|im_start|>";
    static final String CHAT_TAIL = "<|im_end|>\n<|im_start|>assistant\n";

    /** Last position's logits for a prompt. */
    @FunctionalInterface
    interface Forward {
        float[] nextTokenLogits(long[] ids) throws OrtException;
    }

    private final Function<String, long[]> encode;
    private final Function<String, long[]> encodeText;
    private final Forward forward;
    private final Runnable close;
    private final Model model;
    private final int yes;
    private final int no;
    private final int[] letters = new int[LETTERS.length()];
    private final int[] digits = new int[DIGITS.length()];

    /** @param encode for the ChatML template, {@code encodeText} for the user turn (special tokens stay text) */
    QwenEngine(Function<String, long[]> encode, Function<String, long[]> encodeText, Forward forward, Runnable close,
               Model model) {
        this.encode = encode;
        this.encodeText = encodeText;
        this.forward = forward;
        this.close = close;
        this.model = model;
        this.yes = singleToken("Yes");
        this.no = singleToken("No");
        for (int i = 0; i < letters.length; i++) {
            letters[i] = singleToken(String.valueOf(LETTERS.charAt(i)));
        }
        for (int i = 0; i < digits.length; i++) {
            digits[i] = singleToken(String.valueOf(DIGITS.charAt(i)));
        }
    }

    static QwenEngine load(Path dir) {
        return load(dir, false);
    }

    static QwenEngine load(Path dir, boolean gpu) {
        Path modelFile = Onnx.model(dir);
        Path tokenizerFile = Onnx.require(dir, "tokenizer.json");
        try {
            BpeTokenizer tokenizer = Onnx.tokenizer(tokenizerFile);
            OrtSession session = Onnx.session(modelFile, gpu);
            Runnable close = () -> {
                try {
                    session.close();
                } catch (OrtException e) {
                    throw new IllegalStateException(e);
                }
            };
            return new QwenEngine(tokenizer::encode, tokenizer::encodeText, prefill(session), close,
                    new Model("local/" + dir.getFileName()));
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("cannot load " + dir, e);
        }
    }

    /** One prefill with an empty KV cache, asking ONNX Runtime for the logits only (not the cache). As exported, the
     *  graph computes logits at every position (n × 600 KB); {@code scripts/qwen/last_logits.py} cuts it to the last. */
    static Forward prefill(OrtSession session) throws OrtException {
        Map<String, NodeInfo> inputs = session.getInputInfo();
        List<String> cache = inputs.keySet().stream().filter(n -> n.startsWith("past_key_values.")).toList();
        boolean positions = inputs.containsKey("position_ids");
        return ids -> {
            int n = ids.length;
            long[] ones = new long[n];
            long[] pos = new long[n];
            Arrays.fill(ones, 1);
            Arrays.setAll(pos, i -> i);
            Map<String, OnnxTensor> in = new HashMap<>();
            try {
                in.put("input_ids", Onnx.longs(ids, 1, n));
                in.put("attention_mask", Onnx.longs(ones, 1, n));
                if (positions) {
                    in.put("position_ids", Onnx.longs(pos, 1, n));
                }
                for (String name : cache) {
                    in.put(name, emptyCache((TensorInfo) inputs.get(name).getInfo()));
                }
                try (OrtSession.Result result = session.run(in, Set.of("logits"))) {
                    OnnxTensor logits = (OnnxTensor) result.get("logits").orElseThrow();
                    long[] shape = logits.getInfo().getShape(); // [1, n, vocab], or [1, 1, vocab] after last_logits.py
                    int vocab = (int) shape[2];
                    float[] last = new float[vocab];
                    FloatBuffer all = logits.getFloatBuffer();
                    all.position((int) (shape[1] - 1) * vocab);
                    all.get(last);
                    return last;
                }
            } finally {
                in.values().forEach(OnnxTensor::close);
            }
        };
    }

    /** {@code [1, kv_heads, 0, head_dim]}: no past, in whatever precision the model declares. */
    private static OnnxTensor emptyCache(TensorInfo info) throws OrtException {
        long[] shape = {1, info.getShape()[1], 0, info.getShape()[3]};
        return info.type == OnnxJavaType.FLOAT16
                ? OnnxTensor.createTensor(Onnx.ENV, ShortBuffer.allocate(0), shape, OnnxJavaType.FLOAT16)
                : OnnxTensor.createTensor(Onnx.ENV, FloatBuffer.allocate(0), shape);
    }

    @Override
    public Answers answer(Content state, Map<String, Question> questions) throws OrtException {
        String rendered = render(state);
        Map<String, Answer> answers = new LinkedHashMap<>();
        int tokens = 0;
        for (Map.Entry<String, Question> entry : questions.entrySet()) {
            Question question = entry.getValue();
            validate(entry.getKey(), question);
            long[] prompt = chat(prompt(rendered, question));
            tokens += prompt.length;
            answers.put(entry.getKey(), answer(question, forward.nextTokenLogits(prompt)));
        }
        return new Answers(answers, tokens);
    }

    Answer answer(Question question, float[] logits) {
        return switch (question) {
            case Question.Noul ignored -> new Answer.Noul(Probabilities.softmax(logits[yes], logits[no])[0]);
            case Question.Choice(Content ignored, Map<String, String> criteria) ->
                    Probabilities.choice(criteria, Probabilities.softmax(pick(logits, letters, criteria.size())));
            case Question.Score(Content ignored, List<String> criteria) ->
                    Probabilities.score(criteria, Probabilities.softmax(pick(logits, digits, criteria.size())));
        };
    }

    private static float[] pick(float[] logits, int[] tokens, int n) {
        float[] result = new float[n];
        for (int i = 0; i < n; i++) {
            result[i] = logits[tokens[i]];
        }
        return result;
    }

    /** Rejects what single-token labels can't express, as the API would: with a 400. */
    static void validate(String name, Question question) {
        switch (question) {
            case Question.Noul ignored -> {
            }
            case Question.Choice(Content ignored, Map<String, String> criteria) -> {
                if (criteria == null || criteria.isEmpty() || criteria.size() > LETTERS.length()) {
                    throw new TypeSafeException.BadRequest(name + ": a choice needs 1 to " + LETTERS.length() + " options");
                }
            }
            case Question.Score(Content ignored, List<String> criteria) -> {
                if (criteria == null || criteria.size() < 2 || criteria.size() > DIGITS.length()) {
                    throw new TypeSafeException.BadRequest(name + ": a score needs 2 to " + DIGITS.length() + " levels");
                }
            }
        }
    }

    /** ChatML around {@code message}; HF splits text at special tokens, so encoding the three pieces apart gives the
     *  same ids as the whole string, except that a special token spelled in {@code message} stays text. */
    long[] chat(String message) {
        long[] head = encode.apply(CHAT_HEAD);
        long[] user = encodeText.apply("user\n" + message);
        long[] tail = encode.apply(CHAT_TAIL);
        return LongStream.concat(LongStream.concat(Arrays.stream(head), Arrays.stream(user)), Arrays.stream(tail)).toArray();
    }

    /** The user message: the state, then the question, ending where the answer token goes. */
    static String prompt(String state, Question question) {
        return switch (question) {
            case Question.Noul(Content instructions, Map<String, String> criteria) -> {
                StringBuilder sb = question(state, instructions);
                if (criteria != null) {
                    criteria.forEach((k, v) -> sb.append('"').append(k).append("\" means: ").append(v).append('\n'));
                }
                yield sb.append("Answer with Yes or No.").toString();
            }
            case Question.Choice(Content instructions, Map<String, String> criteria) -> {
                StringBuilder sb = question(state, instructions).append("Options:\n");
                int i = 0;
                for (Map.Entry<String, String> option : criteria.entrySet()) {
                    sb.append(LETTERS.charAt(i++)).append(") ").append(option.getKey());
                    if (option.getValue() != null && !option.getValue().isBlank()) {
                        sb.append(": ").append(option.getValue());
                    }
                    sb.append('\n');
                }
                yield sb.append("Answer with the letter of one option.").toString();
            }
            case Question.Score(Content instructions, List<String> criteria) -> {
                StringBuilder sb = question(state, instructions).append("Levels, lowest to highest:\n");
                for (int i = 0; i < criteria.size(); i++) {
                    sb.append(i).append(") ").append(criteria.get(i)).append('\n');
                }
                yield sb.append("Answer with the number of one level.").toString();
            }
        };
    }

    private static StringBuilder question(String state, Content instructions) {
        return new StringBuilder(state).append("\n\nQuestion: ").append(render(instructions)).append('\n');
    }

    static String render(Content content) {
        return switch (content) {
            case Content.Text(String value) -> value;
            case Content.Fields(Map<String, Object> fields) -> fields.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n"));
            case Content.Messages(List<String> values) -> String.join("\n", values);
        };
    }

    // one spelling per answer ("Yes", not also "yes"/" Yes"); sum variants if a model splits its mass
    private int singleToken(String text) {
        long[] ids = encode.apply(text);
        if (ids.length != 1) {
            throw new IllegalStateException("\"" + text + "\" is " + ids.length + " tokens for this model, need 1");
        }
        return (int) ids[0];
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public String description() {
        return "Qwen2.5 chat model answering from next-token logits (local ONNX)";
    }

    @Override
    public void close() {
        close.run();
    }
}
