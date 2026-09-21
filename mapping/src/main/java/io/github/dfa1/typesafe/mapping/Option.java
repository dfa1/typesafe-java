package io.github.dfa1.typesafe.mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One option of a {@link Choice} question. Only valid nested inside {@link Choice#options()}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Option {

    /** The option's key, as it appears in {@link io.github.dfa1.typesafe.core.Answer.Choice#choice()}. */
    String value();

    /** What this option means; may be omitted when the key needs no elaboration. */
    String description() default "";
}
