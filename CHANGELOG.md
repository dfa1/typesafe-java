# Changelog

All notable changes to **typesafe-java** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First version — no released API yet, so nothing to describe changes against.

- Added `cli` module: an executable uber-jar for ad hoc checks against the API from a
  terminal, without writing Java. `--verbose`/`--timing` print the request id / response
  time to stderr; `--version` prints the jar's version and exits without calling the API.
  Not published as a library artifact.
- Removed `TypesafeClient.withDefaultToken()`: its name read as "a default token" rather
  than "the default token *file*". Use `TypesafeClient.builder(ApiKey.fromDefaultFile()).build()`.
- Added `ApiKey.fromEnv()`: reads the `TYPESAFE_API_KEY` environment variable, aligning
  the env-var case with the existing `fromFile`/`fromDefaultFile` factory methods. (#5)
- Changed `HttpTransport.post`/`postAsync` and `HttpTransportResponse.body` from `byte[]` to
  `String`: this SPI only ever carries JSON, which is UTF-8 by construction, so there's no
  charset for this layer to guess at. `HttpTransportResponse` also now copies `headers`
  defensively (`Map.copyOf`).
- Renamed `JsonCodec.writeValueAsBytes`/`readValue(byte[], ...)` to `writeValueAsString`/
  `readValue(String, ...)`, same reasoning as `HttpTransport` above — every call site in this
  codebase immediately wrapped the `byte[]` result in `new String(..., UTF_8)` anyway. A
  caller integrating with a raw-`byte[]` system (e.g. Kafka) converts once at that boundary.
  `TypesafeClient` now passes a request/response straight through both SPIs with no encode/
  decode step in between.
- Added `Question.noul(instructions)`, defaulting `criteria` to `null` for a yes/no question
  that needs no elaboration. Confirmed accepted by the live API before adding it.
- Added `EvaluateRequest.builder()`, a fluent alternative to `of(...)` plus hand-building the
  `questions` map (#1). `state(String)` is sugar for `state(State.text(...))`. Rejects a
  reused question name with `IllegalArgumentException` instead of silently overwriting the
  earlier question — a plain `Map.put` would otherwise drop it with no signal to the caller.
- `TypesafeClient` now implements `AutoCloseable`; `close()` closes the configured
  `HttpTransport`. `HttpTransport` gained a `close()` method (default no-op, so existing
  implementations don't break); `JdkHttpTransport.close()` closes its `HttpClient` (JDK 21+).
- Replaced the old `Model` enum (three hardcoded constants) with `RequestModel`, a sealed
  interface with two nested variants: `RequestModel.Alias` (`LATEST`/`PREVIEW`, the symbolic
  values the server resolves) and `RequestModel.Pinned` (a plain
  `record Pinned(name, description, releaseDate)`, pinning a specific id). `EvaluateRequest.model`
  is now a `RequestModel`; `EvaluateResponse.model` is now a `RequestModel.Pinned` (was `String`),
  with `description`/`releaseDate` left `null` since the evaluate response only reports the id.
  Added `TypesafeClient.listModels()` (`GET /v1/models`) to look up the full list, each with its
  description and release date — `HttpTransport` gained a matching `get(URI, headers)` method.
  Lets a caller discover and pin a model this client has no constant for, rather than being
  limited to the three previously hardcoded enum values.
