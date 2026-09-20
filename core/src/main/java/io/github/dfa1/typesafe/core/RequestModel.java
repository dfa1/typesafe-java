package io.github.dfa1.typesafe.core;

/**
 * A model usable as an {@link EvaluateRequest}'s {@code model}: either a concrete
 * {@link Pinned} model — one returned by {@link TypesafeClient#listModels()}, or pinned
 * directly by id — or a symbolic {@link Alias} the server resolves to its current concrete
 * model. {@link EvaluateResponse#model()} is always a {@link Pinned}.
 */
public sealed interface RequestModel permits RequestModel.Pinned, RequestModel.Alias {

    /** The wire value sent as the request's {@code model} field. */
    String id();

    /**
     * A concrete, versioned model, as returned by {@link TypesafeClient#listModels()} or
     * reported by {@link EvaluateResponse#model()}. Also usable directly as an
     * {@link EvaluateRequest}'s model to pin a request to an id this client has no other
     * constant for — {@code description}/{@code releaseDate} are then unknown and can be left
     * {@code null}, since only {@link #id()} is ever sent on the wire.
     *
     * @param name        model name or alias accepted by a request's {@code model} field (e.g.
     *                    {@code "jev-1.13.0"})
     * @param description human-readable description of the model and its capabilities, or
     *                     {@code null} if unknown
     * @param releaseDate release date formatted as {@code YYYY-MM-DD}, or {@code null} if
     *                     unknown
     */
    record Pinned(String name, String description, String releaseDate) implements RequestModel {

        @Override
        public String id() {
            return name;
        }
    }

    enum Alias implements RequestModel {

        /** Most recent stable, official release. The default in this client. */
        LATEST("jev-latest"),

        /** Most recent release, whether or not it is an official one. */
        PREVIEW("jev-preview");

        private final String id;

        Alias(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
