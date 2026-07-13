package nl.bridgemate.dataconnector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link HttpTransport} built on java.net.http.HttpClient (Java 11). Timeouts mirror the .NET
 * client's HttpClient defaults closely enough: 10 seconds to connect, 100 seconds per request.
 */
public final class JavaNetHttpTransport implements HttpTransport {

    private final HttpClient client;
    private final Duration requestTimeout;

    public JavaNetHttpTransport() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(100));
    }

    public JavaNetHttpTransport(Duration connectTimeout, Duration requestTimeout) {
        // The data connector lives on localhost or the LAN: NO_PROXY guarantees a proxy configured
        // via JVM system properties (http.proxyHost, java.net.useSystemProxies) never hijacks the
        // request (503 from a corporate proxy without a localhost bypass).
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .GET()
                .build();
        return send(request, url);
    }

    @Override
    public String post(String url, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return send(request, url);
    }

    private String send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TransportException("Request to '" + url + "' returned status " + response.statusCode() + ".");
            }
            return response.body();
        } catch (IOException e) {
            throw new TransportException("Request to '" + url + "' failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Request to '" + url + "' was interrupted.", e);
        }
    }
}
