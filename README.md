# typesafe-java

[![CI](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml/badge.svg)](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dfa1.typesafe-java/typesafe-java-core.svg)](https://central.sonatype.com/artifact/io.github.dfa1.typesafe-java/typesafe-java-core)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)

Java 21 client for the [TypeSafe API](https://api.typesafe.ai). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

Save a token to `~/.typesafe.apitoken` first — either way below picks it up automatically.

## Quickstart

### As a Java library

```java
ApiKey token = ApiKey.fromDefaultFile(); // reads ~/.typesafe.apitoken
// or: ApiKey.fromEnv();                 // reads the TYPESAFE_API_KEY environment variable
TypesafeClient client = TypesafeClient.builder(token).build();

EvaluateRequest request = EvaluateRequest.of(
        State.text("Help! My payouts have been failing for 3 days."),
        Map.of("is_urgent", Question.noul("Does this convey urgency?")));

Answer.Noul answer = client.evaluate(request).nouls().get("is_urgent");
answer.noul(); // e.g. 0.92
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
      <version>0.3.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-client-jdk</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-jackson3</artifactId>
  </dependency>
</dependencies>
```

## Modules

| Module | Contains |
|---|---|
| `core` | `TypesafeClient`, the DTOs, and the `JsonCodec`/`HttpTransport` SPIs |
| `client-jdk` | `HttpTransport` backed by `java.net.http` |
| `jackson2` / `jackson3` | `JsonCodec` backed by Jackson 2.x / 3.x |
| `bom` | dependency management for the modules above |
| `cli` | ad hoc checks from a terminal; runnable uber-jar under the `all` classifier, `java -jar` |
| `acceptance` | live-API tests only — not published |

See [ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md) for why it's split
this way.

## Docs

Structured by [Diataxis](https://diataxis.fr/):

- [Tutorial](docs/tutorial.md) — your first evaluation, end to end
- [How-to](docs/how-to.md) — task-oriented recipes: codecs, transports, testing, the CLI, ...
- [Reference](docs/reference.md) — full API surface
- [Explanation](docs/explanation.md) — design rationale

## Build

```bash
./mvnw clean verify                                # build + unit tests, all modules
./mvnw test -pl acceptance -am -DexcludedGroups=    # + live-API acceptance tests, needs ~/.typesafe.apitoken
```

## License

MIT — see [LICENSE](LICENSE).
