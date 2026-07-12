package nl.bridgemate.dataconnector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.bridgemate.dataconnector.dto.DataConnectorResponseData;
import nl.bridgemate.dataconnector.dto.ErrorType;
import nl.bridgemate.dataconnector.dto.ResultDTO;
import nl.bridgemate.dataconnector.dto.ScoringProgramDataConnectorCommands;
import nl.bridgemate.dataconnector.dto.ScoringProgramResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataConnectorClientTest {

    private static final String GUID = "A1B2C3D4E5F60718293A4B5C6D7E8F90";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeTransport transport;
    private DataConnectorClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        client = new DataConnectorClient("CLUB001", "LICENCE-KEY-123", "http://localhost:5079", transport);
    }

    @Test
    void connectPingsTheBaseAddress() {
        transport.respondWith("Bridgemate dataconnector service version 1.2.3");
        ScoringProgramResponse response = client.connect();
        assertEquals(DataConnectorResponseData.OK, response.dataType);
        assertEquals("http://localhost:5079/", transport.calls.get(0).url);
        assertNull(transport.calls.get(0).body);
    }

    @Test
    void connectFailureReturnsNoConnection() {
        transport.failWith("refused");
        ScoringProgramResponse response = client.connect();
        assertEquals(DataConnectorResponseData.Error, response.dataType);
        assertEquals(ErrorType.NoConnection, response.errorType);
    }

    @Test
    void sendResultsBuildsTheEnvelopeAndFiltersBySession() throws IOException {
        transport.respondWith(okResponseJson(ScoringProgramDataConnectorCommands.PutResults));

        ResultDTO mine = new ResultDTO();
        mine.sessionGuid = GUID;
        mine.sectionLetters = "A";
        ResultDTO other = new ResultDTO();
        other.sessionGuid = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";

        ScoringProgramResponse response = client.sendResults(GUID, Arrays.asList(mine, other));
        assertEquals(DataConnectorResponseData.OK, response.dataType);

        assertEquals("http://localhost:5079/dc-scoringprogram", transport.calls.get(0).url);
        JsonNode envelope = MAPPER.readTree(transport.calls.get(0).body);
        assertEquals(ScoringProgramDataConnectorCommands.PutResults.getValue(), envelope.get("Command").asInt());
        assertEquals("CLUB001", envelope.get("ClubId").asText());
        assertEquals("LICENCE-KEY-123", envelope.get("LicenceKey").asText());
        assertEquals(GUID, envelope.get("SessionGuid").asText());
        JsonNode payload = MAPPER.readTree(envelope.get("SerializedData").asText());
        assertEquals(1, payload.size());
        assertEquals("A", payload.get(0).get("SectionLetters").asText());
    }

    @Test
    void pollForResultsUsesFixtureResponseAndCachesLastQueueItemId() throws IOException {
        transport.respondWith(fixture("responses/PollQueueForNewResults.json"));

        List<ResultDTO> results = client.pollForResults(GUID);
        assertEquals(2, results.size());
        assertEquals(42, client.getLastQueueItemId(DataConnectorResponseData.Results));

        JsonNode envelope = MAPPER.readTree(transport.calls.get(0).body);
        assertEquals(ScoringProgramDataConnectorCommands.PollQueueForNewResults.getValue(), envelope.get("Command").asInt());
        assertEquals("", envelope.get("SerializedData").asText());
    }

    @Test
    void acceptQueueDataSendsTheCachedId() throws IOException {
        transport.respondWith(fixture("responses/PollQueueForNewResults.json"));
        transport.respondWith(okResponseJson(ScoringProgramDataConnectorCommands.AcceptResultQueueItems));

        client.pollForResults(GUID);
        ScoringProgramResponse response = client.acceptQueueData(GUID, DataConnectorResponseData.Results);
        assertEquals(DataConnectorResponseData.OK, response.dataType);

        JsonNode envelope = MAPPER.readTree(transport.calls.get(1).body);
        assertEquals(ScoringProgramDataConnectorCommands.AcceptResultQueueItems.getValue(), envelope.get("Command").asInt());
        assertEquals("42", envelope.get("SerializedData").asText());
    }

    @Test
    void acceptQueueDataRejectsInvalidDataType() {
        ScoringProgramResponse response = client.acceptQueueData(GUID, DataConnectorResponseData.EventInfo);
        assertEquals(ErrorType.Validation, response.errorType);
        assertTrue(transport.calls.isEmpty());
    }

    @Test
    void transportFailuresAreRetriedFiveTimesThenReportedAsNoConnection() {
        for (int i = 0; i < 5; i++) {
            transport.failWith("refused");
        }
        ScoringProgramResponse response = client.sendResults(GUID, Collections.emptyList());
        assertEquals(ErrorType.NoConnection, response.errorType);
        assertEquals(5, transport.calls.size());
    }

    @Test
    void pollReturnsEmptyListOnErrorResponse() throws IOException {
        transport.respondWith(fixture("responses/Error.json"));
        assertEquals(Collections.emptyList(), client.pollForResults(GUID));
    }

    @Test
    void invalidBaseAddressIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataConnectorClient("c", "l", "ftp://example.com"));
    }

    private static String fixture(String relativePath) throws IOException {
        return Files.readString(Path.of("src", "test", "resources", "fixtures", relativePath));
    }

    private static String okResponseJson(ScoringProgramDataConnectorCommands command) throws IOException {
        ScoringProgramResponse response = new ScoringProgramResponse();
        response.requestCommand = command;
        response.dataType = DataConnectorResponseData.OK;
        response.sessionGuid = GUID;
        response.serializedData = MAPPER.writeValueAsString("OK");
        return MAPPER.writeValueAsString(response);
    }
}
