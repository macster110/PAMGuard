package PamView;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Class to get called early on which will initialise FX 
 * so that it can be used within the Swing GUI. 
 * @author dg50
 *
 */
public class FXInitialiser {

	public static boolean haveRun = false;
	private static boolean fxOK = false;
	private static volatile boolean running = false;
	
	/**
	 * True if JavaFX was started through a Swing JFXPanel, false if it was started
	 * on its own (or has not been started here).
	 */
	private static boolean startedFromSwing = false;

	public synchronized static void initialise() {
		initialise(false);
	}

	/**
	 * Start JavaFX if it isn't already running.
	 * @param standalone - true to start JavaFX on its own rather than through a Swing
	 * JFXPanel. Used for the JavaFX GUI on macOS: when JavaFX is started from a
	 * JFXPanel the JavaFX menus can't go in the macOS menu bar at the top of the
	 * screen. Ignored on other platforms, where JavaFX is always started from a
	 * JFXPanel as before.
	 */
	public synchronized static void initialise(boolean standalone) {
		if (!haveRun) {
			running = true;
			boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
			if (standalone && mac) {
				try {
					Platform.startup(() -> fxInit());
				}
				catch (IllegalStateException e) {
					// JavaFX is already running.
					Platform.runLater(() -> fxInit());
				}
			}
			else {
				startedFromSwing = true;
				new JFXPanel();
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						fxInit();
					}
				});
			}
			while (running) {
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			haveRun = true;
		}
	}

	private static void fxInit() {
		try {
	        Group  root  =  new  Group();
	        Scene  scene  =  new  Scene(root, Color.BLACK);
			fxOK = true;
		}
		catch (Exception e){
			e.printStackTrace();
			fxOK = false;
		}
		running = false;
	}

	/**
	 * @return true if JavaFX was started through a Swing JFXPanel.
	 */
	public static boolean isStartedFromSwing() {
		return startedFromSwing;
	}

	public static boolean isFxOK() {
		return fxOK;
	}
}
