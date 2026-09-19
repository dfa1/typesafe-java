package ai.typesafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class TypesafeClient {

    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    private static final int MAX_RETRIES = 5;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ApiToken apiToken;

    private TypesafeClient(ApiToken apiToken, HttpClient http) {
        this.apiToken = apiToken;
        this.http = http;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
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

        private Builder(ApiToken apiToken) {
            this.apiToken = apiToken;
        }

        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        public TypesafeClient build() {
            return new TypesafeClient(apiToken, http);
        }
    }

    public EvaluateResponse evaluate(EvaluateRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(ENDPOINT)
                .header("Authorization", apiToken.toHttpHeaderValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request)))
                .build();

        for (int attempt = 0; ; attempt++) {
            HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();

            if (status == 200) {
                return mapper.readValue(response.body(), EvaluateResponse.class);
            }
            if ((status == 429 || status == 529) && attempt < MAX_RETRIES) {
                Thread.sleep(INITIAL_BACKOFF.multipliedBy(1L << attempt));
                continue;
            }
            throw new TypesafeException(status, new String(response.body(), StandardCharsets.UTF_8));
        }
    }
}
