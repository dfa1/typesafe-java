package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
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
 *  {@link Builder#wrap(Function)} for those. */
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

    public static Builder builder(ApiKey apiKey) {
        return new Builder(apiKey);
    }

    @Override
    public EvaluateResponse evaluate(EvaluateRequest request) {
        return await(evaluateAsync(request));
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        Map<String, String> headers = requestHeaders();
        String body;
        try {
            body = jsonCodec.writeValueAsString(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return send(transport.post(endpoint, headers, body), this::toEvaluateResponse);
    }

    @Override
    public List<ModelDetails> listModels() {
        Map<String, String> headers = Map.of("Authorization", apiKey.toHttpHeaderValue());
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
                throw new CompletionException(new TypeSafeException.ResponseDecoding(response.body(), e));
            }
        });
    }

    /** Most specific {@link TypeSafeException} subclass for {@code status}, or the plain
     *  base class as a catch-all when no subclass matches. */
    private static TypeSafeException toException(int status, HttpTransportResponse response) {
        String body = response.body();
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
        headers.put("Authorization", apiKey.toHttpHeaderValue());
        headers.put("Content-Type", "application/json");
        return headers;
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

    public static final class Builder {
        private static final URI DEFAULT_ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");

        private final ApiKey apiKey;
        private HttpTransport transport;
        private JsonCodec jsonCodec;
        private URI endpoint = DEFAULT_ENDPOINT;
        private Function<TypeSafeClient, TypeSafeClient> decorators = Function.identity();

        private Builder(ApiKey apiKey) {
            this.apiKey = apiKey;
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

        /** Adds a decorator {@link #build()} wraps the client in, e.g.
         *  {@code RetryingTypeSafeClient.decorate()}. Each call wraps what the previous ones built,
         *  so the last added is outermost. {@code build()} returns a plain {@link TypeSafeClient};
         *  for a decorator whose own methods you need, pass it to {@link #build(Function)} instead. */
        public Builder wrap(Function<TypeSafeClient, ? extends TypeSafeClient> decorator) {
            this.decorators = decorators.andThen(decorator);
            return this;
        }

        public TypeSafeClient build() {
            HttpTransport resolvedTransport = transport != null ? transport : loadDefaultHttpTransport();
            JsonCodec resolvedCodec = jsonCodec != null ? jsonCodec : loadDefaultJsonCodec();
            return decorators.apply(new DefaultTypeSafeClient(apiKey, resolvedTransport, resolvedCodec, endpoint));
        }

        /** {@link #build()}, then applies {@code decorate} to the result as the outermost
         *  decorator (outside every {@link #wrap}), returning its own type — e.g.
         *  {@code builder(apiKey).build(MappingTypeSafeClient::decorate)}. */
        public <T extends TypeSafeClient> T build(Function<TypeSafeClient, T> decorate) {
            return decorate.apply(build());
        }

        private static HttpTransport loadDefaultHttpTransport() {
            return ServiceLoader.load(HttpTransport.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No HttpTransport found on the classpath. Add typesafe-java-client-jdk "
                                    + "as a dependency, or call Builder.httpTransport(...)."));
        }

        private static JsonCodec loadDefaultJsonCodec() {
            return ServiceLoader.load(JsonCodec.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No JsonCodec found on the classpath. Add typesafe-java-jackson2 or "
                                    + "typesafe-java-jackson3 as a dependency, or call Builder.jsonCodec(...)."));
        }
    }
}
