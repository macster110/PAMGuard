package pamguard.cli;

/**
 * Process exit codes used by PAMGuard so that scripts, batch processors and AI
 * assistants can tell whether a run worked without reading the log.
 * <p>
 * Before version 2.03 PAMGuard exited with its internal status (6, PAM_COMPLETE,
 * after a successful -autoexit run), so anything that tested for 6 should now test
 * for 0.
 */
public enum ExitCode {

	/**
	 * Run completed normally.
	 */
	OK(0, "Completed normally"),

	/**
	 * Unspecified error.
	 */
	ERROR(1, "Error"),

	/**
	 * A command line option was missing a value or had an invalid one.
	 */
	BAD_ARGUMENTS(2, "Invalid command line arguments"),

	/**
	 * Settings could not be loaded, or a settings override could not be applied.
	 */
	SETTINGS_ERROR(3, "Settings could not be loaded or applied"),

	/**
	 * One or more modules failed to prepare, so processing could not start.
	 */
	PREPARE_FAILED(4, "Processing could not start: one or more modules failed to prepare"),

	/**
	 * An offline task or export failed.
	 */
	TASK_FAILED(5, "An offline task or export failed");

	private final int code;

	private final String description;

	private ExitCode(int code, String description) {
		this.code = code;
		this.description = description;
	}

	/**
	 * @return the numeric process exit code
	 */
	public int getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

}
