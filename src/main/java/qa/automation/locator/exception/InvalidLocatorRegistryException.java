package qa.automation.locator.exception;

public final class InvalidLocatorRegistryException extends RegistryBaseException {
    public InvalidLocatorRegistryException(String message) {
        super(message);
    }

    public InvalidLocatorRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
