# CLAUDE.md

Guidance for Claude Code working in this repository.

## What it is

Java 21 client for the TypeSafe API (`https://api.typesafe.ai`). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

## Module structure

```
core      — ai.typesafe wire DTOs, no Jackson dependency at all: Answer, Question,
            EvaluateRequest/EvaluateResponse, Usage, RequestId, and the JsonCodec SPI
            (ai.typesafe.json). DTOs carry no serialization annotations — reusable
            standalone by anything that needs the same payloads (e.g. a Kafka
            producer/consumer), independent of the HTTP client and of which Jackson
            major version the caller uses.
client    — TypesafeClient, ApiToken, TypesafeException. Depends only on core.
            Resolves a JsonCodec via ServiceLoader at Builder.build() time (or an
            explicit Builder.jsonCodec(...) override).
jackson2  — JsonCodec backed by Jackson 2.x. Depends only on core. Owns the `type`
            discriminator for Answer/Question via private Jackson mixins (addMixIn),
            registered through META-INF/services.
jackson3  — same, backed by Jackson 3.x (tools.jackson.databind).
bom       — dependency-management POM listing core/client/jackson2/jackson3.
```

Dependency rule: `client → core`, `jackson2 → core`, `jackson3 → core`. Neither codec
module depends on `client` in `main` scope — `jackson2` depends on `client` in `test`
scope only, to run the live-API demo/acceptance tests end-to-end. See
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
  adding one more codec module; `core`/`client` don't change.
- **`JsonCodec` is discovered via `ServiceLoader`, not a hard compile dependency.** A
  consumer that depends on `client` but forgets a codec module gets a clear
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
