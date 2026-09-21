package io.github.dfa1.typesafe.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code String} record component as a single-select {@code Choice} question. The
 * component is populated with the selected option's key, matching
 * {@link io.github.dfa1.typesafe.core.Answer.Choice#choice()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Choice {

    /** The decision the model should make. */
    String value();

    /** The fixed set of options to choose from; at least one. */
    Option[] options();
}
