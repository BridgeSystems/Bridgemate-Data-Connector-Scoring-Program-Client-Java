package nl.bridgemate.dataconnector.examples;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.bridgemate.dataconnector.DataConnectorClient;
import nl.bridgemate.dataconnector.JavaNetHttpTransport;
import nl.bridgemate.dataconnector.dto.ContinueDTO;
import nl.bridgemate.dataconnector.dto.DataConnectorResponseData;
import nl.bridgemate.dataconnector.dto.InitDTO;
import nl.bridgemate.dataconnector.dto.ParticipationDTO;
import nl.bridgemate.dataconnector.dto.PlayerDataDTO;
import nl.bridgemate.dataconnector.dto.ResultDTO;
import nl.bridgemate.dataconnector.dto.ScoringProgramResponse;
import nl.bridgemate.dataconnector.dto.TableDirection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Getting-started sample for the Bridgemate Data Connector Java client.
 *
 * <p>Interactive:  java ... GettingStarted
 * <br>Unattended:  java ... GettingStarted --scenario
 *
 * <p>Options: --base-address=http://host:5079  --club-id=...  --licence-key=...  --no-trace
 *
 * <p>The "initialize event" action instructs the Data Connector to START Bridgemate Control
 * Software and create a small test event (1 section, 2 tables, 3 rounds). Watch BCS open,
 * enter a result there (or on a Bridgemate), then use the poll actions here to receive it.
 */
public final class GettingStarted {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path STATE_FILE = Path.of(".state.json");

    private final DataConnectorClient client;
    private final EchoTransport trace;

    private GettingStarted(DataConnectorClient client, EchoTransport trace) {
        this.client = client;
        this.trace = trace;
    }

    public static void main(String[] args) {
        String baseAddress = optionValue(args, "--base-address");
        String clubId = optionValue(args, "--club-id");
        String licenceKey = optionValue(args, "--licence-key");

        EchoTransport trace = new EchoTransport(new JavaNetHttpTransport());
        trace.enabled = !hasFlag(args, "--no-trace");
        DataConnectorClient client = new DataConnectorClient(
                clubId != null ? clubId : "",
                licenceKey != null ? licenceKey : "",
                baseAddress,
                trace);
        System.out.println("Data Connector address: " + client.getBaseAddress());

        GettingStarted app = new GettingStarted(client, trace);
        if (hasFlag(args, "--scenario")) {
            System.exit(app.runScenario() ? 0 : 1);
        }
        app.runMenu();
    }

    //--------------------------------------------------------------------------------------------
    // Actions. Each one is a plain use of the client, so this file doubles as sample code.
    //--------------------------------------------------------------------------------------------

    private void connectAndPing() {
        report("Connect", client.connect());
        report("Ping", client.ping());
    }

    /**
     * Creates a fresh event from the template: new event/session guids and today's date.
     * Commands = 7 tells the Data Connector to start BCS (1), reset (2) and start reading (4).
     */
    private ScoringProgramResponse initializeEvent() {
        String sessionGuid = newGuid();
        String eventGuid = newGuid();
        String template = readResource("/data/init-template.json").replace("REPLACED-AT-RUNTIME", sessionGuid);
        InitDTO initDto = readJson(template, InitDTO.class);
        initDto.eventGuid = eventGuid;
        LocalDateTime now = LocalDateTime.now();
        initDto.sessions[0].eventGuid = eventGuid;
        initDto.sessions[0].year = now.getYear();
        initDto.sessions[0].month = now.getMonthValue();
        initDto.sessions[0].day = now.getDayOfMonth();
        initDto.sessions[0].hour = now.getHour();
        initDto.sessions[0].minute = now.getMinute();
        initDto.playerData = loadPlayerData(sessionGuid).toArray(new PlayerDataDTO[0]);
        initDto.participations = loadParticipations(sessionGuid).toArray(new ParticipationDTO[0]);

        ScoringProgramResponse response = client.initialize(initDto);
        saveState(Map.of("sessionGuid", sessionGuid, "eventGuid", eventGuid));
        System.out.println("Session guid: " + sessionGuid + " (saved to .state.json)");
        return response;
    }

    private ScoringProgramResponse continueEvent() {
        ContinueDTO continueDto = new ContinueDTO();
        continueDto.eventGuid = state().get("eventGuid");
        continueDto.commands = 7;
        return client.continueEvent(continueDto);
    }

    private ScoringProgramResponse sendPlayerData() {
        String sessionGuid = state().get("sessionGuid");
        return client.sendPlayerData(sessionGuid, loadPlayerData(sessionGuid));
    }

    /**
     * Uploads one board result: table 1, round 1, board 1 — 1 Clubs by North, 7 tricks, lead club ace.
     */
    private ScoringProgramResponse sendOneResult() {
        String sessionGuid = state().get("sessionGuid");
        ResultDTO result = new ResultDTO();
        result.sessionGuid = sessionGuid;
        result.sectionLetters = "A";
        result.tableNumber = 1;
        result.roundNumber = 1;
        result.boardNumber = 1;
        result.scoringDirection = ResultDTO.ScoringDirection_NSEW;
        result.pairNorthSouth = 1;
        result.pairEastWest = 2;
        result.declaringPair = 1;
        result.declarerDirection = ResultDTO.Direction_North;
        result.level = 1;
        result.denomination = ResultDTO.Denomination_Clubs;
        result.stake = ResultDTO.Stake_Normal;
        result.totalTricks = 7;
        result.leadCardRank = 14;
        result.leadCardSuit = 1;
        return client.sendResults(sessionGuid, List.of(result));
    }

    private void pollQueue(DataConnectorResponseData dataType, boolean all) {
        String sessionGuid = state().get("sessionGuid");
        List<?> items;
        switch (dataType) {
            case Results:
                items = client.pollForResults(sessionGuid, all);
                break;
            case PlayerData:
                items = client.pollForPlayerData(sessionGuid, all);
                break;
            case Participations:
                items = client.pollForParticipations(sessionGuid, all);
                break;
            case Handrecords:
                items = client.pollForHandrecords(sessionGuid, all);
                break;
            case TdCalls:
                items = client.pollForTdCalls(sessionGuid, all);
                break;
            default:
                items = List.of();
        }
        System.out.println("Polled " + dataType + ": " + items.size() + " item(s)");
        for (Object item : items) {
            System.out.println("  " + writeJson(item));
        }
        if (!items.isEmpty()) {
            System.out.println("Last queue item id: " + client.getLastQueueItemId(dataType)
                    + " — use 'accept' so they are not sent again.");
        }
    }

    private void acceptQueue(DataConnectorResponseData dataType) {
        report("Accept " + dataType, client.acceptQueueData(state().get("sessionGuid"), dataType));
    }

    //--------------------------------------------------------------------------------------------
    // Scenario mode: the whole flow in one run. Returns true when every step succeeded.
    //--------------------------------------------------------------------------------------------

    private boolean runScenario() {
        boolean ok = true;
        ok &= check("Connect", client.connect());
        ok &= check("Ping", client.ping());
        ok &= check("InitializeEvent", initializeEvent());
        ok &= check("PutPlayerData", sendPlayerData());
        ok &= check("PutResults", sendOneResult());
        pollQueue(DataConnectorResponseData.Results, false);
        System.out.println(ok ? "SCENARIO OK" : "SCENARIO FAILED");
        return ok;
    }

    private static boolean check(String step, ScoringProgramResponse response) {
        boolean success = response.dataType != DataConnectorResponseData.Error;
        System.out.printf("%-20s %s%s%n", step, success ? "OK" : "FAILED: ", success ? "" : response.errorType);
        return success;
    }

    //--------------------------------------------------------------------------------------------
    // Interactive menu
    //--------------------------------------------------------------------------------------------

    private void runMenu() {
        boolean pollAll = false;
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.printf("%n"
                    + " 1  Connect + ping%n"
                    + " 2  Initialize event (starts BCS, creates a fresh test event)%n"
                    + " 3  Continue event (re-open the event from .state.json)%n"
                    + " 4  Send player data%n"
                    + " 5  Send a board result (A1, round 1, board 1)%n"
                    + " 6  Poll results            7  Accept results%n"
                    + " 8  Poll player data        9  Accept player data%n"
                    + "10  Poll participations    11  Accept participations%n"
                    + "12  Poll handrecords       13  Accept handrecords%n"
                    + "14  Poll TD calls          15  Accept TD calls%n"
                    + "16  Toggle 'poll all' (currently: %s)%n"
                    + "17  Toggle wire trace (currently: %s)%n"
                    + " 0  Quit%n%nChoice: ", pollAll ? "all items" : "new items only", trace.enabled ? "on" : "off");
            String choice = scanner.hasNextLine() ? scanner.nextLine().trim() : "0";
            try {
                switch (choice) {
                    case "1": connectAndPing(); break;
                    case "2": report("InitializeEvent", initializeEvent()); break;
                    case "3": report("ContinueEvent", continueEvent()); break;
                    case "4": report("PutPlayerData", sendPlayerData()); break;
                    case "5": report("PutResults", sendOneResult()); break;
                    case "6": pollQueue(DataConnectorResponseData.Results, pollAll); break;
                    case "7": acceptQueue(DataConnectorResponseData.Results); break;
                    case "8": pollQueue(DataConnectorResponseData.PlayerData, pollAll); break;
                    case "9": acceptQueue(DataConnectorResponseData.PlayerData); break;
                    case "10": pollQueue(DataConnectorResponseData.Participations, pollAll); break;
                    case "11": acceptQueue(DataConnectorResponseData.Participations); break;
                    case "12": pollQueue(DataConnectorResponseData.Handrecords, pollAll); break;
                    case "13": acceptQueue(DataConnectorResponseData.Handrecords); break;
                    case "14": pollQueue(DataConnectorResponseData.TdCalls, pollAll); break;
                    case "15": acceptQueue(DataConnectorResponseData.TdCalls); break;
                    case "16": pollAll = !pollAll; break;
                    case "17": trace.enabled = !trace.enabled; break;
                    case "0": return;
                    default: System.out.println("Unknown choice.");
                }
            } catch (IllegalStateException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    //--------------------------------------------------------------------------------------------
    // Helpers
    //--------------------------------------------------------------------------------------------

    private static void report(String action, ScoringProgramResponse response) {
        String data = response.serializedData != null && response.serializedData.length() > 120
                ? response.serializedData.substring(0, 120)
                : response.serializedData;
        System.out.printf("%s -> DataType=%s ErrorType=%s Data=%s%n", action, response.dataType, response.errorType, data);
    }

    private static String newGuid() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private static List<PlayerDataDTO> loadPlayerData(String sessionGuid) {
        return loadPlayers().stream().map(player -> {
            PlayerDataDTO dto = new PlayerDataDTO();
            dto.sessionGuid = sessionGuid;
            dto.playerNumber = (String) player.get("PlayerNumber");
            dto.firstName = (String) player.get("FirstName");
            dto.lastName = (String) player.get("LastName");
            dto.countryCode = (String) player.get("CountryCode");
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Round-1 seating for the players in players.json.
     */
    private static List<ParticipationDTO> loadParticipations(String sessionGuid) {
        return loadPlayers().stream().map(player -> {
            ParticipationDTO dto = new ParticipationDTO();
            dto.sessionGuid = sessionGuid;
            dto.sectionLetters = (String) player.get("SectionLetters");
            dto.tableNumber = (Integer) player.get("TableNumber");
            dto.direction = TableDirection.fromValue((Integer) player.get("Direction"));
            dto.roundNumber = 1;
            dto.playerNumber = (String) player.get("PlayerNumber");
            return dto;
        }).collect(Collectors.toList());
    }

    private static List<Map<String, Object>> loadPlayers() {
        return readJson(readResource("/data/players.json"), new TypeReference<List<Map<String, Object>>>() { });
    }

    private static Map<String, String> state() {
        if (!Files.exists(STATE_FILE)) {
            throw new IllegalStateException("No event yet: run \"Initialize event\" first.");
        }
        try {
            return readJson(Files.readString(STATE_FILE), new TypeReference<Map<String, String>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void saveState(Map<String, String> state) {
        try {
            Files.writeString(STATE_FILE, writeJson(state));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readResource(String name) {
        try (InputStream stream = GettingStarted.class.getResourceAsStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Could not read resource " + name, e);
        }
    }

    private static <T> T readJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T readJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String optionValue(String[] args, String name) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith(name + "="))
                .map(arg -> arg.substring(name.length() + 1))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasFlag(String[] args, String name) {
        return Arrays.asList(args).contains(name);
    }
}
