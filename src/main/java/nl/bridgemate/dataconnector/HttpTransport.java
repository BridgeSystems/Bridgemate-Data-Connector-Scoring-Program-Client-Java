package nl.bridgemate.dataconnector;

/**
 * The http layer used by the {@link DataConnectorClient}. The default implementation is
 * {@link JavaNetHttpTransport}; tests inject a fake.
 */
public interface HttpTransport {

    /**
     * Performs a GET request and returns the response body.
     *
     * @throws TransportException when the request fails or returns a non-2xx status.
     */
    String get(String url);

    /**
     * POSTs the given JSON body with content type application/json and returns the response body.
     *
     * @throws TransportException when the request fails or returns a non-2xx status.
     */
    String post(String url, String jsonBody);
}
