package qa.automation.locator.loader;

import qa.automation.locator.exception.LocatorRegistryResourceMountingException;
import qa.automation.locator.exception.LocatorRegistryResourceNotFoundException;
import qa.automation.locator.model.ResourceKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class YamlResourceLoaderTest {

    @TempDir
    Path directory;

    @Test
    void choosesExplicitContextAndFallbackClassLoaders() throws IOException {
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader custom = new URLClassLoader(new URL[0], null)) {
            assertSame(custom, new YamlResourceLoader(custom).getClassLoader());
            Thread.currentThread().setContextClassLoader(custom);
            assertSame(custom, new YamlResourceLoader().getClassLoader());
            Thread.currentThread().setContextClassLoader(null);
            assertSame(YamlResourceLoader.class.getClassLoader(), new YamlResourceLoader().getClassLoader());
            assertSame(YamlResourceLoader.class.getClassLoader(), new YamlResourceLoader(null).getClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void loadsMappingWithLowercaseOrderedKeysAndImmutableTopLevel() throws IOException {
        Path file = write("mapping.yml", "Origin: first\nDestination:\n  value: second\n");

        Map<String, Object> result = new YamlResourceLoader().load(file);

        assertEquals(List.of("origin", "destination"), new ArrayList<>(result.keySet()));
        assertEquals("first", result.get("origin"));
        assertEquals(Map.of("value", "second"), result.get("destination"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("extra", "value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n", "- one\n- two\n", "a scalar", "42: value"})
    void rejectsEmptyNonMappingAndNonStringKeyDocuments(String yaml) throws IOException {
        Path file = write("invalid.yml", yaml);

        assertThrows(LocatorRegistryResourceMountingException.class,
                () -> new YamlResourceLoader().load(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"field: [unterminated", "field: one\nfield: two\n"})
    void wrapsYamlSyntaxAndDuplicateKeyErrors(String yaml) throws IOException {
        Path file = write("invalid.yml", yaml);

        LocatorRegistryResourceMountingException exception = assertThrows(
                LocatorRegistryResourceMountingException.class,
                () -> new YamlResourceLoader().load(file));

        assertEquals("Failed to load YAML file: " + file, exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void wrapsMissingFileIoErrors() {
        Path missing = directory.resolve("missing.yml");
        LocatorRegistryResourceMountingException exception = assertThrows(
                LocatorRegistryResourceMountingException.class,
                () -> new YamlResourceLoader().load(missing));

        assertInstanceOf(IOException.class, exception.getCause());
        assertTrue(exception.getMessage().contains(missing.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Origin: first\norigin: second\n",
            "' origin ': first\norigin: second\n", "Origin: null\norigin: second\n"})
    void rejectsNormalizedRootKeyCollisions(String yaml) throws IOException {
        Path file = write("collision.yml", yaml);

        LocatorRegistryResourceMountingException exception = assertThrows(
                LocatorRegistryResourceMountingException.class,
                () -> new YamlResourceLoader().load(file));

        assertTrue(exception.getMessage().contains("origin"));
        assertTrue(exception.getMessage().contains(file.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page: &page\n  child: *page\n",
            "page: &page\n  children:\n    - *page\n",
            "page: &page\n  child:\n    parent: *page\n",
            "children: &children\n  - *children\n"
    })
    void rejectsRecursiveAliasesWithSourceContext(String yaml) throws IOException {
        Path file = write("recursive.yml", yaml);

        LocatorRegistryResourceMountingException exception = assertThrows(
                LocatorRegistryResourceMountingException.class, () -> new YamlResourceLoader().load(file));

        assertEquals("Recursive YAML aliases are not supported: " + file, exception.getMessage());
    }

    @Test
    void acceptsSharedAliasesWithoutTreatingThemAsCycles() throws IOException {
        Path file = write("aliases.yml",
                "first: &definition {strategy: id, value: origin}\nsecond: *definition\n");

        Map<String, Object> result = new YamlResourceLoader().load(file);

        assertEquals(Map.of("strategy", "id", "value", "origin"), result.get("first"));
        assertEquals(result.get("first"), result.get("second"));
    }

    @Test
    void acceptsSharedCollectionAliasesAndEqualIndependentMappings() throws IOException {
        Path file = write("aliases.yml",
                "first: &items [one, two]\nsecond: *items\n"
                        + "third: {name: same}\nfourth: {name: same}\n");

        Map<String, Object> result = new YamlResourceLoader().load(file);

        assertEquals(List.of("one", "two"), result.get("first"));
        assertEquals(result.get("first"), result.get("second"));
        assertEquals(result.get("third"), result.get("fourth"));
    }

    @Test
    void rejectsRecursiveAliasesInsideJars() throws IOException {
        Path jar = writeJar("recursive.yml", "page: &page\n  child: *page\n");
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            LocatorRegistryResourceMountingException exception = assertThrows(
                    LocatorRegistryResourceMountingException.class,
                    () -> new YamlResourceLoader(classLoader).load("recursive.yml"));

            assertEquals("Recursive YAML aliases are not supported: recursive.yml", exception.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"booking", "booking.yml", " /BOOKING.YML "})
    void resolvesClasspathResourceNamesAndExtensions(String resource) throws IOException {
        write("booking.yml", "Origin: origin\n");
        try (URLClassLoader classLoader = resourceClassLoader()) {
            assertEquals(Map.of("origin", "origin"), new YamlResourceLoader(classLoader).load(resource));
        }
    }

    @Test
    void prefersYamlOverYmlWhenBothExist() throws IOException {
        write("booking.yaml", "extension: yaml\n");
        write("booking.yml", "extension: yml\n");
        try (URLClassLoader classLoader = resourceClassLoader()) {
            assertEquals(Map.of("extension", "yaml"),
                    new YamlResourceLoader(classLoader).load("booking"));
            assertEquals(Map.of("extension", "yml"),
                    new YamlResourceLoader(classLoader).load("booking.yml"));
            assertEquals(Map.of("extension", "yaml"),
                    new YamlResourceLoader(classLoader).load("booking.yaml"));
        }
    }

    @Test
    void reportsMissingClasspathResource() throws IOException {
        try (URLClassLoader classLoader = resourceClassLoader()) {
            LocatorRegistryResourceNotFoundException exception = assertThrows(
                    LocatorRegistryResourceNotFoundException.class,
                    () -> new YamlResourceLoader(classLoader).load("missing"));

            assertEquals("YAML resource not found in classpath: missing", exception.getMessage());
        }
    }

    @Test
    void wrapsUnavailableResourceIoErrors() throws IOException {
        URL missingUrl = directory.resolve("missing.yaml").toUri().toURL();
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return missingUrl;
            }
        };

        LocatorRegistryResourceMountingException exception = assertThrows(
                LocatorRegistryResourceMountingException.class,
                () -> new YamlResourceLoader(classLoader).load("booking"));

        assertInstanceOf(IOException.class, exception.getCause());
        assertEquals("Failed to load YAML resource: booking", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nested/deep/booking", "nested/deep/booking.yml",
            " /NESTED/DEEP/BOOKING.YML "})
    void loadsNestedResourcesInsideJars(String resource) throws IOException {
        Path jar = writeJar("nested/deep/booking.yml", "Origin: origin\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            Map<String, Object> result = new YamlResourceLoader(classLoader).load(resource);

            assertEquals(Map.of("origin", "origin"), result);
            assertThrows(UnsupportedOperationException.class, () -> result.put("extra", "value"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {".yaml", ".yml"})
    void loadsBothYamlExtensionsInsideJars(String extension) throws IOException {
        Path jar = writeJar("nested/booking" + extension, "origin: origin\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            assertEquals(Map.of("origin", "origin"),
                    new YamlResourceLoader(classLoader).load("nested/booking" + extension));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"field: [unterminated", "field: one\nfield: two\n"})
    void wrapsYamlSyntaxAndDuplicateKeyErrorsInsideJars(String yaml) throws IOException {
        Path jar = writeJar("nested/invalid.yaml", yaml);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            LocatorRegistryResourceMountingException exception = assertThrows(
                    LocatorRegistryResourceMountingException.class,
                    () -> new YamlResourceLoader(classLoader).load("nested/invalid"));

            assertEquals("Failed to load YAML resource: nested/invalid", exception.getMessage());
            assertNotNull(exception.getCause());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "- one\n", "42: value"})
    void rejectsInvalidDocumentsInsideJars(String yaml) throws IOException {
        Path jar = writeJar("nested/invalid.yaml", yaml);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            LocatorRegistryResourceMountingException exception = assertThrows(
                    LocatorRegistryResourceMountingException.class,
                    () -> new YamlResourceLoader(classLoader).load("nested/invalid"));

            assertTrue(exception.getMessage().contains("nested/invalid"));
        }
    }

    @Test
    void reportsMissingResourceInsideAJar() throws IOException {
        Path jar = writeJar("nested/booking.yml", "origin: origin\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            LocatorRegistryResourceNotFoundException exception = assertThrows(
                    LocatorRegistryResourceNotFoundException.class,
                    () -> new YamlResourceLoader(classLoader).load("nested/missing"));

            assertEquals("YAML resource not found in classpath: nested/missing", exception.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"booking", "custom/booking.yml", "/custom/booking.yaml", " custom/booking "})
    void acceptsValidResourceKeys(String key) {
        assertTrue(new YamlResourceLoader().isResourceKeyValid(key));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../booking", "booking/", "booking.json", "custom//booking"})
    void rejectsInvalidResourceKeys(String key) {
        assertFalse(new YamlResourceLoader().isResourceKeyValid(key));
    }

    @Test
    void cleansAndExtractsResourceKeys() {
        ResourceKey key = new YamlResourceLoader().getResourceKey(" /CUSTOM/Booking.yml ");

        assertEquals("custom/booking.yml", key.getName());
        assertEquals("custom/booking", key.getStem());
        assertEquals("yml", key.getExtension());
        assertEquals("custom/booking", YamlResourceLoader.cleanResourceKeyOrPath(" /CUSTOM/Booking "));
    }

    @Test
    void rejectsNullInputs() {
        YamlResourceLoader loader = new YamlResourceLoader();

        assertThrows(NullPointerException.class, () -> loader.load((Path) null));
        assertThrows(NullPointerException.class, () -> loader.load((String) null));
        assertThrows(NullPointerException.class, () -> loader.getResourceKey(null));
        assertThrows(NullPointerException.class, () -> YamlResourceLoader.cleanResourceKeyOrPath(null));
    }

    private Path write(String name, String yaml) throws IOException {
        return Files.writeString(directory.resolve(name), yaml);
    }

    private URLClassLoader resourceClassLoader() throws IOException {
        return new URLClassLoader(new URL[]{directory.toUri().toURL()}, null);
    }

    private Path writeJar(String resource, String yaml) throws IOException {
        Path jar = directory.resolve("registry resources.jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(resource));
            output.write(yaml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        return jar;
    }
}
