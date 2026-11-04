package qa.automation.locator.model;

import qa.automation.locator.exception.InvalidLocatorDefinitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocatorDefinitionTest {

    @Test
    void preservesStrategyAndTrimsValueWhitespace() {
        LocatorDefinition definition = new LocatorDefinition(LocatorStrategy.XPATH, " //button ");

        assertEquals(LocatorStrategy.XPATH, definition.strategy());
        assertEquals("//button", definition.value());
        assertEquals("XPATH://button", definition.toString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankValues(String value) {
        InvalidLocatorDefinitionException exception = assertThrows(
                InvalidLocatorDefinitionException.class,
                () -> new LocatorDefinition(LocatorStrategy.ID, value));

        assertEquals("Locator definition value must not be blank", exception.getMessage());
    }

    @Test
    void rejectsNullStrategy() {
        NullPointerException exception = assertThrows(
                NullPointerException.class, () -> new LocatorDefinition(null, "origin"));

        assertEquals("Locator definition strategy must not be null", exception.getMessage());
    }

    @Test
    void hasValueBasedEqualityAndHashCode() {
        LocatorDefinition definition = new LocatorDefinition(LocatorStrategy.ID, "origin");
        LocatorDefinition equal = new LocatorDefinition(LocatorStrategy.ID, "origin");

        assertEquals(definition, equal);
        assertEquals(definition.hashCode(), equal.hashCode());
        assertNotEquals(new LocatorDefinition(LocatorStrategy.NAME, "origin"), definition);
        assertNotEquals(new LocatorDefinition(LocatorStrategy.ID, "destination"), definition);
        assertNotEquals(null, definition);
    }
}
