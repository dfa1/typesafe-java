package io.github.dfa1.typesafe.local;

import io.github.dfa1.typesafe.core.Answer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** From per-option logits to TypeSafe answers; shared by both engines. */
final class Probabilities {

    private Probabilities() {
    }

    /** Max-shifted so {@code exp} can't overflow. */
    static double[] softmax(float... logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float l : logits) {
            max = Math.max(max, l);
        }
        double[] p = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            p[i] = Math.exp(logits[i] - max);
            sum += p[i];
        }
        for (int i = 0; i < p.length; i++) {
            p[i] /= sum;
        }
        return p;
    }

    /** {@code 1 − H(p)/ln(n)}: 1 when all mass is on one answer, 0 when spread evenly. TypeSafe doesn't
     *  document its formula; this one (also Laya's) is ours. */
    static double confidence(double[] p) {
        if (p.length < 2) {
            return 1;
        }
        double entropy = 0;
        for (double pi : p) {
            entropy -= pi > 0 ? pi * Math.log(pi) : 0;
        }
        return 1 - entropy / Math.log(p.length);
    }

    /** A choice over {@code criteria}'s keys, given each option's probability in order. */
    static Answer.Choice choice(Map<String, String> criteria, double[] p) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        String best = null;
        int i = 0;
        for (String key : criteria.keySet()) {
            probabilities.put(key, p[i]);
            if (best == null || p[i] > probabilities.get(best)) {
                best = key;
            }
            i++;
        }
        return new Answer.Choice(best, probabilities, confidence(p));
    }

    /** A score over {@code criteria}'s levels (0-based, as the API), given each level's probability in order. */
    static Answer.Score score(List<String> criteria, double[] p) {
        Map<String, String> legend = new LinkedHashMap<>();
        Map<String, Double> probabilities = new LinkedHashMap<>();
        double score = 0;
        for (int i = 0; i < p.length; i++) {
            legend.put(String.valueOf(i), criteria.get(i));
            probabilities.put(String.valueOf(i), p[i]);
            score += i * p[i];
        }
        return new Answer.Score(score, legend, probabilities, confidence(p));
    }
}
