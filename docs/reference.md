# Reference

API surface and module layout. Look here for "what exists and what it accepts."
For task-oriented usage see [how-to.md](how-to.md); for design rationale see [explanation.md](explanation.md).

- [Module layout](#module-layout)
- [Core types](#core-types)
- [JsonCodec SPI](#jsoncodec-spi)
- [HttpTransport SPI](#httptransport-spi)
- [Testkit](#testkit)
- [Answer mapping](#answer-mapping)
- [Client](#client)

## Module layout

| Module | Depends on | Contains |
|---|---|---|
| `typesafe-java-core` | — | `Answer`, `Question`, `State`, `EvaluateRequest`, `EvaluateResponse`, `Usage`, `RequestId`, `Model`, `ModelDetails`, `JsonCodec`, `HttpTransport`, `TypeSafeClient`, `ApiKey`, `TypeSafeException` |
| `typesafe-java-client-jdk` | `core` | `JdkHttpTransport` (java.net.http) |
| `typesafe-java-client-okhttp` | `core` | `OkHttpTransport` (OkHttp) |
| `typesafe-java-jackson2` | `core` | `Jackson2Codec` (Jackson 2.x) |
| `typesafe-java-jackson3` | `core` | `Jackson3Codec` (Jackson 3.x) |
| `typesafe-java-testkit` | `core` | `RecordingTypeSafeClient`, `FailingTypeSafeClient` |
| `typesafe-java-mapping` | `core` | `MappingTypeSafeClient`, `@Noul`/`@Choice`/`@Score`/`@Option` |
| `typesafe-java-bom` | — | dependency management for the seven above |

`core` has zero runtime dependency on any HTTP or JSON library — `TypeSafeClient` talks to
`HttpTransport`/`JsonCodec`, not to `java.net.http`/Jackson directly, so it's safe to bundle
alongside the DTOs without pulling anything extra in.

## Core types

All of the following live in `io.github.dfa1.typesafe.core`.

### `Question` (sealed interface)

Static factories build one of three variants:

| Factory | Fields | Meaning |
|---|---|---|
| `Question.noul(instructions, Map<String,String> criteria)` | `instructions`, `criteria` | Score how strongly `instructions` holds, 0.0–1.0 |
| `Question.noul(instructions)` | `instructions`, `criteria = null` | Same, when `instructions` needs no elaboration |
| `Question.choice(instructions, Map<String,String> criteria)` | `instructions`, `criteria` | Pick the best-matching key in `criteria` |
| `Question.score(instructions, List<String> criteria)` | `instructions`, `criteria` | Rank against an ordered list of labels |

### `Answer` (sealed interface)

One variant per `Question` type, keyed by the same question name in `EvaluateResponse.answers()`:

| Variant | Fields |
|---|---|
| `Answer.Noul` | `noul(): double` |
| `Answer.Choice` | `choice(): String`, `probabilities(): Map<String,Double>`, `confidence(): double` |
| `Answer.Score` | `score(): double`, `legend(): Map<String,String>`, `probabilities(): Map<String,Double>`, `confidence(): double` |

### `State` (sealed interface)

Exactly the three shapes documented at
[docs.typesafe.ai/concepts/state](https://docs.typesafe.ai/concepts/state) — no `type`
discriminator on the wire; each variant serializes as its own raw JSON shape.

| Factory | Wire shape | Example |
|---|---|---|
| `State.text(String value)` | JSON string | `"My card was charged twice."` |
| `State.fields(Map<String, Object> fields)` | JSON object | `{"order_id": "A-104"}` |
| `State.messages(List<String> values)` | JSON array | `["Hi", "My card was charged twice."]` |

### `EvaluateRequest`

```java
record EvaluateRequest(State state, Model model, Map<String, Question> questions)
```

`EvaluateRequest.of(State state, Map<String, Question> questions)` builds one with
`model = Model.LATEST`. `EvaluateRequest.of(State state, Model model, Map<String, Question> questions)`
picks a specific model.

`EvaluateRequest.builder()` is the fluent alternative to `of(...)` plus hand-building the
`questions` map:

```java
EvaluateRequest request = EvaluateRequest.builder()
        .state("Help! My payouts have been failing for 3 days.")
        .noul("is_urgent", "Does this convey urgency?")
        .choice("category", "Which category?", Map.of("billing", "", "technical", ""))
        .score("severity", "Rate the severity", List.of("low", "medium", "high"))
        .build();
```

`state(String)` is sugar for `state(State.text(...))`; `state(State)` accepts any shape.
`model(Model)` defaults to `Model.LATEST` when omitted. Each question method (`noul`, `noul`
with a criteria map, `choice`, `score`) takes the question's name first, then the same
arguments as the matching `Question` factory. Reusing a name throws `IllegalArgumentException`
— each question key must be unique, since a second call with the same name would otherwise
silently overwrite the first in the underlying map.

### `Model`

```java
record Model(String name)
```

A model, by id — usable as an `EvaluateRequest`'s model, and what `EvaluateResponse.model()`
reports back — see [docs.typesafe.ai/models](https://docs.typesafe.ai/models):

| Constant | Wire value | Meaning |
|---|---|---|
| `Model.LATEST` | `jev-latest` | Most recent stable, official release. The default. |
| `Model.PREVIEW` | `jev-preview` | Most recent release, official or not. |

Any other id is pinned directly, e.g. `new Model("jev-1.13.0")` — including one taken from
`ModelDetails.model()` (see `TypeSafeClient.listModels()` below). Codecs serialize/deserialize a
`Model` as its bare `name` string — no `type` discriminator, no wrapping object.

### `EvaluateResponse`

```java
record EvaluateResponse(Model model, Map<String, Answer> answers, Usage usage, Metadata metadata)
record EvaluateResponse.Metadata(RequestId requestId, Duration upstreamServiceTime)
```

`metadata.requestId()` comes from the `x-typesafe-request-id` response header (`null` if absent).
`metadata.upstreamServiceTime()` comes from `x-envoy-upstream-service-time` (`null` if absent).

`nouls()`/`choices()`/`scores()` each return `answers()` narrowed to just that `Answer`
subtype's entries, recomputed on every call (not cached) — a convenience over `instanceof`
filtering the mixed map yourself.

### `Usage`

```java
record Usage(int inputTokens, int outputTokens)
```

### `RequestId`

```java
record RequestId(String value)
```

Codecs serialize/deserialize a `RequestId` as its bare `value` string — no `type` discriminator,
no wrapping object.

### `ModelDetails`

```java
record ModelDetails(String name, String description, String releaseDate)
```

One entry of `TypeSafeClient.listModels()`'s result. `model()` returns this model's id as a
plain `Model`, usable directly as an `EvaluateRequest`'s model.

## JsonCodec SPI

```java
package io.github.dfa1.typesafe.json;

public interface JsonCodec {
    String writeValueAsString(Object value);
    String writeValueAsPrettyString(Object value); // same, indented for human reading
    <T> T readValue(String content, Class<T> type);
}
```

Implementations (`Jackson2Codec`, `Jackson3Codec`) are discovered via
`ServiceLoader.load(JsonCodec.class)` and registered through
`META-INF/services/io.github.dfa1.typesafe.json.JsonCodec`. Both own the `Answer`/`Question` polymorphic
`type` discriminator via Jackson mixins — `core`'s DTOs carry no serialization annotations.
Content is `String`, not `byte[]`: this is always JSON text, which is UTF-8 by construction
(RFC 8259) — a caller integrating with a raw-`byte[]` system (e.g. Kafka) converts once at that
boundary (`.getBytes(UTF_8)` / `new String(bytes, UTF_8)`), same reasoning as `HttpTransport`.

## HttpTransport SPI

```java
package io.github.dfa1.typesafe.transport;

public interface HttpTransport extends AutoCloseable {
    CompletableFuture<HttpTransportResponse> post(URI uri, Map<String, String> headers, String body);
    CompletableFuture<HttpTransportResponse> get(URI uri, Map<String, String> headers);
    void close();   // no default -- every implementation must define one, even a no-op
}

public record HttpTransportResponse(int statusCode, Map<String, String> headers, String body) {
    Optional<String> header(String name);   // case-insensitive lookup
}
```

Every call is asynchronous — there's no separate synchronous/async pair of methods per verb.
`TypeSafeClient.evaluate`/`listModels` (synchronous) block on the returned future internally;
`evaluateAsync` returns it directly. An implementation whose underlying library is inherently
synchronous (e.g. Apache HttpClient's classic API) still returns a `CompletableFuture`, already
completed (or failed) by the time the call returns — see `JdkHttpTransport.get`/`post` below for
the async-native shape most HTTP libraries actually provide.

Bodies are `String`, not `byte[]`: `TypeSafeClient` only ever sends/receives JSON over this SPI,
and JSON text is UTF-8 by construction (RFC 8259), so there's no charset this layer needs to
guess at. `JsonCodec` stays `byte[]`-based (it's reused standalone, e.g. for a Kafka producer/
consumer, where messages are raw bytes); `TypeSafeClient` converts once at the boundary between
the two SPIs. `HttpTransportResponse` copies `headers` defensively (`Map.copyOf`) so a caller
that mutates the map it passed in afterward can't reach back into an already-returned response.

The HTTP calls `TypeSafeClient` needs (a JSON POST for `evaluate`, a GET for `listModels`),
abstracted away from any particular HTTP library. `JdkHttpTransport` (in
`typesafe-java-client-jdk`) and `OkHttpTransport` (in `typesafe-java-client-okhttp`) are each
discovered via `ServiceLoader.load(HttpTransport.class)` through
`META-INF/services/io.github.dfa1.typesafe.transport.HttpTransport`. Implement `HttpTransport`
yourself (e.g. backed by Apache HttpClient) and wire it in the same way, or pass any
implementation explicitly via `Builder.httpTransport(...)`. `JdkHttpTransport.close()` closes
its `HttpClient` (JDK 21+); `OkHttpTransport.close()` shuts down its `OkHttpClient`'s dispatcher
executor, evicts its connection pool, and closes its cache if one is configured.

`JdkHttpTransport(HttpClient http, Duration timeout)` applies `timeout` to every request via
`HttpRequest.Builder#timeout` (`null` disables it); the no-arg and `(HttpClient)` constructors
default it to `JdkHttpTransport.DEFAULT_TIMEOUT` (`10` seconds).

`OkHttpTransport(OkHttpClient http, Duration timeout)` applies `timeout` as `http`'s overall
call timeout (`OkHttpClient.Builder#callTimeout`), rebuilding a derived client with it set; a
`null` timeout uses `http` exactly as given, untouched. The no-arg and `(OkHttpClient)`
constructors default it to `OkHttpTransport.DEFAULT_TIMEOUT` (`10` seconds). Response header
names come back lowercase (OkHttp normalizes them internally) rather than preserving the wire
casing the way `JdkHttpTransport` does — use `HttpTransportResponse#header(String)`, a
case-insensitive lookup, rather than indexing `headers()` directly, and this is transport-agnostic.
Depends on `com.squareup.okhttp3:okhttp-jvm`, not the bare `okhttp` coordinate — OkHttp 5.x
publishes as Kotlin Multiplatform, and plain Maven (unlike Gradle) resolves the bare coordinate
to an empty metadata artifact with no classes.

## Testkit

`io.github.dfa1.typesafe.testkit` (module `typesafe-java-testkit`).

```java
public final class RecordingTypeSafeClient implements TypeSafeClient {
    public RecordingTypeSafeClient enqueueEvaluate(EvaluateResponse response);
    public RecordingTypeSafeClient enqueueModels(List<ModelDetails> models);
    public List<EvaluateRequest> evaluateRequests();
}
```

A `TypeSafeClient` test double at the `EvaluateRequest`/`EvaluateResponse` level — for unit-testing
code that calls `evaluate()`/`listModels()` and reacts to the result, without reaching for
Mockito — see [how-to.md](how-to.md#test-code-that-uses-typesafeclient-without-hitting-the-real-api)
for the recipe. `evaluateRequests()` records every `evaluate()`/`evaluateAsync()` call, in order.
`enqueueEvaluate(response)` queues a response to whichever `evaluate()`/`evaluateAsync()` call
comes next (`enqueueModels` likewise for `listModels()`); a call with nothing left queued throws
(or, for `evaluateAsync`, fails its future with) an `AssertionError`.

```java
public final class FailingTypeSafeClient implements TypeSafeClient {
    public FailingTypeSafeClient(TypeSafeClient delegate, int failEvery, Supplier<? extends RuntimeException> failure);
}
```

A `TypeSafeClient` decorator for unit-testing how calling code handles intermittent failures —
see [how-to.md](how-to.md#test-how-your-code-handles-intermittent-failures) for the recipe. Wraps
any `TypeSafeClient` (a `RecordingTypeSafeClient`, `DefaultTypeSafeClient`, or another decorator);
every `failEvery`-th call — `evaluate()`, `evaluateAsync()`, and `listModels()` share one counter
— throws `failure.get()` instead of reaching the delegate; every other call passes straight
through. The constructor throws `IllegalArgumentException` if `failEvery` isn't positive.

## Answer mapping

`io.github.dfa1.typesafe.mapping` (module `typesafe-java-mapping`).

```java
public final class MappingTypeSafeClient implements TypeSafeClient {
    public MappingTypeSafeClient(TypeSafeClient delegate);
    public <T extends Record> T evaluateTyped(State state, Class<T> type);
    public <T extends Record> T evaluateTyped(State state, Model model, Class<T> type);
    public <T extends Record> CompletableFuture<T> evaluateTypedAsync(State state, Class<T> type);
    public <T extends Record> CompletableFuture<T> evaluateTypedAsync(State state, Model model, Class<T> type);
}
```

A `TypeSafeClient` decorator that reflects over a **record**'s components to build the
`EvaluateRequest`'s questions and map the response back into a new instance of that same record
— see [how-to.md](how-to.md#get-typed-answers-instead-of-mapstring-answer) for the recipe. The
two-argument overloads default to `Model.LATEST`, matching `EvaluateRequest.of`. Every component
must carry exactly one of:

| Annotation | Component type | Populated with |
|---|---|---|
| `@Noul(String value)` | `double` | `Answer.Noul#noul()` |
| `@Choice(String value, Option[] options)` | `String` | `Answer.Choice#choice()` |
| `@Score(String value, String[] levels)` | `double` | `Answer.Score#score()` |

`@Option(String value, String description default "")` only appears nested inside
`@Choice#options()` — `Question.Choice#criteria()` is a `Map<String, String>` an annotation
can't hold directly.

`evaluateTyped`/`evaluateTypedAsync` throw `IllegalArgumentException` at call time (before any
network call), the first time a given record type is used, if: a component carries zero or more
than one of `@Noul`/`@Choice`/`@Score`; a component's type doesn't match its annotation (e.g. a
`@Noul String` instead of `double`); or `@Choice#options()` repeats the same `@Option` key. A
record type's reflection metadata (its per-component question/answer mapping and its canonical
constructor) is computed once and cached for the instance's lifetime, so this validation — and
the underlying `getRecordComponents()` walk — only happens once per record type, not on every
call. Once the response comes back, a missing answer for a component, or an answer whose shape
doesn't match its annotation, throws `IllegalStateException` naming the component (the latter
with the original `ClassCastException` as its cause) rather than a bare, unexplained exception.

`evaluate()`/`evaluateAsync()`/`listModels()`/`close()` delegate straight through, unchanged.

## Client

Also in `io.github.dfa1.typesafe.core`.

### `ApiKey`

```java
record ApiKey(String value)
```

- `ApiKey.fromFile(Path)` — reads and trims the file contents.
- `ApiKey.fromDefaultFile()` — reads `~/.typesafe.apitoken`.
- `ApiKey.fromEnv()` — reads the `TYPESAFE_API_KEY` environment variable; throws
  `IllegalStateException` if it's not set.
- `toHttpHeaderValue()` — `"Bearer " + value`.
- `toString()` never leaks `value`.
- Constructor throws `IllegalArgumentException` on a blank value.

### `TypeSafeClient`

```java
static DefaultTypeSafeClient.Builder builder(ApiKey apiKey)

EvaluateResponse evaluate(EvaluateRequest request)
CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request)
List<ModelDetails> listModels()
```

`TypeSafeClient` is an interface, not a final class, so it can be wrapped in a decorator (a
caching layer, metrics, a circuit breaker, ...) implementing the same interface — anywhere a
`TypeSafeClient` is expected, a decorator around one works too. `builder(apiKey)` is a thin
static factory on the interface that delegates to `DefaultTypeSafeClient.builder(apiKey)` — the
implementation class is public and owns its own `Builder`, since constructing a
`DefaultTypeSafeClient` (defaults, `ServiceLoader` discovery, ...) is squarely that class's
concern, not the interface's. Nothing about the call site changes: `TypeSafeClient.builder(key)`
still works exactly as before.

Default endpoint: `https://api.typesafe.ai/v1/systemone`; `listModels()` hits
`/v1/models` on the same scheme/authority. All three methods retry up to `maxRetries` times
(default `5`) on:

- status `408`, `429`, or any `5xx` — honoring a `retry-after`/`retry-after-ms` response header
  when present (the HTTP-date form of `Retry-After` isn't parsed; that case falls back to
  backoff), otherwise exponential backoff from `initialBackoff` (default `500ms`, doubled each
  attempt)
- any `IOException` from the transport (a connection failure, or `JdkHttpTransport`'s request
  timeout expiring — see below), backed off the same exponential schedule

None of the three methods declares a checked exception — every failure is an unchecked
`TypeSafeException` (see below), including a connection failure/timeout still failing after
`maxRetries`, and the calling thread being interrupted while waiting. See
[ADR 0002](../adr/0002-no-checked-exceptions.md) for why.

`TypeSafeClient` implements `AutoCloseable`; `close()` closes the configured `HttpTransport`,
so a client built from `JdkHttpTransport` releases its underlying `HttpClient`. Use
try-with-resources, or skip closing for a client that lives as long as the process.

#### `TypeSafeClient.Builder`

| Method | Default |
|---|---|
| `httpTransport(HttpTransport)` | resolved via `ServiceLoader` at `build()` time |
| `jsonCodec(JsonCodec)` | resolved via `ServiceLoader` at `build()` time |
| `endpoint(URI)` | `https://api.typesafe.ai/v1/systemone` |
| `maxRetries(int)` | `5` |
| `initialBackoff(Duration)` | `500ms` |
| `build()` | throws `IllegalStateException` if no `HttpTransport` or `JsonCodec` is set or discoverable |
| `<T extends TypeSafeClient> build(Function<TypeSafeClient, T> decorate)` | `decorate.apply(build())` — wraps the built client in a decorator (e.g. `MappingTypeSafeClient::new`) in one call, returning `T` instead of the plain `TypeSafeClient`. Stack more than one via `Function#andThen`. |

### `TypeSafeException`

```java
sealed class TypeSafeException extends RuntimeException {
    int statusCode();
    String body();
}
```

The base class is also the catch-all: constructed directly for a status with no dedicated
subclass below. A `200` response the configured `JsonCodec` couldn't decode throws
`TypeSafeException.ResponseDecoding` (`statusCode()` `200`, `getCause()` the codec's original
exception) instead of that exception escaping directly. This mirrors the per-status hierarchy of
the Python SDK (`typesafe-ai/typesafe-sdk-python`'s `TypeSafeAPIError` subclasses); unlike
Python, everything here — including connection/timeout/interruption failures — is a single
unchecked hierarchy (see [ADR 0002](../adr/0002-no-checked-exceptions.md)), where Python keeps a
separate checked-equivalent `TypeSafeAPIConnectionError`/`TypeSafeAPITimeoutError` family.

| Subclass | Status |
|---|---|
| `TypeSafeException.BadRequest` | `400` |
| `TypeSafeException.Authentication` | `401` |
| `TypeSafeException.PermissionDenied` | `403` |
| `TypeSafeException.NotFound` | `404` |
| `TypeSafeException.UnprocessableEntity` | `422` |
| `TypeSafeException.RateLimit` | `429` — `retryAfter()` returns the `retry-after`/`retry-after-ms` header as an `Optional<Duration>` |
| `TypeSafeException.InternalServer` | any `5xx` |
| `TypeSafeException.ResponseDecoding` | `200`, but decoding the body failed |
| `TypeSafeException.Connection` | no HTTP response — the transport couldn't reach TypeSafe, once retries are exhausted |
| `TypeSafeException.Timeout` | a `Connection` specifically caused by a timeout |
| `TypeSafeException.Interrupted` | the calling thread was interrupted while waiting for a response |

`Connection`, `Timeout`, and `Interrupted` never had an HTTP response to carry: `statusCode()`
returns the sentinel `-1` and `body()` returns `null` on all three. `getCause()` is the
transport's original `IOException` (`Connection`/`Timeout`) or the original `InterruptedException`
(`Interrupted`). Throwing `Interrupted` restores the thread's interrupt status first
(`Thread.currentThread().interrupt()`), so code further up the call stack still observes it.
`Timeout` is thrown instead of `Connection` when the underlying `IOException` was
`java.net.http.HttpTimeoutException` (from `JdkHttpTransport`) or `java.io.InterruptedIOException`
— the supertype of `java.net.SocketTimeoutException` — (from `OkHttpTransport`). See
[ADR 0002](../adr/0002-no-checked-exceptions.md).
