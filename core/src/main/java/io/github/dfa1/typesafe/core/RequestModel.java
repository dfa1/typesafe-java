package io.github.dfa1.typesafe.core;

/**
 * A model usable as an {@link EvaluateRequest}'s {@code model}: either a concrete
 * {@link Model} — one returned by {@link TypesafeClient#listModels()}, or pinned directly by
 * id — or a symbolic {@link Alias} the server resolves to its current concrete model.
 */
public sealed interface RequestModel permits Model, RequestModel.Alias {

    /** The wire value sent as the request's {@code model} field. */
    String id();

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
