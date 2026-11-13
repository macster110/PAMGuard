package pamViewFX;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.List;

import javax.swing.SwingUtilities;

import PamController.PamController;
import PamController.PamSettingManager;
import PamController.PamguardVersionInfo;
import PamController.settings.SettingsImport;
import PamModel.CommonPluginInterface;
import PamModel.PamModel;
import PamUtils.PamFileFilter;
import PamUtils.time.CalendarControl;
import PamView.dialog.warn.WarnOnce;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;

/**
 * General PAMGuard actions for the JavaFX GUI, e.g. saving a configuration to a new file, the about dialog,
 * web links, time settings, module ordering etc. These are the JavaFX equivalents of items in the
 * Swing GUI's File and Help menus. They are used by both the sliding settings pane and the menu bar so
 * the two always do the same thing.
 * <p>
 * Some actions have no JavaFX dialog yet (time, time zone, module ordering, module import and XML export).
 * These open the existing Swing dialog on the Swing thread.
 *
 * @author Jamie Macaulay
 *
 */
public class PamGuiActionsFX {

	/**
	 * Page to check for new PAMGuard releases.
	 */
	public static final String RELEASES_ADDRESS = "https://github.com/PAMGuard/PAMGuard/releases";

	/**
	 * Contact and support page.
	 */
	public static final String CONTACT_ADDRESS = "https://www.pamguard.org/contact.html";

	public static final String ICON_TIME = "mdi2c-clock-outline";
	public static final String ICON_TIME_ZONE = "mdi2e-earth";
	public static final String ICON_ORDER = "mdi2s-sort-variant";
	public static final String ICON_IMPORT = "mdi2p-package-down";
	public static final String ICON_XML = "mdi2c-code-tags";
	public static final String ICON_WARNINGS = "mdi2a-alert-circle-outline";
	public static final String ICON_LOG = "mdi2t-text-box-outline";

	/**
	 * Save settings to a new psfx file.
	 * @param owner - the owner window for the file chooser.
	 */
	public static void saveSettingsAs(Window owner) {
		String currentfileName = PamSettingManager.getInstance().getSettingsFileName();
		File file = currentfileName == null ? null : new File(currentfileName);

		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save Configuration As");
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("PAMGuard settings files", "*.psfx", "*.xml"));
		if (file != null && file.getParentFile() != null && file.getParentFile().isDirectory()) {
			fileChooser.setInitialDirectory(file.getParentFile());
			fileChooser.setInitialFileName(file.getName());
		}

		File selectedFile = fileChooser.showSaveDialog(owner);
		if (selectedFile == null) {
			return;
		}
		selectedFile = PamFileFilter.checkFileEnd(selectedFile, PamSettingManager.getCurrentSettingsFileEnd(), true);

		System.out.println("Saving settings to file " + selectedFile.getAbsolutePath());
		// Insert the new file into the top of the recent psf file list.
		PamSettingManager.getInstance().setDefaultFile(selectedFile.getAbsolutePath());
		if (PamSettingManager.remote_psf != null) {
			PamSettingManager.remote_psf = selectedFile.getAbsolutePath();
		}
		PamSettingManager.getInstance().saveSettings(PamSettingManager.SAVE_PSF);
		PamController.getInstance().sortFrameTitles();
	}

	/**
	 * Open a web page in the system browser.
	 * @param address - the web address.
	 */
	public static void openURL(String address) {
		final String fullAddress = address.startsWith("http") ? address : "https://" + address;
		// Desktop calls can block so don't run on the FX thread.
		Thread t = new Thread(() -> {
			try {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
					Desktop.getDesktop().browse(new URI(fullAddress));
					return;
				}
			}
			catch (Exception e) {
				System.err.println("Unable to open " + fullAddress + ": " + e.getMessage());
			}
			System.out.println("Open " + fullAddress + " in a web browser");
		}, "Open URL");
		t.setDaemon(true);
		t.start();
	}

	public static void openWebsite() {
		openURL(PamguardVersionInfo.webAddress);
	}

	public static void openContact() {
		openURL(CONTACT_ADDRESS);
	}

	public static void checkForUpdates() {
		openURL(RELEASES_ADDRESS);
	}

	/**
	 * Show the about PAMGuard dialog with version information and any plugins.
	 * @param owner - the owner window.
	 */
	public static void showAbout(Window owner) {
		PamVBox content = new PamVBox();
		content.setSpacing(6);
		content.setPadding(new Insets(5));

		Label title = new Label("PAMGuard " + PamguardVersionInfo.version);
		PamGuiManagerFX.titleFont1style(title);

		String runMode = PamController.getInstance() == null ? "" : PamController.getInstance().getRunModeName();
		Label info = new Label(String.format("Version %s, %s\nRun mode: %s\nJava %s (%s)\n%s %s",
				PamguardVersionInfo.version, PamguardVersionInfo.date, runMode,
				System.getProperty("java.version"), System.getProperty("java.vendor"),
				System.getProperty("os.name"), System.getProperty("os.arch")));

		Hyperlink web = new Hyperlink(PamguardVersionInfo.webAddress);
		web.setOnAction(e -> openWebsite());
		Hyperlink contact = new Hyperlink("Contact and support");
		contact.setOnAction(e -> openContact());

		TextArea license = new TextArea(PamguardVersionInfo.license);
		license.setWrapText(true);
		license.setEditable(false);
		license.setPrefRowCount(6);
		license.setPrefColumnCount(40);
		TitledPane licensePane = new TitledPane("Licence", license);
		licensePane.setExpanded(false);

		content.getChildren().addAll(title, info, web, contact);

		List<CommonPluginInterface> plugins = getPlugins();
		if (plugins != null && !plugins.isEmpty()) {
			PamVBox pluginBox = new PamVBox();
			pluginBox.setSpacing(8);
			for (CommonPluginInterface plugin : plugins) {
				Label pl = new Label(String.format("%s %s\nDeveloper: %s (%s)\n%s", plugin.getDefaultName(),
						nullToEmpty(plugin.getVersion()), nullToEmpty(plugin.getDeveloperName()),
						nullToEmpty(plugin.getContactEmail()), nullToEmpty(plugin.getAboutText())));
				pl.setWrapText(true);
				pluginBox.getChildren().add(pl);
			}
			ScrollPane pluginScroll = new ScrollPane(pluginBox);
			pluginScroll.setFitToWidth(true);
			pluginScroll.setPrefViewportHeight(Math.min(200, 60 * plugins.size()));
			TitledPane pluginPane = new TitledPane("Plugins (" + plugins.size() + ")", pluginScroll);
			pluginPane.setExpanded(false);
			content.getChildren().add(pluginPane);
		}
		content.getChildren().add(licensePane);

		Alert alert = new Alert(AlertType.INFORMATION, "", ButtonType.CLOSE);
		alert.setTitle("About PAMGuard");
		alert.setHeaderText(null);
		alert.setGraphic(null);
		alert.getDialogPane().setContent(content);
		alert.setResizable(true);
		if (owner != null) {
			alert.initOwner(owner);
			if (owner.getScene() != null) {
				alert.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
			}
		}
		alert.showAndWait();
	}

	private static String nullToEmpty(String str) {
		return str == null ? "" : str;
	}

	private static List<CommonPluginInterface> getPlugins() {
		try {
			return ((PamModel) PamController.getInstance().getModelInterface()).getPluginList();
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Clear the list of warnings the user has asked not to show again.
	 */
	public static void resetHiddenWarnings() {
		SwingUtilities.invokeLater(() -> WarnOnce.clearHiddenList(null));
	}

	/**
	 * Show the global time (PC clock, NTP, GPS time) settings. Swing dialog.
	 */
	public static void showTimeSettings() {
		SwingUtilities.invokeLater(() -> PamController.getInstance().getGlobalTimeManager().showSettingsDialog(PamController.getMainFrame()));
	}

	/**
	 * Show the display time zone settings. Swing dialog.
	 */
	public static void showTimeZoneSettings() {
		SwingUtilities.invokeLater(() -> CalendarControl.getInstance().showTimeZoneDialog(PamController.getMainFrame()));
	}

	/**
	 * Change the order of modules. Swing dialog.
	 */
	public static void showModuleOrdering() {
		SwingUtilities.invokeLater(() -> PamController.getInstance().orderModules(PamController.getMainFrame()));
	}

	/**
	 * Import module settings from a different configuration. Swing dialog.
	 */
	public static void importModules() {
		SwingUtilities.invokeLater(() -> new SettingsImport(PamController.getInstance()).loadSettingsFile());
	}

	/**
	 * Export the configuration to XML. Swing dialog.
	 */
	public static void exportXML() {
		SwingUtilities.invokeLater(() -> PamController.getInstance().exportGeneralXMLSettings(null, PamCalendarTime()));
	}

	private static long PamCalendarTime() {
		return PamUtils.PamCalendar.getTimeInMillis();
	}

	/**
	 * Open the folder holding PAMGuard log files.
	 */
	public static void openLogFolder() {
		File folder = new File(pamguard.Pamguard.getSettingsFolder());
		Thread t = new Thread(() -> {
			try {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
					Desktop.getDesktop().open(folder);
				}
				else {
					System.out.println("PAMGuard log files are in " + folder.getAbsolutePath());
				}
			}
			catch (Exception e) {
				System.err.println("Unable to open " + folder + ": " + e.getMessage());
			}
		}, "Open log folder");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Create a menu with the general settings. Used in the menu bar and in the settings pane.
	 * @param menuItems - list to add menu items to.
	 * @param withIcons - true to add icons.
	 * @return the list of menu items.
	 */
	public static List<MenuItem> createGeneralSettingsItems(List<MenuItem> menuItems, boolean withIcons) {
		boolean isViewer = PamController.getInstance().getRunMode() == PamController.RUN_PAMVIEW;

		MenuItem time = menuItem("Time and clock settings...", ICON_TIME, withIcons,
				"Configure how PAMGuard gets its time (PC clock, NTP or GPS)");
		time.setOnAction(e -> showTimeSettings());
		time.setDisable(isViewer);

		MenuItem timeZone = menuItem("Display time zone...", ICON_TIME_ZONE, withIcons,
				"The time zone used for displays");
		timeZone.setOnAction(e -> showTimeZoneSettings());

		MenuItem order = menuItem("Module ordering...", ICON_ORDER, withIcons,
				"Change the order of modules in the PAMGuard configuration");
		order.setOnAction(e -> showModuleOrdering());

		MenuItem importMods = menuItem("Import PAMGuard modules...", ICON_IMPORT, withIcons,
				"Import module settings from a different PAMGuard configuration (psfx files only)");
		importMods.setOnAction(e -> importModules());

		MenuItem xml = menuItem("Export XML configuration...", ICON_XML, withIcons,
				"Export the configuration to an XML file");
		xml.setOnAction(e -> exportXML());

		MenuItem warnings = menuItem("Reset hidden warnings", ICON_WARNINGS, withIcons,
				"Warnings told \"Not to show again\" will be shown again");
		warnings.setOnAction(e -> resetHiddenWarnings());

		Menu logMenu = new Menu("Log file");
		if (withIcons) logMenu.setGraphic(PamGlyphDude.createPamIcon(ICON_LOG, PamGuiManagerFX.iconSize));
		MenuItem openLog = new MenuItem("Open log folder");
		openLog.setOnAction(e -> openLogFolder());
		CheckMenuItem enableLog = new CheckMenuItem("Write log file");
		enableLog.setSelected(pamguard.Pamguard.getLogFileName() != null);
		enableLog.setOnAction(e -> {
			if (enableLog.isSelected()) {
				pamguard.Pamguard.enableLogFile();
			}
			else {
				pamguard.Pamguard.disableLogFile();
			}
		});
		CheckMenuItem checkLogs = new CheckMenuItem("Check log files at startup");
		checkLogs.setSelected(PamSettingManager.getInstance().isCheckLogFileErrors());
		checkLogs.setOnAction(e -> PamSettingManager.getInstance().setCheckLogFileErrors(checkLogs.isSelected()));
		logMenu.getItems().addAll(openLog, enableLog, checkLogs);

		menuItems.add(time);
		menuItems.add(timeZone);
		menuItems.add(new SeparatorMenuItem());
		menuItems.add(order);
		menuItems.add(importMods);
		menuItems.add(xml);
		menuItems.add(new SeparatorMenuItem());
		menuItems.add(warnings);
		menuItems.add(logMenu);
		return menuItems;
	}

	private static MenuItem menuItem(String name, String icon, boolean withIcon, String tip) {
		MenuItem item = new MenuItem(name);
		if (withIcon) {
			item.setGraphic(PamGlyphDude.createPamIcon(icon, PamGuiManagerFX.iconSize));
		}
		return item;
	}

}
