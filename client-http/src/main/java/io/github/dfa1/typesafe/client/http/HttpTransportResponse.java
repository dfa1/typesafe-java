package io.github.dfa1.typesafe.client.http;

import java.util.Map;
import java.util.Optional;

/** An HTTP response; {@code body} is the raw bytes, decoded by the {@code Codec} without a {@code String} in between. */
public record HttpTransportResponse(int statusCode, Map<String, String> headers, byte[] body) {

    public HttpTransportResponse {
        headers = Map.copyOf(headers);
    }

    public Optional<String> header(String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
