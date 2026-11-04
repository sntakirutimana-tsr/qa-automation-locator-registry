package qa.automation.locator.model;

import qa.automation.locator.exception.InvalidLocatorDefinitionException;

import java.util.Objects;

public record LocatorDefinition(LocatorStrategy strategy, String value) {

    public LocatorDefinition(LocatorStrategy strategy, String value) {

        this.strategy = validateStrategy(strategy);
        this.value = validateValue(value);
    }

    private LocatorStrategy validateStrategy(LocatorStrategy strategy) {
        return Objects.requireNonNull(strategy, "Locator definition strategy must not be null");
    }

    private String validateValue(String value) {

        if (value == null || value.isBlank()) {
            throw new InvalidLocatorDefinitionException("Locator definition value must not be blank");
        }

        return value.trim();
    }

    @Override
    public String toString() {
        return strategy + ":" + value;
    }
}
