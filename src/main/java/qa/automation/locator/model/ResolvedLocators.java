package qa.automation.locator.model;

import qa.automation.locator.exception.LocatorNotFoundException;
import qa.automation.locator.exception.InvalidLocatorRegistryException;

import java.util.*;

import static qa.automation.locator.utils.FormatterUtils.f;
import static qa.automation.locator.utils.LocatorNameUtils.normalize;

public final class ResolvedLocators {

    private final String registryPath;
    private final String key;
    private final String section;
    private final Map<String, LocatorDefinition> definitions;

    public ResolvedLocators(String registryPath,
                            String key,
                            String section,
                            Map<String, LocatorDefinition> definitions) {

        this.registryPath = Objects.requireNonNull(registryPath, "registryPath");
        this.key = Objects.requireNonNull(key, "key");
        this.section = (section == null) ? "" : section;
        Map<String, LocatorDefinition> normalizedDefinitions = new LinkedHashMap<>();
        Objects.requireNonNull(definitions, "definitions").forEach((name, definition) -> {
            String normalizedName = requireNonNullLocatorName(name);
            if (normalizedDefinitions.containsKey(normalizedName)) {
                throw new InvalidLocatorRegistryException(f(
                        "Duplicate normalized locator name '{}' in registry '{}'", normalizedName, registryPath
                ));
            }
            normalizedDefinitions.put(normalizedName, definition);
        });
        this.definitions = Collections.unmodifiableMap(normalizedDefinitions);
    }

    private static String requireNonNullLocatorName(String name) {
        return normalize(Objects.requireNonNull(name, "Locator reference key must not be null"));
    }

    public LocatorDefinition get(String name) {

        LocatorDefinition definition = definitions.get(requireNonNullLocatorName(name));

        if (definition == null) {
            throw new LocatorNotFoundException(
                    "Locator '"
                            + name
                            + "' was not found in scope "
                            + describeScope()
                            + ". Available locators: \n  "
                            + definitions.keySet()
            );
        }
        return definition;
    }

    public boolean contains(String name) {
        return definitions.containsKey(requireNonNullLocatorName(name));
    }

    private String describeScope() {
        return "'"
                + key
                + (section.isBlank() ? "" : "." + section)
                + "' in '"
                + registryPath
                + "'";
    }
}
