package io.github.dfa1.typesafe.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code double} record component as a {@code Noul} (yes/no) question. The component
 * is populated with the answer's raw probability (0–1), matching
 * {@link io.github.dfa1.typesafe.core.Answer.Noul#noul()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Noul {

    /** The yes/no question itself. */
    String value();
}
