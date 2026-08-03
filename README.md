# Bridgemate Data Connector scoring program client for Java

[![Maven Central](https://img.shields.io/maven-central/v/nl.bridgemate/bridgemate-dataconnector-client)](https://central.sonatype.com/artifact/nl.bridgemate/bridgemate-dataconnector-client)
[![CI](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client-Java/actions/workflows/ci.yml/badge.svg)](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client-Java/actions/workflows/ci.yml)

Java client for scoring programs to communicate with the **Bridgemate Data Connector** over http.
Bridgemate Control Software (BCS 5) is needed to receive, process and return data from the Data
Connector. This library is the Java counterpart of the
[.NET client](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client);
its wire format is generated from the .NET source and verified against golden fixtures, so the two
clients speak an identical protocol.

## Requirements

- Java 11 or later
- A reachable Bridgemate Data Connector (installed with BCS 5). When the scoring program runs on a
  different computer than BCS, enable listening on the local network in BCS and allow the port
  (default 5079) through the firewall on the BCS computer.

## Installation

```xml
<dependency>
  <groupId>nl.bridgemate</groupId>
  <artifactId>bridgemate-dataconnector-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

The only runtime dependency is Jackson (`jackson-databind`).

## Quick start

```java
import nl.bridgemate.dataconnector.DataConnectorClient;
import nl.bridgemate.dataconnector.dto.*;

// Data Connector on the same computer (port discovered through the registry, default 5079):
DataConnectorClient client = new DataConnectorClient("YourClubId", "YourLicenceKey");

// Data Connector on another computer on the local network:
DataConnectorClient client = new DataConnectorClient("YourClubId", "YourLicenceKey", "http://192.168.1.50:5079");

// Check the connection.
ScoringProgramResponse response = client.connect();

// Create an event in BCS (see the developer's guide for how to build the InitDTO).
InitDTO initDto = new InitDTO();
// ... fill sessions, scoring groups, sections, tables, rounds ...
response = client.initialize(initDto);

// Poll for new board results and accept them once processed.
List<ResultDTO> results = client.pollForResults(sessionGuid);
for (ResultDTO result : results) {
    // store the result in your scoring program
}
if (!results.isEmpty()) {
    client.acceptQueueData(sessionGuid, DataConnectorResponseData.Results);
}
```

All methods return a `ScoringProgramResponse` (poll methods return lists of DTOs) and never throw
on communication problems: inspect `dataType`/`errorType` on the response, exactly like with the
.NET client. Methods are synchronous; one request is in flight at a time per client instance.

## Getting started sample

[GettingStarted.java](src/test/java/nl/bridgemate/dataconnector/examples/GettingStarted.java)
(test scope, so it stays out of the published jar) is a small console application that exercises
the whole workflow against a live Data Connector — use it as a template for your own scoring
program:

```
mvnw test-compile exec:java               # or run/debug GettingStarted from your IDE
```

Mind that **"Initialize event" starts Bridgemate Control Software** and creates a small test
event (1 section, 2 tables, 3 rounds, 8 players). The poll queues only carry data once BCS
produces it: enter a result in BCS (or on a Bridgemate) and then poll for results here. The
sample prints every request and response envelope (wire trace, toggleable), which is the fastest
way to learn the protocol.

### Debugging in Visual Studio Code

Open this folder in VS Code with a JDK 11+ installed and accept the recommended
"Extension Pack for Java". After the Java language server finishes importing the Maven project,
press <kbd>F5</kbd> with "Java: getting started (interactive)" selected — launch configurations
are provided in `.vscode/launch.json`. Set a breakpoint in `DataConnectorClient.sendRequest()`
to watch every envelope being built and sent.

## Validation

The DTOs can be validated before they are sent: `DtoValidator.validate(dto)` (with an extra
argument for `ParticipationDTO` and `SessionDTO`) fills the DTO's `validationMessages` and
returns whether the DTO is valid. Validating client-side is advisory — it catches problems
before a round trip — but the Data Connector service re-validates authoritatively and rejects
invalid data with `ErrorType.Validation`, so a client must still handle that response.

Sections created with `HasExplicitParticipations` carry the complete seating for every round as
`ParticipationDTO`s with their `RoundNumber` set; for all other sections participations may only
carry round number zero or one (the first-round seating) and BCS calculates the remaining rounds
from the movement.

Unlike the `dto` package, the validators in `nl.bridgemate.dataconnector.validation` are
hand-written ports of the .NET client's `Validate()` methods, kept in parity by generated golden
fixtures (`src/test/resources/fixtures/validation`) that assert the exact boolean result and
message texts, in order, for every rule.

## Documentation

The protocol, the procedures (initializing an event, updating movements, the queues) and all DTOs
are described in the
[Bridgemate Data Connector developer's guide](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client/blob/master/Documentation/MD/index.md).
The DTO classes in `nl.bridgemate.dataconnector.dto` carry the same names as the guide; the JSON
property names (PascalCase) are mapped through Jackson annotations.

## Scope

This first release covers the core workflow: `connect`/`ping`, `initialize`, `continueEvent`,
`updateMovement`, `updateScoringGroups`, the `send*` methods (results, player data,
participations, handrecords, TD calls, Bridgemate 2/3 settings), the `pollFor*` methods and
`acceptQueueData`. BCS management commands are not wrapped yet; `sendRequest()` is public for
anything the client does not cover.

## Compatibility

| Library version | Data Connector / BCS |
| --- | --- |
| 1.x | BCS 5.x (Data Connector with http support) |

## Development

The `dto` package and `src/test/resources/fixtures` are **generated** from the .NET client
repository (`tools/DtoGenerator` there) — do not edit them by hand. The fixture tests assert
structural JSON equality with the exact bytes the .NET client produces.

```
mvn test
```

## Other platforms and support

The same client exists for
[.NET](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client) (the
reference implementation, including the scoring program emulator),
[PHP](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client-PHP) and
[Python](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client-Python).
Questions are welcome in the
[Discussions](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client/discussions)
of the main repository; see [SUPPORT.md](SUPPORT.md).

## License

Copyright © Bridge Systems BV. This library is free software, licensed under the
**GNU Lesser General Public License v3.0 only** (LGPL-3.0-only) — see [LICENSE](LICENSE) —
like the [.NET reference client](https://github.com/BridgeSystems/Bridgemate-Data-Connector-Scoring-Program-Client).
