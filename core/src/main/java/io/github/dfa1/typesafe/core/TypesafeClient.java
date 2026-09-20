package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TypesafeClient implements AutoCloseable {

    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);

    private final HttpTransport transport;
    private final JsonCodec jsonCodec;
    private final ApiKey apiKey;
    private final URI endpoint;
    private final URI modelsEndpoint;
    private final int maxRetries;
    private final Duration initialBackoff;

    private TypesafeClient(ApiKey apiKey, HttpTransport transport, JsonCodec jsonCodec,
                            URI endpoint, int maxRetries, Duration initialBackoff) {
        this.apiKey = apiKey;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.endpoint = endpoint;
        this.modelsEndpoint = URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/v1/models");
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
    }

    public static Builder builder(ApiKey apiKey) {
        return new Builder(apiKey);
    }

    public static final class Builder {
        private final ApiKey apiKey;
        private HttpTransport transport;
        private JsonCodec jsonCodec;
        private URI endpoint = DEFAULT_ENDPOINT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;

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

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public TypesafeClient build() {
            HttpTransport resolvedTransport = transport != null ? transport : loadDefaultHttpTransport();
            JsonCodec resolvedCodec = jsonCodec != null ? jsonCodec : loadDefaultJsonCodec();
            return new TypesafeClient(apiKey, resolvedTransport, resolvedCodec, endpoint, maxRetries, initialBackoff);
        }

        private static HttpTransport loadDefaultHttpTransport() {
            return ServiceLoader.load(HttpTransport.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No HttpTransport found on the classpath. Add typesafe-java-jdk-http-client "
                                    + "as a dependency, or call Builder.httpTransport(...)."));
        }

        private static JsonCodec loadDefaultJsonCodec() {
            return ServiceLoader.load(JsonCodec.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No JsonCodec found on the classpath. Add typesafe-java-jackson2 or "
                                    + "typesafe-java-jackson3 as a dependency, or call Builder.jsonCodec(...)."));
        }
    }

    public EvaluateResponse evaluate(EvaluateRequest request) throws IOException, InterruptedException {
        return await(evaluateAsync(request));
    }

    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        Map<String, String> headers = requestHeaders();
        String body;
        try {
            body = jsonCodec.writeValueAsString(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return sendWithRetry(() -> transport.post(endpoint, headers, body), this::toEvaluateResponse, 0);
    }

    /** Lists the models available to the account. */
    public List<ModelDetails> listModels() throws IOException, InterruptedException {
        Map<String, String> headers = Map.of("Authorization", apiKey.toHttpHeaderValue());
        return await(sendWithRetry(() -> transport.get(modelsEndpoint, headers), this::toModelDetails, 0));
    }

    private <T> CompletableFuture<T> sendWithRetry(
            Supplier<CompletableFuture<HttpTransportResponse>> call, Function<HttpTransportResponse, T> decode, int attempt) {
        return call.get()
                .handle((response, error) -> error == null
                        ? handleResponse(response, call, decode, attempt)
                        : retryOnConnectionFailure(error, call, decode, attempt))
                .thenCompose(stage -> stage);
    }

    private <T> CompletableFuture<T> handleResponse(
            HttpTransportResponse response, Supplier<CompletableFuture<HttpTransportResponse>> call,
            Function<HttpTransportResponse, T> decode, int attempt) {
        int status = response.statusCode();

        if (status == 200) {
            try {
                return CompletableFuture.completedFuture(decode.apply(response));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        if (isRetryableStatus(status) && attempt < maxRetries) {
            return delayThenRetry(backoffFor(response, attempt), call, decode, attempt);
        }
        return CompletableFuture.failedFuture(new TypesafeException(status, response.body()));
    }

    private <T> CompletableFuture<T> retryOnConnectionFailure(
            Throwable error, Supplier<CompletableFuture<HttpTransportResponse>> call,
            Function<HttpTransportResponse, T> decode, int attempt) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof IOException && attempt < maxRetries) {
            return delayThenRetry(initialBackoff.multipliedBy(1L << attempt), call, decode, attempt);
        }
        return CompletableFuture.failedFuture(cause);
    }

    private <T> CompletableFuture<T> delayThenRetry(
            Duration backoff, Supplier<CompletableFuture<HttpTransportResponse>> call,
            Function<HttpTransportResponse, T> decode, int attempt) {
        return CompletableFuture
                .supplyAsync(() -> null, CompletableFuture.delayedExecutor(backoff.toMillis(), TimeUnit.MILLISECONDS))
                .thenCompose(ignored -> sendWithRetry(call, decode, attempt + 1));
    }

    /** Blocks on {@code future}, unwrapping {@link ExecutionException} back to its cause so a
     *  synchronous call surfaces the same exception an async one would fail with. */
    private static <T> T await(CompletableFuture<T> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error er) {
                throw er;
            }
            throw new IOException(cause);
        }
    }

    /** {@code 408}/{@code 429}/any {@code 5xx}: a client- or server-side hiccup worth retrying,
     *  as opposed to a request TypeSafe rejected outright (e.g. {@code 400}, {@code 401}). */
    static boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || (status >= 500 && status < 600);
    }

    /** Honors a {@code retry-after}/{@code retry-after-ms} response header when present, falling
     *  back to exponential backoff otherwise. Does not parse the HTTP-date form of
     *  {@code Retry-After}; that form falls back to exponential backoff too. */
    Duration backoffFor(HttpTransportResponse response, int attempt) {
        return retryAfter(response).orElseGet(() -> initialBackoff.multipliedBy(1L << attempt));
    }

    static Optional<Duration> retryAfter(HttpTransportResponse response) {
        return response.header("retry-after-ms").flatMap(TypesafeClient::parseNonNegativeLong).map(Duration::ofMillis)
                .or(() -> response.header("retry-after").flatMap(TypesafeClient::parseNonNegativeLong).map(Duration::ofSeconds));
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

    /** Closes the underlying {@link HttpTransport}, releasing any resources it holds. */
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
}
