package nl.bridgemate.dataconnector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.bridgemate.dataconnector.dto.Bridgemate2SettingsDTO;
import nl.bridgemate.dataconnector.dto.Bridgemate3SettingsDTO;
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
import nl.bridgemate.dataconnector.dto.TdCallDTO;
import nl.bridgemate.dataconnector.validation.DtoValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts that DtoValidator reproduces the .NET SharedDTO Validate() methods exactly: for every
 * golden validation fixture the boolean result and the ValidationMessages (text and order) must
 * match what the .NET client produced for the same payload and arguments.
 */
class ValidationFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures", "validation");

    private static final Map<String, Class<?>> DTO_CLASSES = new HashMap<>();

    static {
        DTO_CLASSES.put("ParticipationDTO", ParticipationDTO.class);
        DTO_CLASSES.put("PlayerDataDTO", PlayerDataDTO.class);
        DTO_CLASSES.put("RoundDTO", RoundDTO.class);
        DTO_CLASSES.put("TableDTO", TableDTO.class);
        DTO_CLASSES.put("SectionDTO", SectionDTO.class);
        DTO_CLASSES.put("SectionUpdateDTO", SectionUpdateDTO.class);
        DTO_CLASSES.put("ScoringGroupDTO", ScoringGroupDTO.class);
        DTO_CLASSES.put("SessionDTO", SessionDTO.class);
        DTO_CLASSES.put("InitDTO", InitDTO.class);
        DTO_CLASSES.put("HandrecordDTO", HandrecordDTO.class);
        DTO_CLASSES.put("ResultDTO", ResultDTO.class);
        DTO_CLASSES.put("TdCallDTO", TdCallDTO.class);
        DTO_CLASSES.put("ContinueDTO", ContinueDTO.class);
        DTO_CLASSES.put("Bridgemate2SettingsDTO", Bridgemate2SettingsDTO.class);
        DTO_CLASSES.put("Bridgemate3SettingsDTO", Bridgemate3SettingsDTO.class);
    }

    static Stream<Path> validationFixtures() throws IOException {
        return Files.list(FIXTURES).sorted();
    }

    @ParameterizedTest
    @MethodSource("validationFixtures")
    void validationMatchesFixture(Path path) throws Exception {
        String fileName = path.getFileName().toString();
        JsonNode fixture = MAPPER.readTree(Files.readString(path));
        String dtoName = fixture.get("Dto").asText();
        JsonNode args = fixture.get("Args");
        Class<?> dtoClass = DTO_CLASSES.get(dtoName);
        if (dtoClass == null) {
            throw new IllegalArgumentException("No DTO class mapped for '" + dtoName + "' (" + fileName + ")");
        }
        Object dto = MAPPER.treeToValue(fixture.get("Payload"), dtoClass);

        boolean valid = validate(dto, args);
        String[] validationMessages = (String[]) dto.getClass().getField("validationMessages").get(dto);

        boolean expectedValid = fixture.get("ExpectedValid").asBoolean();
        List<String> expectedMessages = new ArrayList<>();
        for (JsonNode message : fixture.get("ExpectedMessages")) {
            expectedMessages.add(message.asText());
        }
        assertEquals(expectedValid, valid, fileName + ": validation result");
        assertEquals(expectedMessages, Arrays.asList(validationMessages), fileName + ": validation messages");
    }

    private static boolean validate(Object dto, JsonNode args) {
        if (dto instanceof ParticipationDTO) {
            return DtoValidator.validate((ParticipationDTO) dto, boolArg(args, "allowPlayerNumberAndName"));
        }
        if (dto instanceof SessionDTO) {
            return DtoValidator.validate((SessionDTO) dto, boolArg(args, "forAdding"));
        }
        if (dto instanceof PlayerDataDTO) {
            return DtoValidator.validate((PlayerDataDTO) dto);
        }
        if (dto instanceof RoundDTO) {
            return DtoValidator.validate((RoundDTO) dto);
        }
        if (dto instanceof TableDTO) {
            return DtoValidator.validate((TableDTO) dto);
        }
        if (dto instanceof SectionDTO) {
            return DtoValidator.validate((SectionDTO) dto);
        }
        if (dto instanceof SectionUpdateDTO) {
            return DtoValidator.validate((SectionUpdateDTO) dto);
        }
        if (dto instanceof ScoringGroupDTO) {
            return DtoValidator.validate((ScoringGroupDTO) dto);
        }
        if (dto instanceof InitDTO) {
            return DtoValidator.validate((InitDTO) dto);
        }
        if (dto instanceof HandrecordDTO) {
            return DtoValidator.validate((HandrecordDTO) dto);
        }
        if (dto instanceof ResultDTO) {
            return DtoValidator.validate((ResultDTO) dto);
        }
        if (dto instanceof TdCallDTO) {
            return DtoValidator.validate((TdCallDTO) dto);
        }
        if (dto instanceof ContinueDTO) {
            return DtoValidator.validate((ContinueDTO) dto);
        }
        if (dto instanceof Bridgemate2SettingsDTO) {
            return DtoValidator.validate((Bridgemate2SettingsDTO) dto);
        }
        if (dto instanceof Bridgemate3SettingsDTO) {
            return DtoValidator.validate((Bridgemate3SettingsDTO) dto);
        }
        throw new IllegalArgumentException("No validator dispatch for " + dto.getClass().getName());
    }

    private static boolean boolArg(JsonNode args, String name) {
        return args != null && args.path(name).asBoolean(false);
    }

    // The AlternativeDataFolder rules have no fixture (a directory that exists on the generating
    // machine would not exist here), so they get focused tests of their own.

    @Test
    void continueDtoChecksAlternativeDataFolder(@TempDir Path tempDir) {
        ContinueDTO dto = new ContinueDTO();
        dto.commands = 1;
        dto.alternativeDataFolder = tempDir.toString();
        assertEquals(true, DtoValidator.validate(dto), "existing folder should validate");
        assertEquals(Collections.emptyList(), Arrays.asList(dto.validationMessages));

        Path missing = tempDir.resolve("does-not-exist");
        dto.alternativeDataFolder = missing.toString();
        assertEquals(false, DtoValidator.validate(dto), "missing folder should not validate");
        assertEquals(
                Collections.singletonList(
                        "The specified alternative data folder ('" + missing + "' does not exist.)"),
                Arrays.asList(dto.validationMessages));
    }

    @Test
    void initDtoChecksAlternativeDataFolder(@TempDir Path tempDir) {
        InitDTO dto = new InitDTO();
        dto.commands = 1;
        dto.alternativeDataFolder = tempDir.toString();
        DtoValidator.validate(dto);
        assertEquals(Collections.singletonList("At least one session is required."),
                Arrays.asList(dto.validationMessages), "existing folder should add no folder message");

        Path missing = tempDir.resolve("does-not-exist");
        dto.alternativeDataFolder = missing.toString();
        DtoValidator.validate(dto);
        assertEquals(Arrays.asList(
                        "The specified alternative data folder ('" + missing + "' does not exist.)",
                        "At least one session is required."),
                Arrays.asList(dto.validationMessages));
    }
}
