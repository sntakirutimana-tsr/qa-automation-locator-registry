package qa.automation.locator.core;

import qa.automation.locator.annotation.LocatorSource;
import qa.automation.locator.exception.*;
import qa.automation.locator.model.ResolvedLocators;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RegistryEngineTest {

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() throws IOException {
        write("engine.yml", yaml("origin"));
        write("deeply/nested/engine.yml", yaml("nested"));
    }

    @Test
    void resolvesDirectGroupsWithoutAFileNameWrapper() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            ResolvedLocators result = engine(loader, true).resolve(Whole.class);

            assertEquals("origin", result.get("fields.origin").value());
            assertTrue(result.contains("actions.search"));
            assertTrue(result.contains("components.calendar.fields.departure"));
            assertFalse(result.contains("core-fixtures/engine.fields.origin"));
        }
    }

    @Test
    void selectsEntireSubtreeAndReturnsSectionRelativeNames() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertEquals("origin", registry.resolve(Fields.class).get("origin").value());
            ResolvedLocators components = registry.resolve(Components.class);
            assertTrue(components.contains("calendar.fields.departure"));
            assertFalse(components.contains("components.calendar.fields.departure"));
            assertFalse(components.contains("other"));
            assertTrue(registry.resolve(NestedSection.class).contains("departure"));
        }
    }

    @Test
    void normalizesNamesAndPreservesDottedSectionsAndLookup() throws IOException {
        write("engine.yml", "' Components ':\n  Calendar.Fields:\n"
                + "    Departure: {strategy: id, value: DepartureId}\n");
        try (URLClassLoader loader = classLoader()) {
            ResolvedLocators result = engine(loader, true).resolve(NestedSection.class);
            assertEquals("DepartureId", result.get(" DEPARTURE ").value());
        }
    }

    @Test
    void resolvesNestedFilesWithEitherPathOrKeyDirectories() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertEquals("nested", registry.resolve(NestedPath.class).get("fields.origin").value());
            assertEquals("nested", registry.resolve(NestedKey.class).get("fields.origin").value());
        }
    }

    @Test
    void usesInjectedDefaultsForBlankAnnotationPaths() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertTrue(registry.resolve(DefaultPath.class).contains("fields.origin"));
            assertTrue(registry.resolve(WhitespacePath.class).contains("fields.origin"));
        }
    }

    @Test
    void rejectsInvalidAnnotationsBeforeLoadingResources() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertThrows(InvalidLocatorRegistryAnnotationException.class, () -> registry.resolve(null));
            assertThrows(InvalidLocatorRegistryAnnotationException.class, () -> registry.resolve(String.class));
            assertThrows(InvalidLocatorDefinitionException.class, () -> registry.resolve(InvalidKey.class));
            assertThrows(InvalidLocatorDefinitionException.class, () -> registry.resolve(InvalidPath.class));
            Files.delete(directory.resolve("core-fixtures").resolve("engine.yml"));
            assertThrows(InvalidLocatorDefinitionException.class, () -> registry.resolve(InvalidSection.class));
        }
    }

    @Test
    void distinguishesMissingSectionsLocatorsAndFiles() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertThrows(LocatorSectionNotFoundException.class, () -> registry.resolve(MissingSection.class));
            assertThrows(LocatorSectionNotFoundException.class, () -> registry.resolve(LocatorAsSection.class));
            Files.delete(directory.resolve("core-fixtures").resolve("engine.yml"));
            RegistryEngine fresh = engine(loader, true);
            assertThrows(LocatorRegistryResourceNotFoundException.class, () -> fresh.resolve(Whole.class));
        }
    }

    @Test
    void honorsExplicitExtensionsAndKeepsTheirCachesSeparate() throws IOException {
        write("engine.yaml", yaml("yaml"));
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertEquals("origin", registry.resolve(Whole.class).get("fields.origin").value());
            assertEquals("yaml", registry.resolve(YamlFile.class).get("fields.origin").value());
            assertEquals("yaml", registry.resolve(Extensionless.class).get("fields.origin").value());
        }
    }

    @Test
    void explicitExtensionDoesNotFallBackToAnotherFile() throws IOException {
        write("engine.yaml", yaml("yaml"));
        Files.delete(directory.resolve("core-fixtures").resolve("engine.yml"));
        try (URLClassLoader loader = classLoader()) {
            assertThrows(LocatorRegistryResourceNotFoundException.class,
                    () -> engine(loader, true).resolve(Whole.class));
        }
    }

    @Test
    void reusesOnlyTheOwningEnginesCachedDocuments() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertEquals("origin", registry.resolve(Whole.class).get("fields.origin").value());
            write("engine.yml", yaml("updated"));
            assertEquals("origin", registry.resolve(Whole.class).get("fields.origin").value());
            assertEquals("updated", engine(loader, true).resolve(Whole.class).get("fields.origin").value());
        }
    }

    @Test
    void reloadsWhenCachingIsDisabled() throws IOException {
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, false);
            assertEquals("origin", registry.resolve(Whole.class).get("fields.origin").value());
            write("engine.yml", yaml("updated"));
            assertEquals("updated", registry.resolve(Whole.class).get("fields.origin").value());
        }
    }

    @Test
    void supportsConcurrentResolutionsWithoutGlobalConfigurationChanges() throws Exception {
        try (URLClassLoader loader = classLoader();
             var executor = Executors.newFixedThreadPool(4)) {
            RegistryEngine registry = engine(loader, true);
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                results.add(executor.submit(() -> registry.resolve(Fields.class).get("origin").value()));
            }
            for (Future<String> result : results) {
                assertEquals("origin", result.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void isolatesResourcesBetweenClassLoaders() throws IOException {
        Path other = directory.resolve("other-classpath");
        Path file = other.resolve("core-fixtures").resolve("engine.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml("other"));
        try (URLClassLoader first = classLoader();
             URLClassLoader second = new URLClassLoader(new URL[]{other.toUri().toURL()}, null);
             URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            assertEquals("origin", engine(first, true).resolve(Whole.class).get("fields.origin").value());
            assertEquals("other", engine(second, true).resolve(Whole.class).get("fields.origin").value());
            assertThrows(LocatorRegistryResourceNotFoundException.class,
                    () -> engine(empty, true).resolve(Whole.class));
        }
    }

    @Test
    void failedLoadDoesNotPoisonCache() throws IOException {
        write("engine.yml", "field: [unterminated");
        try (URLClassLoader loader = classLoader()) {
            RegistryEngine registry = engine(loader, true);
            assertThrows(LocatorRegistryResourceMountingException.class, () -> registry.resolve(Whole.class));
            write("engine.yml", yaml("recovered"));
            assertEquals("recovered", registry.resolve(Whole.class).get("fields.origin").value());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../outside", "folder//nested", "file.yml"})
    void rejectsInvalidInjectedRegistryDirectories(String path) {
        assertThrows(IllegalLocatorRegistryArgumentException.class,
                () -> new RegistryEngine(path, true, getClass().getClassLoader()));
    }

    @Test
    void rejectsNullInjectedPath() {
        assertThrows(NullPointerException.class, () -> new RegistryEngine(null, true, null));
    }

    @Test
    void defaultConstructorAndNullLoaderUseLibraryConfiguration() {
        assertNotNull(new RegistryEngine());
        RegistryEngine registry = new RegistryEngine("missing-directory", false, null);
        assertThrows(LocatorRegistryResourceNotFoundException.class, () -> registry.resolve(DefaultPath.class));
    }

    private RegistryEngine engine(ClassLoader loader, boolean cacheEnabled) {
        return new RegistryEngine("core-fixtures", cacheEnabled, loader);
    }

    private URLClassLoader classLoader() throws IOException {
        return new URLClassLoader(new URL[]{directory.toUri().toURL()}, null);
    }

    private void write(String name, String yaml) throws IOException {
        Path file = directory.resolve("core-fixtures").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }

    private static String yaml(String value) {
        return "fields:\n  origin: {strategy: id, value: " + value + "}\n"
                + "actions:\n  search: {strategy: xpath, value: '//button'}\n"
                + "components:\n  calendar:\n    fields:\n"
                + "      departure: {strategy: id, value: departure}\n"
                + "components-other:\n  other: {strategy: id, value: other}\n";
    }

    @LocatorSource(path = "core-fixtures", key = "engine.yml")
    private static class Whole { }
    @LocatorSource(path = "core-fixtures", key = "engine.yaml")
    private static class YamlFile { }
    @LocatorSource(path = "core-fixtures", key = "engine")
    private static class Extensionless { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = " Fields ")
    private static class Fields { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = "components")
    private static class Components { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = "components.calendar.fields")
    private static class NestedSection { }
    @LocatorSource(path = "core-fixtures/deeply/nested", key = "engine.yml")
    private static class NestedPath { }
    @LocatorSource(path = "core-fixtures", key = "deeply/nested/engine.yml")
    private static class NestedKey { }
    @LocatorSource(key = "engine.yml")
    private static class DefaultPath { }
    @LocatorSource(path = " \t ", key = "engine.yml")
    private static class WhitespacePath { }
    @LocatorSource(path = "core-fixtures", key = "../engine.yml")
    private static class InvalidKey { }
    @LocatorSource(path = "../invalid", key = "engine.yml")
    private static class InvalidPath { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = "fields..origin")
    private static class InvalidSection { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = "missing")
    private static class MissingSection { }
    @LocatorSource(path = "core-fixtures", key = "engine.yml", section = "fields.origin")
    private static class LocatorAsSection { }
}
