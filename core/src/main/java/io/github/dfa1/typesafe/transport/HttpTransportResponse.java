package io.github.dfa1.typesafe.transport;

import java.util.Map;
import java.util.Optional;

public record HttpTransportResponse(int statusCode, Map<String, String> headers, byte[] body) {

    public Optional<String> header(String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
