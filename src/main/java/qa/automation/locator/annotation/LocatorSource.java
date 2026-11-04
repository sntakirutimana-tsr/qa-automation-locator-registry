package qa.automation.locator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the YAML registry location and scope used for a class's locators.
 *
 * This annotation is metadata only. A consumer must call the registry API at its
 * chosen lifecycle boundary; Java does not process it automatically on instantiation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LocatorSource {

    /**
     * Classpath directory path for the YAML registry. Empty uses the configured
     * default path. This must be a directory path, not a file path.
     */
    String path() default "";

    /**
     * Required YAML file name, optionally including relative subdirectories.
     * An explicit .yml or .yaml extension selects that exact resource.
     */
    String key();

    /**
     * Optional dotted subtree path within the YAML file.
     * Resolved locator names are relative to this section.
     */
    String section() default "";
}
