package io.github.dfa1.typesafe.core;

/**
 * Metadata about one model available to the account, as returned by
 * {@link TypesafeClient#listModels()}.
 *
 * @param name        model name or alias accepted by a request's {@code model} field (e.g.
 *                    {@code "jev-1.13.0"})
 * @param description human-readable description of the model and its capabilities
 * @param releaseDate release date, formatted as {@code YYYY-MM-DD}
 */
public record ModelDetails(String name, String description, String releaseDate) {

    /** This model's id, usable directly as an {@link EvaluateRequest}'s model. */
    public Model model() {
        return new Model(name);
    }
}
