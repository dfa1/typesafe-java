# Explanation

Background reading on the design decisions behind this library. For "what exists," see
[reference.md](reference.md); for "how do I," see [how-to.md](how-to.md).

## Why `core` doesn't know about Jackson

The DTOs (`Answer`, `Question`, `EvaluateRequest`, `EvaluateResponse`, `Usage`, `RequestId`)
started out annotated directly with `@JsonTypeInfo`/`@JsonSubTypes` and hard-wired to
Jackson 2's `ObjectMapper`. That meant every consumer of the client was also a forced consumer of
Jackson 2, and the DTOs themselves couldn't be reused (e.g. to serialize the same payloads onto a
Kafka topic) without dragging in `java.net.http`-specific code too.

Splitting the DTOs into `typesafe-core` with zero Jackson dependency, and moving the `type`
discriminator logic into private mixins inside `jackson2`/`jackson3` (`ObjectMapper.addMixIn` /
`JsonMapper.Builder.addMixIn`), means:

- `core` alone is a valid dependency for anything that just needs the payload shapes.
- Supporting a third JSON library later is "add a fourth codec module," not "touch the DTOs."
- The DTOs stay exactly what they look like: plain records and a sealed interface, with no clue
  which serialization library (if any) is reading them.

See [ADR 0001](../adr/0001-multi-module-layout-with-pluggable-json-codec.md) for the full
decision record.

## Why `JsonCodec` is resolved via `ServiceLoader`, not a compile dependency

`client` cannot declare a compile dependency on `jackson2` or `jackson3` — either choice would
undo the whole point of splitting them out. `ServiceLoader` lets `client` stay codec-agnostic
while still getting a codec automatically the moment one codec module is on the classpath, the
same pattern the JDK itself uses for `java.sql.Driver` or `java.nio.file.spi.FileSystemProvider`.
The tradeoff: a missing codec module fails at `TypesafeClient.Builder.build()` time with a
runtime `IllegalStateException`, not at compile time — deliberately, since a compile-time check
here would mean picking one codec as "the real dependency," which is exactly what this design
avoids.

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
`HttpClient.sendAsync` and schedules retries via `CompletableFuture.delayedExecutor`, so a
backoff wait never blocks a thread — the same retry policy, without the thread cost, which
matters once callers are firing many requests concurrently.
