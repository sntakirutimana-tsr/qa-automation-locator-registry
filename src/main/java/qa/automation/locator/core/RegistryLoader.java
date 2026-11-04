package qa.automation.locator.core;

import qa.automation.locator.exception.InvalidLocatorDefinitionException;
import qa.automation.locator.exception.InvalidLocatorRegistryException;
import qa.automation.locator.loader.YamlResourceLoader;
import qa.automation.locator.model.LocatorDefinition;
import qa.automation.locator.model.LocatorStrategy;

import lombok.NonNull;

import java.util.*;

import static qa.automation.locator.utils.FormatterUtils.f;
import static qa.automation.locator.utils.LocatorNameUtils.normalize;

final class RegistryLoader {

    private final YamlResourceLoader resourceLoader;
    private static final String STRATEGY = "strategy";
    private static final String VALUE = "value";
    private static final Set<String> DEFINITION_ATTRIBUTES = Set.of(STRATEGY, VALUE);

    RegistryLoader(ClassLoader classLoader) {
        resourceLoader = new YamlResourceLoader(classLoader);
    }

    public Map<String, LocatorDefinition> loadAndFlatten(@NonNull String resourceUri) {
        Map<String, Object> sourceDocument = resourceLoader.load(resourceUri);
        Map<String, LocatorDefinition> flattenedDocument = new LinkedHashMap<>();

        flatten(sourceDocument, flattenedDocument, "", resourceUri);

        return Collections.unmodifiableMap(flattenedDocument);
    }

    public void flatten(@NonNull Map<@NonNull String, Object> registry,
                        @NonNull Map<@NonNull String, LocatorDefinition> target,
                        @NonNull String prefix,
                        @NonNull String resourceUri) {

        Set<String> names = new HashSet<>();

        for (Map.Entry<String, Object> entry : registry.entrySet()) {

            String segment = normalize(entry.getKey());
            String name = prefix.isBlank()
                    ? segment
                    : normalize(prefix) + "." + segment;

            if (!names.add(segment) || target.containsKey(name)) {
                throw new InvalidLocatorRegistryException(f(
                        "Duplicate normalized locator path '{}' in '{}'", name, resourceUri
                ));
            }

            Map<String, Object> node = requireMap(entry.getValue(),
                    f("locator node '{}' in '{}'", name, resourceUri));

            if (node.isEmpty() || node.containsKey(STRATEGY) || node.containsKey(VALUE)) {
                target.put(name, parseDefinition(node, name, resourceUri));
            } else {
                flatten(node, target, name, resourceUri);
            }
        }
    }

    private LocatorDefinition parseDefinition(Map<String, Object> node, String name, String resourceUri) {
        if (!node.keySet().equals(DEFINITION_ATTRIBUTES)) {
            throw new InvalidLocatorDefinitionException(f(
                    "Locator '{}' in '{}' must contain exactly strategy and value; found {}",
                    name, resourceUri, node.keySet()
            ));
        }

        Object rawStrategy = node.get(STRATEGY);
        Object rawValue = node.get(VALUE);

        if (!(rawStrategy instanceof String strategyName) || !(rawValue instanceof String value)) {
            throw new InvalidLocatorDefinitionException(f(
                    "Locator '{}' in '{}' must have string values for strategy and value", name, resourceUri
            ));
        }
        LocatorStrategy strategy = LocatorStrategy.from(strategyName, f("{} in '{}'", name, resourceUri));
        try {
            return new LocatorDefinition(strategy, value);
        } catch (InvalidLocatorDefinitionException exception) {
            throw new InvalidLocatorDefinitionException(
                    f("Invalid locator definition '{}.{}'", resourceUri, name), exception);
        }
    }

    private Map<String, Object> requireMap(Object value, String context) {

        if (!(value instanceof Map<?, ?> source)) {
            throw new InvalidLocatorRegistryException(f("Expected a YAML mapping at {}", context));
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : source.entrySet()) {

            if (!(entry.getKey() instanceof String key)) {
                throw new InvalidLocatorRegistryException(f(
                        "YAML mapping keys must be strings at {}",
                        context
                ));
            }

            result.put(key, entry.getValue());
        }

        return Collections.unmodifiableMap(result);
    }
}
