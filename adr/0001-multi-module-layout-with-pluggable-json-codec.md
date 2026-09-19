# 1. Multi-module layout with a pluggable JSON codec

Date: 2026-09-19

## Status

Accepted

## Context

The project started as a single module hard-wired to Jackson 2 (`ObjectMapper` built
directly inside `TypesafeClient`, `@JsonTypeInfo`/`@JsonSubTypes` on the DTOs). We now
need to:

- support both Jackson 2 and Jackson 3 consumers without forking the client, and
- let the request/response DTOs be reused outside the HTTP client (e.g. to
  serialize the same payloads onto/from a Kafka topic) without pulling in
  `java.net.http`-specific code.

## Decision

Split into five Maven modules:

- **core** — the DTOs (`Answer`, `Question`, `EvaluateRequest`/`EvaluateResponse`,
  `Usage`, `RequestId`) and the `ai.typesafe.json.JsonCodec` SPI. No Jackson
  dependency at all, and no Jackson annotations on the DTOs.
- **client** — `TypesafeClient`, `ApiToken`, `TypesafeException`. Depends only on
  `core`. Resolves a `JsonCodec` via `ServiceLoader` at build time (or an explicit
  `Builder.jsonCodec(...)` override).
- **jackson2** / **jackson3** — each depends only on `core`, supplies a
  `JsonCodec` implementation, and owns the `type`-discriminator polymorphism for
  `Answer`/`Question` via private Jackson mixins (`addMixIn`) registered through
  `META-INF/services`, instead of annotations on the DTOs themselves.
- **bom** — dependency-management POM listing the four artifacts above for
  consumers to import.

## Consequences

- `core` can be depended on alone by any code that just needs to (de)serialize
  TypeSafe payloads (e.g. a Kafka producer), picking whichever Jackson codec
  module fits its runtime.
- Adding a third JSON library later means adding one more codec module; `core`
  and `client` don't change.
- A consumer that depends on `client` but forgets a codec module gets a clear
  `IllegalStateException` from `TypesafeClient.Builder.build()` at runtime
  rather than a `NoClassDefFoundError`.
- The polymorphism mixins in `jackson2`/`jackson3` must be kept in sync by hand
  if a new `Answer`/`Question` subtype is added — there's no compiler check
  tying the DTOs to the mixins.
