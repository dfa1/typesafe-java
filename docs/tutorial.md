# Tutorial: Your first TypeSafe evaluation

This tutorial walks you through asking the TypeSafe API a question about a piece of text and
reading back a typed answer. You'll end up with a small Java program that classifies how urgent
a support message is.

**Prerequisites:** Java 21+, Maven 3.9+, a TypeSafe API token.

---

## 1. Add the dependencies

Import the BOM, then add the client plus one JSON codec (Jackson 2 or Jackson 3 — pick whichever
your project already uses):

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
    <artifactId>typesafe-java-jackson2</artifactId>
  </dependency>
</dependencies>
```

## 2. Save your API token

```bash
echo "your-token-here" > ~/.typesafe.apitoken
```

`TypesafeClient.withDefaultToken()` reads this file. (See
[how-to.md#provide-your-api-token](how-to.md#provide-your-api-token) for alternatives.)

## 3. Build the client

```java
import io.github.dfa1.typesafe.TypesafeClient;

TypesafeClient client = TypesafeClient.withDefaultToken();
```

## 4. Ask a question

A `Question.noul` asks the model to score how strongly a statement holds, from 0.0 to 1.0:

```java
import io.github.dfa1.typesafe.EvaluateRequest;
import io.github.dfa1.typesafe.EvaluateResponse;
import io.github.dfa1.typesafe.Question;
import io.github.dfa1.typesafe.Answer;

import java.util.Map;

EvaluateRequest request = EvaluateRequest.of(
        "Help! My payouts have been failing for 3 days.",
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
