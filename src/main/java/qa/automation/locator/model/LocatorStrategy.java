package qa.automation.locator.model;

import qa.automation.locator.exception.InvalidLocatorStrategyException;

import java.util.Arrays;
import java.util.Locale;

import static qa.automation.locator.utils.FormatterUtils.f;

public enum LocatorStrategy {
    ID,
    XPATH,
    ACCESSIBILITY_ID,
    CLASS_NAME,
    TAG_NAME,
    CSS_SELECTOR,
    NAME,
    LINK_TEXT,
    PARTIAL_LINK_TEXT,
    ANDROID_UIAUTOMATOR;

    public static LocatorStrategy from(String value, String locatorName) {

        if (value == null || value.isBlank()) {
            throw new InvalidLocatorStrategyException(f("Missing strategy for locator '{}'", locatorName));
        }

        final String CHARS_TO_REPLACE = "[\\-\\s]";
        final String REPLACEMENT_CHAR = "_";

        String normalized = value.trim()
                .replaceAll(CHARS_TO_REPLACE, REPLACEMENT_CHAR)
                .toUpperCase(Locale.ROOT);

        try {
            return LocatorStrategy.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new InvalidLocatorStrategyException(
                    f(
                            "Unsupported locator strategy '{}' for locator '{}'. Supported strategies: {}",
                            value,
                            locatorName,
                            Arrays.toString(values())
                    ),
                    exception
            );
        }
    }
}
