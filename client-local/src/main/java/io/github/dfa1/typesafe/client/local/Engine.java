package io.github.dfa1.typesafe.client.local;

import ai.onnxruntime.OrtException;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;

import java.util.Map;

/** A local model that answers TypeSafe questions; {@link LocalTypeSafeClient} owns everything else
 *  (the TypeSafe contract, async, timing). Implemented by {@link LayaEngine}, {@link QwenEngine} and {@link ClefEngine}. */
interface Engine extends AutoCloseable {

    record Answers(Map<String, Answer> answers, int inputTokens) {
    }

    /** @throws io.github.dfa1.typesafe.client.TypeSafeException.BadRequest for a question this engine can't express */
    Answers answer(Content state, Map<String, Question> questions) throws OrtException;

    Model model();

    String description();

    @Override
    void close();
}
