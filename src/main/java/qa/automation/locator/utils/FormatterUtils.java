package qa.automation.locator.utils;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.slf4j.helpers.MessageFormatter;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class FormatterUtils {

    public static String f(@NonNull String template, @NonNull Object... args) {
        return MessageFormatter.arrayFormat(template, args).getMessage();
    }
}
