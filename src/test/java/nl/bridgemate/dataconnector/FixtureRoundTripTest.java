package nl.bridgemate.dataconnector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.bridgemate.dataconnector.dto.Bridgemate2SettingsDTO;
import nl.bridgemate.dataconnector.dto.Bridgemate3SettingsDTO;
import nl.bridgemate.dataconnector.dto.ContinueDTO;
import nl.bridgemate.dataconnector.dto.HandrecordDTO;
import nl.bridgemate.dataconnector.dto.InitDTO;
import nl.bridgemate.dataconnector.dto.ParticipationDTO;
import nl.bridgemate.dataconnector.dto.PlayerDataDTO;
import nl.bridgemate.dataconnector.dto.ResultDTO;
import nl.bridgemate.dataconnector.dto.ScoringGroupDTO;
import nl.bridgemate.dataconnector.dto.ScoringProgramRequest;
import nl.bridgemate.dataconnector.dto.ScoringProgramResponse;
import nl.bridgemate.dataconnector.dto.SectionUpdateDTO;
import nl.bridgemate.dataconnector.dto.TdCallDTO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The golden fixtures are the exact JSON the .NET client produces. Deserializing a fixture into
 * the generated DTOs and re-serializing it must yield a structurally identical document
 * (including the nested SerializedData payload); any missing, extra or mistyped property fails
 * this test.
 */
class FixtureRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    /**
     * Maps a request fixture to the payload DTO type inside SerializedData.
     * Absent means the payload is not a DTO (a bare JSON string or number, or empty).
     */
    private static final Map<String, Class<?>> REQUEST_PAYLOADS = new HashMap<>();
    private static final Map<String, Class<?>> RESPONSE_PAYLOADS = new HashMap<>();

    static {
        REQUEST_PAYLOADS.put("InitializeEvent", InitDTO.class);
        REQUEST_PAYLOADS.put("ContinueEvent", ContinueDTO.class);
        REQUEST_PAYLOADS.put("UpdateMovement", SectionUpdateDTO.class);
        REQUEST_PAYLOADS.put("UpdateScoringGroups", ScoringGroupDTO[].class);
        REQUEST_PAYLOADS.put("PutResults", ResultDTO[].class);
        REQUEST_PAYLOADS.put("PutPlayerData", PlayerDataDTO[].class);
        REQUEST_PAYLOADS.put("PutParticipations", ParticipationDTO[].class);
        REQUEST_PAYLOADS.put("PutHandrecords", HandrecordDTO[].class);
        REQUEST_PAYLOADS.put("PutTdCalls", TdCallDTO[].class);
        REQUEST_PAYLOADS.put("PutBridgemate2Settings", Bridgemate2SettingsDTO[].class);
        REQUEST_PAYLOADS.put("PutBridgemate3Settings", Bridgemate3SettingsDTO[].class);

        RESPONSE_PAYLOADS.put("PollQueueForNewResults", ResultDTO[].class);
        RESPONSE_PAYLOADS.put("PollQueueForNewPlayerData", PlayerDataDTO[].class);
        RESPONSE_PAYLOADS.put("PollQueueForNewParticipations", ParticipationDTO[].class);
        RESPONSE_PAYLOADS.put("PollQueueForNewHandrecords", HandrecordDTO[].class);
        RESPONSE_PAYLOADS.put("PollQueueForNewTdCalls", TdCallDTO[].class);
    }

    static Stream<Path> requestFixtures() throws IOException {
        return Files.list(FIXTURES.resolve("requests")).sorted();
    }

    static Stream<Path> responseFixtures() throws IOException {
        return Files.list(FIXTURES.resolve("responses")).sorted();
    }

    @ParameterizedTest
    @MethodSource("requestFixtures")
    void requestEnvelopeRoundTrips(Path path) throws IOException {
        String original = Files.readString(path);
        ScoringProgramRequest request = MAPPER.readValue(original, ScoringProgramRequest.class);
        assertEnvelopeEquals(original, MAPPER.writeValueAsString(request));
    }

    @ParameterizedTest
    @MethodSource("requestFixtures")
    void requestPayloadRoundTrips(Path path) throws IOException {
        assertPayloadRoundTrips(path, REQUEST_PAYLOADS);
    }

    @ParameterizedTest
    @MethodSource("responseFixtures")
    void responseEnvelopeRoundTrips(Path path) throws IOException {
        String original = Files.readString(path);
        ScoringProgramResponse response = MAPPER.readValue(original, ScoringProgramResponse.class);
        assertEnvelopeEquals(original, MAPPER.writeValueAsString(response));
    }

    @ParameterizedTest
    @MethodSource("responseFixtures")
    void responsePayloadRoundTrips(Path path) throws IOException {
        assertPayloadRoundTrips(path, RESPONSE_PAYLOADS);
    }

    private static void assertPayloadRoundTrips(Path path, Map<String, Class<?>> payloadTypes) throws IOException {
        String name = path.getFileName().toString().replace(".json", "");
        Class<?> payloadType = payloadTypes.get(name);
        if (payloadType == null) {
            return;
        }
        JsonNode envelope = MAPPER.readTree(Files.readString(path));
        String serializedData = envelope.get("SerializedData").asText();
        Object payload = MAPPER.readValue(serializedData, payloadType);
        assertEquals(MAPPER.readTree(serializedData), MAPPER.readTree(MAPPER.writeValueAsString(payload)),
                "payload of " + name);
    }

    /**
     * Compares two envelopes structurally. The SerializedData strings are compared as parsed JSON
     * because the .NET serializer escapes differently (e.g. ") than Jackson.
     */
    private static void assertEnvelopeEquals(String expected, String actual) throws IOException {
        assertEquals(normalize(expected), normalize(actual));
    }

    private static JsonNode normalize(String envelopeJson) throws IOException {
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(envelopeJson);
        JsonNode serializedData = envelope.get("SerializedData");
        if (serializedData != null && !serializedData.isNull() && !serializedData.asText().isEmpty()) {
            envelope.set("SerializedData", MAPPER.readTree(serializedData.asText()));
        }
        return envelope;
    }
}
