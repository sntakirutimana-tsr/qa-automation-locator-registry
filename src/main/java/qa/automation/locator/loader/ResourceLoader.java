package qa.automation.locator.loader;

import qa.automation.locator.model.ResourceKey;

import lombok.NonNull;

public interface ResourceLoader<T> {

    ClassLoader getClassLoader();

    T load(@NonNull String resourcePath);

    T load(@NonNull java.nio.file.Path path);

    /**
     * Returns false for null, blank, or invalid resource keys.
     */
    boolean isResourceKeyValid(String key);

    ResourceKey getResourceKey(@NonNull String key);
}
