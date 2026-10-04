# typesafe-java

[![CI](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml/badge.svg)](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dfa1.typesafe-java/typesafe-java-core.svg)](https://central.sonatype.com/artifact/io.github.dfa1.typesafe-java/typesafe-java-core)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)

Java 21 client for the [TypeSafe API](https://api.typesafe.ai). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

Save a token to `~/.typesafe.apikey` first — either way below picks it up automatically.

## Quickstart

### As a Java library

```java
ApiKey token = ApiKey.fromDefaultFile(); // reads ~/.typesafe.apikey
// or: ApiKey.fromEnv();                 // reads the TYPESAFE_API_KEY environment variable
TypeSafeClient client = DefaultTypeSafeClient.builder().apiKey(token).build();

EvaluateRequest request = EvaluateRequest.of(
        Content.text("Help! My payouts have been failing for 3 days."),
        Map.of("is_urgent", Question.noul("Does this convey urgency?")));

Answer.Noul answer = client.evaluate(request).nouls().get("is_urgent");
answer.noul(); // e.g. 0.92
```

Or skip the `Map`/cast with a typed record (`typesafe-java-client-mapping`):

```java
record UrgencyCheck(@Noul("Does this convey urgency?") double isUrgent) {
}

MappingTypeSafeClient client = DefaultTypeSafeClient.builder().apiKey(token).build(MappingTypeSafeClient::decorate);
UrgencyCheck result = client.evaluateTyped(
        Content.text("Help! My payouts have been failing for 3 days."), UrgencyCheck.class);
result.isUrgent(); // e.g. 0.92
```

See [Install](#install) below to add it as a dependency, or the [tutorial](docs/tutorial.md)
for the full walkthrough — including every way to provide the token, in
[how-to.md](docs/how-to.md#provide-your-api-token).

### From the command line

No Java coding required — the `cli` module builds a self-contained uber-jar, handy for wiring a
check into a Jenkins job, a shell script, or any other CI pipeline without writing a line of
Java. Download it from
[Maven Central](https://central.sonatype.com/artifact/io.github.dfa1.typesafe-java/typesafe-java-cli)
(under the `all` classifier — the plain artifact is just this module's own classes, not
runnable on its own; the [latest release](https://github.com/dfa1/typesafe-java/releases/latest)
notes link straight to the jar), or build it from source:

```bash
./mvnw -pl cli -am package -DskipTests
```

Either way, run it the same way:

```bash
java -jar typesafe-java-cli-*-all.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --min "urgent=0.5" || echo "not urgent enough"
```

Exits `1` if `--min`'s threshold isn't met, so it doubles as a pass/fail gate — stdout stays
silent by default; add `--print urgent` for the value or `--verbose` for the full response as
JSON. See [how-to.md](docs/how-to.md#run-a-quick-check-from-the-command-line) for the
full flag reference (`--choice`/`--score`, `--print`, `--verbose`, ...).

## Install

Maven, via the BOM (see the Maven Central badge above for the latest version):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.dfa1.typesafe-java</groupId>
      <artifactId>typesafe-java-bom</artifactId>
      <version>0.6.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-client-http-jdk</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-codec-jackson3</artifactId>
  </dependency>
</dependencies>
```

On Android, or anywhere else `java.net.http` isn't available, swap `typesafe-java-client-http-jdk`
for `typesafe-java-client-http-okhttp`.

## Modules

| Module | Contains |
|---|---|
| `core` | the model (`EvaluateRequest`, `EvaluateResponse`, `Question`, `Answer`, ...), as plain records; no dependencies |
| `codec` | the `Codec` SPI: serialization to and from bytes; no dependencies |
| `client` | the `TypeSafeClient` interface, `TypeSafeException`, and the decorators that wrap any client (retries, deadline, token counting) |
| `codec-jackson2` / `codec-jackson3` | `Codec` backed by Jackson 2.x / 3.x |
| `client-http` | `DefaultTypeSafeClient`, the `TypeSafeClient` that calls the API, and the `HttpTransport` SPI |
| `client-http-jdk` | `HttpTransport` backed by `java.net.http` |
| `client-http-okhttp` | `HttpTransport` backed by OkHttp — an alternative for environments `java.net.http` doesn't cover, e.g. Android |
| `client-local` | `LocalLayaTypeSafeClient`, `LocalQwenTypeSafeClient`, `LocalClefTypeSafeClient` — evaluate in-process on ONNX Runtime instead of calling the API (Laya, Qwen2.5 or Clef-flash, from a local model directory); API parity, not model parity with Jev. See [how-to](docs/how-to.md#run-without-the-api-on-a-local-model); on a Mac, Clef-flash via MLX is closer to Jev ([how-to](docs/how-to.md#run-clef-flash-on-a-mac-with-mlx)) |
| `client-mapping` | `MappingTypeSafeClient` — maps a `@Noul`/`@Choice`/`@Score`-annotated record to/from `EvaluateRequest`/`EvaluateResponse` |
| `client-testkit` | `RecordingTypeSafeClient`/`FailingTypeSafeClient`, `TypeSafeClient` test doubles for unit tests |
| `bom` | dependency management for the modules above |
| `cli` | ad hoc checks from a terminal; runnable uber-jar under the `all` classifier, `java -jar` |
| `acceptance` | live-API tests only — not published |

Each module's directory, artifact (`typesafe-java-<module>`) and package
(`io.github.dfa1.typesafe.<module>`, dashes as dots) share one name. See
[ADR 0003](adr/0003-model-in-core-contract-in-client.md) for why it's split this way.

## Local models at a glance

Every model we tested behind the same `TypeSafeClient`, measured on an Apple M5 (32 GB). "Agrees with Jev"
compares answers with the real `jev-1.13.0` on 104 cached requests (yes/no on the same side of 0.5 · same choice).

| Model | Runs on | Download | Memory | Time per request (1–3 questions) | Agrees with Jev |
|---|---|---|---|---|---|
| TypeSafe API (`jev-1.13.0`) | `api.typesafe.ai` | — | — | ≈ 0.35 s (network included) | — |
| Laya fp32 | `client-local`, CPU | 1.7 GB | not measured | 55–165 ms | 85% · 64% |
| Laya fp16 | `client-local`, CPU | 0.85 GB | not measured | 2–3× slower than fp32 (same answers) | as fp32 |
| Qwen2.5-1.5B, 4-bit | `client-local`, CPU | 1.8 GB | not measured | ≈ 0.8 s per question | 80% · 68% |
| Clef-flash (9B), bf16 as published | `client-local`, CPU | 19 GB | 20 GB peak | ≈ 1 min | not measured |
| Clef-flash, 4-bit | `client-local`, CPU | 19 GB + 4.4 GB | 7.7 GB | 7–13 s | not measured |
| Clef-flash, 4-bit | `client-local`, Apple GPU (`loadOnGpu`) | 19 GB + 4.4 GB | 7.7 GB | 2–4 s | not measured |
| Clef-flash, MLX 4-bit | local MLX server, regular client | 6.2 GB | ≈ 7 GB | ≈ 0.55 s | 95% · 88% |

Laya is the fastest; Clef-flash is the closest to Jev, at the cost of size. The 4-bit ONNX graph keeps
Clef-flash's embeddings and output layer in Cloudflare's original bf16 files, so those 19 GB stay on disk next to it
(hard-linked, not copied); the MLX port doesn't need them. Setup for each: [run without the API](docs/how-to.md#run-without-the-api-on-a-local-model) and
[Clef-flash on a Mac with MLX](docs/how-to.md#run-clef-flash-on-a-mac-with-mlx).

## Docs

Structured by [Diataxis](https://diataxis.fr/):

- [Tutorial](docs/tutorial.md) — your first evaluation, end to end
- [How-to](docs/how-to.md) — task-oriented recipes: codecs, transports, testing, the CLI, ...
- [Reference](docs/reference.md) — full API surface
- [Explanation](docs/explanation.md) — design rationale

## Build

```bash
./mvnw clean verify                                # build + unit tests, all modules
./mvnw test -pl acceptance -am -DexcludedGroups=    # + live-API acceptance tests, needs ~/.typesafe.apikey
```

## License

MIT — see [LICENSE](LICENSE).

## Reference

- [Introduction](https://docs.typesafe.ai/introduction)
- [API reference](https://docs.typesafe.ai/api)
