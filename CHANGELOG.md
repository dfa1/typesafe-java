# Changelog

All notable changes to **typesafe-java** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- `cli`'s runnable uber-jar is now published to Maven Central under the `all` classifier
  (`typesafe-java-cli-VERSION-all.jar`), GPG-signed like every other artifact; the plain
  `typesafe-java-cli` artifact stays a normal, non-executable jar so a mistaken plain
  dependency on it doesn't pull in unrelocated, bundled copies of its dependencies. The `all`
  jar and its signature are also attached to each GitHub release, so it's downloadable and
  verifiable without a Maven client.

## [0.2.0] - 2026-09-20

- `cli`'s uber-jar is now attached to each GitHub release, so it's downloadable without
  building from source. Still not published to Maven Central — it's an uber-jar, not a
  library dependency.

## [0.1.0] - 2026-09-20

Initial release.

- `TypesafeClient`: synchronous `evaluate`, asynchronous `evaluateAsync`, and `listModels`.
  Retries `408`/`429`/any `5xx` (honoring `Retry-After`) and connection failures, up to
  `maxRetries` with exponential backoff. Implements `AutoCloseable`.
- `Noul`/`Choice`/`Score` questions via `Question`; typed `Answer` responses via
  `EvaluateResponse.answers()`, plus `.nouls()`/`.choices()`/`.scores()` grouped by type.
- `EvaluateRequest.builder()`, a fluent alternative to `of(...)`. (#1)
- `Model`/`ModelDetails`: pin a request to `Model.LATEST`/`PREVIEW` or any id, or discover
  what's available via `listModels()`.
- `ApiKey` from a file, `~/.typesafe.apitoken` by default, the `TYPESAFE_API_KEY` environment
  variable (#5), or an in-memory value.
- `JsonCodec` (Jackson 2.x or 3.x) and `HttpTransport` (`java.net.http`) are both pluggable via
  `ServiceLoader`, or wired explicitly. `JdkHttpTransport` applies a 10s request timeout by
  default.
- `cli` module: a self-contained jar for ad hoc checks from a terminal or a CI pipeline,
  without writing Java. Not published as a library artifact.
