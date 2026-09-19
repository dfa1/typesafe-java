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
<dependency><groupId>ai.typesafe</groupId><artifactId>typesafe-jackson2</artifactId></dependency>
<!-- or -->
<dependency><groupId>ai.typesafe</groupId><artifactId>typesafe-jackson3</artifactId></dependency>
```

If neither is present, `TypesafeClient.Builder.build()` throws `IllegalStateException` with a
message telling you to add one. To bypass discovery and wire a codec explicitly (e.g. in tests,
or if you have your own `JsonCodec` implementation):

```java
TypesafeClient client = TypesafeClient.builder(token)
        .jsonCodec(new Jackson2Codec())
        .build();
```

## Ask a Choice question

`Question.choice` picks the best-matching option out of a labeled set:

```java
EvaluateRequest request = EvaluateRequest.of(
        "Give me all instruments on US market of type bond",
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

```java
HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
TypesafeClient client = TypesafeClient.builder(token).httpClient(http).build();
```

## Reuse the DTOs outside the HTTP client

`typesafe-core` has no dependency on `typesafe-jdk-http-client` or on any HTTP library. If you only need
to (de)serialize `EvaluateRequest`/`EvaluateResponse` payloads — for example to publish or consume
them on a Kafka topic — depend on `typesafe-core` plus a codec module directly:

```java
JsonCodec codec = new Jackson2Codec();
byte[] bytes = codec.writeValueAsBytes(request);
EvaluateResponse response = codec.readValue(bytes, EvaluateResponse.class);
```

## Run the acceptance tests against the live API

The demo/acceptance tests in `jackson2` are excluded from a routine build. Opt in once you have
`~/.typesafe.apitoken` in place:

```bash
mvn test -pl jackson2 -am -DexcludedGroups=
```
