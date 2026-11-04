package qa.automation.locator.exception;

/**
 * @deprecated Annotation keys select files, not YAML scopes. Use
 * {@link LocatorRegistryResourceNotFoundException} for missing files.
 */
@Deprecated(since = "1.0", forRemoval = false)
public final class LocatorKeyNotFoundException extends RegistryBaseException {
    public LocatorKeyNotFoundException(String message) {
        super(message);
    }
}
