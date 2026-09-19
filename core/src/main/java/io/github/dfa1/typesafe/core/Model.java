package io.github.dfa1.typesafe.core;

/**
 * Known values for {@link EvaluateRequest#model()}. See
 * <a href="https://docs.typesafe.ai/models">docs.typesafe.ai/models</a>.
 */
public enum Model {

    /** Most recent stable, official release. The default in this client. */
    LATEST("jev-latest"),

    /** Most recent release, whether or not it is an official one. */
    PREVIEW("jev-preview"),

    /** TypeSafe's flagship System One model. */
    JEV_1_13_0("jev-1.13.0");

    private final String id;

    Model(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}
