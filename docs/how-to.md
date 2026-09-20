# How-to guides

Task-oriented recipes. Each section solves one concrete goal.
For API details, see [reference.md](reference.md). For design rationale, see [explanation.md](explanation.md).

---

## Provide your API token

`ApiKey` has a source for each case; `TypesafeClient.builder` takes whichever you build:

```java
// 1. Default file (~/.typesafe.apitoken)
ApiKey token = ApiKey.fromDefaultFile();
TypesafeClient client = TypesafeClient.builder(token).build();

// 2. A specific file
ApiKey token = ApiKey.fromFile(Path.of("/secrets/typesafe.token"));
TypesafeClient client = TypesafeClient.builder(token).build();

// 3. TYPESAFE_API_KEY environment variable
ApiKey token = ApiKey.fromEnv();
TypesafeClient client = TypesafeClient.builder(token).build();

// 4. Any other in-memory value (e.g. a secrets manager)
ApiKey token = new ApiKey(secretsManager.getSecret("typesafe-token"));
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

`EvaluateRequest.of(state, questions)` defaults to `RequestModel.Alias.LATEST`. Pin a specific
one with the three-argument overload — either the other symbolic alias:

```java
EvaluateRequest request = EvaluateRequest.of(state, RequestModel.Alias.PREVIEW, questions);
```

or a concrete `Model` by id, e.g. one returned by `client.listModels()`:

```java
EvaluateRequest request = EvaluateRequest.of(state, new Model("jev-1.13.0", null, null), questions);
```

`description`/`releaseDate` are only used for display — the request only ever sends the id.

## List the available models

```java
List<Model> models = client.listModels();
models.forEach(m -> System.out.println(m.name() + ": " + m.description() + " (" + m.releaseDate() + ")"));
```

See [reference.md#model](reference.md#model) for the full shape.

## Build a request with the fluent builder

`EvaluateRequest.builder()` avoids hand-building the `questions` map for `of(...)`:

```java
EvaluateRequest request = EvaluateRequest.builder()
        .state("Help! My payouts have been failing for 3 days.")
        .noul("is_urgent", "Does this convey urgency?")
        .choice("category", "Which category?", Map.of("billing", "", "technical", ""))
        .score("severity", "Rate the severity", List.of("low", "medium", "high"))
        .build();
```

`state(String)` is sugar for `state(State.text(...))`; pass a `State` directly for the
`fields`/`messages` shapes. Reusing a question name throws `IllegalArgumentException` instead
of silently dropping the earlier question.

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

## Close the client when you're done with it

`TypesafeClient` implements `AutoCloseable` and closes its `HttpTransport` — for
`JdkHttpTransport`, that releases the underlying `HttpClient`:

```java
try (TypesafeClient client = TypesafeClient.builder(token).build()) {
    client.evaluate(request);
}
```

A client that lives for the whole process (e.g. a singleton in a long-running service) doesn't
need closing.

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

given(jsonCodec.writeValueAsString(request)).willReturn(requestBody);
given(httpTransport.post(any(), any(), eq(requestBody)))
        .willReturn(new HttpTransportResponse(200, Map.of(), responseBody));
given(jsonCodec.readValue(responseBody, EvaluateResponse.class)).willReturn(decodedResponse);

client.evaluate(request);

then(httpTransport).should().post(any(), any(), eq(requestBody));
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
String json = codec.writeValueAsString(request);
EvaluateResponse response = codec.readValue(json, EvaluateResponse.class);
```

(A Kafka producer/consumer using a raw-`byte[]` serializer converts once at that boundary —
`json.getBytes(UTF_8)` / `new String(bytes, UTF_8)` — the same one-line conversion any
non-`String`-based transport needs; `TypesafeClient` itself needs none, since `HttpTransport`
is `String`-based too.)

## Run a quick check from the command line

The `cli` module builds an executable uber-jar (JDK `HttpTransport` + Jackson 3 codec) for
ad hoc checks against the real API, without writing any Java:

```bash
./mvnw -pl cli -am package -DskipTests
java -jar cli/target/typesafe-java-cli-*.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --choice "category=What kind of issue is this?|billing,shipping,other" \
        --score "severity=How severe is this?|low,medium,high"
```

Each `--noul`/`--choice`/`--score` is `[<name>=]<instructions>`, with `--choice`/`--score`
taking a `|`-separated, comma-list of options/levels after the instructions. The `name=` prefix
is optional — for a single question, `--noul "Is this urgent?"` is enough (the answer comes
back keyed `noul`); name it explicitly if you're asking more than one question of the same
type, since unnamed ones of the same type overwrite each other. `--model <id>` (e.g.
`jev-preview`) overrides the default `jev-latest`; `jev-latest`/`jev-preview` resolve to their
alias, any other id is pinned directly. Reads the token from
`~/.typesafe.apitoken` and prints the `EvaluateResponse` as JSON. Not published — build and run
it locally.

Add `--verbose` to print the outgoing request JSON, the full response JSON, and the response's
request id, `--timing` to print how long the API took (all go to stderr, so stdout stays clean
— useful alongside `--print`, which otherwise only shows the one value you asked for). Run with
`--version` alone to print the jar's version and exit without calling the API.

For scripting/CI, repeatable `--min <name>=<threshold>` gates on a `noul`/`score` answer's
value: the JSON is still printed either way, but the process exits `1` if any named answer
comes back below its threshold (with a `--min failed: ...` line on stderr per failure).

```bash
java -jar cli/target/typesafe-java-cli-*.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --min "urgent=0.5" || echo "not urgent enough"
```

Repeatable `--print <name>` prints just that answer's value (one per line) instead of the full
response — `choice` for `Choice`, the numeric value for `Noul`/`Score`:

```bash
java -jar cli/target/typesafe-java-cli-*.jar \
        --state "My card was charged twice." \
        --choice "category=What kind of issue is this?|billing,shipping,other" \
        --print category
# billing
```

## Run the acceptance tests against the live API

The acceptance tests in the `acceptance` module run every scenario once per HttpTransport/
JsonCodec combination and are excluded from a routine build. Opt in once you have
`~/.typesafe.apitoken` in place:

```bash
./mvnw test -pl acceptance -am -DexcludedGroups=
```
