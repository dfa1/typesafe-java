package io.github.dfa1.typesafe.testkit;

import io.github.dfa1.typesafe.transport.HttpTransport;
import io.github.dfa1.typesafe.transport.HttpTransportResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A {@link HttpTransport} test double for unit-testing code that calls {@code TypesafeClient}
 * without hitting the real API. Every call is appended to {@link #requests()} in the order it
 * arrived, so tests can assert on call count and order with a plain list assertion. Stub a
 * response with {@link #respond(HttpTransportResponse)} (matches any request) or
 * {@link #respondTo(Predicate, HttpTransportResponse)} (matches only requests satisfying the
 * predicate, e.g. by {@link RecordedRequest#uri()}); each stub is consumed by the first request
 * that matches it, so registering the same predicate twice yields its two responses in order —
 * handy for simulating a retry.
 */
public final class RecordingHttpTransport implements HttpTransport {

    private record Stub(Predicate<RecordedRequest> matcher, HttpTransportResponse response) {
    }

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Stub> stubs = new ArrayList<>();

    public RecordingHttpTransport respond(HttpTransportResponse response) {
        return respondTo(request -> true, response);
    }

    public RecordingHttpTransport respondTo(Predicate<RecordedRequest> matcher, HttpTransportResponse response) {
        synchronized (stubs) {
            stubs.add(new Stub(matcher, response));
        }
        return this;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public CompletableFuture<HttpTransportResponse> post(URI uri, Map<String, String> headers, String body) {
        return respondTo(new RecordedRequest("POST", uri, headers, body));
    }

    @Override
    public CompletableFuture<HttpTransportResponse> get(URI uri, Map<String, String> headers) {
        return respondTo(new RecordedRequest("GET", uri, headers, null));
    }

    @Override
    public void close() {
        // no resources to release
    }

    private CompletableFuture<HttpTransportResponse> respondTo(RecordedRequest request) {
        requests.add(request);
        synchronized (stubs) {
            for (int i = 0; i < stubs.size(); i++) {
                Stub stub = stubs.get(i);
                if (stub.matcher().test(request)) {
                    stubs.remove(i);
                    return CompletableFuture.completedFuture(stub.response());
                }
            }
        }
        return CompletableFuture.failedFuture(new AssertionError("No stubbed response for " + request));
    }
}
