package io.github.dfa1.typesafe.transport;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HttpTransportResponse(int statusCode, Map<String, String> headers, byte[] body) {

    public Optional<String> header(String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpTransportResponse other)) {
            return false;
        }
        return statusCode == other.statusCode
                && Objects.equals(headers, other.headers)
                && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "HttpTransportResponse[statusCode=" + statusCode + ", headers=" + headers
                + ", body=" + body.length + " bytes]";
    }
}
