# CLAUDE.md

Guidance for Claude Code working in this repository.

## What it is

Java 21 client for the TypeSafe API (`https://api.typesafe.ai`). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

## Module structure

```
core      — TypesafeClient, ApiToken, TypesafeException, and the wire DTOs (Answer, Question,
            State, EvaluateRequest/EvaluateResponse, Usage, RequestId, Model), all in
            io.github.dfa1.typesafe.core; plus the JsonCodec (io.github.dfa1.typesafe.json) +
            HttpTransport (io.github.dfa1.typesafe.transport) SPIs. Zero dependency on any
            JSON or HTTP library — TypesafeClient talks to HttpTransport/JsonCodec, never to a
            concrete library directly, so the DTOs + JsonCodec alone are reusable (e.g. by a
            Kafka producer/consumer) without pulling in TypesafeClient's HTTP concerns.
jdk-http-client — HttpTransport backed by java.net.http (artifact
            typesafe-java-jdk-http-client, class JdkHttpTransport, package
            io.github.dfa1.typesafe.jdk). Depends only on core. Discovered via
            ServiceLoader at Builder.build() time (or an explicit
            Builder.httpTransport(...) override).
jackson2  — JsonCodec backed by Jackson 2.x. Depends only on core. Owns the `type`
            discriminator for Answer/Question via private Jackson mixins (addMixIn); State
            (no discriminator — string/object/array on the wire) via a custom serializer,
            registered through META-INF/services.
jackson3  — same, backed by Jackson 3.x (tools.jackson.databind).
bom       — dependency-management POM listing core/jdk-http-client/jackson2/jackson3.
```

Dependency rule: `jdk-http-client → core`, `jackson2 → core`, `jackson3 → core`. Neither
codec module depends on `jdk-http-client` in `main` scope — `jackson2` depends on it in
`test` scope only, to run the live-API demo/acceptance tests end-to-end. See
[ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md) for why.

## Commands

```bash
mvn clean install                 # build + unit tests, all modules
mvn test -pl jackson2             # one module
mvn test -pl jackson2 -Dtest=Jackson2CodecTest
```

Acceptance tests (`TypesafeClientAcceptanceTest`, `GraphqlSlotFillingDemoTest`,
`EntitlementTroubleshootingDemoTest` in `jackson2`) are `@Tag("acceptance")`, hit the
real TypeSafe API, and need a token at `~/.typesafe.apitoken`. Excluded from a routine
`mvn test` via the `excludedGroups=acceptance` property (surefire). Opt in with:

```bash
mvn test -pl jackson2 -am -DexcludedGroups=
```

## Design decisions

- **`core` has zero Jackson dependency and the DTOs carry zero Jackson annotations.**
  Polymorphism (`Answer`/`Question`'s `type` discriminator) is wired up entirely inside
  each codec module via mixins, not on the DTOs. Adding a third JSON library means
  adding one more codec module; `core`/`jdk-http-client` don't change.
- **`JsonCodec` is discovered via `ServiceLoader`, not a hard compile dependency.** A
  consumer that depends on `jdk-http-client` but forgets a codec module gets a clear
  `IllegalStateException` from `Builder.build()`, not a `NoClassDefFoundError`.
- **Small public API.** Don't expose internals — when in doubt, leave it out or make it
  package-private.

## Testing

JUnit 5. Prefer testing behavior through the real classes involved (e.g.
`Jackson2CodecTest`/`Jackson3CodecTest` exercise the codec, not a bare `ObjectMapper`) —
this is what caught that Jackson 3's builder API differs from Jackson 2's mutable
`ObjectMapper` during the initial split.

## Documentation is part of every change

Docs live under `docs/`, structured by [Diataxis](https://diataxis.fr/):
`tutorial.md` (learning-oriented walkthrough), `how-to.md` (task-oriented recipes),
`reference.md` (API surface), `explanation.md` (design rationale). A change to the
public API, module structure, or a documented behavior updates whichever of these
apply, in the same commit — plus `CHANGELOG.md` under `[Unreleased]`. `adr/` and
released `CHANGELOG.md` sections are exempt — they describe the past.
