package io.github.dfa1.typesafe.core;

/**
 * A concrete, versioned model, as returned by {@link TypesafeClient#listModels()}. Also usable
 * directly as an {@link EvaluateRequest}'s model, e.g. to pin a request to an id this client
 * has no other constant for — {@code description}/{@code releaseDate} are then unknown and can
 * be left {@code null}, since only {@link #id()} is ever sent on the wire.
 *
 * @param name        model name or alias accepted by a request's {@code model} field (e.g.
 *                    {@code "jev-1.13.0"})
 * @param description human-readable description of the model and its capabilities, or
 *                     {@code null} if unknown
 * @param releaseDate release date formatted as {@code YYYY-MM-DD}, or {@code null} if unknown
 */
public record Model(String name, String description, String releaseDate) implements RequestModel {

    @Override
    public String id() {
        return name;
    }
}
