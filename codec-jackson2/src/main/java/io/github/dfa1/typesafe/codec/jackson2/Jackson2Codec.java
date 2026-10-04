package io.github.dfa1.typesafe.codec.jackson2;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Content;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.RequestId;
import io.github.dfa1.typesafe.codec.Codec;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Codec backed by Jackson 2.x. Owns the polymorphic {@code type} discriminator for
 * {@link Answer} and {@link Question} via mixins, since the client DTOs carry no Jackson
 * annotations of their own.
 */
public final class Jackson2Codec implements Codec {

    // UTF-8 output escapes characters outside the BMP (e.g. emoji) as surrogate pairs unless told otherwise;
    // a local model tokenizes the text, so they must stay one character, as Jackson 3 writes them
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
            .build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // a field the API adds must not break old clients; Jackson 3's default
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(Answer.class, AnswerMixIn.class)
            .addMixIn(Question.class, QuestionMixIn.class)
            .registerModule(new SimpleModule()
                    .addSerializer(Content.class, new ContentSerializer())
                    .addSerializer(Model.class, new ModelSerializer())
                    .addDeserializer(Model.class, new ModelDeserializer())
                    .addSerializer(RequestId.class, new RequestIdSerializer())
                    .addDeserializer(RequestId.class, new RequestIdDeserializer()));

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
