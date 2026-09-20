# CLAUDE.md

Guidance for Claude Code working in this repository.

## What it is

Java 21 client for the TypeSafe API (`https://api.typesafe.ai`). Send a state (any JSON value)
plus a set of `Noul`/`Choice`/`Score` questions, get back typed answers.

## Module structure

```
core      — TypesafeClient, ApiKey, TypesafeException, and the wire DTOs (Answer, Question,
            State, EvaluateRequest/EvaluateResponse, Usage, RequestId, RequestModel/Model), all in
            io.github.dfa1.typesafe.core; plus the JsonCodec (io.github.dfa1.typesafe.json) +
            HttpTransport (io.github.dfa1.typesafe.transport) SPIs. Zero dependency on any
            JSON or HTTP library — TypesafeClient talks to HttpTransport/JsonCodec, never to a
            concrete library directly, so the DTOs + JsonCodec alone are reusable (e.g. by a
            Kafka producer/consumer) without pulling in TypesafeClient's HTTP concerns.
jdk-http-client — HttpTransport backed by java.net.http (artifact
            typesafe-java-jdk-http-client, class JdkHttpTransport, package
            io.github.dfa1.typesafe.jdk). Depends only on core. Discovered via
            ServiceLoader at Builder.build() time (or an explicit
            Builder.httpTransport(...) override).
jackson2  — JsonCodec backed by Jackson 2.x. Depends only on core. Owns the `type`
            discriminator for Answer/Question via private Jackson mixins (addMixIn); State
            (no discriminator — string/object/array on the wire) via a custom serializer,
            registered through META-INF/services.
jackson3  — same, backed by Jackson 3.x (tools.jackson.databind).
bom       — dependency-management POM listing core/jdk-http-client/jackson2/jackson3.
acceptance — live-API tests only; not published. `AbstractTypesafeClientAcceptanceTest`
            holds every test method; one concrete subclass per HttpTransport/JsonCodec
            combination (`JdkHttpClientWithJackson2AcceptanceTest`,
            `JdkHttpClientWithJackson3AcceptanceTest`) supplies the pair via two abstract
            hooks, explicitly constructing the codec/transport (`new Jackson2Codec()`, ...)
            rather than relying on ServiceLoader, since this module deliberately has more
            than one of each on its test classpath at once. jackson2 and jackson3 both pull
            in `com.fasterxml.jackson.core:jackson-annotations` transitively at different
            versions (2.17.2 vs 2.20); acceptance/pom.xml pins the newer one explicitly, or
            Maven's mediation picks the older one and jackson3 fails at runtime with
            `NoSuchFieldError` on a field only the newer annotations jar has.
cli       — command-line entry point (`Main`), not published as a library artifact; built as
            an executable uber-jar (maven-shade-plugin) over jdk-http-client + jackson3.
            Flat flags: `--state <text>` (the only `State` shape it supports — plain text),
            repeatable `--noul`/`--choice`/`--score <name>=<instructions>[|opt1,opt2,...]`,
            optional `--model <id>`, `--verbose`/`--timing` (request id / response time to
            stderr), `--version` (prints the jar's `Implementation-Version` manifest entry,
            set by the shade plugin, and exits without calling the API). Prints the
            `EvaluateResponse` as JSON to stdout.
```

Dependency rule: `jdk-http-client → core`, `jackson2 → core`, `jackson3 → core`,
`acceptance → core, jdk-http-client, jackson2, jackson3` (test scope only), `cli → core,
jdk-http-client, jackson3` — nothing production depends on `acceptance` or `cli`. See
[ADR 0001](adr/0001-multi-module-layout-with-pluggable-json-codec.md) for why the SPIs
exist at all.

## Commands

```bash
./mvnw clean verify                                                              # build + unit tests, all modules
./mvnw test -pl jackson2 -am                                                     # one module (+ its dependencies)
./mvnw test -pl jackson2 -am -Dtest=Jackson2CodecTest -Dsurefire.failIfNoSpecifiedTests=false
```

No step here uses `install` — a routine build has no reason to write into `~/.m2/repository`.
`-am` ("also make") rebuilds a module's dependencies within the same reactor run instead of
resolving them from the local repo, so a single-module command works right after a fresh clone.
`-Dsurefire.failIfNoSpecifiedTests=false` is only needed alongside a `-Dtest=` filter + `-am`:
without it, surefire errors on the upstream modules `-am` rebuilds that don't contain the
named test class.

Acceptance tests (in `acceptance`, one concrete class per HttpTransport/JsonCodec
combination) are `@Tag("acceptance")`, hit the real TypeSafe API, and need a token at
`~/.typesafe.apitoken`. Excluded from a routine `./mvnw test` via the `excludedGroups=acceptance`
property (surefire). Opt in with:

```bash
./mvnw test -pl acceptance -am -DexcludedGroups=
```

## Design decisions

- **`core` has zero Jackson dependency and the DTOs carry zero Jackson annotations.**
  Polymorphism (`Answer`/`Question`'s `type` discriminator) is wired up entirely inside
  each codec module via mixins, not on the DTOs. Adding a third JSON library means
  adding one more codec module; `core`/`jdk-http-client` don't change.
- **`JsonCodec` is discovered via `ServiceLoader`, not a hard compile dependency.** A
  consumer that depends on `jdk-http-client` but forgets a codec module gets a clear
  `IllegalStateException` from `Builder.build()`, not a `NoClassDefFoundError`.
- **Small public API.** Don't expose internals — when in doubt, leave it out or make it
  package-private.

## Testing

JUnit 5 + AssertJ (`assertThat(...)`, not JUnit's `Assertions.assertEquals`/`assertTrue`) +
Mockito (BDDMockito: static-import only `given`/`then`, e.g. `given(mock.m()).willReturn(v)` /
`then(mock).should().m()` — never `willReturn`/`willThrow`/`verify` unqualified).
Prefer testing behavior through the real classes involved (e.g.
`Jackson2CodecTest`/`Jackson3CodecTest` exercise the codec, not a bare `ObjectMapper`) —
this is what caught that Jackson 3's builder API differs from Jackson 2's mutable
`ObjectMapper` during the initial split. `TypesafeClientTest` mocks `HttpTransport`/`JsonCodec`
to verify `TypesafeClient` calls the SPIs correctly, without a real HTTP round trip. Every test
has `// Given` / `// When` / `// Then` comments marking its three phases (omit `// Given` when
there's nothing to arrange). The pre-built instance a test invokes behavior on is named `sut`
(e.g. a `Jackson2Codec` field, or an object constructed in `// Given` that `// When` calls a
method on); the value produced by the operation under test in `// When` is named `result`. A
static factory call with nothing further invoked on it just produces `result` — there's no
separate `sut`.

## Documentation is part of every change

Docs live under `docs/`, structured by [Diataxis](https://diataxis.fr/):
`tutorial.md` (learning-oriented walkthrough), `how-to.md` (task-oriented recipes),
`reference.md` (API surface), `explanation.md` (design rationale). A change to the
public API, module structure, or a documented behavior updates whichever of these
apply, in the same commit — plus `CHANGELOG.md` under `[Unreleased]`. `adr/` and
released `CHANGELOG.md` sections are exempt — they describe the past.
