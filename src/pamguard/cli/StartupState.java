package pamguard.cli;

import java.io.File;

import PamController.PamController;

/**
 * Start up choices made while reading the command line which the main PAMGuard
 * class needs once all of the options have been read.
 */
public class StartupState {

	/**
	 * Run mode, one of the PamController.RUN_... constants.
	 */
	public int runMode = PamController.RUN_NORMAL;

	/**
	 * Show the splash screen.
	 */
	public boolean showSplash = true;

	/**
	 * Show the start up dialog (-c option).
	 */
	public boolean showStartupDialog = false;

	/**
	 * Settings file or database to open (-openfile option).
	 */
	public File openFile = null;

	/**
	 * Set when an information option (-help, -version) has done its job, so
	 * PAMGuard should exit without starting.
	 */
	public boolean exitNow = false;

}
