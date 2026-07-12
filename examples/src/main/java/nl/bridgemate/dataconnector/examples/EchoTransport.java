package nl.bridgemate.dataconnector.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.bridgemate.dataconnector.HttpTransport;

/**
 * Decorates another HttpTransport and echoes every request and response to the console,
 * with the nested SerializedData expanded, so you can trace the wire traffic of the
 * DataConnectorClient without a debugger.
 */
public final class EchoTransport implements HttpTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CYAN = (char) 27 + "[36m";
    private static final String RESET = (char) 27 + "[0m";

    private final HttpTransport inner;
    public boolean enabled = true;

    public EchoTransport(HttpTransport inner) {
        this.inner = inner;
    }

    @Override
    public String get(String url) {
        echoLine(">> GET  " + url);
        String body = inner.get(url);
        echoLine("<< " + body);
        return body;
    }

    @Override
    public String post(String url, String jsonBody) {
        echoLine(">> POST " + url);
        echoJson(jsonBody);
        String body = inner.post(url, jsonBody);
        echoLine("<<");
        echoJson(body);
        return body;
    }

    private void echoLine(String line) {
        if (enabled) {
            System.out.println(CYAN + line + RESET);
        }
    }

    /**
     * Pretty-prints an envelope. The SerializedData property is itself a JSON string
     * ("double serialization"); expand it so the payload is readable.
     */
    private void echoJson(String json) {
        if (!enabled) {
            return;
        }
        try {
            ObjectNode envelope = (ObjectNode) MAPPER.readTree(json);
            JsonNode serializedData = envelope.get("SerializedData");
            if (serializedData != null && serializedData.isTextual() && !serializedData.asText().isEmpty()) {
                envelope.set("SerializedData", MAPPER.readTree(serializedData.asText()));
            }
            echoLine(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        } catch (Exception e) {
            echoLine(json);
        }
    }
}
