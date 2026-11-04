package qa.automation.locator.loader;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import qa.automation.locator.exception.IllegalLocatorRegistryArgumentException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDataCastTest {

    @Test
    void copiesValidatedMapsAndPreservesOrder() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "origin");
        values.put("optional", null);
        Map<String, Object> result = ResourceDataCast.asMap(values);

        assertEquals(values, result);
        values.clear();
        assertEquals(List.of("name", "optional"), new ArrayList<>(result.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> result.put("extra", "value"));
        assertThrows(IllegalLocatorRegistryArgumentException.class, () -> ResourceDataCast.asMap("not a map"));
        assertThrows(NullPointerException.class, () -> ResourceDataCast.asMap(null));
    }

    @Test
    void rejectsNonStringAndNullMapKeys() {
        assertThrows(IllegalLocatorRegistryArgumentException.class,
                () -> ResourceDataCast.asMap(Map.of(42, "value")));
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put(null, "value");
        assertThrows(IllegalLocatorRegistryArgumentException.class, () -> ResourceDataCast.asMap(values));
    }

    @Test
    void returnsValuesOfRequiredType() {
        assertEquals("origin", ResourceDataCast.requiredAs(Map.of("name", "origin"), "name", String.class));
        assertEquals(42, ResourceDataCast.requiredAs(Map.of("count", 42), "count", Integer.class));
    }

    @ParameterizedTest
    @CsvSource({"missing", "nullValue", "count"})
    void rejectsMissingNullAndWrongTypeValuesWithContext(String key) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nullValue", null);
        values.put("count", 42);

        IllegalLocatorRegistryArgumentException exception = assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                () -> ResourceDataCast.requiredAs(values, key, String.class));

        assertEquals("Missing or invalid cast-type: " + key + ". Expected String, but got "
                + (key.equals("count") ? "Integer" : "null"), exception.getMessage());
    }

    @Test
    void copiesTypedListsIntoImmutableResults() {
        List<String> source = new ArrayList<>(List.of("strategy", "value"));
        List<String> result = ResourceDataCast.requiredAsList(Map.of("attributes", source),
                "attributes", String.class);
        source.clear();

        assertEquals(List.of("strategy", "value"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("extra"));
        assertEquals(List.of(), ResourceDataCast.requiredAsList(
                Map.of("attributes", List.of()), "attributes", String.class));
    }

    private static Stream<Arguments> invalidListArguments() {
        return Stream.of(
                Arguments.of(Map.of("attributes", "not a list")),
                Arguments.of(Map.of("attributes", List.of("strategy", 42))),
                Arguments.of(Map.of("attributes", Arrays.asList("strategy", null))),
                Arguments.of(Map.of())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidListArguments")
    void rejectsInvalidListsAndElements(Map<String, Object> data) {
        assertThrows(
                IllegalLocatorRegistryArgumentException.class,
                () -> ResourceDataCast.requiredAsList(data, "attributes", String.class)
        );
    }

    @ParameterizedTest
    @MethodSource("nullArgumentCasts")
    void rejectsNullArgumentsToTypedCasts(Executable invocation) {
        assertThrows(NullPointerException.class, invocation);
    }

    private static Stream<Executable> nullArgumentCasts() {
        return Stream.of(
                () -> ResourceDataCast.requiredAs(null, "name", String.class),
                () -> ResourceDataCast.requiredAs(Map.of(), null, String.class),
                () -> ResourceDataCast.requiredAs(Map.of(), "name", null),
                () -> ResourceDataCast.requiredAsList(null, "name", String.class),
                () -> ResourceDataCast.requiredAsList(Map.of(), null, String.class),
                () -> ResourceDataCast.requiredAsList(Map.of(), "name", null)
        );
    }
}
