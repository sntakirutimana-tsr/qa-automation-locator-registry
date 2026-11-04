package qa.automation.locator.config;

import qa.automation.locator.exception.IllegalLocatorRegistryArgumentException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryConfigLoaderTest {

    @TempDir
    Path directory;

    private static final String DEFAULT_PATH_PROPERTY = "locator.registry.path.default";
    private static final String CACHE_ENABLED_PROPERTY = "locator.registry.cache-enabled";

    private String previousDefaultPath;
    private String previousCacheEnabled;

    @BeforeEach
    void captureSystemProperties() {
        previousDefaultPath = System.getProperty(DEFAULT_PATH_PROPERTY);
        previousCacheEnabled = System.getProperty(CACHE_ENABLED_PROPERTY);
        System.clearProperty(DEFAULT_PATH_PROPERTY);
        System.clearProperty(CACHE_ENABLED_PROPERTY);
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty(DEFAULT_PATH_PROPERTY, previousDefaultPath);
        restoreProperty(CACHE_ENABLED_PROPERTY, previousCacheEnabled);
    }

    @Test
    void loadsRulesAndAllowedAttributesFromBundledDefaults() {
        RegistryConfigLoader config = new RegistryConfigLoader();

        assertNotNull(config.getLocatorSourceKeyRule());
        assertNotNull(config.getLocatorSourcePathRule());
        assertNotNull(config.getLocatorSourceSectionRule());
        assertEquals(List.of("strategy", "value"), config.getLocatorDefinitionAllowedAttributes());
        assertTrue("custom/registry.yml".matches(config.getLocatorSourceKeyRule()));
        assertTrue("custom/registry".matches(config.getLocatorSourcePathRule()));
        assertTrue("fields.origin".matches(config.getLocatorSourceSectionRule()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application.yaml",
            "qa/automation/locator/config/registry-defaults.yml",
            "qa/automation/locator/config/registry-defaults.yaml"})
    void ignoresConsumerConfigurationResources(String resource) throws IOException {
        Path file = directory.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "consumer: unrelated-configuration\n");
        RegistryConfigLoader expected = new RegistryConfigLoader();
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader consumer = new URLClassLoader(new URL[]{directory.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(consumer);
            RegistryConfigLoader actual = new RegistryConfigLoader();

            assertEquals(RegistryConfigLoader.class.getClassLoader(), actual.getClassLoader());
            assertEquals(expected.getRegistryPath(), actual.getRegistryPath());
            assertEquals(expected.isCacheEnabled(), actual.isCacheEnabled());
            assertEquals(expected.getLocatorSourceKeyRule(), actual.getLocatorSourceKeyRule());
            assertEquals(expected.getLocatorDefinitionAllowedAttributes(),
                    actual.getLocatorDefinitionAllowedAttributes());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void loadsBundledDefaultsWithoutAContextClassLoader() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        RegistryConfigLoader expected = new RegistryConfigLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            assertEquals(expected.getRegistryPath(), new RegistryConfigLoader().getRegistryPath());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void systemPropertiesOverrideDefaultsAndAreTrimmed() {
        System.setProperty(DEFAULT_PATH_PROPERTY, "  CUSTOM/Registry  ");
        System.setProperty(CACHE_ENABLED_PROPERTY, " false ");

        RegistryConfigLoader config = new RegistryConfigLoader();

        assertEquals("custom/registry", config.getRegistryPath());
        assertFalse(config.isCacheEnabled());
    }

    @Test
    void rejectsRegistryPathThatDoesNotMatchConfiguredRule() {
        System.setProperty(DEFAULT_PATH_PROPERTY, "../outside");

        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                RegistryConfigLoader::new);

        assertTrue(exception.getMessage().contains("../outside"));
    }

    @Test
    void publicConfigAccessorsExposeLoadedConfiguration() {
        RegistryConfigLoader config = new RegistryConfigLoader();

        assertEquals(config.getRegistryPath(), RegistryConfig.registryPath());
        assertEquals(config.isCacheEnabled(), RegistryConfig.isCacheEnabled());
        assertEquals(config.getLocatorSourceKeyRule(), RegistryConfig.locatorSourceKeyRule());
        assertEquals(config.getLocatorSourcePathRule(), RegistryConfig.locatorSourcePathRule());
        assertEquals(config.getLocatorSourceSectionRule(), RegistryConfig.locatorSourceSectionRule());
        assertEquals(config.getLocatorDefinitionAllowedAttributes(),
                RegistryConfig.locatorDefinitionAllowedAttributes());
    }

    @Test
    void acceptsCaseInsensitiveBooleanProperty() {
        System.setProperty(CACHE_ENABLED_PROPERTY, " FaLsE ");

        assertFalse(new RegistryConfigLoader().isCacheEnabled());
    }

    @Test
    void rejectsInvalidBooleanProperty() {
        System.setProperty(CACHE_ENABLED_PROPERTY, "sometimes");

        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                RegistryConfigLoader::new);

        assertEquals("Cache configuration must be true or false.", exception.getMessage());
    }

    @Test
    void publicAllowedAttributesAreImmutable() {
        List<String> attributes = RegistryConfig.locatorDefinitionAllowedAttributes();

        assertEquals(List.of("strategy", "value"), attributes);
        assertThrows(UnsupportedOperationException.class, () -> attributes.add("unexpected"));
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
