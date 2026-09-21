package io.github.dfa1.typesafe.core;

/**
 * A model: either a symbolic alias the server resolves ({@link #LATEST}/{@link #PREVIEW}) or a
 * concrete, versioned id, e.g. {@code new Model("jev-1.13.0")}. Usable directly as an
 * {@link EvaluateRequest}'s model, and reported back by {@link EvaluateResponse#model()}. See
 * {@link TypeSafeClient#listModels()} for descriptions and release dates.
 *
 * @param name the model's id (e.g. {@code "jev-latest"}, {@code "jev-1.13.0"})
 */
public record Model(String name) {

    /** Most recent stable, official release. The default in this client. */
    public static final Model LATEST = new Model("jev-latest");

    /** Most recent release, whether or not it is an official one. */
    public static final Model PREVIEW = new Model("jev-preview");
}
