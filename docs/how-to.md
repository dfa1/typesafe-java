# How-to guides

Task-oriented recipes. Each section solves one concrete goal.
For API details, see [reference.md](reference.md). For design rationale, see [explanation.md](explanation.md).

---

## Provide your API token

Three ways, in increasing order of control:

```java
// 1. Default file (~/.typesafe.apitoken)
TypesafeClient client = TypesafeClient.withDefaultToken();

// 2. A specific file
ApiToken token = ApiToken.fromFile(Path.of("/secrets/typesafe.token"));
TypesafeClient client = TypesafeClient.builder(token).build();

// 3. An in-memory value (e.g. from an env var or secrets manager)
ApiToken token = new ApiToken(System.getenv("TYPESAFE_API_TOKEN"));
TypesafeClient client = TypesafeClient.builder(token).build();
```

## Choose a JSON codec

`TypesafeClient` doesn't depend on Jackson directly — it resolves a `JsonCodec` via
`ServiceLoader` from whatever codec module is on your classpath. Add exactly one of:

```xml
<dependency><groupId>io.github.dfa1.typesafe-java</groupId><artifactId>typesafe-java-jackson2</artifactId></dependency>
<!-- or -->
<dependency><groupId>io.github.dfa1.typesafe-java</groupId><artifactId>typesafe-java-jackson3</artifactId></dependency>
```

If neither is present, `TypesafeClient.Builder.build()` throws `IllegalStateException` with a
message telling you to add one. To bypass discovery and wire a codec explicitly (e.g. in tests,
or if you have your own `JsonCodec` implementation):

```java
TypesafeClient client = TypesafeClient.builder(token)
        .jsonCodec(new Jackson2Codec())
        .build();
```

## Choose an HTTP transport

Likewise, `TypesafeClient` doesn't depend on any HTTP library directly — it resolves an
`HttpTransport` via `ServiceLoader`. Add:

```xml
<dependency><groupId>io.github.dfa1.typesafe-java</groupId><artifactId>typesafe-java-jdk-http-client</artifactId></dependency>
```

If it's missing, `build()` throws `IllegalStateException`. To wire one explicitly, or to use
your own `HttpTransport` (e.g. backed by Apache HttpClient or OkHttp):

```java
TypesafeClient client = TypesafeClient.builder(token)
        .httpTransport(new JdkHttpTransport())
        .build();
```

## Build a `State`

`EvaluateRequest.of` takes a `State`, one of exactly three shapes
(see [docs.typesafe.ai/concepts/state](https://docs.typesafe.ai/concepts/state)):

```java
State.text("My card was charged twice.");
State.fields(Map.of("message", "My card was charged twice.", "order_id", "A-104"));
State.messages(List.of("Hi", "My customer number is TS1337.", "My card was charged twice."));
```

## Pick a specific model

`EvaluateRequest.of(state, questions)` defaults to `Model.LATEST`. Pin a specific one (e.g. to
test against a preview build, or a specific version) with the three-argument overload:

```java
EvaluateRequest request = EvaluateRequest.of(state, Model.PREVIEW, questions);
```

See [reference.md#model](reference.md#model) for the full list.

## Ask a Choice question

`Question.choice` picks the best-matching option out of a labeled set:

```java
EvaluateRequest request = EvaluateRequest.of(
        State.text("Give me all instruments on US market of type bond"),
        Map.of("market", Question.choice("Which market is the request about?",
                Map.of("US", "United States market", "EU", "European market"))));

EvaluateResponse response = client.evaluate(request);
Answer.Choice market = (Answer.Choice) response.answers().get("market");
market.choice();          // "US"
market.confidence();      // 0.0–1.0
market.probabilities();   // per-option probability map
```

## Ask a Score question

`Question.score` ranks a statement against an ordered list of labels (e.g. a Likert scale):

```java
Question.score("How frustrated is the customer?", List.of("Calm", "Frustrated", "Very angry"));
```

The `Answer.Score` you get back carries a numeric `score()`, a `legend()` mapping each ordinal to
its label, `probabilities()` per ordinal, and a `confidence()`.

## Call the API asynchronously

`evaluateAsync` returns a `CompletableFuture<EvaluateResponse>` and shares the same retry logic
as `evaluate`:

```java
client.evaluateAsync(request)
        .thenAccept(response -> System.out.println(response.answers()))
        .exceptionally(ex -> { ex.printStackTrace(); return null; });
```

## Handle API errors

A non-`200`, non-retryable response (or exhausted retries) throws `TypesafeException`:

```java
try {
    client.evaluate(request);
} catch (TypesafeException e) {
    System.err.println(e.statusCode() + ": " + e.body());
}
```

`429` and `529` are retried automatically with exponential backoff (5 attempts, starting at
500ms) before `TypesafeException` is thrown.

## Use a custom `HttpClient`

`JdkHttpTransport` takes an `HttpClient`, so any JDK `HttpClient` configuration goes through it:

```java
HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
TypesafeClient client = TypesafeClient.builder(token).httpTransport(new JdkHttpTransport(http)).build();
```

## Configure the endpoint or retry policy

```java
TypesafeClient client = TypesafeClient.builder(token)
        .endpoint(URI.create("https://staging.typesafe.ai/v1/systemone"))
        .maxRetries(2)
        .initialBackoff(Duration.ofMillis(100))
        .build();
```

## Test code that uses `TypesafeClient` without hitting the real API

`TypesafeClient` only ever talks to `HttpTransport`/`JsonCodec`, so a test can mock both and
verify the calls it makes:

```java
@Mock HttpTransport httpTransport;
@Mock JsonCodec jsonCodec;

TypesafeClient client = TypesafeClient.builder(token)
        .httpTransport(httpTransport)
        .jsonCodec(jsonCodec)
        .build();

given(jsonCodec.writeValueAsBytes(request)).willReturn(requestBytes);
given(httpTransport.post(any(), any(), eq(requestBytes)))
        .willReturn(new HttpTransportResponse(200, Map.of(), responseBytes));
given(jsonCodec.readValue(responseBytes, EvaluateResponse.class)).willReturn(decodedResponse);

client.evaluate(request);

then(httpTransport).should().post(any(), any(), eq(requestBytes));
```

See `TypesafeClientTest` in `core` for a complete example.

## Reuse the DTOs without pulling in an HTTP or JSON library

`typesafe-java-core` has zero runtime dependencies — `TypesafeClient` talks to `HttpTransport`/
`JsonCodec`, never to a concrete HTTP or JSON library directly. If you only need to
(de)serialize `EvaluateRequest`/`EvaluateResponse` payloads — for example to publish or consume
them on a Kafka topic — depend on `typesafe-java-core` plus a codec module, and ignore
`TypesafeClient` entirely:

```java
JsonCodec codec = new Jackson2Codec();
byte[] bytes = codec.writeValueAsBytes(request);
EvaluateResponse response = codec.readValue(bytes, EvaluateResponse.class);
```

## Run the acceptance tests against the live API

The acceptance tests in the `acceptance` module run every scenario once per HttpTransport/
JsonCodec combination and are excluded from a routine build. Opt in once you have
`~/.typesafe.apitoken` in place:

```bash
mvn test -pl acceptance -am -DexcludedGroups=
```
