package qa.automation.locator.model;

import qa.automation.locator.exception.LocatorNotFoundException;
import qa.automation.locator.exception.InvalidLocatorRegistryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedLocatorsTest {

    private final LocatorDefinition origin = new LocatorDefinition(LocatorStrategy.ID, "origin");

    @Test
    void retrievesExistingDefinitionsAndReportsMembership() {
        ResolvedLocators locators = new ResolvedLocators(
                "custom/registry", "booking", "fields", Map.of("origin", origin));

        assertSame(origin, locators.get(" ORIGIN "));
        assertTrue(locators.contains("origIn "));
        assertFalse(locators.contains("missing"));
    }

    @Test
    void defensivelyCopiesDefinitionsAndPreservesInsertionOrder() {
        Map<String, LocatorDefinition> definitions = new LinkedHashMap<>();
        definitions.put("origin", origin);
        definitions.put("destination", new LocatorDefinition(LocatorStrategy.NAME, "destination"));
        ResolvedLocators locators = new ResolvedLocators("registry", "booking", "", definitions);
        definitions.clear();

        assertSame(origin, locators.get("origin"));
        assertTrue(locators.contains("destination"));
        LocatorNotFoundException exception = assertThrows(
                LocatorNotFoundException.class, () -> locators.get("missing"));
        assertTrue(exception.getMessage().endsWith("[origin, destination]"));
    }

    @Test
    void normalizesProvidedDefinitionsAndPreservesDottedLookups() {
        ResolvedLocators locators = new ResolvedLocators(
                "registry", "booking", "", Map.of(" Fields.Origin ", origin));

        assertSame(origin, locators.get("FIELDS.ORIGIN"));
        assertTrue(locators.contains(" fields.origin "));
    }

    @Test
    void rejectsProvidedDefinitionsWithNormalizedNameCollisions() {
        Map<String, LocatorDefinition> definitions = new LinkedHashMap<>();
        definitions.put(" Fields.Origin ", origin);
        definitions.put("fields.origin", origin);

        InvalidLocatorRegistryException exception = assertThrows(
                InvalidLocatorRegistryException.class,
                () -> new ResolvedLocators("registry", "booking", "", definitions));

        assertTrue(exception.getMessage().contains("fields.origin"));
        assertTrue(exception.getMessage().contains("registry"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void omitsBlankSectionFromMissingLocatorScope(String section) {
        ResolvedLocators locators = new ResolvedLocators(
                "registry", "booking", section, Map.of("origin", origin));

        LocatorNotFoundException exception = assertThrows(
                LocatorNotFoundException.class, () -> locators.get("missing"));

        assertEquals("Locator 'missing' was not found in scope 'booking' in 'registry'"
                + ". Available locators: \n  [origin]", exception.getMessage());
    }

    @Test
    void includesSectionInMissingLocatorScope() {
        ResolvedLocators locators = new ResolvedLocators(
                "registry", "booking", "fields", Map.of());

        LocatorNotFoundException exception = assertThrows(
                LocatorNotFoundException.class, () -> locators.get("missing"));

        assertEquals("Locator 'missing' was not found in scope 'booking.fields' in 'registry'"
                + ". Available locators: \n  []", exception.getMessage());
    }

    @Test
    void rejectsNullConstructorArguments() {
        assertEquals("registryPath", assertThrows(NullPointerException.class,
                () -> new ResolvedLocators(null, "booking", "", Map.of())).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class,
                () -> new ResolvedLocators("registry", null, "", Map.of())).getMessage());
        assertEquals("definitions", assertThrows(NullPointerException.class,
                () -> new ResolvedLocators("registry", "booking", "", null)).getMessage());
    }

    @Test
    void rejectsNullLookupNames() {
        ResolvedLocators locators = new ResolvedLocators("registry", "booking", "", Map.of());

        assertEquals("Locator reference key must not be null",
                assertThrows(NullPointerException.class, () -> locators.get(null)).getMessage());
        assertEquals("Locator reference key must not be null",
                assertThrows(NullPointerException.class, () -> locators.contains(null)).getMessage());
    }
}
