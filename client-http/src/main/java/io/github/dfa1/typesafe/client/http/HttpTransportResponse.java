package io.github.dfa1.typesafe.client.http;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
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

    // a record compares and prints an array by identity; compare the body by content instead
    @Override
    public boolean equals(Object o) {
        return o instanceof HttpTransportResponse r && statusCode == r.statusCode && headers.equals(r.headers)
                && Arrays.equals(body, r.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "HttpTransportResponse[statusCode=" + statusCode + ", headers=" + headers + ", body=" + body.length + " bytes]";
    }
}
