package qa.automation.locator.model;

import lombok.Getter;
import lombok.NonNull;

import java.util.Locale;

@Getter
public final class ResourceKey {

    private final String name;
    private final String stem;
    private final String extension;

    public ResourceKey(@NonNull String name) {
        this.name = normalizeName(name);
        this.stem = extractStem(this.name);
        this.extension = extractExtension(this.name);
    }

    public static String normalizeName(@NonNull String name) {
        return name.trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
    }

    private static String extractStem(@NonNull String name) {
        return name
                .replaceAll("\\.ya?ml$", "");
    }

    private static String extractExtension(@NonNull String name) {
        return name.replaceAll("^.*\\.(ya?ml)$|^.*$", "$1");
    }

    @Override
    public String toString() {
        return stem;
    }
}
