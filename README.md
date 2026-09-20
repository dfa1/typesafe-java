# typesafe-java

[![CI](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml/badge.svg)](https://github.com/dfa1/typesafe-java/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dfa1_typesafe-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dfa1_typesafe-java)

Java 21 client for the [TypeSafe API](https://api.typesafe.ai). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

Save a token to `~/.typesafe.apitoken` first — either way below picks it up automatically.

## Quickstart

### As a Java library

```java
ApiKey token = ApiKey.fromDefaultFile(); // reads ~/.typesafe.apitoken
TypesafeClient client = TypesafeClient.builder(token).build();

EvaluateRequest request = EvaluateRequest.of(
        State.text("Help! My payouts have been failing for 3 days."),
        Map.of("is_urgent", Question.noul("Does this convey urgency?")));

Answer.Noul answer = (Answer.Noul) client.evaluate(request).answers().get("is_urgent");
answer.noul(); // e.g. 0.92
```

See [Install](#install) below to add it as a dependency, or the [tutorial](docs/tutorial.md)
for the full walkthrough.

### From the command line

No Java coding required — the `cli` module builds a self-contained jar, handy for wiring a
check into a Jenkins job, a shell script, or any other CI pipeline without writing a line of
Java:

```bash
./mvnw -pl cli -am package -DskipTests
java -jar cli/target/typesafe-java-cli-*.jar \
        --state "My card was charged twice." \
        --noul "urgent=Is this urgent?" \
        --min "urgent=0.5" || echo "not urgent enough"
```

Prints the answer as JSON and exits `1` if `--min`'s threshold isn't met, so it doubles as a
pass/fail gate. See [how-to.md](docs/how-to.md#run-a-quick-check-from-the-command-line) for the
full flag reference (`--choice`/`--score`, `--print`, `--verbose`, ...). Not published as a
library artifact — build it locally as shown above.

## Install

Maven, via the BOM:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.dfa1.typesafe-java</groupId>
      <artifactId>typesafe-java-bom</artifactId>
      <version>0.1-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-jdk-http-client</artifactId>
  </dependency>
  <dependency>
    <groupId>io.github.dfa1.typesafe-java</groupId>
    <artifactId>typesafe-java-jackson3</artifactId>
  </dependency>
</dependencies>
```

Not yet on Maven Central — `0.1-SNAPSHOT` builds from source (`./mvnw install`) until the first
release ships.

## Modules

| Module | Contains |
|---|---|
| `core` | `TypesafeClient`, the DTOs, and the `JsonCodec`/`HttpTransport` SPIs |
| `jdk-http-client` | `HttpTransport` backed by `java.net.http` |
| `jackson2` / `jackson3` | `JsonCodec` backed by Jackson 2.x / 3.x |
| `bom` | dependency management for the modules above |
| `cli` | executable uber-jar for ad hoc checks from a terminal — not published |
| `acceptance` | live-API tests only — not published |

See [ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md) for why it's split
this way.

## Docs

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
