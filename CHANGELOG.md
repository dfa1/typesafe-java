# Changelog

All notable changes to **typesafe-java-jdk** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `TypesafeClient`: JDK 21 `HttpClient`-based client for the TypeSafe API, evaluating `Noul`/`Choice`/`Score` questions against a state, with exponential-backoff retry on `429`/`529`.
- `TypesafeClient.evaluateAsync`: `CompletableFuture`-based async variant of `evaluate`, sharing the same retry logic.
- `EvaluateResponse.Metadata`: captures the `x-typesafe-request-id` and `x-envoy-upstream-service-time` response headers alongside the parsed body.
- Split the single module into `core` (DTOs + `JsonCodec` SPI, no Jackson dependency), `client` (`TypesafeClient`/`ApiToken`/`TypesafeException`), `jackson2`/`jackson3` (pluggable codec implementations, resolved via `ServiceLoader`), and `bom`. See [ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md).
- `checkstyle.xml`, wired to the `validate` phase.
- `CLAUDE.md` and Diataxis-structured docs (`docs/tutorial.md`, `docs/how-to.md`, `docs/reference.md`, `docs/explanation.md`).
- MIT license.
