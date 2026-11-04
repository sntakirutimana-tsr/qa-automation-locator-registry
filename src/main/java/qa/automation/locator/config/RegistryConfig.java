package qa.automation.locator.config;

import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class RegistryConfig {

    public static String registryPath() {
        return RegistryConfigLoader.get().getRegistryPath();
    }

    public static boolean isCacheEnabled() {
        return RegistryConfigLoader.get().isCacheEnabled();
    }

    public static String locatorSourceKeyRule() {
        return RegistryConfigLoader.get().getLocatorSourceKeyRule();
    }

    public static String locatorSourcePathRule() {
        return RegistryConfigLoader.get().getLocatorSourcePathRule();
    }

    public static String locatorSourceSectionRule() {
        return RegistryConfigLoader.get().getLocatorSourceSectionRule();
    }

    public static List<String> locatorDefinitionAllowedAttributes() {
        return List.copyOf(RegistryConfigLoader
                .get()
                .getLocatorDefinitionAllowedAttributes());
    }
}
