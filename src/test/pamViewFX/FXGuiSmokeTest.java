package test.pamViewFX;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamGUIManager;
import PamController.SettingsPane;
import PamModel.PamModuleInfo;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import pamViewFX.PamControlledGUIFX;
import pamViewFX.PamGuiManagerFX;

/**
 * A smoke test of the JavaFX GUI. It starts PAMGuard with the JavaFX GUI on a blank 
 * configuration, adds every module which has a JavaFX GUI, opens the settings dialog 
 * of every module and presses OK. The test fails if anything throws an exception, or 
 * if a settings dialog reports an error when reading its own default settings.
 * <p>
 * A dialog which refuses to close because a default value needs the user (e.g. no 
 * channels selected yet) is not a failure: the test presses OK twice and then Cancel. 
 * <p>
 * The test needs a display, so it only runs when asked for, e.g. on Linux
 * <pre>
 * xvfb-run mvn test -Dtest=FXGuiSmokeTest -Dpamguard.fxsmoke=true -Dprism.order=sw
 * </pre>
 * It starts the whole application, so run it in its own JVM. 
 */
public class FXGuiSmokeTest {

	/**
	 * Problems found during the test. 
	 */
	private final List<String> problems = Collections.synchronizedList(new ArrayList<String>());

	/**
	 * What the test is doing, used to label problems. 
	 */
	private volatile String currentStep = "start up";

	/**
	 * Number of times each dialog has been seen during one step. 
	 */
	private final HashMap<String, Integer> dialogCounts = new HashMap<String, Integer>();

	@Test
	@EnabledIfSystemProperty(named = "pamguard.fxsmoke", matches = "true")
	public void addEveryModuleAndOpenEverySettingsDialog() throws Exception {
		File folder = Files.createTempDirectory("pamguardFXSmoke").toFile();
		File psfx = new File(folder, "smoke.psfx");

		Thread launcher = new Thread(() -> pamguard.PamguardFXLauncher.main(
				new String[] { "-psf", psfx.getAbsolutePath(), "-nosplash" }), "PAMGuard FX smoke launcher");
		launcher.setDaemon(true);
		launcher.start();

		// press buttons on any dialog which appears. 
		Thread clicker = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(700);
					Platform.runLater(() -> {
						try {
							clickDialogs();
						}
						catch (Throwable t) {
							t.printStackTrace();
						}
					});
				}
				catch (InterruptedException e) {
					return;
				}
				catch (IllegalStateException e) {
					// the JavaFX toolkit has not started yet. 
				}
			}
		}, "PAMGuard FX smoke dialog clicker");
		clicker.setDaemon(true);
		clicker.start();

		// wait for PAMGuard to start. 
		boolean started = false;
		for (int i = 0; i < 360 && !started; i++) {
			Thread.sleep(500);
			started = PamController.getInstance() != null && PamController.getInstance().isInitializationComplete()
					&& PamGuiManagerFX.getInstance() != null;
		}
		assertTrue(started, "PAMGuard did not start with the JavaFX GUI within 3 minutes");
		Thread.sleep(3000);

		// add every module which has a JavaFX GUI. 
		for (PamModuleInfo moduleInfo : PamModuleInfo.getModuleList()) {
			if (!isFXModule(moduleInfo) || !moduleInfo.canCreate()) {
				continue;
			}
			currentStep = "add " + moduleInfo.getDefaultName();
			dialogCounts.clear();
			onFX(() -> {
				PamControlledUnit unit = PamController.getInstance().addModule(moduleInfo, moduleInfo.getDefaultName());
				if (unit == null) {
					problems.add(currentStep + ": the module was not created");
				}
			});
			Thread.sleep(1000);
		}
		Thread.sleep(2000);

		// open every settings dialog and press OK. 
		int n = PamController.getInstance().getNumControlledUnits();
		for (int i = 0; i < n; i++) {
			PamControlledUnit unit = PamController.getInstance().getControlledUnit(i);
			currentStep = "settings " + unit.getUnitName();
			dialogCounts.clear();
			Object gui = unit.getGUI(PamGUIManager.FX);
			if (!(gui instanceof PamControlledGUIFX)) {
				continue;
			}
			onFX(() -> {
				SettingsPane<?> pane = ((PamControlledGUIFX) gui).getSettingsPane();
				if (pane != null) {
					PamGuiManagerFX.showModuleSettings(unit);
				}
			});
			Thread.sleep(300);
		}

		clicker.interrupt();
		assertTrue(problems.isEmpty(), problems.size() + " problem(s) in the JavaFX GUI:\n" + String.join("\n", problems));
	}

	/**
	 * Run something on the JavaFX thread and wait for it. Any exception is a problem. 
	 */
	private void onFX(Runnable task) throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				task.run();
			}
			catch (Throwable t) {
				t.printStackTrace();
				problems.add(currentStep + ": " + t);
			}
			finally {
				done.countDown();
			}
		});
		if (!done.await(90, TimeUnit.SECONDS)) {
			problems.add(currentStep + ": did not finish within 90 seconds");
		}
	}

	private static boolean isFXModule(PamModuleInfo moduleInfo) {
		for (int i = 0; i < moduleInfo.getNGUIFlags(); i++) {
			if (moduleInfo.getGUICompatabilityFlag(i) == PamGUIManager.FX) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Find the dialog pane of a window. The validator can wrap the pane in another node, so it is 
	 * not always the root of the scene. 
	 */
	private static DialogPane findDialogPane(Window window) {
		Parent root = window.getScene().getRoot();
		if (root instanceof DialogPane) {
			return (DialogPane) root;
		}
		Node node = root.lookup(".dialog-pane");
		return (node instanceof DialogPane) ? (DialogPane) node : null;
	}

	/**
	 * Press a button on the topmost dialog. OK the first two times a dialog is seen in a step, then
	 * Cancel, and close the window if it is still there after six goes. Runs on the JavaFX thread. 
	 */
	private void clickDialogs() {
		Stage main = PamGuiManagerFX.getInstance() == null ? null : (Stage) PamGuiManagerFX.getInstance().getPrimaryStage();
		Window top = null;
		for (Window window : new ArrayList<Window>(Window.getWindows())) {
			if (window == main || !window.isShowing() || window.getScene() == null || findDialogPane(window) == null) {
				continue;
			}
			top = window;
		}
		if (top == null) {
			return;
		}
		// give a new dialog one tick to lay itself out. 
		if (top.getProperties().get("fxSmokeSeen") == null) {
			top.getProperties().put("fxSmokeSeen", Boolean.TRUE);
			return;
		}
		DialogPane dialogPane = findDialogPane(top);
		String title = (top instanceof Stage) ? ((Stage) top).getTitle() : "";
		String content = dialogPane.getContentText();
		if (content != null && content.contains("There was an error reading the settings")) {
			String problem = currentStep + ": " + content;
			if (!problems.contains(problem)) {
				problems.add(problem);
			}
		}
		int count = dialogCounts.merge(currentStep + "|" + title, 1, Integer::sum);
		if (count > 6) {
			top.hide();
			return;
		}
		boolean cancel = count > 2;
		Button target = null;
		for (ButtonType type : dialogPane.getButtonTypes()) {
			ButtonBar.ButtonData data = type.getButtonData();
			boolean isOk = data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES
					|| data == ButtonBar.ButtonData.APPLY;
			boolean isCancel = data.isCancelButton() || data == ButtonBar.ButtonData.NO;
			if (cancel ? isCancel : isOk) {
				target = (Button) dialogPane.lookupButton(type);
				break;
			}
		}
		if (target == null && dialogPane.getButtonTypes().size() > 0) {
			target = (Button) dialogPane.lookupButton(dialogPane.getButtonTypes().get(0));
		}
		if (target != null) {
			target.fire();
		}
		else {
			top.hide();
		}
	}

}
