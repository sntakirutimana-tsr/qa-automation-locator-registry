package qa.automation.locator.loader;

import qa.automation.locator.model.ResourceKey;
import qa.automation.locator.config.RegistryConfig;
import qa.automation.locator.exception.LocatorRegistryResourceNotFoundException;
import qa.automation.locator.exception.LocatorRegistryResourceMountingException;

import lombok.Getter;
import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static qa.automation.locator.utils.FormatterUtils.f;
import static qa.automation.locator.utils.LocatorNameUtils.normalize;

public class YamlResourceLoader implements ResourceLoader<Map<String, Object>> {

    @Getter
    private final ClassLoader classLoader;

    public YamlResourceLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public YamlResourceLoader(ClassLoader classLoader) {
        this.classLoader = (classLoader == null)
                ? getClass().getClassLoader()
                : classLoader;
    }

    @Override
    public Map<String, Object> load(Path path) {
        Objects.requireNonNull(path, "YAML path must not be null");

        try (InputStream input = Files.newInputStream(path)) {
            return parse(input, path.toString());
        } catch (IOException | YAMLException exception) {
            throw new LocatorRegistryResourceMountingException("Failed to load YAML file: " + path, exception);
        }
    }

    public static String cleanResourceKeyOrPath(String resourceKeyOrPath) {

        return ResourceKey.normalizeName(resourceKeyOrPath);
    }

    @Override
    public boolean isResourceKeyValid(String resourceName) {
        return resourceName != null
                && !resourceName.isBlank()
                && resourceName.matches(RegistryConfig.locatorSourceKeyRule());
    }

    @Override
    public ResourceKey getResourceKey(@NonNull String resourceName) {
        return new ResourceKey(cleanResourceKeyOrPath(resourceName));
    }

    @Override
    public Map<String, Object> load(String resourceUri) {

        Objects.requireNonNull(resourceUri, "YAML resource must not be null");

        ResourceKey resourceKey = getResourceKey(resourceUri);
        List<String> extensions = resourceKey.getExtension().isEmpty()
                ? List.of(".yaml", ".yml")
                : List.of("." + resourceKey.getExtension());

        for (String extension : extensions) {

            URL resourceUrl = classLoader.getResource(resourceKey.getStem() + extension);

            if (resourceUrl != null) {
                try (InputStream input = openResourceStream(resourceUrl)) {
                    return parse(input, resourceUri);
                } catch (IOException | YAMLException exception) {
                    throw new LocatorRegistryResourceMountingException(
                            "Failed to load YAML resource: " + resourceUri,
                            exception
                    );
                }
            }
        }

        throw new LocatorRegistryResourceNotFoundException("YAML resource not found in classpath: " + resourceUri);
    }

    private static InputStream openResourceStream(URL resourceUrl) throws IOException {
        URLConnection connection = resourceUrl.openConnection();
        // Avoid retaining cached JAR handles after the resource stream is closed.
        connection.setUseCaches(false);
        return connection.getInputStream();
    }

    private static Object getDocument(InputStream input, @NonNull String source) {

        if (input == null) {
            throw new LocatorRegistryResourceNotFoundException("YAML resource not found in classpath: " + source);
        }

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object document = yaml.load(input);

        if (document == null) {
            throw new LocatorRegistryResourceMountingException(f(
                    "YAML document~{} is empty or malformed", source
            ));
        }

        if (!(document instanceof Map<?, ?>)) {
            throw new LocatorRegistryResourceMountingException("YAML root must be a mapping: " + source);
        }

        rejectRecursiveNodes(document, source,
                Collections.newSetFromMap(new IdentityHashMap<>()),
                Collections.newSetFromMap(new IdentityHashMap<>()));
        return document;
    }

    private static void rejectRecursiveNodes(Object node, String source,
                                             Set<Object> active, Set<Object> visited) {
        if (!(node instanceof Map<?, ?>) && !(node instanceof Collection<?>)) {
            return;
        }
        if (active.contains(node)) {
            throw new LocatorRegistryResourceMountingException("Recursive YAML aliases are not supported: " + source);
        }
        if (!visited.add(node)) {
            return;
        }
        active.add(node);
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                rejectRecursiveNodes(key, source, active, visited);
                rejectRecursiveNodes(value, source, active, visited);
            });
        } else if (node instanceof Collection<?> collection) {
            collection.forEach(value -> rejectRecursiveNodes(value, source, active, visited));
        }
        active.remove(node);
    }

    private static Map<String, Object> parse(InputStream input, @NonNull String source) {

        Object document = getDocument(input, source);

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) document).entrySet()) {

            if (!(entry.getKey() instanceof String key)) {
                throw new LocatorRegistryResourceMountingException("YAML root keys must be strings: " + source);
            }

            String name = normalize(key);

            if (result.containsKey(name)) {
                throw new LocatorRegistryResourceMountingException(f(
                        "Duplicate normalized YAML root key '{}' in '{}'", name, source
                ));
            }

            result.put(name, entry.getValue());
        }

        return Collections.unmodifiableMap(result);
    }
}
