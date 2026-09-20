package io.github.dfa1.typesafe.core;

import io.github.dfa1.typesafe.json.JsonCodec;
import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class TypesafeClient {

    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);

    private final HttpTransport transport;
    private final JsonCodec jsonCodec;
    private final ApiToken apiToken;
    private final URI endpoint;
    private final int maxRetries;
    private final Duration initialBackoff;

    private TypesafeClient(ApiToken apiToken, HttpTransport transport, JsonCodec jsonCodec,
                            URI endpoint, int maxRetries, Duration initialBackoff) {
        this.apiToken = apiToken;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.endpoint = endpoint;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
    }

    public static Builder builder(ApiToken apiToken) {
        return new Builder(apiToken);
    }

    public static final class Builder {
        private final ApiToken apiToken;
        private HttpTransport transport;
        private JsonCodec jsonCodec;
        private URI endpoint = DEFAULT_ENDPOINT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;

        private Builder(ApiToken apiToken) {
            this.apiToken = apiToken;
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
            return new TypesafeClient(apiToken, resolvedTransport, resolvedCodec, endpoint, maxRetries, initialBackoff);
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
        Map<String, String> headers = requestHeaders();
        String body = new String(jsonCodec.writeValueAsBytes(request), StandardCharsets.UTF_8);

        for (int attempt = 0; ; attempt++) {
            HttpTransportResponse response = transport.post(endpoint, headers, body);
            int status = response.statusCode();

            if (status == 200) {
                return toEvaluateResponse(response);
            }
            if ((status == 429 || status == 529) && attempt < maxRetries) {
                Thread.sleep(initialBackoff.multipliedBy(1L << attempt).toMillis());
                continue;
            }
            throw new TypesafeException(status, response.body());
        }
    }

    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        Map<String, String> headers = requestHeaders();
        String body;
        try {
            body = new String(jsonCodec.writeValueAsBytes(request), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return evaluateAsync(headers, body, 0);
    }

    private CompletableFuture<EvaluateResponse> evaluateAsync(Map<String, String> headers, String body, int attempt) {
        return transport.postAsync(endpoint, headers, body)
                .thenCompose(response -> {
                    int status = response.statusCode();

                    if (status == 200) {
                        try {
                            return CompletableFuture.completedFuture(toEvaluateResponse(response));
                        } catch (RuntimeException e) {
                            return CompletableFuture.failedFuture(e);
                        }
                    }
                    if ((status == 429 || status == 529) && attempt < maxRetries) {
                        Duration backoff = initialBackoff.multipliedBy(1L << attempt);
                        return CompletableFuture
                                .supplyAsync(() -> null,
                                        CompletableFuture.delayedExecutor(backoff.toMillis(), TimeUnit.MILLISECONDS))
                                .thenCompose(ignored -> evaluateAsync(headers, body, attempt + 1));
                    }
                    return CompletableFuture.failedFuture(new TypesafeException(status, response.body()));
                });
    }

    private Map<String, String> requestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", apiToken.toHttpHeaderValue());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private EvaluateResponse toEvaluateResponse(HttpTransportResponse response) {
        EvaluateResponse body = jsonCodec.readValue(response.body().getBytes(StandardCharsets.UTF_8), EvaluateResponse.class);
        EvaluateResponse.Metadata metadata = new EvaluateResponse.Metadata(
                response.header("x-typesafe-request-id").map(RequestId::new).orElse(null),
                response.header("x-envoy-upstream-service-time")
                        .map(Long::parseLong).map(Duration::ofMillis).orElse(null));
        return new EvaluateResponse(body.model(), body.answers(), body.usage(), metadata);
    }
}
