package nl.bridgemate.dataconnector;

/**
 * Thrown by an {@link HttpTransport} when the request could not be completed (connection failure,
 * timeout or a non-2xx status). The {@link DataConnectorClient} catches it and retries; it never
 * escapes to calling code.
 */
public class TransportException extends RuntimeException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
