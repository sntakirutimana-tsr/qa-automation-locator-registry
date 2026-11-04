package qa.automation.locator.model;

import qa.automation.locator.exception.InvalidLocatorStrategyException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LocatorStrategyTest {

    @ParameterizedTest
    @EnumSource(LocatorStrategy.class)
    void acceptsEverySupportedStrategyIgnoringCaseAndOuterWhitespace(LocatorStrategy strategy) {
        assertEquals(strategy,
                LocatorStrategy.from(" " + strategy.name().toLowerCase(Locale.ROOT) + " ", "origin"));
    }

    @ParameterizedTest
    @CsvSource({
            "accessibility-id, ACCESSIBILITY_ID",
            "accessibility id, ACCESSIBILITY_ID",
            "class-name, CLASS_NAME",
            "class name, CLASS_NAME",
            "tag_name, TAG_NAME",
            "paRtial-link text, PARTIAL_LINK_TEXT",
            "android-uiautomator, ANDROID_UIAUTOMATOR",
            "android uiautomator, ANDROID_UIAUTOMATOR"
    })
    void normalizesHyphensAndSpaces(String value, LocatorStrategy expected) {
        assertEquals(expected, LocatorStrategy.from(value, "origin"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingStrategy(String value) {
        InvalidLocatorStrategyException exception = assertThrows(
                InvalidLocatorStrategyException.class, () -> LocatorStrategy.from(value, "origin"));

        assertEquals("Missing strategy for locator 'origin'", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"css", "unknown", "class--name"})
    void reportsUnsupportedStrategyWithContextAndCause(String value) {
        InvalidLocatorStrategyException exception = assertThrows(
                InvalidLocatorStrategyException.class, () -> LocatorStrategy.from(value, "origin"));

        assertTrue(exception.getMessage().contains("'" + value + "'"));
        assertTrue(exception.getMessage().contains("'origin'"));
        assertTrue(exception.getMessage().contains(Arrays.toString(LocatorStrategy.values())));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
