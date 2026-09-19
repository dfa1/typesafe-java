# Reference

API surface and module layout. Look here for "what exists and what it accepts."
For task-oriented usage see [how-to.md](how-to.md); for design rationale see [explanation.md](explanation.md).

- [Module layout](#module-layout)
- [Core types](#core-types)
- [JsonCodec SPI](#jsoncodec-spi)
- [HttpTransport SPI](#httptransport-spi)
- [Client](#client)

## Module layout

| Module | Depends on | Contains |
|---|---|---|
| `typesafe-java-core` | — | `Answer`, `Question`, `State`, `EvaluateRequest`, `EvaluateResponse`, `Usage`, `RequestId`, `Model`, `JsonCodec`, `HttpTransport`, `TypesafeClient`, `ApiToken`, `TypesafeException` |
| `typesafe-java-jdk-http-client` | `core` | `JdkHttpTransport` (java.net.http) |
| `typesafe-java-jackson2` | `core` | `Jackson2Codec` (Jackson 2.x) |
| `typesafe-java-jackson3` | `core` | `Jackson3Codec` (Jackson 3.x) |
| `typesafe-java-bom` | — | dependency management for the four above |

`core` has zero runtime dependency on any HTTP or JSON library — `TypesafeClient` talks to
`HttpTransport`/`JsonCodec`, not to `java.net.http`/Jackson directly, so it's safe to bundle
alongside the DTOs without pulling anything extra in.

## Core types

All of the following live in `io.github.dfa1.typesafe.core`.

### `Question` (sealed interface)

Static factories build one of three variants:

| Factory | Fields | Meaning |
|---|---|---|
| `Question.noul(instructions, Map<String,String> criteria)` | `instructions`, `criteria` | Score how strongly `instructions` holds, 0.0–1.0 |
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

### `Model`

Known values for `EvaluateRequest.model()` — see [docs.typesafe.ai/models](https://docs.typesafe.ai/models):

| Constant | Wire value | Meaning |
|---|---|---|
| `Model.LATEST` | `jev-latest` | Most recent stable, official release. The default. |
| `Model.PREVIEW` | `jev-preview` | Most recent release, official or not. |
| `Model.JEV_1_13_0` | `jev-1.13.0` | TypeSafe's flagship System One model. |

`model.id()` returns the wire value; codecs serialize a `Model` by calling its `toString()`
(which returns `id()`), so the wire value is what's written, not the enum constant name.
`EvaluateResponse.model()` stays a plain `String`, since a response can report a versioned id
this enum doesn't (yet) have a constant for.

### `EvaluateResponse`

```java
record EvaluateResponse(String model, Map<String, Answer> answers, Usage usage, Metadata metadata)
record EvaluateResponse.Metadata(RequestId requestId, Duration upstreamServiceTime)
```

`metadata.requestId()` comes from the `x-typesafe-request-id` response header (`null` if absent).
`metadata.upstreamServiceTime()` comes from `x-envoy-upstream-service-time` (`null` if absent).

### `Usage`

```java
record Usage(int inputTokens, int outputTokens)
```

### `RequestId`

```java
record RequestId(String value)
```

## JsonCodec SPI

```java
package io.github.dfa1.typesafe.json;

public interface JsonCodec {
    byte[] writeValueAsBytes(Object value);
    <T> T readValue(byte[] content, Class<T> type);
}
```

Implementations (`Jackson2Codec`, `Jackson3Codec`) are discovered via
`ServiceLoader.load(JsonCodec.class)` and registered through
`META-INF/services/io.github.dfa1.typesafe.json.JsonCodec`. Both own the `Answer`/`Question` polymorphic
`type` discriminator via Jackson mixins — `core`'s DTOs carry no serialization annotations.

## HttpTransport SPI

```java
package io.github.dfa1.typesafe.transport;

public interface HttpTransport {
    HttpTransportResponse post(URI uri, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException;
    CompletableFuture<HttpTransportResponse> postAsync(URI uri, Map<String, String> headers, byte[] body);
}

public record HttpTransportResponse(int statusCode, Map<String, String> headers, byte[] body) {
    Optional<String> header(String name);   // case-insensitive lookup
}
```

The single HTTP call `TypesafeClient` needs (a JSON POST), abstracted away from any particular
HTTP library. `JdkHttpTransport` (in `typesafe-java-jdk-http-client`) is discovered via
`ServiceLoader.load(HttpTransport.class)` through
`META-INF/services/io.github.dfa1.typesafe.transport.HttpTransport`. Implement `HttpTransport`
yourself (e.g. backed by Apache HttpClient, OkHttp, ...) and wire it in the same way, or pass it
explicitly via `Builder.httpTransport(...)`.

## Client

Also in `io.github.dfa1.typesafe.core`.

### `ApiToken`

```java
record ApiToken(String value)
```

- `ApiToken.fromFile(Path)` — reads and trims the file contents.
- `ApiToken.fromDefaultFile()` — reads `~/.typesafe.apitoken`.
- `toHttpHeaderValue()` — `"Bearer " + value`.
- `toString()` never leaks `value`.
- Constructor throws `IllegalArgumentException` on a blank value.

### `TypesafeClient`

```java
static TypesafeClient.Builder builder(ApiToken apiToken)
static TypesafeClient withDefaultToken() throws IOException   // builder(ApiToken.fromDefaultFile()).build()

EvaluateResponse evaluate(EvaluateRequest request) throws IOException, InterruptedException
CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request)
```

Endpoint: `https://api.typesafe.ai/v1/systemone`. Retries `429`/`529` up to 5 times with
exponential backoff starting at 500ms; any other non-`200` status (or a retry-exhausted `429`/
`529`) throws `TypesafeException`.

#### `TypesafeClient.Builder`

| Method | Default |
|---|---|
| `httpTransport(HttpTransport)` | resolved via `ServiceLoader` at `build()` time |
| `jsonCodec(JsonCodec)` | resolved via `ServiceLoader` at `build()` time |
| `build()` | throws `IllegalStateException` if no `HttpTransport` or `JsonCodec` is set or discoverable |

### `TypesafeException`

```java
class TypesafeException extends RuntimeException {
    int statusCode();
    String body();
}
```
