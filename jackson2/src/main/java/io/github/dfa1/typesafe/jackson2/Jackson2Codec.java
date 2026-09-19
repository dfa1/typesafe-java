package io.github.dfa1.typesafe.jackson2;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.json.JsonCodec;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * JsonCodec backed by Jackson 2.x. Owns the polymorphic {@code type} discriminator for
 * {@link Answer} and {@link Question} via mixins, since the client DTOs carry no Jackson
 * annotations of their own.
 */
public final class Jackson2Codec implements JsonCodec {

    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
            .addMixIn(Answer.class, AnswerMixIn.class)
            .addMixIn(Question.class, QuestionMixIn.class)
            .registerModule(new SimpleModule().addSerializer(State.class, new StateSerializer()));

    @Override
    public byte[] writeValueAsBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> T readValue(byte[] content, Class<T> type) {
        try {
            return mapper.readValue(content, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Answer.Noul.class, name = "noul"),
            @JsonSubTypes.Type(value = Answer.Choice.class, name = "choice"),
            @JsonSubTypes.Type(value = Answer.Score.class, name = "score")
    })
    private interface AnswerMixIn {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Question.Noul.class, name = "noul"),
            @JsonSubTypes.Type(value = Question.Choice.class, name = "choice"),
            @JsonSubTypes.Type(value = Question.Score.class, name = "score")
    })
    private interface QuestionMixIn {
    }
}
