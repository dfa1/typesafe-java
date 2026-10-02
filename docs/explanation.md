# Explanation

Background reading on the design decisions behind this library. For "what exists," see
[reference.md](reference.md); for "how do I," see [how-to.md](how-to.md).

## Why `local` exists, and why it reads answers instead of generating them

`TypeSafeClient` is an interface, so an in-process engine is just another implementation: everything built on it
(`MappingTypeSafeClient`, `RetryingTypeSafeClient`, `TokenCounter`, the testkit) keeps working. Jev's weights aren't
public, so `local` offers API parity with a different model behind it, and says so.

Jev's answers are probability distributions, never text, and both engines produce distributions directly. Laya, a
ModernBERT encoder trained for Choice/Score/Noul, scores each option with a decision head. Qwen, a general chat model,
is prompted and its next-token logits over the candidate answers (`Yes`/`No`, option letters, level digits) are read
after one prefill. Neither generates, so there's no output to parse and the result is deterministic. Laya is the
recommended engine: four times smaller than Qwen, about ten times faster, and closer to Jev on yes/no and scores.

The module depends only on `core` and ONNX Runtime. Tokenization is a small pure-Java byte-level BPE reading
`tokenizer.json`, checked id for id against HuggingFace `tokenizers`. JSON is a minimal reader, enough for
`tokenizer.json` and Laya's config. The alternatives were an 18 MB native tokenizer library, or a Java ML library whose
tokenizer silently dropped newlines and special tokens. Model files come from a directory the caller prepares, never
from a download at run time: the client works offline and a deployment pins exactly the files it runs. Laya's ONNX is
[onnx-community's export](https://huggingface.co/onnx-community/laya-typed-decisions-ONNX), which matches PyTorch, as
our own export does. `LayaEngineTest` checks the Java port's logits against a PyTorch fixture. There's no int8 model:
its results depended on the CPU's int8 kernels. On one x86 runner it agreed with Jev less, and on another it silently
returned flat distributions.

Several things were measured and rejected:

- **Parallel sessions.** On a CPU, one ONNX session already uses every core, so splitting the same work across
  sessions or threads finished no sooner, and N sessions mean N copies of the weights competing for memory bandwidth.
- **CoreML.** The execution provider ran Laya 2–3× slower than the CPU.
- **Smaller LLMs.** SmolLM2, Qwen2.5-0.5B and Qwen3-0.6B didn't track Jev at all.

## Why `core` doesn't know about Jackson

The DTOs (`Answer`, `Question`, `EvaluateRequest`, `EvaluateResponse`, `Usage`, `RequestId`)
started out annotated directly with `@JsonTypeInfo`/`@JsonSubTypes` and hard-wired to
Jackson 2's `ObjectMapper`. That meant every consumer of the client was also a forced consumer of
Jackson 2, and the DTOs themselves couldn't be reused (e.g. to serialize the same payloads onto a
Kafka topic) without dragging in `java.net.http`-specific code too.

Splitting the DTOs into `typesafe-java-core` with zero Jackson dependency, and moving the `type`
discriminator logic into private mixins inside `jackson2`/`jackson3` (`ObjectMapper.addMixIn` /
`JsonMapper.Builder.addMixIn`), means:

- `core` alone is a valid dependency for anything that just needs the payload shapes.
- Supporting a third JSON library later is "add a fourth codec module," not "touch the DTOs."
- The DTOs stay exactly what they look like: plain records and a sealed interface, with no clue
  which serialization library (if any) is reading them.

See [ADR 0001](../adr/0001-multi-module-layout-with-pluggable-json-codec.md) for the full
decision record.

## Why `JsonCodec` and `HttpTransport` are resolved via `ServiceLoader`, not a compile dependency

`core` cannot declare a compile dependency on `jackson2`/`jackson3` or on `client-jdk` —
any of those choices would undo the whole point of splitting them out. `ServiceLoader` lets
`TypeSafeClient` stay agnostic to both while still getting real implementations automatically
the moment one codec module and one transport module are on the classpath, the same pattern the
JDK itself uses for `java.sql.Driver` or `java.nio.file.spi.FileSystemProvider`. The tradeoff: a
missing codec or transport module fails at `TypeSafeClient.Builder.build()` time with a runtime
`IllegalStateException`, not at compile time — deliberately, since a compile-time check here
would mean picking one codec/transport as "the real dependency," which is exactly what this
design avoids.

## Why `HttpTransport` exists (and why `client` isn't a separate module)

`TypeSafeClient` originally called `java.net.http.HttpClient` directly. Abstracting that behind
`HttpTransport` — mirroring `JsonCodec` — means someone who wants Apache HttpClient, OkHttp, or a
mocked transport for tests can implement one interface (`post`/`get`, both already
`CompletableFuture`-returning) instead of forking the client.

Once that abstraction exists, `TypeSafeClient` itself has no HTTP-library dependency any more —
its only import from `java.net` is `URI`, which every JDK module already has. That removed the
original reason for a separate `client` module (keeping `core` free of `java.net.http`), so
`TypeSafeClient`/`ApiKey`/`TypeSafeException` live in `core` next to the DTOs: one fewer module
to version and depend on, with `core` exactly as dependency-free as before. See
[ADR 0001](../adr/0001-multi-module-layout-with-pluggable-json-codec.md) for the full decision record.

## Why `TypeSafeClient` is an interface, not a final class

It started out `public final class TypeSafeClient`. Mockito 5's default (inline) mock maker
already mocks final classes, so finality was never actually blocking a caller from unit-testing
code that depends on `TypeSafeClient` — but it did block a different, legitimate use: a
decorator. `final` means nothing can `implements`/present itself as a `TypeSafeClient`, so a
caller who wants to wrap one with caching, metrics, a circuit breaker, or anything else in the
classic Decorator shape has no supertype to implement — they'd have to invent their own
interface with the same three methods and get every call site to depend on that instead of on
`TypeSafeClient` directly.

Making it an interface costs nothing observable at existing call sites: `TypeSafeClient.builder().apiKey(key).build()`
still type-checks and behaves identically, since `Builder.build()` always returned the interface
type as far as callers could tell. What moved is the implementation — the retry/backoff/header/
decode logic, previously `TypeSafeClient`'s own body, now lives in `DefaultTypeSafeClient`, the
only concrete `TypeSafeClient` this library produces. A consumer can now write
`class CachingTypeSafeClient implements TypeSafeClient` and hand it anywhere a `TypeSafeClient`
was expected.

`Builder` moved with it, onto `DefaultTypeSafeClient` rather than staying on the `TypeSafeClient`
interface: constructing a `DefaultTypeSafeClient` — picking defaults, discovering a
`HttpTransport`/`JsonCodec` via `ServiceLoader` — is that class's own concern, not something a
pure contract interface should carry. That required making `DefaultTypeSafeClient` itself
public (a nested class can't be more accessible than its enclosing class), so it's no longer
hidden — but `TypeSafeClient.builder()` still exists as a one-line delegating static method
on the interface, so nothing at the call site changes; a consumer only sees `DefaultTypeSafeClient`
by name if they explicitly go looking for it.

## Why `testkit` ships a `TypeSafeClient` fake instead of "just mock it with Mockito"

Once `TypeSafeClient` became an interface (see above), `Mockito.mock(TypeSafeClient.class)` was
already enough to stub `evaluate()`/`listModels()` — so `RecordingTypeSafeClient` isn't there to
make something possible that wasn't. It's there so a consumer's test doesn't need a Mockito
dependency at all, and so the two or three lines of "queue a response, assert on what was sent"
every such test wants don't get rewritten by hand each time. It's deliberately a plain FIFO
(`enqueueEvaluate`/`enqueueModels`) rather than a matcher-based expectations DSL: a test already
controls call order — it's the one deciding when to call `evaluate()`/`listModels()` — so
matching by request content would only restate what the test already knows.

## Why `mapping` uses reflection over records, not an annotation processor or a fluent builder

[Issue #2](https://github.com/dfa1/typesafe-java/issues/2) flagged that reading an answer back
means `Map<String, Answer>` plus a manual `(Answer.Noul)`-style cast, and that a `Noul` question
with no criteria still needed an empty `Map.of()` at the call site (since fixed — `Question.noul`
now has a criteria-less overload too). `MappingTypeSafeClient` fixes the cast: a caller's own
record carries `@Noul`/`@Choice`/`@Score` on its components, and gets a populated instance of
that same record back.

Three ways to build that mapping, in ascending complexity: a fluent builder (no annotations,
just explicit `.noul("isUrgent", "...")` calls mapped to record positions by hand — doesn't
remove the cast, only moves it into the builder's own return type); reflection over
`Class#getRecordComponents()` (no new build step, matches how the rest of this project already
avoids codegen — `jackson2`/`jackson3`'s polymorphism is hand-written mixins, not generated); or
an annotation processor generating a real mapper class at compile time (fully typed at compile
time, zero reflection cost per call, but a new `javac`-time dependency and generated-sources
step nothing else in this repo has). Reflection won: even repeated, its cost is dwarfed by the
network round trip each call wraps, and it keeps `mapping`'s dependency footprint identical to
every other module here (`core` only).

That said, `MappingTypeSafeClient` still caches each record type's reflection metadata — its
per-component question/answer mapping and canonical constructor — the first time that type is
used, keyed by `Class` on the instance. A single call's reflection cost being noise doesn't mean
redoing it on every call is free; caching it is nearly free to add (one `ConcurrentHashMap`) and
turns "noise per call" into "noise once per record type," which also means a caller only pays
the validation cost (see below) once, not on every request.

`@Option` only exists nested inside `@Choice#options()` (`@Target({})`, not usable on its own) —
`Question.Choice#criteria()` is a `Map<String, String>`, and a Java annotation attribute can't
be a `Map`; an array of a small carrier annotation is the standard workaround. `@Noul`/`@Score`
map to a `double` component (the raw `Answer.Noul#noul()`/`Answer.Score#score()` value, not a
derived `boolean`/enum) deliberately: a probability-to-boolean threshold is a policy decision
this library shouldn't make on a caller's behalf.

The scalar mapping (`double`/`String`) drops `Answer`'s other fields — `probabilities()`,
`confidence()`, and (for `Score`) `legend()` — which is fine for the common case but was a real
gap for a caller who wants them: the only escape hatch was dropping `evaluateTyped` for a plain
`evaluate()` call and going back to `Map<String, Answer>`. Rather than a second family of
confidence-carrying annotations/types, each component's type is simply allowed to be the full
`Answer.Noul`/`Answer.Choice`/`Answer.Score` as an alternative to the scalar —
`componentMappingFor` picks an identity extraction instead of unwrapping the scalar when it sees
the full type. Same annotation, same validation path, no new concepts.

## Why `MappingTypeSafeClient` doesn't have its own `Builder`

The natural-looking ask — `MappingTypeSafeClient.builder()...build()`, mirroring
`TypeSafeClient.builder()` — was rejected. `TypeSafeClient.builder` works because
`TypeSafeClient` has exactly one production implementation to build. `MappingTypeSafeClient` is
a decorator, meant to wrap *any* `TypeSafeClient` (a plain one, one already wrapped in caching,
a `FailingTypeSafeClient` for testing, a test double) — a builder that constructs its own
`DefaultTypeSafeClient` internally would bake in "wrap a fresh default client" as the only path,
against the entire reason the decorator shape exists (see "Why `TypeSafeClient` is an interface,
not a final class" above). It would also duplicate `DefaultTypeSafeClient.Builder`'s whole
surface (`httpTransport`, `jsonCodec`, `endpoint`, ...) as forwarding
methods that go stale the moment the original gains an option this copy doesn't.

What shipped instead is a single addition to the *existing* `Builder`:
`build(Function<TypeSafeClient, T> decorate)`, one line (`decorate.apply(build())`) that applies
a decorator to the client it just built and returns `T` instead of the plain `TypeSafeClient` —
no cast needed to reach `evaluateTyped`. It's generic, so `core` never needs to know
`MappingTypeSafeClient` exists, and it's purely additive: the plain
`MappingTypeSafeClient.decorate(anyDelegate)` static factory still works for every case this
doesn't cover — there's no public constructor to call instead.
Stacking more than one decorator goes through `Builder#decorateWith(...)` (see "Why retrying is a
decorator" below). It returns the builder, not the decorator's type, so `build()` can only return `TypeSafeClient` — which is why
`build(Function<TypeSafeClient, T>)` stays: it's the one slot that keeps a decorator's own type
(`MappingTypeSafeClient`'s `evaluateTyped`) without a cast.

## Why `Content` is a sealed interface, not `Object`

`EvaluateRequest.state()` used to be a bare `Object` — "whatever the caller's `JsonCodec` can
serialize." But [docs.typesafe.ai/concepts/state](https://docs.typesafe.ai/concepts/state)
documents `state` as exactly three shapes: a string, a JSON object, or an array of text values —
not open-ended JSON. `Object` was strictly looser than the real API contract: a caller could pass
a `List<Integer>` or a custom record and it would compile, then fail (or silently misbehave)
against the actual API. `Content` (`Text`/`Fields`/`Messages`) makes the three valid shapes a
compile-time fact, the same way `Answer`/`Question` already do for their own domains.

The type isn't called `State`, even though `EvaluateRequest.state()` is the field it first
existed for: `docs.typesafe.ai/api` documents a `Question`'s `instructions` as the exact same
three shapes ("an object can hold the question in one field and data it refers to in others"),
and `Question.Noul`/`Choice`/`Score`'s `instructions` component is this same sealed interface.
Calling a type used for both fields `State` would read backwards at the `instructions` call
site — `Content` is the word already used to describe both ("the content an `EvaluateRequest`
evaluates its questions against"), so it reads correctly at both.

Unlike `Answer`/`Question`, neither field has a `type` discriminator on the wire — the API tells
the three shapes apart by their raw JSON type (string vs. object vs. array), not a tag field. So
each codec's `Content` handling is a serializer that writes the variant's raw value directly
(`gen.writeString(...)` / `gen.writeObject(...)`/`writePOJO(...)`), not a `type`-keyed mixin like
`Answer`/`Question` use. There's also no deserializer: `Content` only ever appears on
`EvaluateRequest`/`Question`, which this client only ever writes, never reads back.

## Why `evaluate`/`listModels` declare no checked exception

They used to declare `throws IOException, InterruptedException`, mirroring
`java.net.http.HttpClient`'s own convention. That was an inconsistency with `TypeSafeException`
itself (already unchecked, for non-2xx statuses): the same call had two failure modes treated
differently — one a caller could ignore, the other forced onto every call site's signature or a
`try`/`catch`. A connection failure, a timeout, and the calling thread being interrupted are now
`TypeSafeException.Connection`/`.Timeout`/`.Interrupted` — all unchecked, alongside the
per-status subclasses. See [ADR 0002](../adr/0002-no-checked-exceptions.md) for the full
decision record.

## Why retries are bounded and exponential

`RetryingTypeSafeClient::decorate` retries `408`, `429`, and any `5xx` up to 5 times, doubling
the backoff from an initial 500ms each time (500ms, 1s, 2s, 4s, 8s). Any other status — including a retryable
one that outlasts the retry budget — surfaces immediately as
`TypeSafeException` rather than being swallowed or retried indefinitely: a caller should always
be able to tell "this request permanently failed" from "this request is still in flight,"
and an unbounded retry loop against a struggling upstream only makes the overload worse.

## Why retrying is a decorator, not part of `DefaultTypeSafeClient`

Retry/backoff used to live inside `DefaultTypeSafeClient`, next to the request/response
plumbing. That made it the one piece of cross-cutting behavior that *wasn't* a `TypeSafeClient`
decorator like everything else here (`mapping`, `testkit`'s `FailingTypeSafeClient`, a caller's
own cache) — so it couldn't be turned off, reordered relative to other decorators, or replaced
without reimplementing the client. `RetryingTypeSafeClient` now does it purely in terms of the
`TypeSafeException` the client throws, which is why `InternalServer` carries `retryAfter()` too:
the decorator never sees the raw response headers.

It's also opt-in: `build()` returns a client that makes one attempt per call. Applying it by
default would need an opt-out knob (`maxRetries(0)`), plus a second way to configure the same
thing (builder setters *and* `decorate(...)`), plus a double-retry trap for anyone adding their
own. One explicit `decorateWith(RetryingTypeSafeClient::decorate)` is cheaper than all three. Its
parameters stay configurable for the same reason the endpoint is: fast unit tests don't have to
wait out a real 500ms+ backoff.

Stacking decorators was first left to `Function#andThen` inside `build(...)`, which works but is
hard to discover. `Builder#decorateWith(...)` adds them one call at a time, each around what the
previous ones built. The name went through `decorator(...)` (reads like a setter that replaces)
and `wrap(...)` before landing here; `with(...)` was rejected because in Java a `with` method
conventionally returns a copy with one value *replaced* (`LocalDate.withYear`), the opposite of
what this does. `decorateWith` says it adds, and pairs with the `decorate` factories it takes. Every decorator's `decorate` takes the client to wrap first, the same shape as
`MappingTypeSafeClient.decorate`, so the no-argument cases are method references
(`RetryingTypeSafeClient::decorate`) and the rest are a lambda.

Order changes behavior (a deadline outside retrying is a total budget, inside it's per attempt),
but only one order is ever wrong: two `RetryingTypeSafeClient`s, which multiply attempts.
`build()` rejects that one, since it sees each decorator as it applies it. It can't see a
`RetryingTypeSafeClient` hidden inside a caller's own decorator; every other ordering is a valid
choice, so it's documented rather than enforced.

## Why `evaluateAsync` isn't just `evaluate` wrapped in `supplyAsync`

A naive `CompletableFuture.supplyAsync(() -> evaluate(request))` would burn one thread per
in-flight request, blocked on `Thread.sleep` during backoff. `evaluateAsync` instead chains off
`HttpTransport.post`'s returned future, and `RetryingTypeSafeClient.evaluateAsync` schedules
retries via `CompletableFuture.delayedExecutor`,
so a backoff wait never blocks a thread — the same retry policy, without the thread cost, which
matters once callers are firing many requests concurrently. This only holds if the
`HttpTransport` implementation's `post` is itself genuinely non-blocking (`JdkHttpTransport` is,
since it delegates to `HttpClient.sendAsync`); a transport backed by a blocking-only HTTP library
has no non-blocking send to chain off and has to fall back to a thread-per-call `post`.
