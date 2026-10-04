package io.github.dfa1.typesafe.client.http;

import io.github.dfa1.typesafe.core.EvaluateRequest;
import io.github.dfa1.typesafe.core.EvaluateResponse;
import io.github.dfa1.typesafe.core.ModelDetails;
import io.github.dfa1.typesafe.core.RequestId;
import io.github.dfa1.typesafe.client.RetryingTypeSafeClient;
import io.github.dfa1.typesafe.client.TypeSafeClient;
import io.github.dfa1.typesafe.client.TypeSafeException;
import io.github.dfa1.typesafe.core.JsonCodec;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** The only {@link TypeSafeClient} implementation this library ships; built via {@link #builder}.
 *  One request, one response, no retries — add {@link RetryingTypeSafeClient} via
 *  {@link Builder#decorateWith(Function)} for those. */
public final class DefaultTypeSafeClient implements TypeSafeClient {

    private final HttpTransport transport;
    private final JsonCodec jsonCodec;
    private final ApiKey apiKey;
    private final URI endpoint;
    private final URI modelsEndpoint;

    private DefaultTypeSafeClient(ApiKey apiKey, HttpTransport transport, JsonCodec jsonCodec, URI endpoint) {
        this.apiKey = apiKey;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.endpoint = endpoint;
        this.modelsEndpoint = URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/v1/models");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        return await(evaluateAsync(request));
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        Map<String, String> headers = requestHeaders();
        byte[] body;
        try {
            body = jsonCodec.writeValueAsBytes(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return send(transport.post(endpoint, headers, body), this::toEvaluateResponse);
    }

    @Override
    public List<ModelDetails> listModels() {
        Map<String, String> headers = new LinkedHashMap<>();
        authorize(headers);
        return await(send(transport.get(modelsEndpoint, headers), this::toModelDetails));
    }

    private <T> CompletableFuture<T> send(
            CompletableFuture<HttpTransportResponse> call, Function<HttpTransportResponse, T> decode) {
        return call.handle((response, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                throw new CompletionException(cause instanceof IOException io ? toConnectionException(io) : cause);
            }
            if (response.statusCode() != 200) {
                throw new CompletionException(toException(response.statusCode(), response));
            }
            try {
                return decode.apply(response);
            } catch (RuntimeException e) {
                throw new CompletionException(new TypeSafeException.ResponseDecoding(text(response), e));
            }
        });
    }

    /** The body as text, for an error message; the success path never builds a {@code String}. */
    private static String text(HttpTransportResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Most specific {@link TypeSafeException} subclass for {@code status}, or the plain
     *  base class as a catch-all when no subclass matches. */
    private static TypeSafeException toException(int status, HttpTransportResponse response) {
        String body = text(response);
        return switch (status) {
            case 400 -> new TypeSafeException.BadRequest(body);
            case 401 -> new TypeSafeException.Authentication(body);
            case 403 -> new TypeSafeException.PermissionDenied(body);
            case 404 -> new TypeSafeException.NotFound(body);
            case 422 -> new TypeSafeException.UnprocessableEntity(body);
            case 429 -> new TypeSafeException.RateLimit(body, retryAfter(response).orElse(null));
            default -> status >= 500 && status < 600
                    ? new TypeSafeException.InternalServer(status, body, retryAfter(response).orElse(null))
                    : new TypeSafeException(status, body);
        };
    }

    /** Wraps a transport's raw {@link IOException} as
     *  {@link TypeSafeException.Timeout} when it's one of the JDK's timeout exception types,
     *  {@link TypeSafeException.Connection} otherwise. */
    private static TypeSafeException.Connection toConnectionException(IOException cause) {
        return cause instanceof HttpTimeoutException || cause instanceof InterruptedIOException
                ? new TypeSafeException.Timeout(cause)
                : new TypeSafeException.Connection(cause);
    }

    /** Blocks on {@code future}, unwrapping {@link ExecutionException} back to its cause so a
     *  synchronous call surfaces the same exception an async one would fail with. An
     *  {@link InterruptedException} while waiting becomes {@link TypeSafeException.Interrupted},
     *  restoring the thread's interrupt status first. */
    static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error er) {
                throw er;
            }
            throw new TypeSafeException.Connection(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeException.Interrupted(e);
        }
    }

    /** The {@code retry-after-ms}/{@code retry-after} response header, preferring the former.
     *  Doesn't parse the HTTP-date form of {@code Retry-After}; that form is empty. */
    static Optional<Duration> retryAfter(HttpTransportResponse response) {
        return response.header("retry-after-ms").flatMap(DefaultTypeSafeClient::parseNonNegativeLong).map(Duration::ofMillis)
                .or(() -> response.header("retry-after").flatMap(DefaultTypeSafeClient::parseNonNegativeLong).map(Duration::ofSeconds));
    }

    private static Optional<Long> parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    record ModelsResponse(List<ModelDetails> models) {
    }

    @Override
    public void close() {
        transport.close();
    }

    private Map<String, String> requestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        authorize(headers);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /** No key, no header: a local TypeSafe-compatible server needs none. */
    private void authorize(Map<String, String> headers) {
        if (apiKey != null) {
            headers.put("Authorization", apiKey.toHttpHeaderValue());
        }
    }

    private EvaluateResponse toEvaluateResponse(HttpTransportResponse response) {
        EvaluateResponse body = jsonCodec.readValue(response.body(), EvaluateResponse.class);
        EvaluateResponse.Metadata metadata = new EvaluateResponse.Metadata(
                response.header("x-typesafe-request-id").map(RequestId::new).orElse(null),
                response.header("x-envoy-upstream-service-time")
                        .map(Long::parseLong).map(Duration::ofMillis).orElse(null));
        return new EvaluateResponse(body.model(), body.answers(), body.usage(), metadata);
    }

    private List<ModelDetails> toModelDetails(HttpTransportResponse response) {
        return jsonCodec.readValue(response.body(), ModelsResponse.class).models();
    }

    /**
     * Configures and builds a {@link TypeSafeClient}. Every setting is optional: the transport and
     * codec are discovered via {@link ServiceLoader} when not set, the endpoint defaults to
     * {@code https://api.typesafe.ai/v1/systemone}, and without an {@link #apiKey} no
     * {@code Authorization} header is sent (TypeSafe's API then answers 401, which
     * {@link TypeSafeException.Authentication} reports).
     *
     * <p>The client {@link #build()} returns makes exactly one attempt per call. Anything beyond
     * that — retries, deadlines, caching, metrics — is a decorator, added with
     * {@link #decorateWith}:
     *
     * <pre>{@code
     * MappingTypeSafeClient client = DefaultTypeSafeClient.builder().apiKey(apiKey)
     *         .decorateWith(RetryingTypeSafeClient::decorate)
     *         .decorateWith(c -> DeadlineTypeSafeClient.decorate(c, Duration.ofSeconds(20)))
     *         .build(MappingTypeSafeClient::decorate);
     * }</pre>
     */
    public static final class Builder {
        private static final URI DEFAULT_ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");

        private ApiKey apiKey;
        private HttpTransport transport;
        private JsonCodec jsonCodec;
        private URI endpoint = DEFAULT_ENDPOINT;
        private final List<Function<TypeSafeClient, ? extends TypeSafeClient>> decorators = new ArrayList<>();

        private Builder() {
        }

        public Builder apiKey(ApiKey apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder httpTransport(HttpTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder jsonCodec(JsonCodec jsonCodec) {
            this.jsonCodec = jsonCodec;
            return this;
        }

        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Adds a decorator around the client {@link #build()} creates. Each call adds rather than
         * replaces: the decorator goes around everything added before it, so the <em>last</em> one
         * added is the <em>outermost</em> — the first to see a call, the last to see its result.
         *
         * <pre>{@code
         * builder().apiKey(apiKey)
         *         .decorateWith(RetryingTypeSafeClient::decorate)                                  // inner
         *         .decorateWith(c -> DeadlineTypeSafeClient.decorate(c, Duration.ofSeconds(20)))  // outer
         *         .build();
         * // Deadline( Retrying( DefaultTypeSafeClient ) ): 20s for the whole call, retries included
         * }</pre>
         *
         * <p>Order changes behavior. Swap the two above and the deadline bounds each attempt
         * instead, and a timed-out attempt is retried. Likewise, a metrics decorator outside
         * retrying counts logical calls, inside it counts attempts. The only order that's always
         * wrong — {@link RetryingTypeSafeClient} added twice, multiplying the attempts — is
         * rejected by {@link #build()}.
         *
         * <p>{@code build()} returns a plain {@link TypeSafeClient}, so a decorator added here is
         * only reachable through that interface. For one whose own methods you need (e.g.
         * {@code MappingTypeSafeClient#evaluateTyped}), pass it to {@link #build(Function)}
         * instead: it goes outermost and keeps its type.
         *
         * @param decorator takes the client built so far and returns it decorated, e.g.
         *                  {@code RetryingTypeSafeClient::decorate}
         * @return this builder
         */
        public Builder decorateWith(Function<TypeSafeClient, ? extends TypeSafeClient> decorator) {
            decorators.add(decorator);
            return this;
        }

        /**
         * Builds the client, applying every {@link #decorateWith} decorator in the order added.
         *
         * @throws IllegalStateException if no {@link HttpTransport} or {@link JsonCodec} is set or
         *         discoverable, or if more than one {@link RetryingTypeSafeClient} was added (the
         *         attempts would multiply)
         */
        public TypeSafeClient build() {
            return build(Function.<TypeSafeClient>identity());
        }

        /**
         * {@link #build()}, then applies {@code decorate} as the outermost decorator — outside
         * every {@link #decorateWith} one — and returns its own type, so its extra methods need no
         * cast:
         *
         * <pre>{@code
         * MappingTypeSafeClient client = builder().apiKey(apiKey).build(MappingTypeSafeClient::decorate);
         * }</pre>
         *
         * @throws IllegalStateException as {@link #build()}; a {@link RetryingTypeSafeClient}
         *         passed here counts toward the at-most-once check too
         */
        public <T extends TypeSafeClient> T build(Function<TypeSafeClient, T> decorate) {
            HttpTransport resolvedTransport = transport != null ? transport : loadDefaultHttpTransport();
            JsonCodec resolvedCodec = jsonCodec != null ? jsonCodec : loadDefaultJsonCodec();
            TypeSafeClient client = new DefaultTypeSafeClient(apiKey, resolvedTransport, resolvedCodec, endpoint);
            int retrying = 0;
            for (Function<TypeSafeClient, ? extends TypeSafeClient> decorator : decorators) {
                client = decorator.apply(client);
                retrying += client instanceof RetryingTypeSafeClient ? 1 : 0;
            }
            T result = decorate.apply(client);
            retrying += result != client && result instanceof RetryingTypeSafeClient ? 1 : 0;
            if (retrying > 1) {
                result.close();
                throw new IllegalStateException(
                        "RetryingTypeSafeClient added " + retrying + " times; the attempts would multiply. Add it once.");
            }
            return result;
        }

        private static HttpTransport loadDefaultHttpTransport() {
            return ServiceLoader.load(HttpTransport.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No HttpTransport found on the classpath. Add typesafe-java-client-http-jdk "
                                    + "as a dependency, or call Builder.httpTransport(...)."));
        }

        private static JsonCodec loadDefaultJsonCodec() {
            return ServiceLoader.load(JsonCodec.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No JsonCodec found on the classpath. Add typesafe-java-codec-jackson2 or "
                                    + "typesafe-java-codec-jackson3 as a dependency, or call Builder.jsonCodec(...)."));
        }
    }
}
