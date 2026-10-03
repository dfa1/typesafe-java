# How-to guides

Task-oriented recipes. Each section solves one concrete goal.
For API details, see [reference.md](reference.md). For design rationale, see [explanation.md](explanation.md).

---

## Provide your API token

`ApiKey` has a source for each case; `Builder.apiKey(...)` takes whichever you build. Leave it out only
for a local TypeSafe-compatible server (`Builder.endpoint(...)`): without a key no `Authorization` header is sent.

```java
// 1. Default file (~/.typesafe.apikey)
ApiKey token = ApiKey.fromDefaultFile();
TypeSafeClient client = TypeSafeClient.builder().apiKey(token).build();

// 2. A specific file
ApiKey token = ApiKey.fromFile(Path.of("/secrets/typesafe.token"));
TypeSafeClient client = TypeSafeClient.builder().apiKey(token).build();

// 3. TYPESAFE_API_KEY environment variable
ApiKey token = ApiKey.fromEnv();
TypeSafeClient client = TypeSafeClient.builder().apiKey(token).build();

// 4. Any other in-memory value (e.g. a secrets manager)
ApiKey token = ApiKey.of(secretsManager.getSecret("typesafe-token"));
TypeSafeClient client = TypeSafeClient.builder().apiKey(token).build();
```

## Choose a JSON codec

`TypeSafeClient` doesn't depend on Jackson directly — it resolves a `JsonCodec` via
`ServiceLoader` from whatever codec module is on your classpath. Add exactly one of:

```xml
<dependency>
  <groupId>io.github.dfa1.typesafe-java</groupId>
  <artifactId>typesafe-java-jackson2</artifactId>
</dependency>
<!-- or -->
<dependency>
  <groupId>io.github.dfa1.typesafe-java</groupId>
  <artifactId>typesafe-java-jackson3</artifactId>
</dependency>
```

If neither is present, `TypeSafeClient.Builder.build()` throws `IllegalStateException` with a
message telling you to add one. To bypass discovery and wire a codec explicitly (e.g. in tests,
or if you have your own `JsonCodec` implementation):

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .jsonCodec(new Jackson2Codec())
        .build();
```

## Choose an HTTP transport

Likewise, `TypeSafeClient` doesn't depend on any HTTP library directly — it resolves an
`HttpTransport` via `ServiceLoader`. Add one of:

```xml
<dependency>
  <groupId>io.github.dfa1.typesafe-java</groupId>
  <artifactId>typesafe-java-client-jdk</artifactId>
</dependency>
<!-- or, e.g. on Android, where java.net.http isn't available -->
<dependency>
  <groupId>io.github.dfa1.typesafe-java</groupId>
  <artifactId>typesafe-java-client-okhttp</artifactId>
</dependency>
```

If neither is present, `build()` throws `IllegalStateException`. Only add one — having both on
the classpath makes `ServiceLoader` resolution between them non-deterministic. To wire one
explicitly, or to use your own `HttpTransport` (e.g. backed by Apache HttpClient):

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .httpTransport(new JdkHttpTransport())
        // or: .httpTransport(new OkHttpTransport())
        .build();
```

## Build a `Content`

`EvaluateRequest.of` takes a `Content` for `state`, one of exactly three shapes
(see [docs.typesafe.ai/concepts/state](https://docs.typesafe.ai/concepts/state)). The same type
also backs a `Question`'s `instructions` — see
[Ask a question with structured instructions](#ask-a-question-with-structured-instructions)
below:

```java
Content.text("My card was charged twice.");
Content.fields(Map.of("message", "My card was charged twice.", "order_id", "A-104"));
Content.messages(List.of("Hi", "My customer number is TS1337.", "My card was charged twice."));
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

`state(String)` is sugar for `state(Content.text(...))`; pass a `Content` directly for the
`fields`/`messages` shapes. Reusing a question name throws `IllegalArgumentException` instead
of silently dropping the earlier question.

## Ask a Choice question

`Question.choice` picks the best-matching option out of a labeled set:

```java
EvaluateRequest request = EvaluateRequest.of(
        Content.text("Give me all instruments on US market of type bond"),
        Map.of("market", Question.choice("Which market is the request about?",
                Map.of("US", "United States market", "EU", "European market"))));

EvaluateResponse response = client.evaluate(request);
Answer.Choice market = (Answer.Choice) response.answers().get("market");
market.choice();          // "US"
market.confidence();      // 0.0–1.0
market.probabilities();   // per-option probability map
```

`response.choices().get("market")` does the same cast for you — see the next section.

## Ask a question with structured instructions

`instructions` doesn't have to be a plain string — pass a `Content` (the same type `state`
takes) to hold the question in one field alongside data it refers to in others, instead of
splicing that data into a string:

```java
Question.noul(Content.fields(Map.of(
        "potential_duplicate", Map.of(
                "name", "John Smith",
                "location", "Oakland, California",
                "last_employer", "Google"),
        "question", "Is the resume for the same person as `potential_duplicate`?")));
```

`Question.noul(String, ...)`/`choice(String, ...)`/`score(String, ...)` remain the shortcut for
the common plain-text case — sugar for the same factories taking `Content.text(instructions)`.

## Read only the Noul/Choice/Score answers you asked for

`answers()` mixes every answer type in one map, keyed by question name. If a request only asks
`Choice` questions (or you only care about the `Choice` ones back), skip the `instanceof`/cast:

```java
Map<String, Answer.Choice> choices = response.choices();   // also: .nouls(), .scores()
choices.get("market").choice();
```

Each is `answers()` narrowed to that subtype, recomputed on every call.

## Get typed answers instead of `Map<String, Answer>`

`typesafe-java-mapping` maps a **record**'s annotated components into the request's questions and
the response back into a new instance of that record — no `answers().get("name")`, no
`(Answer.Noul)` cast:

```java
record TicketUrgency(
        @Noul("Does this convey urgency?") double isUrgent,
        @Choice(value = "Who's at fault?", options = {
                @Option("wrong_toppings"), @Option(value = "late_delivery", description = "Arrived late")})
        String culprit,
        @Score(value = "How spicy?", levels = {"Mild", "Medium", "Hot", "Face-melting"}) double spiciness) {
}

MappingTypeSafeClient client = TypeSafeClient.builder().apiKey(token).build(MappingTypeSafeClient::decorate);
TicketUrgency result = client.evaluateTyped(Content.text("..."), TicketUrgency.class);
result.isUrgent();   // double, from Answer.Noul#noul()
result.culprit();    // String, from Answer.Choice#choice()
```

Component type must match its annotation (`double` for `@Noul`/`@Score`, `String` for
`@Choice`) — see [reference.md](reference.md#answer-mapping) for the full mapping table,
including its validation and error-reporting rules.

Need `probabilities()`/`confidence()` too (dropped by the scalar form above)? Type the
component as the full `Answer.Noul`/`Answer.Choice`/`Answer.Score` instead of the scalar:

```java
record TicketUrgencyWithConfidence(
        @Noul("Does this convey urgency?") Answer.Noul isUrgent,
        @Choice(value = "Who's at fault?", options = {
                @Option("wrong_toppings"), @Option(value = "late_delivery", description = "Arrived late")})
        Answer.Choice culprit,
        @Score(value = "How spicy?", levels = {"Mild", "Medium", "Hot", "Face-melting"}) Answer.Score spiciness) {
}

TicketUrgencyWithConfidence result = client.evaluateTyped(Content.text("..."), TicketUrgencyWithConfidence.class);
result.isUrgent().noul();         // same double, now via the full Answer.Noul
result.culprit().confidence();    // double, from Answer.Choice#confidence()
```

Since `MappingTypeSafeClient` is a
`TypeSafeClient` decorator, `evaluateTypedAsync` and the plain `evaluate`/`evaluateAsync`/
`listModels`/`close` methods are all available on the same instance. Pass a `Model` as the
second argument (`evaluateTyped(state, Model.PREVIEW, TicketUrgency.class)`) to pin one, the
same as `EvaluateRequest.of`; the two-argument form defaults to `Model.LATEST`.

## Ask a Score question

`Question.score` ranks a statement against an ordered list of labels (e.g. a Likert scale):

```java
Question.score("How frustrated is the customer?", List.of("Calm", "Frustrated", "Very angry"));
```

The `Answer.Score` you get back carries a numeric `score()`, a `legend()` mapping each ordinal to
its label, `probabilities()` per ordinal, and a `confidence()`.

## Call the API asynchronously

`evaluateAsync` returns a `CompletableFuture<EvaluateResponse>`, failing the same way `evaluate`
throws:

```java
client.evaluateAsync(request)
        .thenAccept(response -> System.out.println(response.answers()))
        .exceptionally(ex -> { ex.printStackTrace(); return null; });
```

## Handle API errors

A non-`200` response throws `TypeSafeException`, one of
its per-status subclasses (see [reference](reference.md#typesafeexception)) when the status
matches one, or the base class itself otherwise:

```java
try {
    client.evaluate(request);
} catch (TypeSafeException.RateLimit e) {
    e.retryAfter().ifPresent(delay -> System.err.println("retry after " + delay));
} catch (TypeSafeException.Authentication e) {
    System.err.println("bad API key");
} catch (TypeSafeException.Timeout e) {
    System.err.println("timed out: " + e.getMessage());
} catch (TypeSafeException.Connection e) {
    System.err.println("couldn't reach TypeSafe: " + e.getMessage());
} catch (TypeSafeException e) {
    System.err.println(e.statusCode() + ": " + e.body());
}
```

`evaluate`/`evaluateAsync`/`listModels` declare no checked exception — every failure above,
including a connection failure/timeout and the calling thread being interrupted, is an unchecked
`TypeSafeException` (see [ADR 0002](../adr/0002-no-checked-exceptions.md)). Nothing is retried
unless you [add retries](#retry-transient-failures).

## Use a custom `HttpClient`

`JdkHttpTransport` takes an `HttpClient`, so any JDK `HttpClient` configuration goes through it
— e.g. `connectTimeout`, which only bounds the TCP handshake, not the wait for a response:

```java
HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
TypeSafeClient client = TypeSafeClient.builder().apiKey(token).httpTransport(new JdkHttpTransport(http)).build();
```

## Configure the per-request timeout

`JdkHttpTransport` applies a timeout to every request (`10` seconds by default,
`JdkHttpTransport.DEFAULT_TIMEOUT`) — this is the actual "give up waiting for a response" bound,
distinct from `HttpClient`'s `connectTimeout` above. Override it with the `(HttpClient, Duration)`
constructor, or pass `null` to disable it entirely:

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .httpTransport(new JdkHttpTransport(HttpClient.newHttpClient(), Duration.ofSeconds(30)))
        .build();
```

A timed-out request surfaces as `TypeSafeException.Timeout`, not the transport's raw
`HttpTimeoutException` — retried like a connection failure if you
[add retries](#retry-transient-failures).

## Close the client when you're done with it

`TypeSafeClient` implements `AutoCloseable` and closes its `HttpTransport` — for
`JdkHttpTransport`, that releases the underlying `HttpClient`; for `OkHttpTransport`, it shuts
down the `OkHttpClient`'s dispatcher executor, evicts its connection pool, and closes its cache:

```java
try (TypeSafeClient client = TypeSafeClient.builder().apiKey(token).build()) {
    client.evaluate(request);
}
```

A client that lives for the whole process (e.g. a singleton in a long-running service) doesn't
need closing.

## Configure the endpoint

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .endpoint(URI.create("https://staging.typesafe.ai/v1/systemone"))
        .build();
```

## Retry transient failures

A client from `build()` makes exactly one attempt per call. Add retries with
`RetryingTypeSafeClient`:

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .decorateWith(RetryingTypeSafeClient::decorate)       // 5 retries, backoff from 500ms
        .build();

TypeSafeClient tuned = TypeSafeClient.builder().apiKey(token)
        .decorateWith(c -> RetryingTypeSafeClient.decorate(c, 2, Duration.ofMillis(100)))
        .build();
```

`evaluate`/`evaluateAsync`/`listModels` then retry on:

- `408`, `429`, or any `5xx` status — honoring a `retry-after`/`retry-after-ms` response header
  when present, falling back to exponential backoff (doubled each attempt) otherwise
- a connection failure or a request timeout (`TypeSafeException.Connection`/`.Timeout`)

Any other failure, or a retryable one that's still failing once retries run out, throws
`TypeSafeException` (see [reference](reference.md#typesafeexception)). Add it once: two would
multiply the attempts, so `build()` throws `IllegalStateException` if it sees more than one.

## Cap the total time of a call, retries included

`JdkHttpTransport`'s timeout bounds one attempt; with retries, one `evaluate` can still take over
a minute in the worst case. Decorate the retrying client with a `DeadlineTypeSafeClient` to bound
the whole call:

```java
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .decorateWith(RetryingTypeSafeClient::decorate)
        .decorateWith(c -> DeadlineTypeSafeClient.decorate(c, Duration.ofSeconds(20)))
        .build();
```

Past the deadline, `evaluate`/`evaluateAsync` fail with `TypeSafeException.Timeout` and no further
retry is started (an attempt already in flight isn't aborted; its response is ignored).
`listModels` isn't bounded. Order matters: added the other way round, the deadline applies to
each attempt instead — see the next section.

## Count the tokens you've used

Every successful response reports its token usage (`EvaluateResponse#usage()`). To keep a running
total, create a `TokenCounter` and add its decorator:

```java
TokenCounter tokens = new TokenCounter();
TypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .decorateWith(RetryingTypeSafeClient::decorate)
        .decorateWith(tokens::decorate)
        .build();

client.evaluate(request);
System.out.println(tokens.inputTokens() + " in, " + tokens.outputTokens() + " out");
```

The counter is a separate object so you can still read it once its decorator is buried in a
stack, and one counter can be shared across several clients (`tokens::decorate` on each). It's
thread-safe. Only successful responses carry usage, so failed calls and retried attempts add
nothing — its position in the stack doesn't matter.

## Choose the order of decorators

Each `decorateWith(...)` adds a decorator *around* everything added before it, and
`build(...)`'s decorator goes around all of them. Reading the builder top to bottom is reading
the stack from the inside out:

```java
TypeSafeClient.builder().apiKey(token)
        .decorateWith(RetryingTypeSafeClient::decorate)                                  // 1
        .decorateWith(c -> DeadlineTypeSafeClient.decorate(c, Duration.ofSeconds(20)))  // 2
        .build(MappingTypeSafeClient::decorate);                                         // 3
```

```
caller → Mapping (3) → Deadline (2) → Retrying (1) → DefaultTypeSafeClient → HTTP
```

A call passes through the outermost decorator first; its result (or failure) comes back through
them in reverse. Which one is outside which changes behavior:

| Stack (innermost → outermost) | Effect |
|---|---|
| retrying → deadline | the deadline is a **total** budget, retries included |
| deadline → retrying | the deadline is **per attempt**; a timed-out attempt is retried |
| retrying → retrying | attempts multiply — `build()` rejects this |
| retrying → your metrics | metrics count **logical calls** (what the caller sees) |
| your metrics → retrying | metrics count **attempts** |
| `FailingTypeSafeClient` → retrying | injected failures are retried — handy for testing retry itself |
| `TokenCounter` anywhere | same totals — only successful responses carry usage |
| `MappingTypeSafeClient` anywhere but outermost | its own methods are unreachable; pass it to `build(...)`, which keeps its type |

A good default, outermost first: mapping → metrics → deadline → cache → retrying.

## Decorate `TypeSafeClient` with your own cross-cutting concerns

`TypeSafeClient` is an interface, so wrap one in another implementation of the same interface —
the classic Decorator pattern — to add caching, metrics, a circuit breaker, or anything else,
transparently to callers that just depend on `TypeSafeClient`:

```java
record CachingTypeSafeClient(TypeSafeClient delegate, Map<EvaluateRequest, EvaluateResponse> cache)
        implements TypeSafeClient {

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        EvaluateResponse cached = cache.get(request);
        if (cached != null) {
            return cached;
        }
        EvaluateResponse response = delegate.evaluate(request);
        cache.put(request, response);
        return response;
    }

    // evaluateAsync/listModels/close delegate straight through

}

TypeSafeClient client = new CachingTypeSafeClient(TypeSafeClient.builder().apiKey(token).build(), new ConcurrentHashMap<>());
```

`Builder#build(Function<TypeSafeClient, T>)` applies a decorator to the built client in one call,
returning `T` (the decorator's own type, e.g. `MappingTypeSafeClient` — no cast needed to reach
its extra methods) instead of the plain `TypeSafeClient`:

```java
CachingTypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .build(base -> new CachingTypeSafeClient(base, new ConcurrentHashMap<>()));
```

Stack more with `Builder#decorateWith(...)` (see
[Choose the order of decorators](#choose-the-order-of-decorators)). It returns the builder, so
`build()` can only give back a `TypeSafeClient` — put a decorator whose own methods you need (like
`MappingTypeSafeClient`) in `build(...)`:

```java
Function<TypeSafeClient, TypeSafeClient> caching = base -> new CachingTypeSafeClient(base, new ConcurrentHashMap<>());

MappingTypeSafeClient client = TypeSafeClient.builder().apiKey(token)
        .decorateWith(caching)
        .decorateWith(RetryingTypeSafeClient::decorate)
        .build(MappingTypeSafeClient::decorate);
```

## Test code that uses `TypeSafeClient` without hitting the real API

Two options:

1. **Add `typesafe-java-testkit` (test scope) and use `RecordingTypeSafeClient`, or mock
   `TypeSafeClient` directly.** Both work at the `EvaluateRequest`/`EvaluateResponse` level, with
   no setup — the simplest option for testing code that just calls `evaluate()`/`listModels()`
   and reacts to the result:

   ```java
   RecordingTypeSafeClient client = new RecordingTypeSafeClient()
           .enqueueEvaluate(response);

   codeUnderTest.run(client);

   assertThat(client.evaluateRequests()).containsExactly(expectedRequest);
   ```

   is equivalent to `Mockito.mock(TypeSafeClient.class)` plus
   `given(client.evaluate(request)).willReturn(response)` (works because `TypeSafeClient` is an
   interface) — pick whichever fits your test's style; `RecordingTypeSafeClient` needs no Mockito
   dependency and records every request for free, Mockito's `verify`/`ArgumentCaptor` give you
   more control over matching a specific request to a specific stub. `enqueueEvaluate(response)`
   queues a response to whichever `evaluate()`/`evaluateAsync()` call comes next (`enqueueModels`
   likewise for `listModels()`); a call with nothing left queued throws (or, for `evaluateAsync`,
   fails its future with) an `AssertionError`.
2. **Wrap it behind an interface of your own** if you want zero coupling to this library's types
   in your domain code, or need a shape it doesn't have (e.g. a synchronous-only facade). Mock
   *that* interface instead.

Don't mock the `HttpTransport`/`JsonCodec` SPIs directly, though — they're lower-level than
anything your code calls (they don't even appear in `TypeSafeClient`'s public methods), and a
test built on them breaks whenever this library's internals change for reasons that have nothing
to do with your code.

## Test how your code handles intermittent failures

`FailingTypeSafeClient` decorates any `TypeSafeClient` — including a `RecordingTypeSafeClient`,
so recording and periodic failure compose — and throws every `failEvery`-th call instead of
reaching the delegate (`evaluate()`, `evaluateAsync()`, and `listModels()` share one counter):

```java
TypeSafeClient client = new FailingTypeSafeClient(
        new RecordingTypeSafeClient().enqueueEvaluate(response),
        3,
        () -> new TypeSafeException(503, "overloaded"));

client.evaluate(request); // 1st call: succeeds
client.evaluate(request); // 2nd call: succeeds
client.evaluate(request); // 3rd call: throws TypeSafeException(503, "overloaded")
```

The failure supplier runs once per triggered call, so it can return a fresh exception instance
each time, or a different one depending on external state. `failEvery` must be positive.

## Reuse the DTOs without pulling in an HTTP or JSON library

`typesafe-java-core` has zero runtime dependencies — `TypeSafeClient` talks to `HttpTransport`/
`JsonCodec`, never to a concrete HTTP or JSON library directly. If you only need to
(de)serialize `EvaluateRequest`/`EvaluateResponse` payloads — for example to publish or consume
them on a Kafka topic — depend on `typesafe-java-core` plus a codec module, and ignore
`TypeSafeClient` entirely:

```java
JsonCodec codec = new Jackson2Codec();
String json = codec.writeValueAsString(request);
EvaluateResponse response = codec.readValue(json, EvaluateResponse.class);
```

(A Kafka producer/consumer using a raw-`byte[]` serializer converts once at that boundary —
`json.getBytes(UTF_8)` / `new String(bytes, UTF_8)` — the same one-line conversion any
non-`String`-based transport needs; `TypeSafeClient` itself needs none, since `HttpTransport`
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
alias, any other id is pinned directly. Reads the token from `~/.typesafe.apikey`.

Stdout is silent by default — reach for `--print`/`--verbose` below to see anything. `--verbose`
prints the full `EvaluateResponse` as pretty-printed JSON to stdout, plus the outgoing request
(also pretty-printed) and the response's request id to stderr; `--timing` prints how long the
API took, to stderr. Run with `--version` alone to print the jar's version, or `--help`/`-h`
alone to print usage, and exit without calling the API.

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

## Run without the API, on a local model

`typesafe-java-local` evaluates in-process on ONNX Runtime: no network, no API key, same `EvaluateRequest`/
`EvaluateResponse`. Jev's weights aren't public, so the answers come from an open model — Jev's contract, not Jev's
judgement.

Download a model directory once with Hugging Face's CLI (`pip install huggingface_hub`, or run it through
[uv](https://docs.astral.sh/uv/) as below). Files keep their Hugging Face names; the client finds the one `.onnx` model
in the directory:

```bash
# Laya fp32 (1.7 GB), the recommended engine
uvx --from huggingface_hub hf download onnx-community/laya-typed-decisions-ONNX \
    tokenizer.json config.json onnx/model.onnx onnx/model.onnx_data --local-dir ~/.cache/typesafe-local/laya-fp32
# optional: Laya fp16 (0.85 GB, see below)
uvx --from huggingface_hub hf download onnx-community/laya-typed-decisions-ONNX \
    tokenizer.json config.json onnx/model_fp16.onnx onnx/model_fp16.onnx_data --local-dir ~/.cache/typesafe-local/laya-fp16
# optional: Qwen2.5-1.5B 4-bit (1.8 GB)
uvx --from huggingface_hub hf download onnx-community/Qwen2.5-1.5B-Instruct \
    tokenizer.json onnx/model_q4.onnx --local-dir ~/.cache/typesafe-local/qwen2.5-1.5b
# optional: Clef-flash (19 GB of bf16 weights, needs about as much free memory): Cloudflare's weights,
# plus Ollaya's ONNX graph, which reads them in place
uvx --from huggingface_hub hf download Cloudflare/clef-flash --revision 17f0b0ad64efb65d273590632833508766b2aae6 \
    --include '*.safetensors' --include tokenizer.json --local-dir ~/.cache/typesafe-local/clef-flash
uvx --from huggingface_hub hf download ollaya-dev/clef flash/model-fp32.onnx --local-dir ~/.cache/typesafe-local/clef-flash
```

Then use it like any other `TypeSafeClient`, decorators and `MappingTypeSafeClient` included:

```java
try (TypeSafeClient client = LocalLayaTypeSafeClient.load(Path.of(System.getProperty("user.home"), ".cache/typesafe-local/laya-fp32"))) {
    EvaluateResponse response = client.evaluate(EvaluateRequest.of(
            Content.text("Help! My payouts have been failing for 3 days."),
            Map.of("is_urgent", Question.noul("Does this convey urgency?"))));
}
```

Laya is the recommended engine: about 55 ms for a one-question request and about 165 ms for three on an Apple M5
CPU. The fp16 variant gives the same answers in half the download, but ONNX Runtime's CPU kernels convert fp16 on the
fly, so it's 2–3× slower on a CPU; it only pays off on a GPU. Qwen is a baseline, not a recommendation: a prompted
general LLM, about 10× slower than Laya and less like Jev (table below). Measured against the real
`jev-1.13.0` on 104 cached requests, Laya agrees on 85% of yes/no answers and 64% of choices, with a mean score error
of 0.19 on a 0–1 scale — good enough to triage or pre-filter, not a drop-in where you depend on Jev's exact judgement.
Clef-flash is a 9B model. As downloaded (bf16 weights, fp32 compute) it peaked at 20 GB on a 32 GB Apple M5 and took
about a minute per request. Convert it to 4-bit weights once (31 s, about 1.3 GB of memory; the new directory hard-links
the original files, so no extra copy):

```bash
cd local && uv run scripts/clef/quantize_q4.py   # writes ~/.cache/typesafe-local/clef-flash-q4
```

`LocalClefTypeSafeClient.load(Path.of(..., "clef-flash-q4"))` then needs 7.7 GB and, on the same M5, answers in 7–13 s
for 1–3 questions on the CPU, or 2–4 s on the GPU (WebGPU, still experimental here): batch work, not interactive use.
On a Mac, the same model runs about 5× faster through MLX; see
[Run Clef-flash on a Mac with MLX](#run-clef-flash-on-a-mac-with-mlx).

The `Local engines` GitHub workflow re-measures agreement and throughput on Linux and macOS runners and writes both
tables to its job summary. To reproduce locally:

```bash
./mvnw -pl local -am test -DexcludedGroups=acceptance -Dengine=laya      # real-model tests (laya, qwen, clef, ...)
```

## Run Clef-flash on a Mac with MLX

[Clef-flash](https://huggingface.co/Cloudflare/clef-flash) is the local model closest to Jev, and on Apple Silicon
the fastest way to run it is [MLX](https://github.com/ml-explore/mlx), Apple's array framework for its GPUs.
[mlx-community's 4-bit port](https://huggingface.co/mlx-community/clef-flash-4bit) ships a small server speaking
TypeSafe's `POST /v1/systemone`, so the regular client talks to it: no `local` module, no API key.

Download the model (6.2 GB) and start the server (needs [uv](https://docs.astral.sh/uv/); it serves one request
at a time):

```bash
M=~/.cache/typesafe-local/clef-flash-mlx-4bit
uvx --from huggingface_hub hf download mlx-community/clef-flash-4bit --exclude '__pycache__/*' --local-dir $M
uv run --with 'mlx-vlm>=0.7.4,<0.8' python $M/clef_mlx.py serve --model $M --port 8000
```

Then point the client at it:

```java
try (TypeSafeClient client = TypeSafeClient.builder()
        .endpoint(URI.create("http://localhost:8000/v1/systemone"))
        .build()) {
    EvaluateResponse response = client.evaluate(request);
}
```

On an Apple M5 (32 GB) it answers a 3-question request in about 0.55 s and needs about 7 GB. On the same 104 cached
requests as above, it agrees with `jev-1.13.0` far more often than the in-process models:

| Agreement with `jev-1.13.0` | Qwen2.5-1.5B (prompted) | Laya | Clef-flash (MLX 4-bit) |
|---|---|---|---|
| Yes/no: same side of 0.5 | 80% | 85% | 95% |
| Choice: same pick | 68% | 64% | 88% |
| Score: mean error (0–1 scale) | 0.26 | 0.19 | 0.07 |
| Time per 3-question request | 2.05 s | 0.19 s | 0.6 s |

Things to know:
- It's macOS on Apple Silicon only, and the server is mlx-community's code, not this project's.
- `listModels()` fails: the server lists models in OpenAI's format, not TypeSafe's.
- The response's `model` is whatever the request asked for (`jev-latest` by default), not the model that answered.

## Run the acceptance tests against the live API

The acceptance tests in the `acceptance` module run every scenario once per HttpTransport/
JsonCodec combination and are excluded from a routine build. Opt in once you have
`~/.typesafe.apikey` in place:

```bash
./mvnw test -pl acceptance -am -DexcludedGroups=
```
