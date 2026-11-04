package qa.automation.locator.config;

import qa.automation.locator.exception.IllegalLocatorRegistryArgumentException;
import qa.automation.locator.loader.YamlResourceLoader;

import lombok.Getter;
import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static qa.automation.locator.loader.ResourceDataCast.requiredAs;
import static qa.automation.locator.loader.ResourceDataCast.requiredAsList;
import static qa.automation.locator.loader.ResourceDataCast.asMap;

final class RegistryConfigLoader {

    private static final String DEFAULT_CONFIG = "qa/automation/locator/config/registry-defaults.yml";

    private final YamlResourceLoader resourceLoader;

    private static final String DEFAULT_PATH_KEY = "locator.registry.path.default";
    private static final String REGISTRY_PATH_RULE_KEY = "locator.registry.path.rule";
    private static final String CACHE_ENABLED_KEY = "locator.registry.cache-enabled";
    private static final String LOCATOR_SOURCE_KEY_RULE_KEY = "locator.source.key.rule";
    private static final String LOCATOR_SOURCE_PATH_RULE_KEY = "locator.source.path.rule";
    private static final String LOCATOR_SOURCE_SECTION_RULE_KEY = "locator.source.section.rule";
    private static final String LOCATOR_DEFINITION_ALLOWED_ATTRIBS_KEY = "locator.definition.allowed-attributes";

    private static final String DEFAULT_PATH_ENV = "LOCATOR_REGISTRY_DEFAULT_PATH";
    private static final String CACHE_ENABLED_ENV = "LOCATOR_REGISTRY_CACHE_ENABLED";

    @Getter
    private final String registryPath;

    @Getter
    private final boolean cacheEnabled;

    @Getter
    private final String locatorSourceKeyRule;

    @Getter
    private final String locatorSourcePathRule;

    @Getter
    private final String locatorSourceSectionRule;

    @Getter
    private final List<String> locatorDefinitionAllowedAttributes;

    public RegistryConfigLoader() {
        resourceLoader = new YamlResourceLoader(RegistryConfigLoader.class.getClassLoader());

        Map<String, Object> defaults = new LinkedHashMap<>();

        flatten("", resourceLoader.load(DEFAULT_CONFIG), defaults);

        registryPath = resolveRegistryPath(defaults);
        cacheEnabled = resolveBoolean(CACHE_ENABLED_KEY, CACHE_ENABLED_ENV, defaults);
        locatorSourceKeyRule = requiredAsString(defaults, LOCATOR_SOURCE_KEY_RULE_KEY);
        locatorSourcePathRule = requiredAsString(defaults, LOCATOR_SOURCE_PATH_RULE_KEY);
        locatorSourceSectionRule = requiredAsString(defaults, LOCATOR_SOURCE_SECTION_RULE_KEY);
        locatorDefinitionAllowedAttributes = requiredAsList(
                defaults,
                LOCATOR_DEFINITION_ALLOWED_ATTRIBS_KEY, String.class);
        if (locatorDefinitionAllowedAttributes.size() != 2
                || !Set.copyOf(locatorDefinitionAllowedAttributes).equals(Set.of("strategy", "value"))) {
            throw new IllegalLocatorRegistryArgumentException(
                    "Locator definition attributes must contain exactly strategy and value.");
        }
    }

    ClassLoader getClassLoader() {
        return resourceLoader.getClassLoader();
    }

    static RegistryConfigLoader get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final RegistryConfigLoader INSTANCE = new RegistryConfigLoader();
    }

    private static String resolve(@NonNull String propertyKey, @NonNull String environmentKey) {

        String value = System.getProperty(propertyKey);

        if (isSet(value)) {
            return value.trim();
        }

        value = System.getenv(environmentKey);

        if (isSet(value)) {
            return value.trim();
        }

        return value;
    }

    private static String resolve(@NonNull String propertyKey,
                                  @NonNull String environmentKey,
                                  @NonNull Map<@NonNull String, Object> defaults) {

        String value = resolve(propertyKey, environmentKey);

        return isSet(value)
                ? value
                : requiredAsString(defaults, propertyKey);
    }

    private static String resolveRegistryPath(@NonNull Map<@NonNull String, Object> defaults) {

        String pathRule = requiredAsString(defaults, REGISTRY_PATH_RULE_KEY);
        String pathRawValue = resolve(DEFAULT_PATH_KEY, DEFAULT_PATH_ENV, defaults);

        if (pathRawValue.matches(pathRule)) {
            return pathRawValue.trim().toLowerCase(Locale.ROOT);
        }

        throw new IllegalLocatorRegistryArgumentException("Invalid registry path configuration: " + pathRawValue);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }

        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        throw new IllegalLocatorRegistryArgumentException("Cache configuration must be true or false.");
    }

    private static boolean resolveBoolean(@NonNull String propertyKey,
                                          @NonNull String environmentKey,
                                          @NonNull Map<@NonNull String, Object> defaults) {

        String value = resolve(propertyKey, environmentKey);

        if (isSet(value)) {
            return parseBoolean(value);
        }

        Object defaultValue = defaults.get(propertyKey);

        if (defaultValue instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (defaultValue instanceof String stringValue) {
            return parseBoolean(stringValue.trim());
        }

        throw new IllegalLocatorRegistryArgumentException(
                "Missing or invalid boolean configuration: " + propertyKey);
    }

    private static String requiredAsString(@NonNull Map<@NonNull String, Object> values,
                                           @NonNull String key) {

        String safeValue = requiredAs(values, key, String.class);

        if (safeValue.isBlank()) {
            throw new IllegalLocatorRegistryArgumentException("Missing required configuration: " + key);
        }

        return safeValue.trim();
    }

    private static boolean isSet(String value) {
        return !(value == null || value.isBlank());
    }

    private static void flatten(@NonNull String prefix,
                                @NonNull Map<@NonNull String, Object> source,
                                @NonNull Map<@NonNull String, Object> target) {

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isBlank()
                    ? entry.getKey()
                    : prefix + "." + entry.getKey();

            Object value = entry.getValue();

            if (value instanceof Map) {
                flatten(key, asMap(value), target);
            } else {
                target.put(key, value);
            }
        }
    }
}
