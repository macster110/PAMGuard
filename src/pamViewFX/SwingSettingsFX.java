package pamViewFX;

import java.util.ArrayList;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import PamController.PamControlledUnit;
import PamController.PamController;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;

/**
 * Opens the standard (Swing) settings dialogs of a module from the JavaFX GUI. This is used for 
 * modules which do not have a JavaFX user interface yet, so that they can still be set up when 
 * they are part of a configuration. The options are those the module adds to the Settings and 
 * Display menus of the Swing GUI.  
 * 
 * @author PAMGuard
 */
public class SwingSettingsFX {

	/**
	 * A settings option from a module's Swing menu. 
	 */
	private static class SwingOption {
		String name;
		JMenuItem menuItem;

		SwingOption(String name, JMenuItem menuItem) {
			this.name = name;
			this.menuItem = menuItem;
		}
	}

	/**
	 * Show the Swing settings of a module. If the module has one settings option then it is opened 
	 * straight away. If it has several then a menu of the options is shown. Call from the JavaFX thread. 
	 * @param unit - the module. 
	 * @param anchor - a node to show the menu of options next to. Can be null. 
	 */
	public static void showSwingSettings(PamControlledUnit unit, Node anchor) {
		if (unit == null) {
			return;
		}
		// Swing menus must be made on the Swing thread. 
		SwingUtilities.invokeLater(() -> {
			ArrayList<SwingOption> options = new ArrayList<SwingOption>();
			try {
				addOptions(options, unit.createDetectionMenu(PamController.getMainFrame()), null);
				addOptions(options, unit.createDisplayMenu(PamController.getMainFrame()), null);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			Platform.runLater(() -> showOptions(unit, options, anchor));
		});
	}

	/**
	 * Collect the options in a Swing menu item, including everything in any sub menus. 
	 */
	private static void addOptions(ArrayList<SwingOption> options, JMenuItem item, String prefix) {
		if (item == null) {
			return;
		}
		String text = item.getText() == null ? "" : item.getText().trim();
		if (item instanceof JMenu) {
			JMenu menu = (JMenu) item;
			// the top menu is just the name of the module, so leave it out of the option names. 
			String subPrefix = prefix == null ? "" : prefix + text + " > ";
			for (int i = 0; i < menu.getItemCount(); i++) {
				addOptions(options, menu.getItem(i), subPrefix);
			}
		}
		else if (item.isEnabled() && text.length() > 0) {
			options.add(new SwingOption((prefix == null ? "" : prefix) + text, item));
		}
	}

	private static void showOptions(PamControlledUnit unit, ArrayList<SwingOption> options, Node anchor) {
		if (options.isEmpty()) {
			PamDialogFX.showWarning(null, unit.getUnitName(), "The module " + unit.getUnitName() 
					+ " does not have a JavaFX user interface yet and has no settings which can be opened from here.");
			return;
		}
		if (options.size() == 1 || anchor == null || anchor.getScene() == null) {
			fire(options.get(0));
			return;
		}
		ContextMenu menu = new ContextMenu();
		for (SwingOption option : options) {
			MenuItem menuItem = new MenuItem(option.name);
			menuItem.setOnAction(e -> fire(option));
			menu.getItems().add(menuItem);
		}
		menu.show(anchor, Side.BOTTOM, 0, 0);
	}

	/**
	 * Run a Swing menu option on the Swing thread, then tell PAMGuard that settings may have changed. 
	 */
	private static void fire(SwingOption option) {
		SwingUtilities.invokeLater(() -> {
			try {
				option.menuItem.doClick(0);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

}
