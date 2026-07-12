package nl.bridgemate.dataconnector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.bridgemate.dataconnector.dto.Bridgemate2SettingsDTO;
import nl.bridgemate.dataconnector.dto.Bridgemate3SettingsDTO;
import nl.bridgemate.dataconnector.dto.ContinueDTO;
import nl.bridgemate.dataconnector.dto.DataConnectorResponseData;
import nl.bridgemate.dataconnector.dto.ErrorType;
import nl.bridgemate.dataconnector.dto.HandrecordDTO;
import nl.bridgemate.dataconnector.dto.InitDTO;
import nl.bridgemate.dataconnector.dto.ParticipationDTO;
import nl.bridgemate.dataconnector.dto.PlayerDataDTO;
import nl.bridgemate.dataconnector.dto.ResultDTO;
import nl.bridgemate.dataconnector.dto.ScoringGroupDTO;
import nl.bridgemate.dataconnector.dto.ScoringProgramDataConnectorCommands;
import nl.bridgemate.dataconnector.dto.ScoringProgramRequest;
import nl.bridgemate.dataconnector.dto.ScoringProgramResponse;
import nl.bridgemate.dataconnector.dto.SectionUpdateDTO;
import nl.bridgemate.dataconnector.dto.TdCallDTO;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scoring program client for the Bridgemate Data Connector over http.
 *
 * <p>The wire behaviour mirrors the .NET ScoringProgramDataConnectorHttpClient: a JSON serialized
 * {@link ScoringProgramRequest} is POSTed to the dc-scoringprogram endpoint and answered with a
 * JSON serialized {@link ScoringProgramResponse}. Since http is stateless there is no persistent
 * connection: {@link #connect()} is a ping and there is no disconnect.
 *
 * <p>Methods never throw on communication problems; they return a response with DataType Error
 * (poll methods return an empty list), exactly like the .NET client. Requests are serialized:
 * one request is in flight at a time per client instance.
 */
public class DataConnectorClient {

    /**
     * The default http port of the Data Connector.
     */
    public static final int DEFAULT_PORT = 5079;

    /**
     * The endpoint that handles scoring program requests.
     */
    public static final String SCORING_PROGRAM_ENDPOINT = "dc-scoringprogram";

    /**
     * Part of the expected response body when pinging the Data Connector with a GET request.
     */
    public static final String API_PING_RESPONSE = "Bridgemate dataconnector service version";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern REG_HTTP_PORT = Pattern.compile("HttpPort\\s+REG_DWORD\\s+0x([0-9A-Fa-f]+)");

    private final String clubId;
    private final String licenceKey;
    private final String baseAddress;
    private final HttpTransport transport;
    private final Map<DataConnectorResponseData, Integer> lastQueueItemIds = new EnumMap<>(DataConnectorResponseData.class);

    /**
     * Creates a client that targets the Data Connector on the local computer, discovering its
     * port through the Windows registry (default 5079).
     *
     * @param clubId the id of the club that is using the client; required for http communication.
     * @param licenceKey the licence key for the club using the client; required for http communication.
     */
    public DataConnectorClient(String clubId, String licenceKey) {
        this(clubId, licenceKey, null, null);
    }

    /**
     * Creates a client that targets the Data Connector at the given base address,
     * e.g. "http://192.168.1.50:5079".
     */
    public DataConnectorClient(String clubId, String licenceKey, String baseAddress) {
        this(clubId, licenceKey, baseAddress, null);
    }

    /**
     * @param baseAddress the Data Connector host, or null to target the local computer.
     * @param transport override the http implementation; used by tests. Null for the default.
     */
    public DataConnectorClient(String clubId, String licenceKey, String baseAddress, HttpTransport transport) {
        this.clubId = clubId;
        this.licenceKey = licenceKey;
        if (baseAddress != null) {
            String trimmed = baseAddress.replaceAll("/+$", "");
            if (!trimmed.matches("(?i)^https?://.+")) {
                throw new IllegalArgumentException("'" + baseAddress + "' is not an absolute http or https url.");
            }
            this.baseAddress = trimmed;
        } else {
            this.baseAddress = discoverLocalBaseAddress();
        }
        this.transport = transport != null ? transport : new JavaNetHttpTransport();
    }

    /**
     * The base address in use, e.g. "http://localhost:5079".
     */
    public String getBaseAddress() {
        return baseAddress;
    }

    /**
     * The url of the Data Connector on the local computer. On Windows the Data Connector service
     * publishes the port it listens on in the registry (HKEY_CURRENT_USER\Software\Bridge Systems BV\
     * BridgemateDataConnector, value HttpPort); when nothing is published the default port 5079 is
     * assumed.
     */
    public static String discoverLocalBaseAddress() {
        String os = System.getProperty("os.name", "");
        if (os.toLowerCase().contains("windows")) {
            try {
                Process process = new ProcessBuilder(
                        "reg", "query", "HKCU\\Software\\Bridge Systems BV\\BridgemateDataConnector", "/v", "HttpPort")
                        .redirectErrorStream(true)
                        .start();
                String output;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    output = reader.lines().collect(Collectors.joining("\n"));
                }
                process.waitFor();
                Matcher matcher = REG_HTTP_PORT.matcher(output);
                if (matcher.find()) {
                    int port = Integer.parseInt(matcher.group(1), 16);
                    if (port > 0 && port <= 65535) {
                        return "http://localhost:" + port;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                //No registry access: fall through to the default port.
            }
        }
        return "http://localhost:" + DEFAULT_PORT;
    }

    /**
     * Checks if the Data Connector can be reached by sending a GET request to its base address.
     */
    public ScoringProgramResponse connect() {
        String body;
        boolean success;
        try {
            body = transport.get(baseAddress + "/");
            success = body.contains(API_PING_RESPONSE);
        } catch (TransportException e) {
            body = e.getMessage();
            success = false;
        }
        ScoringProgramResponse response = new ScoringProgramResponse();
        response.requestCommand = ScoringProgramDataConnectorCommands.Connect;
        response.dataType = success ? DataConnectorResponseData.OK : DataConnectorResponseData.Error;
        response.errorType = success ? ErrorType.None : ErrorType.NoConnection;
        response.serializedData = writeJson(body);
        return response;
    }

    /**
     * Checks that the Data Connector is responsive by sending it a piece of data that it must echo.
     */
    public ScoringProgramResponse ping() {
        String requestTicks = String.valueOf(System.nanoTime());
        ScoringProgramResponse response = send("", ScoringProgramDataConnectorCommands.Ping, writeJson(requestTicks));
        if (response.requestCommand != ScoringProgramDataConnectorCommands.Ping) {
            return errorResponse(ScoringProgramDataConnectorCommands.Ping, ErrorType.Unknown,
                    "Invalid command in response to Ping: '" + response.requestCommand + "'");
        }
        if (response.dataType != DataConnectorResponseData.OK) {
            return response;
        }
        String responseTicks = readJson(response.serializedData, String.class);
        boolean error = !requestTicks.equals(responseTicks);
        ScoringProgramResponse result = new ScoringProgramResponse();
        result.requestCommand = ScoringProgramDataConnectorCommands.Ping;
        result.dataType = error ? DataConnectorResponseData.Error : DataConnectorResponseData.OK;
        result.errorType = error ? ErrorType.Validation : ErrorType.None;
        result.serializedData = response.serializedData;
        return result;
    }

    /**
     * Instructs BCS to create a new event with the provided sessions, scoring groups, sections,
     * tables and rounds. Player data, participations and handrecords can be included.
     */
    public ScoringProgramResponse initialize(InitDTO initDTO) {
        return send("", ScoringProgramDataConnectorCommands.InitializeEvent, writeJson(initDTO));
    }

    /**
     * Instructs BCS to continue working with a previously created event.
     */
    public ScoringProgramResponse continueEvent(ContinueDTO continueDTO) {
        return send("", ScoringProgramDataConnectorCommands.ContinueEvent, writeJson(continueDTO));
    }

    /**
     * Updates the movement for a section, or deletes the section.
     */
    public ScoringProgramResponse updateMovement(SectionUpdateDTO updatedSection) {
        String sessionGuid = updatedSection.sessionGuid != null ? updatedSection.sessionGuid : "";
        return send(sessionGuid, ScoringProgramDataConnectorCommands.UpdateMovement, writeJson(updatedSection));
    }

    /**
     * Updates the scoring method of the scoring groups and/or rearranges the assignment of the
     * sections to them. Sends one request per session, like the .NET client.
     */
    public ScoringProgramResponse updateScoringGroups(List<ScoringGroupDTO> scoringGroups) {
        Map<String, List<ScoringGroupDTO>> groups = new LinkedHashMap<>();
        for (ScoringGroupDTO scoringGroup : scoringGroups) {
            String key = scoringGroup.sessionGuid != null ? scoringGroup.sessionGuid : "";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(scoringGroup);
        }
        for (Map.Entry<String, List<ScoringGroupDTO>> entry : groups.entrySet()) {
            ScoringProgramResponse response =
                    send(entry.getKey(), ScoringProgramDataConnectorCommands.UpdateScoringGroups, writeJson(entry.getValue()));
            if (response.dataType != DataConnectorResponseData.OK) {
                return response;
            }
        }
        ScoringProgramResponse response = new ScoringProgramResponse();
        response.requestCommand = ScoringProgramDataConnectorCommands.UpdateScoringGroups;
        response.dataType = DataConnectorResponseData.OK;
        response.serializedData = writeJson("Scoring groups updated.");
        return response;
    }

    /**
     * Sends board results to the BCS queue. Only DTOs whose SessionGuid matches are sent.
     */
    public ScoringProgramResponse sendResults(String sessionGuid, List<ResultDTO> results) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutResults, results, dto -> dto.sessionGuid);
    }

    /**
     * Sends player data to the BCS queue. Only DTOs whose SessionGuid matches are sent.
     */
    public ScoringProgramResponse sendPlayerData(String sessionGuid, List<PlayerDataDTO> playerData) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutPlayerData, playerData, dto -> dto.sessionGuid);
    }

    /**
     * Sends participations to the BCS queue. Only DTOs whose SessionGuid matches are sent; when
     * none match a NoData error is returned without calling the Data Connector.
     */
    public ScoringProgramResponse sendParticipations(String sessionGuid, List<ParticipationDTO> participations) {
        List<ParticipationDTO> forSession = filterBySession(sessionGuid, participations, dto -> dto.sessionGuid);
        if (forSession.isEmpty()) {
            return errorResponse(ScoringProgramDataConnectorCommands.PutParticipations, ErrorType.NoData, "Empty data");
        }
        return send(sessionGuid, ScoringProgramDataConnectorCommands.PutParticipations, writeJson(forSession));
    }

    /**
     * Sends handrecords to the BCS queue. Only DTOs whose SessionGuid matches are sent.
     */
    public ScoringProgramResponse sendHandrecords(String sessionGuid, List<HandrecordDTO> handrecords) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutHandrecords, handrecords, dto -> dto.sessionGuid);
    }

    /**
     * Sends TD calls to the BCS queue. Only DTOs whose SessionGuid matches are sent.
     */
    public ScoringProgramResponse sendTdCalls(String sessionGuid, List<TdCallDTO> tdCalls) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutTdCalls, tdCalls, dto -> dto.sessionGuid);
    }

    /**
     * Adds or updates the Bridgemate 2 settings for the given sections. One DTO per section.
     */
    public ScoringProgramResponse sendBridgemate2Settings(String sessionGuid, List<Bridgemate2SettingsDTO> settings) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutBridgemate2Settings, settings, dto -> dto.sessionGuid);
    }

    /**
     * Adds or updates the Bridgemate 3 settings for the given sections. One DTO per section.
     */
    public ScoringProgramResponse sendBridgemate3Settings(String sessionGuid, List<Bridgemate3SettingsDTO> settings) {
        return sendForSession(sessionGuid, ScoringProgramDataConnectorCommands.PutBridgemate3Settings, settings, dto -> dto.sessionGuid);
    }

    /**
     * Polls the queue for new board results.
     */
    public List<ResultDTO> pollForResults(String sessionGuid) {
        return pollForResults(sessionGuid, false);
    }

    /**
     * Polls the queue for board results.
     *
     * @param all also return results that were polled before.
     */
    public List<ResultDTO> pollForResults(String sessionGuid, boolean all) {
        return poll(sessionGuid,
                all ? ScoringProgramDataConnectorCommands.PollQueueForAllResults : ScoringProgramDataConnectorCommands.PollQueueForNewResults,
                DataConnectorResponseData.Results, ResultDTO[].class);
    }

    /**
     * Polls the queue for new player data.
     */
    public List<PlayerDataDTO> pollForPlayerData(String sessionGuid) {
        return pollForPlayerData(sessionGuid, false);
    }

    /**
     * Polls the queue for player data.
     *
     * @param all also return player data that was polled before.
     */
    public List<PlayerDataDTO> pollForPlayerData(String sessionGuid, boolean all) {
        return poll(sessionGuid,
                all ? ScoringProgramDataConnectorCommands.PollQueueForAllPlayerData : ScoringProgramDataConnectorCommands.PollQueueForNewPlayerData,
                DataConnectorResponseData.PlayerData, PlayerDataDTO[].class);
    }

    /**
     * Polls the queue for new participations.
     */
    public List<ParticipationDTO> pollForParticipations(String sessionGuid) {
        return pollForParticipations(sessionGuid, false);
    }

    /**
     * Polls the queue for participations.
     *
     * @param all also return participations that were polled before.
     */
    public List<ParticipationDTO> pollForParticipations(String sessionGuid, boolean all) {
        return poll(sessionGuid,
                all ? ScoringProgramDataConnectorCommands.PollQueueForAllParticipations : ScoringProgramDataConnectorCommands.PollQueueForNewParticipations,
                DataConnectorResponseData.Participations, ParticipationDTO[].class);
    }

    /**
     * Polls the queue for new handrecords.
     */
    public List<HandrecordDTO> pollForHandrecords(String sessionGuid) {
        return pollForHandrecords(sessionGuid, false);
    }

    /**
     * Polls the queue for handrecords.
     *
     * @param all also return handrecords that were polled before.
     */
    public List<HandrecordDTO> pollForHandrecords(String sessionGuid, boolean all) {
        return poll(sessionGuid,
                all ? ScoringProgramDataConnectorCommands.PollQueueForAllHandrecords : ScoringProgramDataConnectorCommands.PollQueueForNewHandrecords,
                DataConnectorResponseData.Handrecords, HandrecordDTO[].class);
    }

    /**
     * Polls the queue for new TD calls.
     */
    public List<TdCallDTO> pollForTdCalls(String sessionGuid) {
        return pollForTdCalls(sessionGuid, false);
    }

    /**
     * Polls the queue for TD calls.
     *
     * @param all also return TD calls that were polled before.
     */
    public List<TdCallDTO> pollForTdCalls(String sessionGuid, boolean all) {
        return poll(sessionGuid,
                all ? ScoringProgramDataConnectorCommands.PollQueueForAllTdCalls : ScoringProgramDataConnectorCommands.PollQueueForNewTdCalls,
                DataConnectorResponseData.TdCalls, TdCallDTO[].class);
    }

    /**
     * Signals to the Data Connector that queue data of the given type, up to and including the
     * last polled item, does not need to be sent again.
     *
     * @param dataType Results, PlayerData, Participations, Handrecords or TdCalls.
     */
    public ScoringProgramResponse acceptQueueData(String sessionGuid, DataConnectorResponseData dataType) {
        ScoringProgramDataConnectorCommands command;
        switch (dataType) {
            case Results:
                command = ScoringProgramDataConnectorCommands.AcceptResultQueueItems;
                break;
            case PlayerData:
                command = ScoringProgramDataConnectorCommands.AcceptPlayerDataQueueItems;
                break;
            case Participations:
                command = ScoringProgramDataConnectorCommands.AcceptParticipantQueueItems;
                break;
            case Handrecords:
                command = ScoringProgramDataConnectorCommands.AcceptHandrecordQueueItems;
                break;
            case TdCalls:
                command = ScoringProgramDataConnectorCommands.AcceptTdCallQueueItems;
                break;
            default:
                return errorResponse(ScoringProgramDataConnectorCommands.None, ErrorType.Validation,
                        "Invalid datatype '" + dataType + "'");
        }
        int lastQueueItemId = getLastQueueItemId(dataType);
        return send(sessionGuid, command, writeJson(lastQueueItemId));
    }

    /**
     * The id of the last queue item downloaded for the given data type, as cached by the poll
     * methods. Zero when nothing has been polled yet.
     */
    public int getLastQueueItemId(DataConnectorResponseData dataType) {
        return lastQueueItemIds.getOrDefault(dataType, 0);
    }

    /**
     * Sends a raw request. The ClubId and LicenceKey are set on the request by this method.
     * All higher level methods funnel through here; use it directly for commands this client
     * does not wrap yet. One request is in flight at a time per client instance.
     */
    public synchronized ScoringProgramResponse sendRequest(ScoringProgramRequest request) {
        request.clubId = clubId;
        request.licenceKey = licenceKey;
        String json = writeJson(request);
        String url = baseAddress + "/" + SCORING_PROGRAM_ENDPOINT;

        String lastErrorMessage = "No connection";
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                String body = transport.post(url, json);
                ScoringProgramResponse response = MAPPER.readValue(body, ScoringProgramResponse.class);
                if (response == null) {
                    return errorResponse(request.command, ErrorType.EmptyResponse, "Empty response");
                }
                return response;
            } catch (TransportException | JsonProcessingException e) {
                lastErrorMessage = e.getMessage();
                //The .NET client backs off 200/400/600/800 ms between its five attempts.
                if (attempt < 4) {
                    try {
                        Thread.sleep(200L + attempt * 200L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return errorResponse(request.command, ErrorType.NoConnection, "Interrupted");
                    }
                }
            }
        }
        return errorResponse(request.command, ErrorType.NoConnection, lastErrorMessage);
    }

    private ScoringProgramResponse send(String sessionGuid, ScoringProgramDataConnectorCommands command, String serializedData) {
        ScoringProgramRequest request = new ScoringProgramRequest();
        request.command = command;
        request.sessionGuid = sessionGuid;
        request.serializedData = serializedData;
        return sendRequest(request);
    }

    private <T> ScoringProgramResponse sendForSession(String sessionGuid, ScoringProgramDataConnectorCommands command,
                                                      List<T> dtos, Function<T, String> sessionGuidOf) {
        return send(sessionGuid, command, writeJson(filterBySession(sessionGuid, dtos, sessionGuidOf)));
    }

    private static <T> List<T> filterBySession(String sessionGuid, List<T> dtos, Function<T, String> sessionGuidOf) {
        return dtos.stream().filter(dto -> sessionGuid.equals(sessionGuidOf.apply(dto))).collect(Collectors.toList());
    }

    private <T> List<T> poll(String sessionGuid, ScoringProgramDataConnectorCommands command,
                             DataConnectorResponseData expectedDataType, Class<T[]> dtoArrayClass) {
        ScoringProgramResponse response = send(sessionGuid, command, "");
        if (response.dataType != expectedDataType) {
            return Collections.emptyList();
        }
        if (response.serializedData == null || response.serializedData.trim().isEmpty()) {
            return Collections.emptyList();
        }
        T[] items = readJson(response.serializedData, dtoArrayClass);
        if (items == null || items.length == 0) {
            return Collections.emptyList();
        }
        lastQueueItemIds.put(expectedDataType, response.lastQueueItemId);
        return Arrays.asList(items);
    }

    private ScoringProgramResponse errorResponse(ScoringProgramDataConnectorCommands command, ErrorType errorType, String message) {
        ScoringProgramResponse response = new ScoringProgramResponse();
        response.requestCommand = command;
        response.dataType = DataConnectorResponseData.Error;
        response.errorType = errorType;
        response.serializedData = writeJson(message);
        return response;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            //The DTOs are plain data holders; serializing them cannot legitimately fail.
            throw new IllegalStateException(e);
        }
    }

    private static <T> T readJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
