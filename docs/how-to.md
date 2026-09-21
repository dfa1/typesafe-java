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
<dependency><groupId>io.github.dfa1.typesafe-java</groupId><artifactId>typesafe-java-client-jdk</artifactId></dependency>
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

`EvaluateRequest.of(state, questions)` defaults to `Model.LATEST`. Pin a specific one with the
three-argument overload — either the other constant:

```java
EvaluateRequest request = EvaluateRequest.of(state, Model.PREVIEW, questions);
```

or any other id directly, e.g. one taken from `client.listModels()`:

```java
EvaluateRequest request = EvaluateRequest.of(state, new Model("jev-1.13.0"), questions);
```

## List the available models

```java
List<ModelDetails> models = client.listModels();
models.forEach(m -> System.out.println(m.name() + ": " + m.description() + " (" + m.releaseDate() + ")"));

// pin a request to one of them:
EvaluateRequest request = EvaluateRequest.of(state, models.get(0).model(), questions);
```

See [reference.md#modeldetails](reference.md#modeldetails) for the full shape.

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

`response.choices().get("market")` does the same cast for you — see the next section.

## Read only the Noul/Choice/Score answers you asked for

`answers()` mixes every answer type in one map, keyed by question name. If a request only asks
`Choice` questions (or you only care about the `Choice` ones back), skip the `instanceof`/cast:

```java
Map<String, Answer.Choice> choices = response.choices();   // also: .nouls(), .scores()
choices.get("market").choice();
```

Each is `answers()` narrowed to that subtype, recomputed on every call.

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

`408`, `429`, and any `5xx` are retried automatically with exponential backoff (5 attempts,
starting at 500ms) before `TypesafeException` is thrown.

## Use a custom `HttpClient`

`JdkHttpTransport` takes an `HttpClient`, so any JDK `HttpClient` configuration goes through it
— e.g. `connectTimeout`, which only bounds the TCP handshake, not the wait for a response:

```java
HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
TypesafeClient client = TypesafeClient.builder(token).httpTransport(new JdkHttpTransport(http)).build();
```

## Configure the per-request timeout

`JdkHttpTransport` applies a timeout to every request (`10` seconds by default,
`JdkHttpTransport.DEFAULT_TIMEOUT`) — this is the actual "give up waiting for a response" bound,
distinct from `HttpClient`'s `connectTimeout` above. Override it with the `(HttpClient, Duration)`
constructor, or pass `null` to disable it entirely:

```java
TypesafeClient client = TypesafeClient.builder(token)
        .httpTransport(new JdkHttpTransport(HttpClient.newHttpClient(), Duration.ofSeconds(30)))
        .build();
```

A timed-out request surfaces as an `HttpTimeoutException` (a subtype of `IOException`) from
`evaluate`/`evaluateAsync`/`listModels` — retried automatically the same as a connection failure,
up to `maxRetries` (see below).

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

`evaluate`/`evaluateAsync`/`listModels` all retry (up to `maxRetries`, default `5`) on:

- `408`, `429`, or any `5xx` status — honoring a `retry-after`/`retry-after-ms` response header
  when present, falling back to exponential backoff from `initialBackoff` (default `500ms`,
  doubled each attempt) otherwise
- a connection failure or a request timeout (any `IOException` from the transport)

Any other non-`200` status, or a retryable failure that's still failing after `maxRetries`,
throws `TypesafeException` (or the `IOException`/`HttpTimeoutException` itself, for a
connection/timeout failure).

## Test code that uses `TypesafeClient` without hitting the real API

Don't mock `TypesafeClient` (or the `HttpTransport`/`JsonCodec` SPIs it talks to) directly in
your own unit tests — they're types this library owns, not yours, so a test built on top of
them breaks whenever this library's internals change, for reasons that have nothing to do with
your code (see [Mockito's "don't mock types you don't own"](https://github.com/mockito/mockito/wiki/How-to-write-good-tests#dont-mock-a-type-you-dont-own)).
Wrap `TypesafeClient` behind a narrow interface of your own that fits your domain, and mock
*that* in unit tests of the code that calls it.

To verify your wrapper itself calls `TypesafeClient` correctly — an integration-style test
of the wiring, not a unit test of your business logic — add `typesafe-java-testkit`
(test scope) and swap in `RecordingHttpTransport`, a `HttpTransport` test double that runs the
real `TypesafeClient` (real retry/header/decode logic, real `JsonCodec`) against canned HTTP
responses instead of the network:

```java
RecordingHttpTransport transport = new RecordingHttpTransport()
        .respond(new HttpTransportResponse(200, Map.of(), responseBody));

TypesafeClient client = TypesafeClient.builder(token)
        .httpTransport(transport)
        .build();

client.evaluate(request);

assertThat(transport.requests()).hasSize(1);
```

Every call is recorded in `transport.requests()` in order, so count/order assertions are plain
`AssertJ` list assertions — no `InOrder`/`times(n)` verification needed. Stub a response to a
specific request with `respondTo(Predicate<RecordedRequest>, HttpTransportResponse)` instead of
`respond(...)` (which matches any request); each stub is consumed by the first request that
matches it, so registering the same predicate twice — e.g. once for a `500` and once for a
`200` — simulates a retry. A request with no matching stub fails with an `AssertionError` naming
the unmatched request. See `RecordingHttpTransportTest` in `testkit` for more examples.

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
ad hoc checks against the real API, without writing any Java — published under the `all`
classifier (`typesafe-java-cli-VERSION-all.jar`; the plain artifact is just this module's own
classes, not runnable). Download it from
[Maven Central](https://central.sonatype.com/artifact/io.github.dfa1.typesafe-java/typesafe-java-cli)
(the [latest release](https://github.com/dfa1/typesafe-java/releases/latest) notes link straight
to the jar), or build it: `./mvnw -pl cli -am package -DskipTests` (jar lands in `cli/target/`).

```bash
java -jar typesafe-java-cli-*-all.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --choice "category=What kind of issue is this?|billing,shipping,other" \
        --score "severity=How severe is this?|low,medium,high" \
        --verbose
```

Each `--noul`/`--choice`/`--score` is `[<name>=]<instructions>`, with `--choice`/`--score`
taking a `|`-separated, comma-list of options/levels after the instructions. The `name=` prefix
is optional — for a single question, `--noul "Is this urgent?"` is enough (the answer comes
back keyed `noul`); name it explicitly if you're asking more than one question of the same
type, since unnamed ones of the same type overwrite each other. `--model <id>` (e.g.
`jev-preview`) overrides the default `jev-latest`; `jev-latest`/`jev-preview` resolve to their
alias, any other id is pinned directly. Reads the token from `~/.typesafe.apitoken`.

Stdout is silent by default — reach for `--print`/`--verbose` below to see anything. `--verbose`
prints the full `EvaluateResponse` as pretty-printed JSON to stdout, plus the outgoing request
(also pretty-printed) and the response's request id to stderr; `--timing` prints how long the
API took, to stderr. Run with `--version` alone to print the jar's version and exit without
calling the API.

For scripting/CI, repeatable `--min <name>=<threshold>` gates on a `noul`/`score` answer's
value, exiting `1` if any named answer comes back below its threshold (with a
`--min failed: ...` line on stderr per failure) — no need for `--print`/`--verbose` if all you
want is the exit code:

```bash
java -jar cli/target/typesafe-java-cli-*-all.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --min "urgent=0.5" || echo "not urgent enough"
```

Repeatable `--print <name>` prints just that answer's value (one per line) instead of the full
response — `choice` for `Choice`, the numeric value for `Noul`/`Score`:

```bash
java -jar cli/target/typesafe-java-cli-*-all.jar \
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
