package qa.automation.locator.core;

import qa.automation.locator.annotation.LocatorSource;
import qa.automation.locator.config.RegistryConfig;
import qa.automation.locator.loader.YamlResourceLoader;
import qa.automation.locator.exception.*;
import qa.automation.locator.model.*;

import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static qa.automation.locator.utils.FormatterUtils.f;
import static qa.automation.locator.utils.LocatorNameUtils.normalize;

public final class RegistryEngine {

    private final RegistryLoader loader;
    private final String registryPath;
    private final boolean cacheEnabled;
    private final Pattern keyRule;
    private final Pattern pathRule;
    private final Pattern sectionRule;
    private final ConcurrentMap<
            @NonNull String,
            @NonNull Map<@NonNull String, LocatorDefinition>> registryCache = new ConcurrentHashMap<>();

    public RegistryEngine() {
        this(RegistryConfig.registryPath(), RegistryConfig.isCacheEnabled(),
                Thread.currentThread().getContextClassLoader());
    }

    public RegistryEngine(@NonNull String registryPath, boolean cacheEnabled, ClassLoader classLoader) {
        keyRule = Pattern.compile(RegistryConfig.locatorSourceKeyRule());
        pathRule = Pattern.compile(RegistryConfig.locatorSourcePathRule());
        sectionRule = Pattern.compile(RegistryConfig.locatorSourceSectionRule());
        if (!pathRule.matcher(registryPath).matches()) {
            throw new IllegalLocatorRegistryArgumentException("Invalid registry path configuration: " + registryPath);
        }
        this.registryPath = YamlResourceLoader.cleanResourceKeyOrPath(registryPath);
        this.cacheEnabled = cacheEnabled;
        this.loader = new RegistryLoader(classLoader);
    }

    private LocatorSource getLocatorSourceAnnotation(Class<?> annotatedType) {

        if (annotatedType == null) {
            throw new InvalidLocatorRegistryAnnotationException("Annotated type must not be null");
        }

        LocatorSource source = annotatedType.getAnnotation(LocatorSource.class);

        if (source == null) {
            throw new InvalidLocatorRegistryAnnotationException(f(
                    "Class '{}' is missing @LocatorSource annotation",
                    annotatedType.getName()
            ));
        }

        return source;
    }

    private ResourceKey getLocatorSourceKey(LocatorSource source) {

        if (!source.key().isBlank() && keyRule.matcher(source.key()).matches()) {
            return new ResourceKey(source.key());
        }

        throw new InvalidLocatorDefinitionException(f(
                "Invalid @LocatorSource YAML file name on {}",
                source.annotationType().getName()
        ));
    }

    private String getLocatorSourcePath(String path, Class<?> type) {

        if (path == null || path.isBlank()) {
            return registryPath;
        }

        if (pathRule.matcher(path).matches()) {
            return YamlResourceLoader.cleanResourceKeyOrPath(path);
        }

        throw new InvalidLocatorDefinitionException(f(
                "Invalid classpath registry path '{}' declared on {}",
                path,
                type.getName()
        ));
    }

    private String buildFullResourceUri(String path, String fileName) {
        return f("{}/{}", path, fileName).toLowerCase(Locale.ROOT);
    }

    private String getSourceSection(String section) {

        if (section == null || section.isBlank()) {
            return "";
        }

        if (sectionRule.matcher(section).matches()) {
            return normalize(section);
        }

        throw new InvalidLocatorDefinitionException(f(
                "Invalid registry section '{}' declared",
                section
        ));
    }

    public ResolvedLocators resolve(Class<?> annotatedType) {

        LocatorSource source = getLocatorSourceAnnotation(annotatedType);
        String sourcePath = getLocatorSourcePath(source.path(), annotatedType);
        ResourceKey sourceKey = getLocatorSourceKey(source);
        String resourceUri = buildFullResourceUri(sourcePath, sourceKey.getName());
        String sourceSection = getSourceSection(source.section());

        Map<String, LocatorDefinition> resolvedDefinitions = getDocument(resourceUri);

        return sourceSection.isBlank()
                ? new ResolvedLocators(sourcePath, sourceKey.getStem(), "", resolvedDefinitions)
                : resolveSectionLocators(resolvedDefinitions, sourcePath, sourceKey.getStem(), sourceSection);
    }

    private Map<String, LocatorDefinition> getDocument(String resourceUri) {

        return (!cacheEnabled)
                ? loader.loadAndFlatten(resourceUri)
                : registryCache.computeIfAbsent(resourceUri, loader::loadAndFlatten);
    }

    private ResolvedLocators resolveSectionLocators(Map<String, LocatorDefinition> definitions,
                                                    String sourcePath,
                                                    String sourceKeyStem,
                                                    String sourceSection) {

        String resourceUri = buildFullResourceUri(sourcePath, sourceKeyStem);
        String prefix = sourceSection + ".";
        Map<String, LocatorDefinition> filtered = new LinkedHashMap<>();
        definitions.forEach((name, definition) -> {
            if (name.startsWith(prefix)) {
                filtered.put(name.substring(prefix.length()), definition);
            }
        });

        if (filtered.isEmpty()) {
            throw new LocatorSectionNotFoundException(f(
                    "No section '{}' found in registry '{}'", sourceSection, resourceUri
            ));
        }

        return new ResolvedLocators(sourcePath, sourceKeyStem, sourceSection, filtered);
    }
}
