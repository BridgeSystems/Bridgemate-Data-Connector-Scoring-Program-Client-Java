package nl.bridgemate.dataconnector.validation;

import nl.bridgemate.dataconnector.dto.Bridgemate2SettingsDTO;
import nl.bridgemate.dataconnector.dto.Bridgemate3SettingsDTO;
import nl.bridgemate.dataconnector.dto.BridgemateSettingsDTO;
import nl.bridgemate.dataconnector.dto.ContinueDTO;
import nl.bridgemate.dataconnector.dto.HandrecordDTO;
import nl.bridgemate.dataconnector.dto.InitDTO;
import nl.bridgemate.dataconnector.dto.ParticipationDTO;
import nl.bridgemate.dataconnector.dto.PlayerDataDTO;
import nl.bridgemate.dataconnector.dto.ResultDTO;
import nl.bridgemate.dataconnector.dto.RoundDTO;
import nl.bridgemate.dataconnector.dto.ScoringGroupDTO;
import nl.bridgemate.dataconnector.dto.SectionDTO;
import nl.bridgemate.dataconnector.dto.SectionUpdateDTO;
import nl.bridgemate.dataconnector.dto.SessionDTO;
import nl.bridgemate.dataconnector.dto.TableDTO;
import nl.bridgemate.dataconnector.dto.TableDirection;
import nl.bridgemate.dataconnector.dto.TdCallDTO;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client-side validation for the DTOs, hand-ported from the Validate() methods of the .NET
 * reference client (SharedDTO). The boolean result and the ValidationMessages (text and order)
 * are asserted to be byte-identical to the .NET implementation by the golden fixtures in
 * src/test/resources/fixtures/validation. Deliberate quirks in the original messages
 * ("validation errrors", inconsistent spacing, a truncated sentence) are therefore reproduced
 * on purpose - do not "fix" them here.
 *
 * <p>Each validate method assigns the DTO's validationMessages field and returns true when the
 * DTO is valid, exactly like the .NET methods do.
 */
public final class DtoValidator {

    private DtoValidator() {
    }

    private static final Pattern SECTION_LETTERS_PATTERN = Pattern.compile("^([A-Z])\\1{0,2}$");

    /** "AKQJT98765432" with its characters sorted, as the .NET validator computes it. */
    private static final String CORRECT_CARDS = "23456789AJKQT";

    private static final Set<Integer> VALID_SCORING_METHODS = new HashSet<>(Arrays.asList(
            ScoringGroupDTO.ScoringType_Pairs,
            ScoringGroupDTO.ScoringType_Imp2_Weighted, ScoringGroupDTO.ScoringType_Imp2_10Percent,
            ScoringGroupDTO.ScoringType_Imp2_NoCorrection,
            ScoringGroupDTO.ScoringType_Imp3_Weighted, ScoringGroupDTO.ScoringType_Imp3_10Percent,
            ScoringGroupDTO.ScoringType_Imp3_NoCorrection,
            ScoringGroupDTO.ScoringType_XImp2_Total, ScoringGroupDTO.ScoringType_XImp2_Average,
            ScoringGroupDTO.ScoringType_XImp3_Total, ScoringGroupDTO.ScoringType_XImp3_Average,
            ScoringGroupDTO.ScoringType_TeamImps, ScoringGroupDTO.ScoringType_TeamVPDiscrete,
            ScoringGroupDTO.ScoringType_TeamVPContinuous,
            ScoringGroupDTO.ScoringType_Bam, ScoringGroupDTO.ScoringType_Patton));

    // ---------------------------------------------------------------------------------------------
    // ParticipationDTO
    // ---------------------------------------------------------------------------------------------

    /**
     * Validates a ParticipationDTO.
     *
     * @param allowPlayerNumberAndName when false (the value used for participations nested in an
     *                                 InitDTO or SectionUpdateDTO) specifying both a player number
     *                                 and a last name is an error.
     */
    public static boolean validate(ParticipationDTO dto, boolean allowPlayerNumberAndName) {
        List<String> messages = new ArrayList<>();
        if (dto.sessionGuid == null || dto.sessionGuid.length() != 32) {
            messages.add("Invalid SessionGuid (" + nz(dto.sessionGuid)
                    + "). The value must be in capitals and be exactly 32 characters long.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        int directionValue = direction(dto).getValue();
        if (directionValue < 1 || directionValue > 4) {
            messages.add("Invalid Direction (" + direction(dto).name() + "). The value must be between 1 and 4.");
        }
        if (dto.tableNumber < 1) {
            messages.add("Invalid TableNumber (" + dto.tableNumber + "). The value must be greater than zero.");
        }
        if (dto.roundNumber < 0) {
            messages.add("Invalid RoundNumber (" + dto.roundNumber + "). The value cannot be negative.");
        }
        if (isNullOrWhiteSpace(dto.lastName) && isNullOrWhiteSpace(dto.playerNumber)) {
            messages.add("Either the LastName or the PlayerNumber must be specified.");
        }
        if (!allowPlayerNumberAndName && !isNullOrWhiteSpace(dto.lastName) && !isNullOrWhiteSpace(dto.playerNumber)) {
            messages.add("Either the LastName or the PlayerNumber must be specified, but not both.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    /**
     * The ParticipationDTO.ToString() of the .NET client, reproduced exactly (null strings
     * interpolate as empty). It is embedded in InitDTO and SectionUpdateDTO messages.
     */
    private static String participationToString(ParticipationDTO dto) {
        return (dto.isPlayerSwap ? "SWAP " : "") + nz(dto.sectionLetters) + dto.tableNumber
                + " " + direction(dto).name() + " round " + dto.roundNumber
                + ": " + nz(dto.playerNumber) + " " + nz(dto.firstName) + " " + nz(dto.lastName);
    }

    private static TableDirection direction(ParticipationDTO dto) {
        return dto.direction == null ? TableDirection.None : dto.direction;
    }

    // ---------------------------------------------------------------------------------------------
    // PlayerDataDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a PlayerDataDTO. */
    public static boolean validate(PlayerDataDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (isNullOrWhiteSpace(dto.playerNumber)) {
            messages.add("The PlayerNumber is required.");
        }
        if (isNullOrWhiteSpace(dto.lastName)) {
            messages.add("The LastName is required.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // RoundDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a RoundDTO. */
    public static boolean validate(RoundDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.tableNumber <= 0) {
            messages.add("TableNumber (" + dto.tableNumber + ") must be greater than zero.");
        }
        if (dto.roundNumber <= 0) {
            messages.add("RoundNumber (" + dto.roundNumber + ") must be greater than zero.");
        }
        if ((dto.pairNS == 0 || dto.pairEW == 0) && (dto.lowBoardNumber > 0 || dto.highBoardNumber > 0)) {
            messages.add("No boards may be specified for a sit-out round or an empty round.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // TableDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a TableDTO, cascading into its rounds. */
    public static boolean validate(TableDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.tableNumber <= 0) {
            messages.add("TableNumber (" + dto.tableNumber + ") must be greater than zero.");
        }
        // Like the .NET original, Rounds is required: a null Rounds array throws.
        List<Integer> roundNumbers = new ArrayList<>();
        for (RoundDTO round : dto.rounds) {
            roundNumbers.add(round.roundNumber);
        }
        Collections.sort(roundNumbers);
        if (!roundNumbers.isEmpty()) {
            if (new HashSet<>(roundNumbers).size() != dto.rounds.length) {
                messages.add("The roundnumbers on a table must be unique.");
            }
            Set<Integer> differences = new HashSet<>();
            for (int index = 0; index < roundNumbers.size(); index++) {
                differences.add(roundNumbers.get(index) - index);
            }
            if (differences.size() != 1) {
                messages.add("The roundnumbers must be consecutive.");
            }
            if (roundNumbers.get(0) > 1) {
                messages.add("The round numbers must start with 1, but the lowest round is " + roundNumbers.get(0));
            }
        }
        for (RoundDTO round : dto.rounds) {
            if (!Objects.equals(round.sessionGuid, dto.sessionGuid)) {
                messages.add("Round " + round.roundNumber + " on table '" + nz(dto.sectionLetters) + dto.tableNumber
                        + "' must have SessionGuid '" + nz(dto.sessionGuid)
                        + "' but it is '" + nz(round.sessionGuid) + "'");
            }
            if (!Objects.equals(round.sectionLetters, dto.sectionLetters)) {
                messages.add("Round " + round.roundNumber + " on table ' " + nz(dto.sectionLetters) + " " + dto.tableNumber
                        + "' must have SectionLetters '" + nz(dto.sectionLetters)
                        + "' but it is '" + nz(round.sectionLetters) + "'");
            }
            if (round.tableNumber != dto.tableNumber) {
                messages.add("Round " + round.roundNumber + " on table ' " + nz(dto.sectionLetters) + " " + dto.tableNumber
                        + "' must have TableNumber '" + dto.tableNumber
                        + "' but it is '" + round.tableNumber + "'");
            }
            if (!validate(round)) {
                messages.add("Round " + round.roundNumber + " on '" + nz(dto.sectionLetters) + dto.tableNumber
                        + "' has validation errrors: " + String.join("; ", round.validationMessages) + ".");
            }
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // SectionDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a SectionDTO, cascading into its tables and rounds. */
    public static boolean validate(SectionDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.letters)) {
            messages.add("Invalid Letters (" + nz(dto.letters) + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.scoringGroupNumber <= 0) {
            messages.add("ScoringGroupNumber (" + dto.scoringGroupNumber + ") must be greater than zero.");
        }
        if (dto.missingPair < 0 && dto.winners != 2) {
            messages.add("MissingPair (" + dto.missingPair + ") must at least be zero for a one winner section.");
        }
        if (dto.winners < 1 || dto.winners > 2) {
            messages.add("Invalid Winners (" + dto.winners + "). Valid values are 1 or 2.");
        }
        if (dto.gameType != 10 && dto.gameType != 20 && dto.gameType != 30) {
            messages.add("Invalid GameType (" + dto.gameType + "). Valid values are 10, 20 or 30.");
        }
        if (dto.isCombiSection) {
            if (!isValidSectionLetters(dto.northSouthPairSectionLetters)) {
                messages.add("Invalid NorthSouthPairSectionLetters (" + nz(dto.northSouthPairSectionLetters)
                        + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
            }
            if (!isValidSectionLetters(dto.eastWestPairSectionLetters)) {
                messages.add("Invalid EastWestPairSectionLetters (" + nz(dto.eastWestPairSectionLetters)
                        + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
            }
        }
        TableDTO[] tables = dto.tables == null ? new TableDTO[0] : dto.tables;
        validateTablesOfSection(messages, tables, dto.eWMoveBeforePlay, dto.sessionGuid, dto.letters);
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    /**
     * The table checks shared verbatim between SectionDTO.Validate and SectionUpdateDTO.Validate
     * in the .NET client (EWMoveBeforePlay bound, per-table guid/letters/cascade, duplicate
     * table numbers).
     */
    private static void validateTablesOfSection(List<String> messages, TableDTO[] tables, int ewMoveBeforePlay,
                                                String sessionGuid, String letters) {
        if (Math.abs(ewMoveBeforePlay) > tables.length) {
            messages.add("The absolute value of EWMoveBeforePlay (" + ewMoveBeforePlay
                    + ") cannot be higher than the number of tables (" + tables.length + ").");
        }
        for (TableDTO table : tables) {
            if (!Objects.equals(table.sessionGuid, sessionGuid)) {
                messages.add("Table '" + nz(letters) + table.tableNumber
                        + "' must have SessionGuid '" + nz(sessionGuid)
                        + "' but it is '" + nz(table.sessionGuid) + "'");
            }
            if (!Objects.equals(table.sectionLetters, letters)) {
                messages.add("Table ' " + nz(letters) + " " + table.tableNumber
                        + "' must have SectionLetters '" + nz(letters)
                        + "' but it is '" + nz(table.sectionLetters) + "'");
            }
            if (!validate(table)) {
                messages.add("Table '" + nz(table.sectionLetters) + table.tableNumber
                        + "' has validation errrors: " + String.join("; ", table.validationMessages) + ".");
            }
        }
        List<Integer> tableNumbers = new ArrayList<>();
        for (TableDTO table : tables) {
            tableNumbers.add(table.tableNumber);
        }
        Collections.sort(tableNumbers);
        Map<Integer, Integer> groupedTableNumbers = new LinkedHashMap<>();
        for (Integer number : tableNumbers) {
            groupedTableNumbers.merge(number, 1, Integer::sum);
        }
        for (Map.Entry<Integer, Integer> group : groupedTableNumbers.entrySet()) {
            if (group.getValue() > 1) {
                messages.add("Tablenumber " + group.getKey() + " occurs " + group.getValue() + " times. ");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SectionUpdateDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a SectionUpdateDTO, cascading into its tables, rounds and participations. */
    public static boolean validate(SectionUpdateDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.letters)) {
            messages.add("Invalid Letters (" + nz(dto.letters) + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.isDeleted) {
            if (dto.tables != null && dto.tables.length > 0) {
                messages.add("A deleted section cannot contain tables.");
            }
            dto.validationMessages = messages.toArray(new String[0]);
            return messages.isEmpty();
        }
        if (dto.scoringGroupNumber <= 0) {
            messages.add("ScoringGroupNumber (" + dto.scoringGroupNumber + ") must be greater than zero.");
        }
        if (!VALID_SCORING_METHODS.contains(dto.scoringGroupScoringMethod)) {
            messages.add("Invalid ScoringGroupScoringMethod (" + dto.scoringGroupScoringMethod
                    + "). The value must be a multiple of 10 between 10 and 70 or 51. ");
        }
        if (dto.missingPair < 0 && dto.winners != 2) {
            messages.add("MissingPair (" + dto.missingPair + ") must at least be zero for a one winner section.");
        }
        if (dto.winners < 1 || dto.winners > 2) {
            messages.add("Invalid Winners (" + dto.winners + "). Valid values are 1 or 2.");
        }
        if (dto.gameType != 10 && dto.gameType != 20 && dto.gameType != 30) {
            messages.add("Invalid GameType (" + dto.gameType + "). Valid values are 10, 20 or 30.");
        }
        if (dto.isCombiSection) {
            if (!isValidSectionLetters(dto.northSouthPairSectionLetters)) {
                messages.add("Invalid NorthSouthPairSectionLetters (" + nz(dto.northSouthPairSectionLetters)
                        + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
            }
            if (!isValidSectionLetters(dto.eastWestPairSectionLetters)) {
                messages.add("Invalid EastWestPairSectionLetters (" + nz(dto.eastWestPairSectionLetters)
                        + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
            }
        }
        TableDTO[] tables = dto.tables == null ? new TableDTO[0] : dto.tables;
        validateTablesOfSection(messages, tables, dto.eWMoveBeforePlay, dto.sessionGuid, dto.letters);
        ParticipationDTO[] participations = dto.participations == null ? new ParticipationDTO[0] : dto.participations;
        if (dto.hasExplicitParticipations && participations.length == 0) {
            messages.add("HasExplicitParticipations is set for section '" + nz(dto.letters)
                    + "', but the update does not carry any Participations. "
                    + "An update for such a section must include the complete seating for all rounds.");
        }
        for (ParticipationDTO participation : participations) {
            if (!validate(participation, false)) {
                messages.add("Participation '" + participationToString(participation)
                        + "' has validation errors: " + String.join("; ", participation.validationMessages) + ".");
            }
            if (!Objects.equals(participation.sessionGuid, dto.sessionGuid)) {
                messages.add("Participation '" + participationToString(participation)
                        + "' must have SessionGuid '" + nz(dto.sessionGuid)
                        + "' but it is '" + nz(participation.sessionGuid) + "'.");
            }
            if (!Objects.equals(participation.sectionLetters, dto.letters)) {
                messages.add("Participation '" + participationToString(participation)
                        + "' must have SectionLetters '" + nz(dto.letters)
                        + "' but it is '" + nz(participation.sectionLetters) + "'.");
            }
            if (participation.roundNumber > 1 && !dto.hasExplicitParticipations) {
                messages.add("Participation '" + participationToString(participation)
                        + "' has RoundNumber " + participation.roundNumber
                        + ", but section '" + nz(dto.letters) + "' does not have HasExplicitParticipations set. "
                        + "Round numbers greater than one are only valid for sections with explicit participations.");
            }
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // ScoringGroupDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a ScoringGroupDTO, cascading into its sections. */
    public static boolean validate(ScoringGroupDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (dto.scoringGroupNumber <= 0) {
            messages.add("ScoringGroupNumber (" + dto.scoringGroupNumber + ") must be greater than zero.");
        }
        if (!VALID_SCORING_METHODS.contains(dto.scoringMethod)) {
            messages.add("Invalid ScoringMethod (" + dto.scoringMethod
                    + "). The value must be a multiple of 10 between 10 and 70 or 51. ");
        }
        if (dto.isDeleted) {
            if (dto.sections != null && dto.sections.length > 0) {
                messages.add("A scoringgroup marked for deletion must not have any sections defined.");
            }
        } else if (dto.sections == null || dto.sections.length == 0) {
            messages.add("The scoringgroup must have at least one section.");
        } else {
            Set<String> distinctLetters = new HashSet<>();
            for (SectionDTO section : dto.sections) {
                distinctLetters.add(section.letters);
            }
            if (distinctLetters.size() != dto.sections.length) {
                messages.add("The sections cannot have the same Letters");
            }
            for (SectionDTO section : dto.sections) {
                if (!Objects.equals(section.sessionGuid, dto.sessionGuid)) {
                    messages.add("Section '" + nz(section.letters)
                            + "' must have SessionGuid '" + nz(dto.sessionGuid)
                            + "' but it is '" + nz(section.sessionGuid) + "'");
                }
                if (section.scoringGroupNumber != dto.scoringGroupNumber) {
                    messages.add("Section '" + nz(section.letters)
                            + "' must have ScoringGroupNumber '" + dto.scoringGroupNumber
                            + "' but it is '" + section.scoringGroupNumber + "'");
                }
                if (!validate(section)) {
                    messages.add("Section '" + nz(section.letters)
                            + "' has validation errrors: " + String.join("; ", section.validationMessages) + ".");
                }
            }
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // SessionDTO
    // ---------------------------------------------------------------------------------------------

    /**
     * Validates a SessionDTO, cascading into its scoring groups.
     *
     * @param forAdding when true (the value used by the .NET AddSession command) the EventGuid is
     *                  required; sessions nested in an InitDTO are validated with false.
     */
    public static boolean validate(SessionDTO dto, boolean forAdding) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (forAdding) {
            if (dto.eventGuid == null || dto.eventGuid.isEmpty()) {
                messages.add("When adding a session the EventGuid must not be empty.");
            }
        }
        if (dto.eventGuid != null && (dto.eventGuid.length() != 32 || hasInvalidGuidCharacter(dto.eventGuid))) {
            messages.add("The event guid, if used, must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (dto.scoringGroups == null || dto.scoringGroups.length == 0) {
            messages.add("At least one scoringroup is required.");
            dto.validationMessages = messages.toArray(new String[0]);
            return false;
        }
        Set<Integer> distinctGroupNumbers = new HashSet<>();
        for (ScoringGroupDTO group : dto.scoringGroups) {
            distinctGroupNumbers.add(group.scoringGroupNumber);
        }
        if (distinctGroupNumbers.size() != dto.scoringGroups.length) {
            messages.add("The scoring groups cannot have the same ScoringGroupNumber");
        }
        for (ScoringGroupDTO group : dto.scoringGroups) {
            if (!Objects.equals(group.sessionGuid, dto.sessionGuid)) {
                messages.add("Scoring group with id " + group.scoringGroupNumber
                        + " must have SessionGuid '" + nz(dto.sessionGuid)
                        + "' but it is '" + nz(group.sessionGuid) + "'");
            }
            if (!validate(group)) {
                messages.add("Scoringgroup " + group.scoringGroupNumber
                        + " has validation errrors: " + String.join("; ", group.validationMessages) + ".");
            }
        }
        List<SectionDTO> sections = new ArrayList<>();
        for (ScoringGroupDTO group : dto.scoringGroups) {
            if (group.sections != null) {
                sections.addAll(Arrays.asList(group.sections));
            }
        }
        boolean anyCombiSection = false;
        for (SectionDTO section : sections) {
            if (section.isCombiSection) {
                anyCombiSection = true;
                break;
            }
        }
        if (anyCombiSection) {
            for (SectionDTO combiSection : sections) {
                if (!combiSection.isCombiSection) {
                    continue;
                }
                String[] sources = {combiSection.northSouthPairSectionLetters, combiSection.eastWestPairSectionLetters};
                if (new HashSet<>(Arrays.asList(sources)).size() != 2) {
                    messages.add("The combisection '" + nz(combiSection.letters)
                            + "' must have two different sections as its source, "
                            + "but they are for NS '" + nz(sources[0]) + "' and for EW '" + nz(sources[1]) + "'");
                }
                for (String sourceSection : sources) {
                    boolean exists = false;
                    for (SectionDTO section : sections) {
                        if (Objects.equals(section.letters, sourceSection)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        messages.add("Combisection '" + nz(combiSection.letters)
                                + "' specifies section '" + nz(sourceSection)
                                + "' as one of its source sections, but this section does not exist.");
                    }
                }
            }
        }
        if (dto.name == null || dto.name.length() < 1) {
            messages.add("The name of the session is required.");
        }
        if (dto.year < 2000) {
            messages.add("The year (" + dto.year + ") for the session must be at least 2000.");
        }
        if (dto.month < 1 || dto.month > 12) {
            messages.add("The month (" + dto.month + ") for the session must be between 1 and 12.");
        }
        if (dto.day < 1 || dto.day > 31) {
            messages.add("The day (" + dto.day + ") for the session must be between 1 and 31.");
        }
        if (dto.hour < 0 || dto.hour >= 24) {
            messages.add("The hour (" + dto.hour + ") of the day must be between 0 and 23");
        }
        if (dto.minute < 0 || dto.minute >= 60) {
            messages.add("The minute (" + dto.minute + ")must be between 0 and 59");
        }
        if (!isConstructibleDate(dto.year, dto.month, dto.day)) {
            messages.add("The date " + dto.year + "-" + dto.month + "-" + dto.day + " is invalid.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    /** Mirrors what the .NET DateTime(year, month, day) constructor accepts. */
    private static boolean isConstructibleDate(int year, int month, int day) {
        if (year < 1 || year > 9999 || month < 1 || month > 12 || day < 1) {
            return false;
        }
        return day <= YearMonth.of(year, month).lengthOfMonth();
    }

    // ---------------------------------------------------------------------------------------------
    // InitDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates an InitDTO, cascading into all nested DTOs. */
    public static boolean validate(InitDTO dto) {
        List<String> messages = new ArrayList<>();
        if (dto.commands < 0 || dto.commands > 255) {
            messages.add("The Commands (" + dto.commands + ") must be between 0 and 255.");
        }
        if (!isNullOrWhiteSpace(dto.alternativeDataFolder)) {
            if (!directoryExists(dto.alternativeDataFolder)) {
                messages.add("The specified alternative data folder ('" + dto.alternativeDataFolder
                        + "' does not exist.)");
            }
        }
        if (dto.sessions == null || dto.sessions.length == 0) {
            messages.add("At least one session is required.");
            dto.validationMessages = messages.toArray(new String[0]);
            return false;
        }
        if (dto.sessions.length > 1) {
            if (isNullOrWhiteSpace(dto.eventGuid)) {
                messages.add("If there is more than one session the EventGuid must be specified and it must be identical "
                        + "to the sessions' EventGuid.");
            } else {
                for (SessionDTO session : dto.sessions) {
                    if (!Objects.equals(session.eventGuid, dto.eventGuid)) {
                        messages.add("The EventGuid ('" + nz(session.eventGuid) + "') of session '"
                                + nz(session.name) + "' (" + nz(session.sessionGuid) + ") are not the same.");
                    }
                }
            }
        }
        if (dto.eventGuid != null && (dto.eventGuid.length() != 32 || hasInvalidGuidCharacter(dto.eventGuid))) {
            messages.add("The event guid, if used, must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        for (SessionDTO session : dto.sessions) {
            if (!validate(session, false)) {
                messages.add("Session " + nz(session.sessionGuid) + " did not validate: "
                        + String.join(", ", session.validationMessages));
            }
        }
        Map<String, Integer> groupedSectionLetters = new LinkedHashMap<>();
        for (SessionDTO session : dto.sessions) {
            for (ScoringGroupDTO scoringGroup : session.scoringGroups) {
                for (SectionDTO section : scoringGroup.sections) {
                    groupedSectionLetters.merge(section.letters, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> groupedSection : groupedSectionLetters.entrySet()) {
            if (groupedSection.getValue() > 1) {
                messages.add("Section '" + nz(groupedSection.getKey()) + "' appears " + groupedSection.getValue()
                        + " times. Each section letter must be unique.");
            }
        }
        Map<Integer, Integer> groupedScoringGroupNumbers = new LinkedHashMap<>();
        for (SessionDTO session : dto.sessions) {
            for (ScoringGroupDTO scoringGroup : session.scoringGroups) {
                groupedScoringGroupNumbers.merge(scoringGroup.scoringGroupNumber, 1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> groupedScoringGroup : groupedScoringGroupNumbers.entrySet()) {
            if (groupedScoringGroup.getValue() > 1) {
                messages.add("Scoringgroup '" + groupedScoringGroup.getKey() + "' appears "
                        + groupedScoringGroup.getValue() + " times. Each scoringgroup number must be unique.");
            }
        }
        if (dto.playerData != null && dto.playerData.length > 0) {
            List<String> sessionGuids = new ArrayList<>();
            for (SessionDTO session : dto.sessions) {
                sessionGuids.add(session.sessionGuid);
            }
            for (PlayerDataDTO data : dto.playerData) {
                if (!validate(data)) {
                    messages.add("PlayerData '" + nz(data.firstName) + " " + nz(data.lastName)
                            + " (" + nz(data.sessionGuid) + "-" + nz(data.playerNumber) + ")': "
                            + String.join(", ", data.validationMessages) + " ");
                }
                if (!sessionGuids.contains(data.sessionGuid)) {
                    messages.add("PlayerData.SessionGuid ('" + nz(data.sessionGuid)
                            + "') must be one of the sessions' guids (" + joinNz(", ", sessionGuids) + ").");
                }
            }
            Map<String, List<PlayerDataDTO>> groupedPlayerNumbers = new LinkedHashMap<>();
            for (PlayerDataDTO data : dto.playerData) {
                groupedPlayerNumbers.computeIfAbsent(nz(data.playerNumber), key -> new ArrayList<>()).add(data);
            }
            for (Map.Entry<String, List<PlayerDataDTO>> id : groupedPlayerNumbers.entrySet()) {
                if (id.getValue().size() > 1) {
                    PlayerDataDTO first = id.getValue().get(0);
                    messages.add("Duplicate (" + id.getValue().size() + ") entries for player data '"
                            + nz(first.firstName) + "+" + nz(first.lastName) + "'");
                }
            }
        }
        if (dto.participations != null && dto.participations.length > 0) {
            if (dto.playerData == null) {
                messages.add("No PlayerDataDTO defined, but there are " + dto.participations.length
                        + " ParticipationDTOs defined. "
                        + "Each ParticipationDTO with its SessionGuid and PlayerNumber properties set "
                        + "must have a corresponding PlayerDataDTO that specifies at least its name.");
            }
            for (ParticipationDTO participation : dto.participations) {
                if (!validate(participation, false)) {
                    messages.add("ParticipationDTO  '" + nz(participation.sessionGuid) + "-"
                            + nz(participation.playerNumber) + "': "
                            + String.join(", ", participation.validationMessages) + " ");
                }
            }
            for (ParticipationDTO participation : dto.participations) {
                String id = nz(participation.sessionGuid) + nz(participation.playerNumber);
                if (id.isEmpty() || id.equals(nz(participation.sessionGuid))) {
                    continue;
                }
                boolean hasPlayerData = false;
                if (dto.playerData != null) {
                    for (PlayerDataDTO data : dto.playerData) {
                        if ((nz(data.sessionGuid) + nz(data.playerNumber)).equals(id)) {
                            hasPlayerData = true;
                            break;
                        }
                    }
                }
                if (hasPlayerData) {
                    continue;
                }
                messages.add("ParticipationDTO '" + nz(participation.sessionGuid) + "-"
                        + nz(participation.playerNumber) + "' has no corresponding PlayerDataDTO");
            }
            Set<String> explicitSectionKeys = new HashSet<>();
            for (SessionDTO session : dto.sessions) {
                ScoringGroupDTO[] scoringGroups =
                        session.scoringGroups == null ? new ScoringGroupDTO[0] : session.scoringGroups;
                for (ScoringGroupDTO scoringGroup : scoringGroups) {
                    SectionDTO[] scoringGroupSections =
                            scoringGroup.sections == null ? new SectionDTO[0] : scoringGroup.sections;
                    for (SectionDTO section : scoringGroupSections) {
                        if (section.hasExplicitParticipations) {
                            explicitSectionKeys.add(nz(section.sessionGuid) + "-" + nz(section.letters));
                        }
                    }
                }
            }
            for (ParticipationDTO participation : dto.participations) {
                if (participation.roundNumber <= 1) {
                    continue;
                }
                if (!explicitSectionKeys.contains(
                        nz(participation.sessionGuid) + "-" + nz(participation.sectionLetters))) {
                    messages.add("ParticipationDTO '" + participationToString(participation)
                            + "' has RoundNumber " + participation.roundNumber
                            + ", but section '" + nz(participation.sectionLetters)
                            + "' does not have HasExplicitParticipations set. "
                            + "Round numbers greater than one are only valid for sections with explicit participations.");
                }
            }
        }
        if (dto.handrecords != null && dto.handrecords.length > 0) {
            for (HandrecordDTO handrecord : dto.handrecords) {
                if (!validate(handrecord)) {
                    messages.add("HandrecordDTO  '" + nz(handrecord.sectionLetters) + "-" + handrecord.boardNumber
                            + "': " + String.join(", ", handrecord.validationMessages) + " ");
                }
            }
        }
        if (dto.bridgemate2Settings != null && dto.bridgemate2Settings.length > 0) {
            boolean settingsErrors = false;
            for (Bridgemate2SettingsDTO settings : dto.bridgemate2Settings) {
                if (!validate(settings)) {
                    settingsErrors = true;
                    messages.add("Bridgemate2SettingsDTO  '" + nz(settings.sectionLetters) + "': "
                            + String.join(", ", settings.validationMessages) + " ");
                }
            }
            if (!settingsErrors) {
                Map<String, Integer> sectionLettersGroups = new LinkedHashMap<>();
                for (Bridgemate2SettingsDTO settings : dto.bridgemate2Settings) {
                    sectionLettersGroups.merge(settings.sectionLetters, 1, Integer::sum);
                }
                for (Map.Entry<String, Integer> group : sectionLettersGroups.entrySet()) {
                    if (group.getValue() > 1) {
                        messages.add("Duplicate (" + group.getValue() + ") settings for section '"
                                + nz(group.getKey()) + "'");
                    }
                }
            }
        }
        if (dto.bridgemate3Settings != null && dto.bridgemate3Settings.length > 0) {
            for (Bridgemate3SettingsDTO settings : dto.bridgemate3Settings) {
                if (!validate(settings)) {
                    messages.add("Bridgemate3SettingsDTO  '" + nz(settings.sectionLetters) + "': "
                            + String.join(", ", settings.validationMessages) + " ");
                }
            }
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // HandrecordDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a HandrecordDTO. */
    public static boolean validate(HandrecordDTO dto) {
        List<String> messages = new ArrayList<>();
        if (dto.sessionGuid == null || dto.sessionGuid.length() != 32 || hasInvalidGuidCharacter(dto.sessionGuid)) {
            messages.add("The guid (" + nz(dto.sessionGuid)
                    + ") must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (dto.scoringGroupNumber <= 0) {
            messages.add("ScoringGroupNumber (" + dto.scoringGroupNumber + ") must be greater than zero.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.boardNumber <= 0) {
            messages.add("BoardNumber (" + dto.boardNumber + ") must be greater than zero.");
        }
        // All sixteen suits, in the order the .NET validator declares them.
        String[][] allSuits = {
                {"NorthClubs", dto.northClubs}, {"NorthDiamonds", dto.northDiamonds},
                {"NorthHearts", dto.northHearts}, {"NorthSpades", dto.northSpades},
                {"EastClubs", dto.eastClubs}, {"EastDiamonds", dto.eastDiamonds},
                {"EastHearts", dto.eastHearts}, {"EastSpades", dto.eastSpades},
                {"SouthClubs", dto.southClubs}, {"SouthDiamonds", dto.southDiamonds},
                {"SouthHearts", dto.southHearts}, {"SouthSpades", dto.southSpades},
                {"WestClubs", dto.westClubs}, {"WestDiamonds", dto.westDiamonds},
                {"WestHearts", dto.westHearts}, {"WestSpades", dto.westSpades}
        };
        List<String> invalidSuits = new ArrayList<>();
        for (String[] suit : allSuits) {
            if (suit[1] == null || hasInvalidCard(suit[1])) {
                invalidSuits.add(suit[0]);
            }
        }
        if (!invalidSuits.isEmpty()) {
            messages.add("Invalid suits (null value or invalid card) in " + String.join(", ", invalidSuits)
                    + ". Valid cards are '" + CORRECT_CARDS + "'");
        } else {
            // Note: "clubs" is lowercase in the .NET messages, the other suits are capitalized.
            checkSuit(messages, "clubs", dto.northClubs, dto.eastClubs, dto.southClubs, dto.westClubs);
            checkSuit(messages, "Diamonds", dto.northDiamonds, dto.eastDiamonds, dto.southDiamonds, dto.westDiamonds);
            checkSuit(messages, "Hearts", dto.northHearts, dto.eastHearts, dto.southHearts, dto.westHearts);
            checkSuit(messages, "Spades", dto.northSpades, dto.eastSpades, dto.southSpades, dto.westSpades);
            checkHand(messages, "North", dto.northClubs, dto.northDiamonds, dto.northHearts, dto.northSpades);
            checkHand(messages, "East", dto.eastClubs, dto.eastDiamonds, dto.eastHearts, dto.eastSpades);
            checkHand(messages, "South", dto.southClubs, dto.southDiamonds, dto.southHearts, dto.southSpades);
            checkHand(messages, "West", dto.westClubs, dto.westDiamonds, dto.westHearts, dto.westSpades);
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    private static boolean hasInvalidCard(String suit) {
        for (int index = 0; index < suit.length(); index++) {
            if (CORRECT_CARDS.indexOf(suit.charAt(index)) < 0) {
                return true;
            }
        }
        return false;
    }

    private static void checkSuit(List<String> messages, String suitName,
                                  String north, String east, String south, String west) {
        String allCards = north + east + south + west;
        if (allCards.length() != 13) {
            messages.add("Invalid number of " + suitName + " (" + allCards.length()
                    + "). The suit must add up to 13 cards.");
        } else {
            char[] cards = allCards.toCharArray();
            Arrays.sort(cards);
            String suitString = new String(cards);
            if (!suitString.equals(CORRECT_CARDS)) {
                messages.add("Duplicate cards in the " + suitName + " suit '" + suitString + "'");
            }
        }
    }

    private static void checkHand(List<String> messages, String handName,
                                  String clubs, String diamonds, String hearts, String spades) {
        int numberOfCards = clubs.length() + diamonds.length() + hearts.length() + spades.length();
        if (numberOfCards != 13) {
            messages.add("Invalid number of cards for " + handName + " (" + numberOfCards
                    + "). The hand must add up to 13 cards.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ResultDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a ResultDTO. */
    public static boolean validate(ResultDTO dto) {
        List<String> messages = new ArrayList<>();
        if (dto.sessionGuid == null || dto.sessionGuid.isEmpty() || dto.sessionGuid.length() != 32) {
            // The .NET message really ends mid-sentence.
            messages.add("Invalid SessionGuid (" + nz(dto.sessionGuid)
                    + "). The value must be in capitals and be exactly");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.tableNumber < 1) {
            messages.add("Invalid TableNumber (" + dto.tableNumber + "). The value must be greater than zero.");
        }
        if (dto.roundNumber < 1) {
            messages.add("Invalid RoundNumber (" + dto.roundNumber + "). The value must be greater than zero.");
        }
        if (dto.boardNumber < 1) {
            messages.add("Invalid BoardNumber (" + dto.boardNumber + "). The value must be greater than zero.");
        }
        if (dto.isDeleted) {
            dto.validationMessages = messages.toArray(new String[0]);
            return messages.isEmpty();
        }
        if (dto.pairEastWest < 1) {
            messages.add("Invalid PairEastWest (" + dto.pairEastWest + "). The value must be greater than zero.");
        }
        if (dto.pairNorthSouth < 1) {
            messages.add("Invalid PairNorthSouth (" + dto.pairNorthSouth + "). The value must be greater than zero.");
        }
        if (dto.declaringPair != dto.pairNorthSouth && dto.declaringPair != dto.pairEastWest) {
            messages.add("Invalid DeclaringPair (" + dto.declaringPair + "). The value must be either "
                    + dto.pairNorthSouth + " or " + dto.pairEastWest + ".");
        }
        if (dto.level >= 1 && (dto.declarerDirection < 1 || dto.declarerDirection > 4)) {
            messages.add("Invalid DeclarerDirection (" + dto.declarerDirection
                    + "). The value must be between 1 and 4.");
        }
        if (dto.level >= 1 && (dto.scoringDirection < 1 || dto.scoringDirection > 3)) {
            messages.add("Invalid ScoringDirection (" + dto.scoringDirection
                    + "). The value must be between 1 and 3.");
        }
        if (dto.level < -10 || dto.level > 7) {
            messages.add("Invalid Level (" + dto.level + "). The value must be between -10 and +7.");
        }
        if ((dto.denomination < 1 || dto.denomination > 5) && dto.level >= 1) {
            messages.add("Invalid Denomination (" + dto.denomination + "). The value must be between 1 and 5.");
        }
        if (dto.stake < 0 || dto.stake > 2) {
            messages.add("Invalid Stake (" + dto.stake + "). The value must be between 0 and 2.");
        }
        if (dto.totalTricks < 0 || dto.totalTricks > 13) {
            messages.add("Invalid TotalTricks (" + dto.totalTricks + "). The value must be between 0 and 13.");
        }
        if ((dto.leadCardRank < 2 || dto.leadCardRank > 14) && dto.leadCardRank != 0) {
            messages.add("Invalid LeadCardRank (" + dto.leadCardRank
                    + "). The value must be between 2 and 14 (Ace).");
        } else if ((dto.leadCardSuit < 1 || dto.leadCardSuit > 4) && dto.leadCardRank != 0) {
            messages.add("Invalid LeadCardSuit (" + dto.leadCardSuit
                    + "). If LeadCardRank>0  the value must be between 1 and 4.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // TdCallDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a TdCallDTO. */
    public static boolean validate(TdCallDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        if (dto.tableNumber <= 0) {
            messages.add("TableNumber (" + dto.tableNumber + ") must be greater than zero.");
        }
        if (dto.roundNumber <= 0) {
            messages.add("RoundNumber (" + dto.roundNumber + ") must be greater than zero.");
        }
        if (dto.status <= 0 || dto.status > 4) {
            messages.add("Invalide Status:(" + dto.status + "). Valid values are 1,2,3 or 4.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // ContinueDTO
    // ---------------------------------------------------------------------------------------------

    /** Validates a ContinueDTO. */
    public static boolean validate(ContinueDTO dto) {
        List<String> messages = new ArrayList<>();
        int mask = 255 & ~InitDTO.StartBCS & ~InitDTO.Command_StartReading & ~InitDTO.Command_ClearData
                & ~InitDTO.Command_Minimize & ~InitDTO.Command_AutoShutDownBPC & ~InitDTO.Command_LogLevel_Debug;
        if ((dto.commands & mask) != 0) {
            messages.add("Invalid value for Commands (" + dto.commands + "). "
                    + "Valid values are a sum of 0 and/or 1 and/or 4 and/or 128.");
        }
        if (!isNullOrWhiteSpace(dto.alternativeDataFolder)) {
            if (!directoryExists(dto.alternativeDataFolder)) {
                messages.add("The specified alternative data folder ('" + dto.alternativeDataFolder
                        + "' does not exist.)");
            }
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Bridgemate settings
    // ---------------------------------------------------------------------------------------------

    /** Validates a Bridgemate2SettingsDTO. */
    public static boolean validate(Bridgemate2SettingsDTO dto) {
        List<String> messages = baseSettingsMessages(dto);
        if (isNullOrWhiteSpace(dto.bM2PINcode) || dto.bM2PINcode.length() != 4 || !intParses(dto.bM2PINcode)) {
            messages.add("Invalid BM2PINcode ('" + nz(dto.bM2PINcode) + "'). The pincode must be four digits.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    /** Validates a Bridgemate3SettingsDTO. */
    public static boolean validate(Bridgemate3SettingsDTO dto) {
        List<String> messages = baseSettingsMessages(dto);
        if (dto.bM3ScreenDimMode < 0 || dto.bM3ScreenDimMode > 15) {
            messages.add("Invalid BM3ScreenDimMode (" + dto.bM3ScreenDimMode + "). Value must be between 0 and 15");
        }
        if (dto.bM3ScreenBrightness < 1 || dto.bM3ScreenBrightness > 7) {
            messages.add("Invalid BM3ScreenBrightness (" + dto.bM3ScreenBrightness + "). Value must be between 1 and 7");
        }
        if (dto.bM3SleepMode < 0 || dto.bM3SleepMode > 120) {
            messages.add("Invalid BM3SleepMode (" + dto.bM3SleepMode + "). Value must be between 0 and 120");
        }
        if (dto.bM3AudioVolume < 0 || dto.bM3AudioVolume > 7) {
            messages.add("Invalid BM3AudioVolume (" + dto.bM3AudioVolume + "). Value must be between 0 and 7");
        }
        if (isNullOrWhiteSpace(dto.bM3PINcode) || dto.bM3PINcode.length() != 4 || !intParses(dto.bM3PINcode)) {
            messages.add("Invalid BM3PINcode ('" + nz(dto.bM3PINcode) + "'). The pincode must be four digits.");
        }
        dto.validationMessages = messages.toArray(new String[0]);
        return messages.isEmpty();
    }

    /** The BridgemateSettingsDTO base class checks shared by the BM2 and BM3 settings. */
    private static List<String> baseSettingsMessages(BridgemateSettingsDTO dto) {
        List<String> messages = new ArrayList<>();
        if (isInvalidStrictGuid(dto.sessionGuid)) {
            messages.add("The guid must be exactly 32 character long and can only contain capital A to F or digits 0 to 9.");
        }
        if (!isValidSectionLetters(dto.sectionLetters)) {
            messages.add("Invalid SectionLetters (" + nz(dto.sectionLetters)
                    + "). Valid values are: 'A-Z', 'AA-ZZ' or 'AAA','ZZZ'");
        }
        return messages;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------------

    /** C# string interpolation renders a null string as empty; Java concatenation would print "null". */
    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String joinNz(String separator, List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(nz(value));
        }
        return builder.toString();
    }

    /** Mirrors C# string.IsNullOrWhiteSpace. */
    private static boolean isNullOrWhiteSpace(String value) {
        return value == null || value.isBlank();
    }

    /** The strict session guid rule: exactly 32 characters, capitals A-F or digits only. */
    private static boolean isInvalidStrictGuid(String guid) {
        return guid == null || guid.length() != 32 || hasInvalidGuidCharacter(guid);
    }

    private static boolean hasInvalidGuidCharacter(String guid) {
        for (int index = 0; index < guid.length(); index++) {
            char character = guid.charAt(index);
            if (!((character >= 'A' && character <= 'F') || (character >= '0' && character <= '9'))) {
                return true;
            }
        }
        return false;
    }

    /** The section letters rule: A-Z, or the same letter two or three times (regex with backreference). */
    private static boolean isValidSectionLetters(String letters) {
        return SECTION_LETTERS_PATTERN.matcher(nz(letters)).matches();
    }

    /**
     * Mirrors C# int.TryParse: optional surrounding whitespace, optional leading sign, must fit
     * in a 32-bit int.
     */
    private static boolean intParses(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(trimmed);
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** Mirrors C# Directory.Exists: false for missing paths and for any path that cannot be probed. */
    private static boolean directoryExists(String path) {
        try {
            return Files.isDirectory(Paths.get(path));
        } catch (Exception cannotProbe) {
            return false;
        }
    }
}
