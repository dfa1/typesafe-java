package io.github.dfa1.typesafe.testkit;

import java.net.URI;
import java.util.Map;

/** One call {@link RecordingHttpTransport} received, in the order it arrived. */
public record RecordedRequest(String method, URI uri, Map<String, String> headers, String body) {

    public RecordedRequest {
        headers = Map.copyOf(headers);
    }
}
