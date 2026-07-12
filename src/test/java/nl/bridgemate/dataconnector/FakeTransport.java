package nl.bridgemate.dataconnector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Records requests and replays scripted responses. A scripted TransportException is thrown
 * instead of returned.
 */
final class FakeTransport implements HttpTransport {

    static final class Call {
        final String url;
        final String body;

        Call(String url, String body) {
            this.url = url;
            this.body = body;
        }
    }

    final List<Call> calls = new ArrayList<>();
    final Deque<Object> responses = new ArrayDeque<>();

    void respondWith(String body) {
        responses.add(body);
    }

    void failWith(String message) {
        responses.add(new TransportException(message));
    }

    @Override
    public String get(String url) {
        return record(url, null);
    }

    @Override
    public String post(String url, String jsonBody) {
        return record(url, jsonBody);
    }

    private String record(String url, String body) {
        calls.add(new Call(url, body));
        Object response = responses.poll();
        if (response == null) {
            throw new TransportException("No scripted response.");
        }
        if (response instanceof TransportException) {
            throw (TransportException) response;
        }
        return (String) response;
    }
}
