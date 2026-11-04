package qa.automation.locator.core;

import qa.automation.locator.exception.InvalidLocatorDefinitionException;
import qa.automation.locator.exception.InvalidLocatorRegistryException;
import qa.automation.locator.exception.InvalidLocatorStrategyException;
import qa.automation.locator.model.LocatorDefinition;
import qa.automation.locator.model.LocatorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryLoaderTest {

    @TempDir
    Path temporaryDirectory;

    private URLClassLoader temporaryClassLoader;
    private RegistryLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        temporaryClassLoader = new URLClassLoader(
                new java.net.URL[]{temporaryDirectory.toUri().toURL()},
                RegistryLoader.class.getClassLoader());
        loader = new RegistryLoader(temporaryClassLoader);
        write("core-fixtures/loader.yml",
                "fields:\n"
                        + "  origin:\n"
                        + "    strategy: accessibility-id\n"
                        + "    value: origin\n"
                        + "actions:\n"
                        + "  search:\n"
                        + "    strategy: xpath\n"
                        + "    value: //button\n"
                        + "components:\n"
                        + "  date-picker:\n"
                        + "    fields:\n"
                        + "      departure:\n"
                        + "        strategy: id\n"
                        + "        value: departure\n");
    }

    @AfterEach
    void tearDown() throws IOException {
        temporaryClassLoader.close();
    }

    @Test
    void flattensNestedLocatorGroupsInOrderAndReturnsAnImmutableMap() {
        Map<String, LocatorDefinition> result = loader.loadAndFlatten("core-fixtures/loader");

        assertEquals(List.of("fields.origin", "actions.search", "components.date-picker.fields.departure"),
                new ArrayList<>(result.keySet()));
        assertEquals(new LocatorDefinition(LocatorStrategy.ACCESSIBILITY_ID, "origin"),
                result.get("fields.origin"));
        assertEquals(new LocatorDefinition(LocatorStrategy.XPATH, "//button"),
                result.get("actions.search"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.put("extra", new LocatorDefinition(LocatorStrategy.ID, "extra")));
    }

    @Test
    void acceptsDefinitionsWithOnlyAllowedAttributes() {
        Map<String, LocatorDefinition> result = new LinkedHashMap<>();

        loader.flatten(Map.of("field", Map.of("strategy", "id", "value", "field-id")),
                result, "", "test-registry");

        assertEquals(Map.of("field", new LocatorDefinition(LocatorStrategy.ID, "field-id")), result);
    }

    @Test
    void definitionAttributeOrderDoesNotChangeMeaning() {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("value", "field-id");
        definition.put("strategy", "id");
        Map<String, LocatorDefinition> result = new LinkedHashMap<>();

        loader.flatten(Map.of("field", definition), result, "", "test-registry");

        assertEquals(new LocatorDefinition(LocatorStrategy.ID, "field-id"), result.get("field"));
    }

    @Test
    void resolvesSharedAliasesAtSeparateLocatorPaths() throws IOException {
        write("core-fixtures/aliases.yml",
                "first: &definition {strategy: id, value: ' Origin '}\nsecond: *definition\n");

        Map<String, LocatorDefinition> result = loader.loadAndFlatten("core-fixtures/aliases.yml");

        assertEquals(List.of("first", "second"), new ArrayList<>(result.keySet()));
        assertEquals(new LocatorDefinition(LocatorStrategy.ID, "Origin"), result.get("first"));
        assertEquals(result.get("first"), result.get("second"));
    }

    @Test
    void normalizesNestedNamesAndPreservesDottedNamesAndValues() throws IOException {
        write("core-fixtures/names.yml",
                "' Page ':\n"
                        + "  ' Fields ':\n"
                        + "    ' Origin ':\n"
                        + "      strategy: id\n"
                        + "      value: MixedCaseId\n"
                        + "  Actions.Search:\n"
                        + "    strategy: id\n"
                        + "    value: SearchButton\n");

        Map<String, LocatorDefinition> result = loader.loadAndFlatten("core-fixtures/names");

        assertEquals(List.of("page.fields.origin", "page.actions.search"),
                new ArrayList<>(result.keySet()));
        assertEquals("MixedCaseId", result.get("page.fields.origin").value());
        assertEquals("SearchButton", result.get("page.actions.search").value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page:\n  Origin: {strategy: id, value: first}\n  origin: {strategy: id, value: second}\n",
            "page:\n  ' origin ': {strategy: id, value: first}\n  origin: {strategy: id, value: second}\n",
            "page:\n  Fields:\n    first: {strategy: id, value: first}\n  fields:\n    second: {strategy: id, value: second}\n",
            "page:\n  fields.origin: {strategy: id, value: first}\n  fields:\n    origin: {strategy: id, value: second}\n",
            "page:\n  fields:\n    origin: {strategy: id, value: first}\n  fields.origin: {strategy: id, value: second}\n"
    })
    void rejectsNestedAndFlattenedNameCollisions(String yaml) throws IOException {
        write("core-fixtures/collision.yml", yaml);

        InvalidLocatorRegistryException exception = assertThrows(
                InvalidLocatorRegistryException.class,
                () -> loader.loadAndFlatten("core-fixtures/collision"));

        assertTrue(exception.getMessage().contains("Duplicate normalized locator path"));
        assertTrue(exception.getMessage().contains("core-fixtures/collision"));
    }

    @Test
    void rejectsEmptyMappingAsAnIncompleteDefinition() {
        assertThrows(InvalidLocatorDefinitionException.class,
                () -> loader.flatten(Map.of("empty", Map.of()),
                        new LinkedHashMap<>(), "", "test-registry"));
    }

    @Test
    void reportsNonMappingLocatorNodesAndNullValues() {
        assertThrows(InvalidLocatorRegistryException.class,
                () -> loader.flatten(Map.of("field", "not-a-map"),
                        new LinkedHashMap<>(), "", "test-registry"));

        Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("field", null);
        assertThrows(InvalidLocatorRegistryException.class,
                () -> loader.flatten(registry, new LinkedHashMap<>(), "", "test-registry"));
    }

    @Test
    void reportsNonStringMappingKeys() {
        Map<Object, Object> rawNode = new LinkedHashMap<>();
        rawNode.put(42, "not-a-string-key");
        Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("field", rawNode);

        assertThrows(InvalidLocatorRegistryException.class,
                () -> loader.flatten(registry, new LinkedHashMap<>(), "", "test-registry"));
    }

    @Test
    void rejectsDefinitionsWithMissingOrExtraAttributes() {
        assertThrows(InvalidLocatorDefinitionException.class,
                () -> loader.flatten(Map.of("field", Map.of("strategy", "id")),
                        new LinkedHashMap<>(), "", "test-registry"));
        assertThrows(InvalidLocatorDefinitionException.class,
                () -> loader.flatten(Map.of("field", Map.of(
                                "strategy", "id", "value", "field", "unexpected", "extra")),
                        new LinkedHashMap<>(), "", "test-registry"));
    }

    @Test
    void rejectsNonStringStrategiesAndValues() {
        assertInvalidDefinition(Map.of("strategy", 42, "value", "field"));
        assertInvalidDefinition(Map.of("strategy", "id", "value", 42));
        assertInvalidDefinition(Map.of("value", "field"));
    }

    @Test
    void rejectsUnsupportedStrategies() {
        InvalidLocatorStrategyException exception = assertThrows(InvalidLocatorStrategyException.class,
                () -> loader.flatten(Map.of("field", Map.of(
                                "strategy", "not-supported", "value", "field")),
                        new LinkedHashMap<>(), "", "test-registry"));
        assertTrue(exception.getMessage().contains("field"));
        assertTrue(exception.getMessage().contains("test-registry"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void rejectsBlankLocatorValues() {
        InvalidLocatorDefinitionException exception = assertThrows(
                InvalidLocatorDefinitionException.class,
                () -> loader.flatten(Map.of("field", Map.of("strategy", "id", "value", " ")),
                        new LinkedHashMap<>(), "", "test-registry"));

        assertEquals("Invalid locator definition 'test-registry.field'", exception.getMessage());
        assertInstanceOf(InvalidLocatorDefinitionException.class, exception.getCause());
    }

    @Test
    void rejectsNullArgumentsToPublicLoaderMethods() {
        assertThrows(NullPointerException.class, () -> loader.loadAndFlatten(null));
        assertThrows(NullPointerException.class,
                () -> loader.flatten(null, new LinkedHashMap<>(), "", "test-registry"));
        assertThrows(NullPointerException.class,
                () -> loader.flatten(Map.of(), null, "", "test-registry"));
        assertThrows(NullPointerException.class,
                () -> loader.flatten(Map.of(), new LinkedHashMap<>(), null, "test-registry"));
        assertThrows(NullPointerException.class,
                () -> loader.flatten(Map.of(), new LinkedHashMap<>(), "", null));
    }

    private void assertInvalidDefinition(Map<String, Object> definition) {
        InvalidLocatorDefinitionException exception = assertThrows(InvalidLocatorDefinitionException.class,
                () -> loader.flatten(Map.of("field", definition),
                        new LinkedHashMap<>(), "", "test-registry"));
        assertTrue(exception.getMessage().contains("field"));
        assertTrue(exception.getMessage().contains("test-registry"));
    }

    private void write(String resource, String yaml) throws IOException {
        Path file = temporaryDirectory.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }
}
