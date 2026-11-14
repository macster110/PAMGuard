package pamguard;

import PamController.PamController;
import PamController.PamGUIManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The JavaFX application for PAMGuard.
 * <p>
 * All command line handling, the start up dialog, logging and platform
 * integration are in {@link Pamguard#main(String[])}, which is shared by the
 * Swing and JavaFX GUIs. This class only holds the JavaFX {@link Application}
 * which gives PAMGuard its primary stage. {@link #main(String[])} is the same
 * as running {@link Pamguard} with the -fx option.
 * 
 * @author Jamie Macaulay
 *
 */
public class PamguardFX extends Application {

	private static int runMode = PamController.RUN_NORMAL;

	@Override
	public void start(Stage primaryStage) throws Exception {
		try {
			System.out.println("javafx.runtime.version: " + System.getProperty("javafx.runtime.version"));
			PamController.create(runMode, primaryStage);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start PAMGuard with the JavaFX GUI. Does not return until PAMGuard exits.
	 * Called from {@link Pamguard#main(String[])} once the command line has been
	 * read and the run mode is known.
	 * @param runMode - the PAMGuard run mode.
	 */
	public static void launchFX(int runMode) {
		PamguardFX.runMode = runMode;
		PamGUIManager.setType(PamGUIManager.FX);
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		launch(PamguardFX.class);
	}

	/**
	 * Start PAMGuard with the JavaFX GUI. Takes the same options as
	 * {@link Pamguard#main(String[])}.
	 * @param args - command line options.
	 */
	public static void main(String[] args) {
		PamGUIManager.setType(PamGUIManager.FX);
		Pamguard.main(args);
	}

}
