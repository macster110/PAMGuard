package pamguard.cli;

/**
 * Thrown when a command line option is missing a value or has an invalid one.
 */
public class CommandLineException extends Exception {

	private static final long serialVersionUID = 1L;

	public CommandLineException(String message) {
		super(message);
	}

}
