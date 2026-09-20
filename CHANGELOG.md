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
- Changed `Model` from a fixed 3-constant enum to `record Model(String name)`, still with
  `Model.LATEST`/`Model.PREVIEW` constants, but now also constructible for any id
  (`new Model("jev-1.13.0")`) — used unchanged as `EvaluateRequest.model` and
  `EvaluateResponse.model` (was `String`). Added `ModelDetails` (`name`, `description`,
  `releaseDate`, plus a `model()` accessor back to a plain `Model`), returned by the new
  `TypesafeClient.listModels()` (`GET /v1/models`) — `HttpTransport` gained a matching
  `get(URI, headers)` method. Lets a caller discover and pin a model this client has no
  constant for, rather than being limited to the three previously hardcoded enum values.
- Added `EvaluateResponse.nouls()`/`.choices()`/`.scores()`, each `answers()` narrowed to that
  `Answer` subtype's entries — an alternative to `instanceof`/casting the mixed map yourself.
- Added a per-request timeout: `JdkHttpTransport`'s new `(HttpClient, Duration)` constructor
  applies it via `HttpRequest.Builder#timeout`, defaulting to `JdkHttpTransport.DEFAULT_TIMEOUT`
  (10s) — distinct from `HttpClient`'s own `connectTimeout`, which only bounds the TCP handshake.
- Broadened `evaluate`/`evaluateAsync`/`listModels` retries from just `429`/`529` to `408`/`429`/
  any `5xx`, honoring a `retry-after`/`retry-after-ms` response header when present before
  falling back to exponential backoff, and to also retry any `IOException` from the transport
  (a connection failure, or a request timing out) — previously not retried at all.
- Normalized `HttpTransport` to a single asynchronous method per verb: `post`/`get` now both
  return `CompletableFuture<HttpTransportResponse>` (no more separate `postAsync`, no more
  `throws IOException, InterruptedException` on the sync-looking methods). `TypesafeClient`'s
  synchronous `evaluate`/`listModels` block on the future internally; a single retry engine now
  backs both the sync and async paths instead of duplicating the loop. `close()` is abstract
  again (no default no-op) — every implementation must define one, even if it's empty.
