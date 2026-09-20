package io.github.dfa1.typesafe.jackson3;

import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.Model;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.State;
import io.github.dfa1.typesafe.json.JsonCodec;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * JsonCodec backed by Jackson 3.x. Owns the polymorphic {@code type} discriminator for
 * {@link Answer} and {@link Question} via mixins, since the client DTOs carry no Jackson
 * annotations of their own.
 */
public final class Jackson3Codec implements JsonCodec {

    private final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(Answer.class, AnswerMixIn.class)
            .addMixIn(Question.class, QuestionMixIn.class)
            .addModule(new SimpleModule()
                    .addSerializer(State.class, new StateSerializer())
                    .addSerializer(Model.class, new ModelSerializer())
                    .addDeserializer(Model.class, new ModelDeserializer()))
            .build();

    @Override
    public String writeValueAsString(Object value) {
        return mapper.writeValueAsString(value);
    }

    @Override
    public <T> T readValue(String content, Class<T> type) {
        return mapper.readValue(content, type);
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
