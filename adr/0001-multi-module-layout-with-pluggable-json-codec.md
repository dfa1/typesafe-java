# 1. Multi-module layout with pluggable JSON and HTTP transport

Date: 2026-09-19

## Status

Accepted

## Context

The project started as a single module hard-wired to Jackson 2 (`ObjectMapper` built directly
inside `TypeSafeClient`, `@JsonTypeInfo`/`@JsonSubTypes` on the DTOs) and to
`java.net.http.HttpClient`. We need to:

- support both Jackson 2 and Jackson 3 consumers, and other HTTP libraries (Apache HttpClient,
  OkHttp, a mock transport for tests), without forking `TypeSafeClient`'s retry/backoff logic;
- let the request/response DTOs be reused outside the HTTP client (e.g. to serialize the same
  payloads onto/from a Kafka topic) without pulling in a JSON or HTTP library at all.

## Decision

Split into five Maven modules:

- **core** — `TypeSafeClient`, `ApiToken`, `TypeSafeException`, and the wire DTOs `Answer`,
  `Question`, `EvaluateRequest`/`EvaluateResponse`, `Usage`, `RequestId` — all in
  `io.github.dfa1.typesafe.core`; plus two SPIs:
  - `JsonCodec` (`io.github.dfa1.typesafe.json`) — `writeValueAsBytes`/`readValue`.
  - `HttpTransport` (`io.github.dfa1.typesafe.transport`) — `post`/`postAsync`, the single HTTP
    call `TypeSafeClient` needs.

  `core` has zero dependency on any JSON or HTTP library: `TypeSafeClient` talks to
  `HttpTransport`/`JsonCodec`, never to a concrete library directly. Its only `java.net` import
  is `URI`, which every JDK ships. The DTOs carry no serialization annotations either.
- **jdk-http-client** — implements `HttpTransport` with `JdkHttpTransport`
  (`java.net.http.HttpClient`), discovered via `ServiceLoader` through
  `META-INF/services/io.github.dfa1.typesafe.transport.HttpTransport` (or wired explicitly via
  `Builder.httpTransport(...)`).
- **jackson2** / **jackson3** — each implements `JsonCodec`, discovered the same way through
  `META-INF/services/io.github.dfa1.typesafe.json.JsonCodec` (or `Builder.jsonCodec(...)`), and
  owns the `type`-discriminator polymorphism for `Answer`/`Question` via private Jackson mixins
  (`addMixIn`) instead of annotations on the DTOs themselves.
- **bom** — dependency-management POM listing the four artifacts above for consumers to import.

There is no separate `client` module: once `TypeSafeClient` depends only on the `HttpTransport`
abstraction rather than `java.net.http` directly, keeping it apart from `core` no longer buys
anything, so it lives with the DTOs and SPIs in `core`.

`TypeSafeClient.Builder` resolves both `HttpTransport` and `JsonCodec` via `ServiceLoader` at
`build()` time (or explicit `Builder.httpTransport(...)`/`Builder.jsonCodec(...)` overrides) —
the same pattern the JDK itself uses for `java.sql.Driver` or
`java.nio.file.spi.FileSystemProvider`.

## Consequences

- `core` can be depended on alone by any code that just needs to (de)serialize TypeSafe payloads
  (e.g. a Kafka producer), picking whichever `JsonCodec` module fits its runtime — it still pulls
  in `TypeSafeClient`/`ApiToken`/`HttpTransport` unused, but harmlessly, since `core` has zero
  transitive dependencies either way. Worth revisiting if `core` ever grows a real dependency of
  its own.
- Adding a third JSON library or a different HTTP library later means adding one more codec or
  transport module; `core` doesn't change.
- A consumer that forgets a codec or transport module gets a clear `IllegalStateException` from
  `TypeSafeClient.Builder.build()` at runtime, not a `NoClassDefFoundError` — deliberately a
  runtime check, not a compile-time one, since a compile-time check would mean picking one
  codec/transport as "the real dependency," which is exactly what this design avoids.
- The polymorphism mixins in `jackson2`/`jackson3` must be kept in sync by hand if a new
  `Answer`/`Question` subtype is added — there's no compiler check tying the DTOs to the mixins.
- A blocking-only HTTP library's `HttpTransport.postAsync` has no non-blocking send to chain off
  (unlike `JdkHttpTransport`, which delegates straight to `HttpClient.sendAsync`) and has to fall
  back to running `post` on a separate thread per call — a real cost under heavy concurrent
  `evaluateAsync` use that the `HttpTransport` interface itself can't prevent.
