# Reference

API surface and module layout. Look here for "what exists and what it accepts."
For task-oriented usage see [how-to.md](how-to.md); for design rationale see [explanation.md](explanation.md).

- [Module layout](#module-layout)
- [Core types](#core-types)
- [JsonCodec SPI](#jsoncodec-spi)
- [Client](#client)

## Module layout

| Module | Depends on | Contains |
|---|---|---|
| `typesafe-core` | — | `Answer`, `Question`, `EvaluateRequest`, `EvaluateResponse`, `Usage`, `RequestId`, `JsonCodec` |
| `typesafe-jdk-http-client` | `core` | `TypesafeClient`, `ApiToken`, `TypesafeException` |
| `typesafe-jackson2` | `core` | `Jackson2Codec` (Jackson 2.x) |
| `typesafe-jackson3` | `core` | `Jackson3Codec` (Jackson 3.x) |
| `typesafe-bom` | — | dependency management for the four above |

## Core types

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

### `EvaluateRequest`

```java
record EvaluateRequest(Object state, String model, Map<String, Question> questions)
```

`EvaluateRequest.of(Object state, Map<String, Question> questions)` builds one with
`model = "jev-latest"`. `state` is any JSON-serializable value — a `String`, a `record`, a
`Map`, etc.

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
package ai.typesafe.json;

public interface JsonCodec {
    byte[] writeValueAsBytes(Object value);
    <T> T readValue(byte[] content, Class<T> type);
}
```

Implementations (`Jackson2Codec`, `Jackson3Codec`) are discovered via
`ServiceLoader.load(JsonCodec.class)` and registered through
`META-INF/services/ai.typesafe.json.JsonCodec`. Both own the `Answer`/`Question` polymorphic
`type` discriminator via Jackson mixins — `core`'s DTOs carry no serialization annotations.

## Client

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
| `httpClient(HttpClient)` | `HttpClient.newHttpClient()` |
| `jsonCodec(JsonCodec)` | resolved via `ServiceLoader` at `build()` time |
| `build()` | throws `IllegalStateException` if no `JsonCodec` is set or discoverable |

### `TypesafeException`

```java
class TypesafeException extends RuntimeException {
    int statusCode();
    String body();
}
```
