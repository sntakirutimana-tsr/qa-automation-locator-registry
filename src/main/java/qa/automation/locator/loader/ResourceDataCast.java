package qa.automation.locator.loader;

import qa.automation.locator.exception.IllegalLocatorRegistryArgumentException;

import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

import static qa.automation.locator.utils.FormatterUtils.f;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class ResourceDataCast {

    public static Map<String, Object> asMap(@NonNull Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalLocatorRegistryArgumentException("Expected a mapping with string keys.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalLocatorRegistryArgumentException("Mapping keys must be strings.");
            }
            result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    public static <T> List<T> requiredAsList(@NonNull Map<@NonNull String, Object> values,
                                             @NonNull String key,
                                             @NonNull Class<T> requiredType) {

        List<?> rawList = requiredAs(values, key, List.class);

        if (!rawList.stream().allMatch(requiredType::isInstance)) {
            throw new IllegalLocatorRegistryArgumentException(f(
                    "Invalid list sub-cast-type: {}. Expected List<{}>",
                    key,
                    requiredType.getSimpleName()
            ));
        }

        return rawList.stream()
                .map(requiredType::cast)
                .toList();
    }

    public static <T> T requiredAs(@NonNull Map<@NonNull String, Object> values,
                                   @NonNull String key,
                                   @NonNull Class<T> requiredType) {

        Object value = values.get(key);

        if (!(requiredType.isInstance(value))) {
            throw new IllegalLocatorRegistryArgumentException(f(
                    "Missing or invalid cast-type: {}. Expected {}, but got {}",
                    key,
                    requiredType.getSimpleName(),
                    (value == null ? "null" : value.getClass().getSimpleName())
            ));
        }

        return requiredType.cast(value);
    }
}
