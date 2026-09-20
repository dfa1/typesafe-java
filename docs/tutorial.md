# Tutorial: Your first TypeSafe evaluation

This tutorial walks you through asking the TypeSafe API a question about a piece of text and
reading back a typed answer. You'll end up with a small Java program that classifies how urgent
a support message is.

**Prerequisites:** Java 21+, Maven 3.9+, a TypeSafe API token.

---

## 1. Add the dependencies

Import the BOM, then add an HTTP transport (the JDK one, unless you have your own) plus one JSON
codec (Jackson 2 or Jackson 3 — pick whichever your project already uses). Both pull in
`typesafe-java-core`, which holds `TypesafeClient` itself, transitively:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.dfa1.typesafe-java</groupId>
      <artifactId>typesafe-java-bom</artifactId>
      <version>0.1.0</version>
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
echo "your-token-here" > ~/.typesafe.apitoken
```

`ApiKey.fromDefaultFile()` reads this file. (See
[how-to.md#provide-your-api-token](how-to.md#provide-your-api-token) for alternatives.)

## 3. Build the client

```java
import io.github.dfa1.typesafe.core.ApiKey;
import io.github.dfa1.typesafe.core.TypesafeClient;

TypesafeClient client = TypesafeClient.builder(ApiKey.fromDefaultFile()).build();
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
Answer.Noul answer = (Answer.Noul) response.answers().get("is_urgent");
System.out.println("urgency score: " + answer.noul());          // e.g. 0.92
System.out.println("input tokens: " + response.usage().inputTokens());
System.out.println("request id: " + response.metadata().requestId().value());
```

That's the whole round trip. From here:

- [how-to.md](how-to.md) for `Choice`/`Score` questions, async calls, and picking a codec.
- [reference.md](reference.md) for the full API surface.
- [explanation.md](explanation.md) for why the library is split the way it is.
