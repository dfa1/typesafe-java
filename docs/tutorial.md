# Tutorial: Your first TypeSafe evaluation

This tutorial walks you through asking the TypeSafe API a question about a piece of text and
reading back a typed answer. You'll end up with a small Java program that classifies how urgent
a support message is.

**Prerequisites:** Java 21+, Maven 3.9+, a TypeSafe API token.

---

## 1. Add the dependencies

Import the BOM, then add an HTTP transport (the JDK one, unless you have your own) plus one JSON
codec (Jackson 2 or Jackson 3 — pick whichever your project already uses). Both pull in
`typesafe-java-core`, which holds `TypeSafeClient` itself, transitively (see the
[Maven Central badge](../README.md) for the latest version):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.dfa1.typesafe-java</groupId>
      <artifactId>typesafe-java-bom</artifactId>
      <version>0.5.0</version>
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
    <artifactId>typesafe-java-jackson2</artifactId>
  </dependency>
</dependencies>
```

## 2. Save your API token

```bash
echo "your-token-here" > ~/.typesafe.apikey
```

`ApiKey.fromDefaultFile()` reads this file. (See
[how-to.md#provide-your-api-token](how-to.md#provide-your-api-token) for alternatives.)

## 3. Build the client

```java
import io.github.dfa1.typesafe.core.ApiKey;
import io.github.dfa1.typesafe.core.TypeSafeClient;

TypeSafeClient client = TypeSafeClient.builder(ApiKey.fromDefaultFile()).build();
```

## 4. Ask a question

A `Question.noul` asks the model to score how strongly a statement holds, from 0.0 to 1.0:

```java
import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.Question;
import io.github.dfa1.typesafe.core.Answer;
import io.github.dfa1.typesafe.core.State;

import java.util.Map;

EvaluateRequest request = EvaluateRequest.of(
        State.text("Help! My payouts have been failing for 3 days."),
        Map.of("is_urgent", Question.noul("Does this convey urgency?",
                Map.of("true", "Explicitly time-sensitive", "false", "No urgency expressed"))));

EvaluateResponse response = client.evaluate(request);
```

## 5. Read the answer

```java
Answer.Noul answer = response.nouls().get("is_urgent");
System.out.println("urgency score: " + answer.noul());          // e.g. 0.92
System.out.println("input tokens: " + response.usage().inputTokens());
System.out.println("request id: " + response.metadata().requestId().value());
```

## 6. Optional: get a typed record back instead

Add `typesafe-java-mapping` alongside the BOM, and the same round trip becomes one typed method
call — no question map to build, no `nouls()`/cast to read back:

```xml
<dependency>
  <groupId>io.github.dfa1.typesafe-java</groupId>
  <artifactId>typesafe-java-mapping</artifactId>
</dependency>
```

```java
import io.github.dfa1.typesafe.mapping.MappingTypeSafeClient;
import io.github.dfa1.typesafe.mapping.Noul;

record UrgencyCheck(@Noul("Does this convey urgency?") double isUrgent) {
}

MappingTypeSafeClient typedClient = TypeSafeClient.builder(ApiKey.fromDefaultFile())
        .build(MappingTypeSafeClient::decorate);

UrgencyCheck result = typedClient.evaluateTyped(
        State.text("Help! My payouts have been failing for 3 days."), UrgencyCheck.class);
System.out.println("urgency score: " + result.isUrgent());
```

`isUrgent` is populated straight from `Answer.Noul#noul()` — the record's field name is the
question's key. See [how-to.md](how-to.md#get-typed-answers-instead-of-mapstring-answer) for
`@Choice`/`@Score` annotations too.

That's the whole round trip, either way. From here:

- [how-to.md](how-to.md) for `Choice`/`Score` questions, async calls, and picking a codec.
- [reference.md](reference.md) for the full API surface.
- [explanation.md](explanation.md) for why the library is split the way it is.
