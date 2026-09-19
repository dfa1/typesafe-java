# Explanation

Background reading on the design decisions behind this library. For "what exists," see
[reference.md](reference.md); for "how do I," see [how-to.md](how-to.md).

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

`core` cannot declare a compile dependency on `jackson2`/`jackson3` or on `jdk-http-client` —
any of those choices would undo the whole point of splitting them out. `ServiceLoader` lets
`TypesafeClient` stay agnostic to both while still getting real implementations automatically
the moment one codec module and one transport module are on the classpath, the same pattern the
JDK itself uses for `java.sql.Driver` or `java.nio.file.spi.FileSystemProvider`. The tradeoff: a
missing codec or transport module fails at `TypesafeClient.Builder.build()` time with a runtime
`IllegalStateException`, not at compile time — deliberately, since a compile-time check here
would mean picking one codec/transport as "the real dependency," which is exactly what this
design avoids.

## Why `HttpTransport` exists (and why `client` isn't a separate module)

`TypesafeClient` originally called `java.net.http.HttpClient` directly. Abstracting that behind
`HttpTransport` — mirroring `JsonCodec` — means someone who wants Apache HttpClient, OkHttp, or a
mocked transport for tests can implement one interface (`post`/`postAsync`) instead of forking
the retry/backoff logic.

Once that abstraction exists, `TypesafeClient` itself has no HTTP-library dependency any more —
its only import from `java.net` is `URI`, which every JDK module already has. That removed the
original reason for a separate `client` module (keeping `core` free of `java.net.http`), so
`TypesafeClient`/`ApiToken`/`TypesafeException` live in `core` next to the DTOs: one fewer module
to version and depend on, with `core` exactly as dependency-free as before. See
[ADR 0001](../adr/0001-multi-module-layout-with-pluggable-json-codec.md) for the full decision record.

## Why retries are bounded and exponential

`evaluate`/`evaluateAsync` retry `429` (rate limited) and `529` (upstream overloaded) up to 5
times, doubling the backoff from an initial 500ms each time (500ms, 1s, 2s, 4s, 8s). Any other
status — including a `429`/`529` that outlasts the retry budget — surfaces immediately as
`TypesafeException` rather than being swallowed or retried indefinitely: a caller should always
be able to tell "this request permanently failed" from "this request is still in flight,"
and an unbounded retry loop against a struggling upstream only makes the overload worse.

## Why `evaluateAsync` isn't just `evaluate` wrapped in `supplyAsync`

A naive `CompletableFuture.supplyAsync(() -> evaluate(request))` would burn one thread per
in-flight request, blocked on `Thread.sleep` during backoff. `evaluateAsync` instead chains off
`HttpTransport.postAsync` and schedules retries via `CompletableFuture.delayedExecutor`, so a
backoff wait never blocks a thread — the same retry policy, without the thread cost, which
matters once callers are firing many requests concurrently. This only holds if the
`HttpTransport` implementation's `postAsync` is itself genuinely non-blocking (`JdkHttpTransport`
is, since it delegates to `HttpClient.sendAsync`); a transport backed by a blocking-only HTTP
library has no non-blocking send to chain off and has to fall back to a thread-per-call
`postAsync`.
