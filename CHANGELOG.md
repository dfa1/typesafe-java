# Changelog

All notable changes to **typesafe-java** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- **`local`: shared request validation, `evaluateAsync` off virtual threads** — every engine rejects no questions or empty criteria with `BadRequest` (Laya used to NPE or answer a null choice), and `evaluateAsync` runs on one platform thread so native inference can't pin the JVM's virtual-thread carriers.
- **`local`: `LocalClefTypeSafeClient.loadOnGpu(...)`** — Clef-flash on ONNX Runtime's WebGPU backend (macOS on Apple Silicon), same answers as the CPU; Laya gets no GPU option, since its WebGPU answers drift between launches. (#19)
- **docs: run Clef-flash on a Mac with MLX** — a how-to pointing the regular client at mlx-community's local Clef-flash server: about 0.55 s per request on an M5, much closer to Jev than Laya. (#14)
- **`local`: `LocalClefTypeSafeClient`** — Cloudflare's Clef-flash (Qwen3.5-9B + joint schema head) on Ollaya's ONNX graph over the upstream bf16 weights; one forward pass per request; `scripts/clef/quantize_q4.py` converts it to 4-bit weights (7.7 GB, 2–4 s per request on an M5 GPU). (#14)
- **`local`: `LocalLayaTypeSafeClient`, `LocalQwenTypeSafeClient`** — new module, one client class per model, evaluating in-process on ONNX Runtime from a local model directory (Laya fp32 from onnx-community, or Qwen2.5 as a prompted-LLM baseline), with a `Local engines` workflow publishing agreement-with-Jev and throughput tables. (#14)
- **build: checkstyle engine 14.3.0** — the plugin's default 9.3 can't parse Java 21 pattern matching for switch; same rules. (#14)
- **Breaking: optional API key, `ApiKey` without a public constructor** — `TypeSafeClient.builder()` takes no argument and `.apiKey(key)` is optional (no key, no `Authorization` header, for local servers); `ApiKey` is a final class built with `of(String)`/`fromFile`/`fromDefaultFile`/`fromEnv`, and no longer exposes its value. (#18)
- **`jackson2`: ignore unknown fields, like `jackson3`** — a field the API adds to a response no longer fails decoding with `ResponseDecoding`; Jackson 2's default rejected it. (#18)
- **`core`: `Content.fields(...)` and `EvaluateRequest.Builder` keep their order** — both copied with `Map.copyOf`, which reshuffles keys per JVM run, so the same request serialized with fields/questions in a different order each run; they now keep the caller's iteration order.
- **`core`: `TokenCounter`** — running totals of input/output tokens from `EvaluateResponse#usage()`, added via `.decorateWith(tokens::decorate)`; thread-safe, shareable across clients.
- **`core`: `Builder.decorateWith(...)` naming and docs** — named to read as additive and to pair with the `decorate(...)` factories; `Builder` javadoc and the how-to explain decorator ordering.
- **`core`: `build()` rejects a second `RetryingTypeSafeClient`** — throws `IllegalStateException`, since stacked retries multiply attempts; `decorate(...)` now takes the delegate first on every decorator.
- **Breaking: retries are opt-in** — `build()` makes one attempt per call; `Builder.maxRetries`/`initialBackoff` are gone (use `.decorateWith(RetryingTypeSafeClient::decorate)`, 5 retries from 500ms), and `Builder.decorator(...)` is renamed `decorateWith(...)`.
- **`core`: `Builder.decorator(...)`** (renamed `decorateWith(...)` below) — stacks decorators from the builder, last added outermost; `build(Function)` stays for a type-preserving outermost decorator.
- **`core`: `RetryingTypeSafeClient.decorate(...)`/`DeadlineTypeSafeClient.decorate(...)`** — static factories taking the client to wrap first, matching `MappingTypeSafeClient::decorate`; constructors are package-private.
- **`core`: `DeadlineTypeSafeClient` caps a call's total time, retries included** — fails with `TypeSafeException.Timeout` past the deadline; `RetryingTypeSafeClient` stops retrying once its future is done.
- **`core`: retry/backoff extracted into a `RetryingTypeSafeClient` decorator** — `DefaultTypeSafeClient` no longer retries itself, and `InternalServer` gains `retryAfter()`.

## [0.6.0] - 2026-09-30

- **`bom`: flatten the published POM** — drops the `typesafe-java` parent reference and writes managed versions out literally, so importing the BOM doesn't also pull in the parent build. [`37b1b78`](https://github.com/dfa1/typesafe-java/commit/37b1b78)
- **`mapping`: fast-path `cache.get()` before `computeIfAbsent` in `mappingFor`** — avoids the lambda allocation and per-bin lock on the common cache-hit path. [`4bdac6a`](https://github.com/dfa1/typesafe-java/commit/4bdac6a)
- **`cli`: add `--help`/`-h`** — prints usage and exits without calling the API, same as `--version`. [`898d2ee`](https://github.com/dfa1/typesafe-java/commit/898d2ee)
- **Breaking: `State` renamed to `Content`**, and `Question.Noul`/`Choice`/`Score`'s `instructions`
  is now typed `Content` instead of `String` — `docs.typesafe.ai/api` documents `instructions` as
  the same string/object/array shapes as `state`, so one type now backs both fields; `State`
  read backwards once reused for a question's `instructions`. `Question.noul`/`choice`/`score`
  keep their `String`-taking overloads (sugar for `Content.text(...)`) and gain `Content`-taking
  ones for structured instructions, so existing plain-text call sites are unaffected — only code
  reading `Question.*#instructions()` as a `String`, or referencing the `State` type by name,
  needs updating. [`9b98b79`](https://github.com/dfa1/typesafe-java/commit/9b98b79)

## [0.5.0] - 2026-09-22

- `DefaultTypeSafeClient.Builder` gained `build(Function<TypeSafeClient, T> decorate)`: builds
  the client and applies a decorator to it in one call (e.g.
  `builder(apiKey).build(MappingTypeSafeClient::decorate)`), returning `T` instead of the plain
  `TypeSafeClient` — no cast needed to reach a decorator's own methods. Stack more than one
  decorator via `Function#andThen`. The plain `build()` is unchanged.
- New `mapping` module: `MappingTypeSafeClient`, a `TypeSafeClient` decorator adding
  `evaluateTyped(State, [Model,] Class<T>)`/`evaluateTypedAsync(...)` for a caller-defined
  **record** whose components carry `@Noul`/`@Choice`/`@Score` (`@Noul`/`@Score` on a `double`
  component, `@Choice` on a `String` one, with its options as a nested `@Option[]`). A component
  may instead be typed as the full `Answer.Noul`/`Answer.Choice`/`Answer.Score`, to get
  `probabilities()`/`confidence()`/`legend()` too — the scalar form only gives you the headline
  value. Reflects over the record's components to build the request's questions and maps the
  response back into a new instance of that same record — no `Map<String, Answer>` and manual
  `(Answer.Noul)`-style cast at the call site. Validates each component (exactly one annotation,
  matching type, no duplicate `@Option` key) once per record type and caches the result; a missing or
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
- `./mvnw verify` (and so CI) now fails on a broken `{@link}`/`{@see}` javadoc reference, via a
  `maven-javadoc-plugin` execution bound to every module's `verify` phase with
  `doclint=reference`. Narrower than the release profile's `attach-javadocs` execution (which
  keeps `doclint=none` to avoid failing a release over doc-completeness gaps) — this one only
  catches a dangling reference, the kind that renders unresolved in an IDE.
- **Breaking:** `ApiKey.fromDefaultFile()`'s default path is now `~/.typesafe.apikey`, not
  `~/.typesafe.apitoken` — matching `ApiKey` itself and the `TYPESAFE_API_KEY` env var, both of
  which already said "key," not "token." Checked against `typesafe-sdk-python`: it has no
  file-based credential source at all (constructor argument or `TYPESAFE_API_KEY` only), so
  there was no cross-SDK filename to preserve — this is a Java-only convenience, renamed purely
  for internal consistency. Existing `~/.typesafe.apitoken` files need to be renamed (or
  re-created) at the new path.

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
