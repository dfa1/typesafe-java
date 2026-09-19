# typesafe-java

Java 21 client for the [TypeSafe API](https://api.typesafe.ai). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

```java
TypesafeClient client = TypesafeClient.withDefaultToken();

EvaluateRequest request = EvaluateRequest.of(
        State.text("Help! My payouts have been failing for 3 days."),
        Map.of("is_urgent", Question.noul("Does this convey urgency?", Map.of())));

Answer.Noul answer = (Answer.Noul) client.evaluate(request).answers().get("is_urgent");
answer.noul(); // e.g. 0.92
```

## Install

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

Save a token to `~/.typesafe.apitoken` and you're set — see the [tutorial](docs/tutorial.md)
for the full walkthrough.

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
./mvnw clean install                               # build + unit tests, all modules
./mvnw test -pl acceptance -am -DexcludedGroups=    # + live-API acceptance tests, needs ~/.typesafe.apitoken
```

## License

MIT — see [LICENSE](LICENSE).
