package qa.automation.locator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ResourceKeyTest {

    @ParameterizedTest
    @CsvSource({
            "booking.yml, booking, yml",
            "booking.yaml, booking, yaml",
            "Custom/Booking.yml, custom/booking, yml",
            "Booking.YML, booking, yml",
            " /Custom/Booking.YAML , custom/booking, yaml",
            "booking, booking, ''",
            "booking.json, booking.json, ''",
            "booking.yaml.yml, booking.yaml, yml"
    })
    void normalizesNameAndExtractsStemAndExtension(String name, String stem, String extension) {
        ResourceKey key = new ResourceKey(name);

        assertEquals(stem + (extension.isEmpty() ? "" : "." + extension), key.getName());
        assertEquals(stem, key.getStem());
        assertEquals(extension, key.getExtension());
        assertEquals(stem, key.toString());
    }

    @Test
    void rejectsNullName() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new ResourceKey(null));

        assertEquals("name is marked non-null but is null", exception.getMessage());
    }
}
