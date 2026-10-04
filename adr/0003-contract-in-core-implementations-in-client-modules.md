# 3. The contract in `core`, each implementation in a `client-*` module

Date: 2026-10-04

## Status

Accepted. Refines the module layout of [ADR 0001](0001-multi-module-layout-with-pluggable-json-codec.md);
its reasons for the `JsonCodec`/`HttpTransport` SPIs still hold.

## Context

`core` held everything but the concrete libraries: the model, the `TypeSafeClient` interface and its
decorators, and also the HTTP client itself (`DefaultTypeSafeClient`, `ApiKey`, the `HttpTransport`
SPI). That was fine while the HTTP client was the only `TypeSafeClient`. Then `local` added three
in-process ones, and the layout stopped saying what depended on what:

- `local` depended on `core`, and so on the HTTP client it doesn't use.
- `TypeSafeClient.builder()` returned the HTTP client's builder, as if it were the only implementation.
- "`core` never imports the HTTP client" was a convention, not something the build enforced.
- Artifact names didn't match their packages: `typesafe-java-client-jdk` held `...jdk`,
  `typesafe-java-jackson2` held `...jackson2`, and `local`/`mapping`/`testkit` gave no hint they're
  all built on `TypeSafeClient`.

[Issue #12](https://github.com/dfa1/typesafe-java/issues/12) first proposed moving the interface and
both SPIs into a new `client` module. That would have pushed `local`, `mapping`, `testkit` and the
codecs onto the HTTP client too, the opposite of the goal.

## Decision

Split by contract versus implementation:

- **`core`** is the contract, with no dependencies: the `TypeSafeClient` interface, the decorators that
  wrap any implementation (`RetryingTypeSafeClient`, `DeadlineTypeSafeClient`, `TokenCounter`),
  `TypeSafeException`, the model, and the `JsonCodec` SPI. `JsonCodec` stays here because
  `client-local` reads its model configs through it, and the model plus a codec should serialize
  payloads (e.g. for Kafka) without any HTTP code.
- **`client-*`** modules implement or build on `TypeSafeClient`, and none depends on another:
  `client-http` (`DefaultTypeSafeClient`, `ApiKey`, the `HttpTransport` SPI) with its transports
  `client-http-jdk` and `client-http-okhttp`; `client-local`; `client-mapping`; `client-testkit`.
- **`codec-*`** modules implement `JsonCodec`: `codec-jackson2`, `codec-jackson3`. They depend only on `core`.
- **One name per module**: its directory, its artifact (`typesafe-java-<module>`) and its package
  (`io.github.dfa1.typesafe.<module>`, dashes as dots) are the same.

`TypeSafeClient.builder()` is removed: `core` can't reference `client-http`. Callers write
`DefaultTypeSafeClient.builder()`.

## Consequences

- The compiler enforces the boundary. javadoc in `core` can't `{@link}` into a `client-*` module, so it
  names `DefaultTypeSafeClient` in `{@code ...}` instead. `DeadlineTypeSafeClient` keeps its own copy of
  the small helper that blocks on a future, rather than reaching into `client-http` for it.
- A typical API user still adds two dependencies, a transport and a codec; `client-http` and `core`
  come transitively.
- Breaking: every artifact except `core`, `cli` and `bom` is renamed, every package except `core` and
  `cli` moves, and `TypeSafeClient.builder()` is gone. There are no users yet, so there are no
  deprecation shims; the artifacts already published under the old names stay as they are.
