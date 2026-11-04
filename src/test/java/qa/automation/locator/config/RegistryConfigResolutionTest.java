package qa.automation.locator.config;

import qa.automation.locator.exception.IllegalLocatorRegistryArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryConfigResolutionTest {

    private static final String PROPERTY = "locator.registry.test.unset";
    private static final String ENVIRONMENT = "LOCATOR_REGISTRY_TEST_UNSET";

    @Test
    void resolvesAndTrimsAnExistingEnvironmentVariable() throws Throwable {
        Map.Entry<String, String> environment = System.getenv().entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .findFirst()
                .orElseThrow();

        assertNull(System.getProperty(PROPERTY));
        assertEquals(environment.getValue().trim(),
                invoke("resolve", PROPERTY, environment.getKey()));
    }

    @Test
    void systemPropertyTakesPrecedenceOverAnExistingEnvironmentVariable() throws Throwable {
        String environment = System.getenv().keySet().stream().findFirst().orElseThrow();
        String previousValue = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, " property-value ");

            assertEquals("property-value", invoke("resolve", PROPERTY, environment));
        } finally {
            if (previousValue == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, previousValue);
            }
        }
    }

    @Test
    void blankSystemPropertyFallsBackToDefault() throws Throwable {
        assertNull(System.getenv(ENVIRONMENT));
        String previousValue = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, " \t ");

            assertEquals("default-value",
                    invoke("resolve", PROPERTY, ENVIRONMENT, Map.of(PROPERTY, " default-value ")));
        } finally {
            if (previousValue == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, previousValue);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", " TrUe ", "false", " FaLsE "})
    void acceptsStringBooleanDefaults(String value) throws Throwable {
        assertOverridesAreUnset();

        assertEquals(Boolean.parseBoolean(value.trim()),
                invoke("resolveBoolean", PROPERTY, ENVIRONMENT, Map.of(PROPERTY, value)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsNativeBooleanDefaults(boolean value) throws Throwable {
        assertOverridesAreUnset();

        assertEquals(value,
                invoke("resolveBoolean", PROPERTY, ENVIRONMENT, Map.of(PROPERTY, value)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1})
    void rejectsMissingOrWrongTypeBooleanDefaults(Integer value) {
        assertOverridesAreUnset();
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(PROPERTY, value);

        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                () -> invoke("resolveBoolean", PROPERTY, ENVIRONMENT, defaults));

        assertEquals("Missing or invalid boolean configuration: " + PROPERTY, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "yes", "1"})
    void rejectsInvalidStringBooleanDefaults(String value) {
        assertOverridesAreUnset();

        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                () -> invoke("resolveBoolean", PROPERTY, ENVIRONMENT, Map.of(PROPERTY, value)));

        assertEquals("Cache configuration must be true or false.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    void rejectsBlankRequiredStrings(String value) {
        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                () -> invoke("requiredAsString", Map.of(PROPERTY, value), PROPERTY));

        assertEquals("Missing required configuration: " + PROPERTY, exception.getMessage());
    }

    @Test
    void flattensNestedMapsAndPreservesLists() throws Throwable {
        Map<String, Object> flattened = new LinkedHashMap<>();
        Map<String, Object> source = Map.of(
                "registry", Map.of("path", "custom/registry"),
                "attributes", List.of("strategy", "value"));

        invoke("flatten", "", source, flattened);

        assertEquals(Map.of(
                "registry.path", "custom/registry",
                "attributes", List.of("strategy", "value")), flattened);
    }

    @ParameterizedTest
    @MethodSource("nullArguments")
    void rejectsNullHelperArguments(String method, Object[] arguments) {
        NullPointerException exception = assertThrows(
                NullPointerException.class, () -> invoke(method, arguments));

        assertTrue(exception.getMessage().contains("is marked non-null but is null"));
    }

    static Stream<Arguments> nullArguments() {
        return Stream.of(
                Arguments.of("resolve", new Object[]{null, ENVIRONMENT}),
                Arguments.of("resolve", new Object[]{PROPERTY, null}),
                Arguments.of("resolve", new Object[]{null, ENVIRONMENT, Map.of()}),
                Arguments.of("resolve", new Object[]{PROPERTY, null, Map.of()}),
                Arguments.of("resolve", new Object[]{PROPERTY, ENVIRONMENT, null}),
                Arguments.of("resolveRegistryPath", new Object[]{null}),
                Arguments.of("resolveBoolean", new Object[]{null, ENVIRONMENT, Map.of()}),
                Arguments.of("resolveBoolean", new Object[]{PROPERTY, null, Map.of()}),
                Arguments.of("resolveBoolean", new Object[]{PROPERTY, ENVIRONMENT, null}),
                Arguments.of("requiredAsString", new Object[]{null, PROPERTY}),
                Arguments.of("requiredAsString", new Object[]{Map.of(), null}),
                Arguments.of("flatten", new Object[]{null, Map.of(), new LinkedHashMap<>()}),
                Arguments.of("flatten", new Object[]{"", null, new LinkedHashMap<>()}),
                Arguments.of("flatten", new Object[]{"", Map.of(), null}));
    }

    private static void assertOverridesAreUnset() {
        assertNull(System.getProperty(PROPERTY));
        assertNull(System.getenv(ENVIRONMENT));
    }

    // Keep production helpers private while exercising their validation and fallback contracts.
    private static Object invoke(String name, Object... arguments) throws Throwable {
        Method method = Arrays.stream(RegistryConfigLoader.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == arguments.length)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
