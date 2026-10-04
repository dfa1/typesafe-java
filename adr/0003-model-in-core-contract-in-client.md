# 3. The model in `core`, the contract in `client`, each implementation in a `client-*` module

Date: 2026-10-04

## Status

Accepted. Refines the module layout of [ADR 0001](0001-multi-module-layout-with-pluggable-json-codec.md);
its reasons for the `JsonCodec`/`HttpTransport` SPIs still hold.

## Context

`core` held everything but the concrete libraries: the model, the `TypeSafeClient` interface and its
decorators, and the HTTP client itself (`DefaultTypeSafeClient`, `ApiKey`, the `HttpTransport` SPI).
That was fine while the HTTP client was the only `TypeSafeClient`. Then `local` added three in-process
ones, and the layout stopped saying what depended on what:

- `local` depended on `core`, and so on the HTTP client it doesn't use.
- `TypeSafeClient.builder()` returned the HTTP client's builder, as if it were the only implementation.
- Serializing the model (e.g. for Kafka) pulled in the client contract and the HTTP client with it.
- "`core` never imports the HTTP client" was a convention, not something the build enforced.
- Artifact names didn't match their packages: `typesafe-java-client-jdk` held `...jdk`,
  `typesafe-java-jackson2` held `...jackson2`, and `local`/`mapping`/`testkit` gave no hint they're
  all built on `TypeSafeClient`.

## Decision

Three layers, each a module (or a family of modules) that only depends on the layers below it:

- **`core`, the model**, with no dependencies: `Answer`, `Question`, `Content`, `EvaluateRequest`,
  `EvaluateResponse`, `Usage`, `RequestId`, `Model`, `ModelDetails`, and the `JsonCodec` SPI. With a
  `codec-*` module it serializes TypeSafe payloads without any client code.
- **`client`, the contract**: the `TypeSafeClient` interface, `TypeSafeException`, and the decorators
  that wrap any implementation (`RetryingTypeSafeClient`, `DeadlineTypeSafeClient`, `TokenCounter`).
- **`client-*`, the implementations and what builds on the contract**, none depending on another:
  `client-http` (`DefaultTypeSafeClient`, `ApiKey`, the `HttpTransport` SPI) with its transports
  `client-http-jdk` and `client-http-okhttp`; `client-local`; `client-mapping`; `client-testkit`.
- **`codec-*`** modules implement `JsonCodec` on `core` alone: `codec-jackson2`, `codec-jackson3`.

**One name per module**: its directory, its artifact (`typesafe-java-<module>`) and its single package
(`io.github.dfa1.typesafe.<module>`, dashes as dots) are the same, and no package spans two modules. A
module named `X-Y` builds on module `X`.

`TypeSafeClient.builder()` is removed: `client` can't reference `client-http`. Callers write
`DefaultTypeSafeClient.builder()`.

`JsonCodec` stays in `core` rather than in a `codec` module of its own: everything that uses it
(`client-http`, `client-local`, the codecs) needs `core` anyway, so a separate artifact would always
travel with it.

## Consequences

- The compiler enforces every boundary. javadoc can't `{@link}` upwards (from `core` into `client`, or
  from `client` into `client-http`), so it names those types in `{@code ...}`. `DeadlineTypeSafeClient`
  keeps its own copy of the small helper that blocks on a future, rather than reaching into
  `client-http` for it.
- A typical API user still adds two dependencies, a transport and a codec; `client-http`, `client` and
  `core` come transitively.
- Breaking: every artifact except `core`, `cli` and `bom` is renamed, every package except `core` and
  `cli` moves, and `TypeSafeClient.builder()` is gone. There are no users yet, so there are no
  deprecation shims; the artifacts already published under the old names stay as they are.
