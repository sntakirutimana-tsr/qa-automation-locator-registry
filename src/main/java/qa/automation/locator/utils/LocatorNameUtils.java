package qa.automation.locator.utils;

import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Locale;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class LocatorNameUtils {

    public static String normalize(@NonNull String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
