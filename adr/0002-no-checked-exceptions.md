# 2. No checked exceptions

Date: 2026-09-21

## Status

Accepted

## Context

[Issue #7](https://github.com/dfa1/typesafe-java/issues/7) asked for a typed exception hierarchy
per HTTP status, matching the Python SDK's `TypeSafeAPIError` subclasses (`TypeSafeRateLimitError`,
`TypeSafeAuthenticationError`, ...), so a caller could `catch` a specific failure instead of
switching on `statusCode()`. Implementing that turned `TypeSafeException` into a sealed hierarchy
(`TypeSafeException.RateLimit`, `.Authentication`, `.NotFound`, ...) — all unchecked, since
`TypeSafeException` already extended `RuntimeException`.

That surfaced an inconsistency already in the codebase: `TypeSafeClient.evaluate`/`listModels`
declared `throws IOException, InterruptedException`, mirroring `java.net.http.HttpClient`'s own
checked-exception convention. So the same call had two failure modes treated differently for no
principled reason — a non-2xx status was unchecked (`TypeSafeException`, catch it if you care),
while a connection failure, a timeout, or the calling thread being interrupted was checked
(forced `catch`/`throws` at every call site, `evaluate`/`listModels`/`evaluateAsync` included).
Extending the new per-status hierarchy to connection failures and timeouts (`TypeSafeConnectionException`/
`TypeSafeTimeoutException`, mirroring the Python SDK's `TypeSafeAPIConnectionError`/
`TypeSafeAPITimeoutError`) while *keeping* them checked would have meant two parallel exception
families for what a caller experiences as one thing: "the call to TypeSafe failed."

## Decision

Every failure `TypeSafeClient.evaluate`/`evaluateAsync`/`listModels` can produce is an unchecked
`TypeSafeException` subclass. No method on the interface declares a checked exception any more.

- A non-`200` status: the matching per-status subclass (`BadRequest`, `Authentication`,
  `PermissionDenied`, `NotFound`, `UnprocessableEntity`, `RateLimit`, `InternalServer`), or the
  base `TypeSafeException` itself for a status with no dedicated subclass.
- A `200` response the configured `JsonCodec` couldn't decode: `TypeSafeException.ResponseDecoding`.
- A connection failure, once retries are exhausted: `TypeSafeException.Connection`. A timeout
  specifically: `TypeSafeException.Timeout`, a subclass of `Connection`.
- The calling thread interrupted while blocked waiting for a response: `TypeSafeException.Interrupted`
  — restoring the thread's interrupt status (`Thread.currentThread().interrupt()`) before
  throwing, the standard Java move for converting a swallowed `InterruptedException` to unchecked,
  so code further up the call stack that polls `Thread.interrupted()`/`isInterrupted()` still
  observes it.

`Connection`/`Timeout`/`Interrupted` never had an HTTP response to carry, so `statusCode()`
returns the sentinel `-1` and `body()` returns `null` on all three — documented on each class,
since a caller that reads `statusCode()` without checking which subclass it caught could
otherwise mistake `-1` for a real status.

`HttpTransport` implementations (`JdkHttpTransport`, `OkHttpTransport`) are unaffected: they
still fail their returned `CompletableFuture` with a checked `IOException` internally: that SPI
boundary didn't change. Only `DefaultTypeSafeClient.await()`, where a blocked `evaluate`/
`listModels` call unwraps the future, changed — it's where the conversion to
`TypeSafeException.Connection`/`.Timeout`/`.Interrupted` now happens.

## Consequences

- `evaluate`/`listModels` no longer force a `try`/`catch` or a `throws` clause at every call
  site for the common path; a caller that wants to handle a specific failure catches the
  `TypeSafeException` subclass it cares about, same as it already does for `RateLimit`/
  `Authentication`.
- This breaks source compatibility for any code written against the checked-exception
  signatures. Acceptable now — no released version has consumers depending on this API yet, the
  same reasoning that allowed the `TypesafeClient`/`TypesafeException` rename in `0.4.0`. Not a
  decision to revisit once real consumers exist.
- `TypeSafeException.Connection`/`.Timeout`/`.Interrupted` reuse `statusCode()`/`body()` with
  sentinel values rather than `TypeSafeException` gaining an `Optional<Integer> statusCode()` or
  a parallel field-free base type — the simpler option, at the cost of those three accessors
  being meaningless (and documented as such) on those three subclasses.
- Every subclass constructor is public (not just the base `TypeSafeException(int, String)`),
  matching the base class rather than leaving subclass construction as an internal-only detail.
  This lets `testkit`'s `FailingTypeSafeClient` — and any consumer's own tests — simulate a
  specific subclass (`() -> new TypeSafeException.RateLimit("slow down", Duration.ofSeconds(2))`)
  instead of only the generic base class, so a caller can unit-test its own `catch
  (TypeSafeException.RateLimit e)` logic without hitting the real API.
