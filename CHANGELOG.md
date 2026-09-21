# Changelog

All notable changes to **typesafe-java** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- `DefaultTypeSafeClient.Builder` gained `build(Function<TypeSafeClient, T> decorate)`: builds
  the client and applies a decorator to it in one call (e.g.
  `builder(apiKey).build(MappingTypeSafeClient::decorate)`), returning `T` instead of the plain
  `TypeSafeClient` — no cast needed to reach a decorator's own methods. Stack more than one
  decorator via `Function#andThen`. The plain `build()` is unchanged.
- New `mapping` module: `MappingTypeSafeClient`, a `TypeSafeClient` decorator adding
  `evaluateTyped(State, [Model,] Class<T>)`/`evaluateTypedAsync(...)` for a caller-defined
  **record** whose components carry `@Noul`/`@Choice`/`@Score` (`@Noul`/`@Score` on a `double`
  component, `@Choice` on a `String` one, with its options as a nested `@Option[]`). Reflects
  over the record's components to build the request's questions and maps the response back into
  a new instance of that same record — no `Map<String, Answer>` and manual `(Answer.Noul)`-style
  cast at the call site. Validates each component (exactly one annotation, matching type, no
  duplicate `@Option` key) once per record type and caches the result; a missing or
  shape-mismatched answer in the response throws a clear `IllegalStateException` naming the
  component instead of a bare `NullPointerException`/`ClassCastException`. Depends only on
  `core`; its own tests depend on `testkit`'s `RecordingTypeSafeClient` (test scope only). Built
  via the static factory `MappingTypeSafeClient.decorate(TypeSafeClient)`, not a public
  constructor.
  Addresses the first half of [#2](https://github.com/dfa1/typesafe-java/issues/2) — the second
  half (`Question.noul` without an empty criteria map) already shipped earlier. `acceptance` now
  covers it too, against the live API.
- **Breaking:** `TypeSafeClient.evaluate`/`evaluateAsync`/`listModels` no longer declare any
  checked exception — `throws IOException, InterruptedException` is gone from `evaluate`/
  `listModels`. `TypeSafeException` is now sealed, with a subclass per HTTP status
  (`BadRequest` 400, `Authentication` 401, `PermissionDenied` 403, `NotFound` 404,
  `UnprocessableEntity` 422, `RateLimit` 429 with a `retryAfter()` accessor, `InternalServer`
  5xx) so callers can catch specific failures instead of switching on `statusCode()`. A `200`
  response the `JsonCodec` fails to decode now throws `TypeSafeException.ResponseDecoding`
  (wrapping the codec's exception as its cause) instead of that exception escaping directly. A
  connection/timeout failure still failing after `maxRetries` now throws
  `TypeSafeException.Connection`/`.Timeout` (a subclass of `Connection`) instead of the
  transport's raw `IOException`; the calling thread being interrupted while waiting now throws
  `TypeSafeException.Interrupted`, restoring the thread's interrupt status first. These three
  have no real HTTP response behind them, so `statusCode()`/`body()` are `-1`/`null` on all of
  them. `TypeSafeException` itself remains the catch-all base class for any other status. Every
  subclass constructor is public, so `testkit`'s `FailingTypeSafeClient` (or any test) can
  simulate a specific one (e.g. `() -> new TypeSafeException.RateLimit(...)`) for callers to
  catch. Mirrors the per-status hierarchy of the Python SDK
  (`typesafe-ai/typesafe-sdk-python`'s `TypeSafeAPIError` subclasses), folding what Python keeps
  as a separate checked-equivalent `TypeSafeAPIConnectionError`/`TypeSafeAPITimeoutError` family
  into this same unchecked hierarchy instead. See [ADR 0002](adr/0002-no-checked-exceptions.md).
  [#7](https://github.com/dfa1/typesafe-java/issues/7)
- New `client-okhttp` module: `OkHttpTransport`, an `HttpTransport` backed by OkHttp — an
  alternative to `client-jdk` for environments `java.net.http` doesn't cover, e.g. Android.
  Depends on `com.squareup.okhttp3:okhttp-jvm` (OkHttp 5.x publishes as Kotlin Multiplatform;
  the bare `okhttp` coordinate resolves to an empty jar under plain Maven). The `acceptance`
  module now runs its full test suite against OkHttp too (`OkHttpClientWithJackson2/3AcceptanceTest`).
- `testkit`: new `FailingTypeSafeClient`, a `TypeSafeClient` decorator that fails every Nth call
  (`evaluate()`/`evaluateAsync()`/`listModels()` share one counter) — for unit-testing how
  calling code handles intermittent failures. Wraps any `TypeSafeClient`, including a
  `RecordingTypeSafeClient`.

## [0.4.0] - 2026-09-21

- **Breaking:** `TypesafeClient`/`TypesafeException` renamed to `TypeSafeClient`/`TypeSafeException`
  (matching the product's actual capitalization, "TypeSafe"), along with every class named after
  them (`DefaultTypeSafeClient`, `RecordingTypeSafeClient`, ...):
  [`10f6f0f`](https://github.com/dfa1/typesafe-java/commit/10f6f0f). Package names and Maven
  artifact IDs are unaffected — they stay lowercase (`io.github.dfa1.typesafe`,
  `typesafe-java-*`), regular Java/Maven convention regardless of class capitalization.
- `TypeSafeClient` is now an interface, decoratable (caching, metrics, a circuit breaker, ...);
  new `testkit` module with `RecordingTypeSafeClient`, a `TypeSafeClient` test double:
  [`4d1c6f4`](https://github.com/dfa1/typesafe-java/commit/4d1c6f4).
- `RequestId` now serializes/deserializes as its bare `value` string (e.g. `"req_..."`) instead
  of a wrapping `{"value": "req_..."}` object, matching how `Model` is already handled.
- `cli`: stdout is now silent by default instead of always printing the full `EvaluateResponse`
  as JSON — use `--print <name>` for a specific answer or `--verbose` for the full response.
  `--min`-only invocations (a pass/fail gate) no longer print anything on stdout either way.
- `JsonCodec` gained `writeValueAsPrettyString(Object)`, implemented by both `jackson2` and
  `jackson3`; `cli`'s `--verbose` now uses it, so the request/response JSON it prints is
  indented instead of one long line.

## [0.3.0] - 2026-09-20

- `jdk-http-client` module renamed to `client-jdk` (artifact `typesafe-java-jdk-http-client` ->
  `typesafe-java-client-jdk`); package and class names are unchanged.
- `cli`'s runnable uber-jar is now published to Maven Central under the `all` classifier
  (`typesafe-java-cli-VERSION-all.jar`), GPG-signed like every other artifact; the plain
  `typesafe-java-cli` artifact stays a normal, non-executable jar so a mistaken plain
  dependency on it doesn't pull in unrelocated, bundled copies of its dependencies. The GitHub
  release notes link straight to the jar and its signature on Central rather than duplicating
  them as release assets.

## [0.2.0] - 2026-09-20

- `cli`'s uber-jar is now attached to each GitHub release, so it's downloadable without
  building from source. Still not published to Maven Central — it's an uber-jar, not a
  library dependency.

## [0.1.0] - 2026-09-20

Initial release.

- `TypeSafeClient`: synchronous `evaluate`, asynchronous `evaluateAsync`, and `listModels`.
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
