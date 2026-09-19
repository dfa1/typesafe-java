# Changelog

All notable changes to **typesafe-java-jdk** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `TypesafeClient`: client for the TypeSafe API, evaluating `Noul`/`Choice`/`Score` questions against a state, with exponential-backoff retry on `429`/`529`.
- `TypesafeClient.evaluateAsync`: `CompletableFuture`-based async variant of `evaluate`, sharing the same retry logic.
- `EvaluateResponse.Metadata`: captures the `x-typesafe-request-id` and `x-envoy-upstream-service-time` response headers alongside the parsed body.
- `HttpTransport` SPI (`io.github.dfa1.typesafe.transport`): decouples `TypesafeClient` from any concrete HTTP library, mirroring `JsonCodec`. `typesafe-java-jdk-http-client` supplies `JdkHttpTransport` (`java.net.http`); implement `HttpTransport` yourself for Apache HttpClient, OkHttp, etc.
- Split into Maven modules: `core` (`TypesafeClient`/`ApiToken`/`TypesafeException` and the DTOs, all in `io.github.dfa1.typesafe.core`, plus the `JsonCodec`/`HttpTransport` SPIs — no dependency on any JSON or HTTP library), `jdk-http-client`/`jackson2`/`jackson3` (pluggable implementations, resolved via `ServiceLoader`), and `bom`. See [ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md).
- `checkstyle.xml`, wired to the `validate` phase.
- `CLAUDE.md` and Diataxis-structured docs (`docs/tutorial.md`, `docs/how-to.md`, `docs/reference.md`, `docs/explanation.md`).
- MIT license.
