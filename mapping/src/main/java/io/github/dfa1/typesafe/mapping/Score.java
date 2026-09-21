package io.github.dfa1.typesafe.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code double} record component as a {@code Score} question along an ordered scale.
 * The component is populated with the probability-weighted value across {@link #levels()},
 * matching {@link io.github.dfa1.typesafe.core.Answer.Score#score()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Score {

    /** What to evaluate. */
    String value();

    /** Ordered level descriptions, lowest to highest; at least two. */
    String[] levels();
}
