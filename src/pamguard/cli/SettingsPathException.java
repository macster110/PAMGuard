package pamguard.cli;

/**
 * Thrown when a settings path can't be found or a value can't be set.
 */
public class SettingsPathException extends Exception {

	private static final long serialVersionUID = 1L;

	public SettingsPathException(String message) {
		super(message);
	}

}
