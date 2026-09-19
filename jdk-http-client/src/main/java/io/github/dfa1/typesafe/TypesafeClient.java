package io.github.dfa1.typesafe;

import io.github.dfa1.typesafe.json.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class TypesafeClient {

    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    private static final int MAX_RETRIES = 5;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private final HttpClient http;
    private final JsonCodec jsonCodec;
    private final ApiToken apiToken;

    private TypesafeClient(ApiToken apiToken, HttpClient http, JsonCodec jsonCodec) {
        this.apiToken = apiToken;
        this.http = http;
        this.jsonCodec = jsonCodec;
    }

    public static Builder builder(ApiToken apiToken) {
        return new Builder(apiToken);
    }

    public static TypesafeClient withDefaultToken() throws IOException {
        return builder(ApiToken.fromDefaultFile()).build();
    }

    public static final class Builder {
        private final ApiToken apiToken;
        private HttpClient http = HttpClient.newHttpClient();
        private JsonCodec jsonCodec;

        private Builder(ApiToken apiToken) {
            this.apiToken = apiToken;
        }

        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        public Builder jsonCodec(JsonCodec jsonCodec) {
            this.jsonCodec = jsonCodec;
            return this;
        }

        public TypesafeClient build() {
            JsonCodec codec = jsonCodec != null ? jsonCodec : loadDefaultJsonCodec();
            return new TypesafeClient(apiToken, http, codec);
        }

        private static JsonCodec loadDefaultJsonCodec() {
            return ServiceLoader.load(JsonCodec.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No JsonCodec found on the classpath. Add typesafe-jackson2 or "
                                    + "typesafe-jackson3 as a dependency, or call Builder.jsonCodec(...)."));
        }
    }

    public EvaluateResponse evaluate(EvaluateRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = buildHttpRequest(request);

        for (int attempt = 0; ; attempt++) {
            HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();

            if (status == 200) {
                return toEvaluateResponse(response);
            }
            if ((status == 429 || status == 529) && attempt < MAX_RETRIES) {
                Thread.sleep(INITIAL_BACKOFF.multipliedBy(1L << attempt));
                continue;
            }
            throw new TypesafeException(status, new String(response.body(), StandardCharsets.UTF_8));
        }
    }

    public CompletableFuture<EvaluateResponse> evaluateAsync(EvaluateRequest request) {
        HttpRequest httpRequest;
        try {
            httpRequest = buildHttpRequest(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return evaluateAsync(httpRequest, 0);
    }

    private CompletableFuture<EvaluateResponse> evaluateAsync(HttpRequest httpRequest, int attempt) {
        return http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    int status = response.statusCode();

                    if (status == 200) {
                        try {
                            return CompletableFuture.completedFuture(toEvaluateResponse(response));
                        } catch (RuntimeException e) {
                            return CompletableFuture.<EvaluateResponse>failedFuture(e);
                        }
                    }
                    if ((status == 429 || status == 529) && attempt < MAX_RETRIES) {
                        Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << attempt);
                        return CompletableFuture
                                .supplyAsync(() -> null,
                                        CompletableFuture.delayedExecutor(backoff.toMillis(), TimeUnit.MILLISECONDS))
                                .thenCompose(ignored -> evaluateAsync(httpRequest, attempt + 1));
                    }
                    return CompletableFuture.failedFuture(
                            new TypesafeException(status, new String(response.body(), StandardCharsets.UTF_8)));
                });
    }

    private HttpRequest buildHttpRequest(EvaluateRequest request) {
        return HttpRequest.newBuilder(ENDPOINT)
                .header("Authorization", apiToken.toHttpHeaderValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonCodec.writeValueAsBytes(request)))
                .build();
    }

    private EvaluateResponse toEvaluateResponse(HttpResponse<byte[]> response) {
        EvaluateResponse body = jsonCodec.readValue(response.body(), EvaluateResponse.class);
        EvaluateResponse.Metadata metadata = new EvaluateResponse.Metadata(
                response.headers().firstValue("x-typesafe-request-id").map(RequestId::new).orElse(null),
                response.headers().firstValueAsLong("x-envoy-upstream-service-time")
                        .stream().mapToObj(Duration::ofMillis).findFirst().orElse(null));
        return new EvaluateResponse(body.model(), body.answers(), body.usage(), metadata);
    }
}
