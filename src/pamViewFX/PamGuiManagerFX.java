package pamViewFX;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Optional;

import javax.swing.JFrame;

import Acquisition.layoutFX.PaneFactory;
import PamController.PAMControllerGUI;
import PamController.PamControlledUnit;
import PamController.PamControlledUnitSettings;
import PamController.PamController;
import PamController.PamControllerInterface;
import PamController.PamGUIManager;
import PamController.SettingsPane;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamController.StorageOptions;
import PamController.StorageParameters;
import PamController.soundMedium.GlobalMedium;
import PamController.soundMedium.GlobalMedium.SoundMedium;
import PamModel.PamModel;
import PamModel.PamModuleInfo;
import PamUtils.PamFileFilter;
import PamView.ColourScheme;
import PamView.PamColors;
import PamView.PamViewInterface;
import binaryFileStorage.BinaryStore;
import dataMap.layoutFX.DataMapPaneFX;
import dataModelFX.DataModelPaneFX;
import generalDatabase.DBControlUnit;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import helpFX.HelpManager;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamTabPane;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.internalNode.PamInternalPane;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.pamDialogFX.PamSettingsDialogFX;
import pamViewFX.fxNodes.utilityPanes.SettingsDialog;
import pamViewFX.fxSettingsPanes.StorageOptionsPane;
import pamViewFX.fxStyles.PamAtlantaStyle;
import pamViewFX.fxStyles.PamDefaultStyle;
import pamViewFX.fxStyles.PamStylesManagerFX;
import PamController.pamWizard.PamAutoConfigUtils;
import pamViewFX.pamTask.PamTaskUpdate;
import userDisplayFX.UserDisplayNodeFX;

/**
 * 
 * Handles the FX based GUI. The GUI is made up of multiple PAMGuiFX stages. The
 * PAMGuiManagerFX handles the number of these stages and starting/closing the
 * GUI.
 * 
 * @author Jamie Macaulay
 *
 */
public class PamGuiManagerFX implements PAMControllerGUI, PamSettings {

	/**
	 * The main tab pane. This is the tab pane of the first unit and is only 
	 * referred to so that new modules know which tab pane to create a new default tab in. 
	 */
	private PamTabPane mainTabPane; 

	/**
	 * A list of open stages.
	 */
	private ArrayList<PamGuiFX> stages=new ArrayList<PamGuiFX>();

	/**
	 * The data model pane. This sits in an non closable tab. 
	 */
	private DataModelPaneFX dataModelFX;

	/**
	 * The primary stage. 
	 */
	private Stage primaryStage;

	/**
	 * Data map pane
	 */
	private DataMapPaneFX dataMapFX;

	/**
	 * The primary view. 
	 */
	private PamGuiFX primaryView;

	/**
	 * Reference to the PAMContorller
	 */
	private PamController pamController;
	
	
	public  ArrayList<PaneFactory> toolBarPanes = new ArrayList<PaneFactory>();


	/**
	 * @return the primaryView
	 */
	public PamGuiFX getPrimaryView() {
		return primaryView;
	}

	/**
	 * @param primaryView the primaryView to set
	 */
	public void setPrimaryView(PamGuiFX primaryView) {
		this.primaryView = primaryView;
	}

	////	//Font sizes for titles and sub titles. 
	//	public static Font titleFontSize=createTitleFont();
	////
	//	public static Font titleFontSize2=createTitleFont2();

	/**
	 * The default size for icons in PAMGaurd- helps keep everything looking coherent. 
	 */
	public static int iconSize=17;

	/*
	 * The default icon colour. 
	 */
	public static Color iconColor=Color.DARKGRAY;

	/**
	 * The general GUI settings 
	 */
	private  PAMGuiFXSettings pamGuiSettings;

	private Scene scene;

	/**
	 * The system menu bar. On macOS, setUseSystemMenuBar(true) must be called
	 * AFTER the Stage is shown, so we keep a reference here.
	 */
	private MenuBar systemMenuBar;

	/** Storage options pane - lazily created for the Storage Manager menu item. */
	private StorageOptionsPane storageOptionPane;

	private static PamGuiManagerFX instance;

	/*
	 * The default padding for panes. 
	 */
	public static Insets defaultPadding = new Insets(5,5,5,5);

	public PamGuiManagerFX(PamController pamController, Object stage) {

		this.pamController=pamController; 
		pamGuiSettings= new PAMGuiFXSettings(); 

		primaryStage= (Stage) stage;

		primaryStage.setOnCloseRequest(e->{
			pamStop(e);
		});

		start(primaryStage);

		instance=this;

	}

	/**
	 * Get the instance of the PAMGuiManager
	 * @return the instance; 
	 */
	public static PamGuiManagerFX getInstance() {
		return instance; 
	}




	/**
	 * Start the GUI.
	 * @param primaryStage
	 */
	private void start(Stage primaryStage) {

		PamStylesManagerFX.getPamStylesManagerFX().setCurStyle(new PamAtlantaStyle());

		//add stage
		stages.add(primaryView = new PamGuiFX(primaryStage, this)); 
		//create new data model. 
		dataModelFX=stages.get(0).addDataModelTab();

		// Build the MenuBar. setUseSystemMenuBar(true) is deferred until after show().
		systemMenuBar = buildSystemMenuBar();

		// Wrap PamGuiFX in a VBox with the MenuBar at the top.
		// On macOS the MenuBar is moved to the native top bar, so don't add it to the
		// scene graph (it would leave a blank strip at the top of the window).
		PamVBox sceneRoot = new PamVBox();
		sceneRoot.setSpacing(0);
		sceneRoot.setPadding(javafx.geometry.Insets.EMPTY);
		PamVBox.setVgrow(stages.get(0), javafx.scene.layout.Priority.ALWAYS);
		if (screenMenuBar) {
			systemMenuBar.setMinHeight(0); //needed or we end up with a little bar at the top of the window
		}
		//on macOS the menu bar moves to the top of the screen, on Windows and Linux it sits at the top of the window. 
		sceneRoot.getChildren().addAll(systemMenuBar, stages.get(0));

		scene = new Scene(sceneRoot);
		//register with the style manager rather than adding the sheets directly, so the whole scene
		//is restyled when the user switches colour scheme.
		PamStylesManagerFX.getPamStylesManagerFX().styleScene(scene, PamStylesManagerFX.STYLE_GUI);

		primaryStage.setScene(scene);
	}

	/**
	 * Build the JavaFX MenuBar for PAMGuard. On macOS, calling
	 * {@code setUseSystemMenuBar(true)} moves it to the native top menu bar.
	 * Contains all the functionality that is also present in {@link PamSettingsMenuPane}.
	 */
	/** True when running on macOS — the native menu bar does not render JavaFX Node graphics. */
	private static final boolean IS_MACOS =
			System.getProperty("os.name", "").toLowerCase().contains("mac");

	/**
	 * Set a graphic on a MenuItem only when not using the macOS system menu bar.
	 * The native macOS menu bar silently ignores JavaFX Node graphics, so we skip
	 * the call entirely to keep the node graph clean.
	 */
	private static void setMenuItemGraphic(MenuItem item, String iconCode) {
		if (!screenMenuBar) {
			item.setGraphic(PamGlyphDude.createPamIcon(iconCode, iconSize));
		}
	}

	/**
	 * True if the menus are in the macOS menu bar at the top of the screen rather
	 * than at the top of the PAMGuard window.
	 */
	private static boolean screenMenuBar = IS_MACOS;

	/**
	 * Can the menus go in the macOS menu bar at the top of the screen? Not if
	 * JavaFX was started from Swing (a JFXPanel), which happens when the start up
	 * dialog is shown: the Swing toolkit then owns the macOS menu bar and the
	 * JavaFX menus never appear there. They go at the top of the window instead,
	 * as on Windows and Linux.
	 */
	private static boolean canUseScreenMenuBar() {
		return IS_MACOS && !PamView.FXInitialiser.haveRun;
	}

	private MenuBar buildSystemMenuBar() {
		screenMenuBar = canUseScreenMenuBar();
		MenuBar menuBar = new MenuBar();
		menuBar.setUseSystemMenuBar(screenMenuBar);

		// ── File menu ─────────────────────────────────────────────────────────
		Menu fileMenu = new Menu("File");

		MenuItem saveConfig = new MenuItem("Save Configuration");
		saveConfig.setOnAction(e -> PamSettingManager.getInstance().saveSettings(null));

		MenuItem saveConfigAs = new MenuItem("Save Configuration As...");
		saveConfigAs.setOnAction(e -> PamGuiActionsFX.saveSettingsAs(primaryStage));

		MenuItem quit = new MenuItem("Quit PAMGuard");
		quit.setOnAction(e -> primaryStage.close());

		fileMenu.getItems().addAll(saveConfig, saveConfigAs, new SeparatorMenuItem(), quit);

		// ── Settings menu ─────────────────────────────────────────────────────
		Menu settingsMenu = new Menu("Settings");

		Menu generalSettings = new Menu("General Settings");
		setMenuItemGraphic(generalSettings, PamSettingsMenuPane.ICON_GENERAL_SETTINGS);
		// General settings opens via the first PamGuiFX if available
		PamGuiActionsFX.createGeneralSettingsItems(generalSettings.getItems(), !screenMenuBar);

		// Module Settings submenu — populated dynamically when the menu is shown
		Menu moduleSettings = new Menu("Module Settings");
		setMenuItemGraphic(moduleSettings, PamSettingsMenuPane.ICON_MODULE_SETTINGS);
		moduleSettings.setOnShowing(e -> {
			moduleSettings.getItems().clear();
			int nUnits = PamController.getInstance().getNumControlledUnits();
			for (int i = 0; i < nUnits; i++) {
				final int n = i;
				MenuItem mi = new MenuItem(PamController.getInstance().getControlledUnit(i).getUnitName());
				mi.setOnAction(ev -> menuBarOpenSettingsDialog(PamController.getInstance().getControlledUnit(n)));
				moduleSettings.getItems().add(mi);
			}
		});

		// Sound medium submenu
		Menu mediumMenu = new Menu("Sound Medium");
		setMenuItemGraphic(mediumMenu, PamSettingsMenuPane.ICON_SOUND_MEDIUM);
		javafx.scene.control.ToggleGroup mediumGroup = new javafx.scene.control.ToggleGroup();
		javafx.scene.control.RadioMenuItem waterItem = new javafx.scene.control.RadioMenuItem("Water");
		waterItem.setToggleGroup(mediumGroup);
		waterItem.setOnAction(e -> {
			if (PamController.getInstance().getGlobalMediumManager().getGlobalMediumParameters().currentMedium != SoundMedium.Water)
				PamController.getInstance().getGlobalMediumManager().setCurrentMedium(SoundMedium.Water);
		});
		javafx.scene.control.RadioMenuItem airItem = new javafx.scene.control.RadioMenuItem("Air");
		airItem.setToggleGroup(mediumGroup);
		//show the current medium whenever the menu opens. 
		mediumMenu.setOnShowing(e -> {
			boolean water = PamController.getInstance().getGlobalMediumManager().getGlobalMediumParameters().currentMedium == SoundMedium.Water;
			waterItem.setSelected(water);
			airItem.setSelected(!water);
		});
		airItem.setOnAction(e -> {
			if (PamController.getInstance().getGlobalMediumManager().getGlobalMediumParameters().currentMedium != SoundMedium.Air)
				PamController.getInstance().getGlobalMediumManager().setCurrentMedium(SoundMedium.Air);
		});
		mediumMenu.getItems().addAll(waterItem, airItem);

		// Colour scheme: Light, Light/Dark, Dark or Night. Also in General Settings.
		Menu colourModeMenu = PamGuiActionsFX.createColourModeMenu(true);

		settingsMenu.getItems().addAll(generalSettings, moduleSettings, new SeparatorMenuItem(),
				mediumMenu, colourModeMenu);

		// ── Storage menu ──────────────────────────────────────────────────────
		Menu storageMenu = new Menu("Storage");

		MenuItem storageManager = new MenuItem("Storage Manager...");
		setMenuItemGraphic(storageManager, PamSettingsMenuPane.ICON_STORAGE_MGR);
		storageManager.setOnAction(e -> {
			if (storageOptionPane == null) storageOptionPane = new StorageOptionsPane();
			storageOptionPane.setParams(StorageOptions.getInstance().getStorageParameters());
			PamSettingsDialogFX<?> dlg = new PamSettingsDialogFX(storageOptionPane);
			Optional<?> result = dlg.showAndWait();
			if (result.isPresent() && result.get() != null)
				StorageOptions.getInstance().setStorageParameters((StorageParameters) result.get());
		});

		MenuItem databaseItem = new MenuItem("Database Storage...");
		setMenuItemGraphic(databaseItem, PamSettingsMenuPane.ICON_DATABASE);
		databaseItem.setOnAction(e -> {
			if (DBControlUnit.findDatabaseControl() == null) {
				boolean add = PamDialogFX.showMessageDialog("No Database Module Found",
						"No database module has been added to the data model. Would you like to add one now?");
				if (add) {
					PamController.getInstance().addModule(PamModuleInfo.findModuleInfo("generalDatabase.DBControlUnit"), "Database");
					menuBarOpenSettingsDialog(DBControlUnit.findDatabaseControl());
				}
			} else {
				menuBarOpenSettingsDialog(DBControlUnit.findDatabaseControl());
			}
		});

		MenuItem binaryItem = new MenuItem("Binary Storage...");
		setMenuItemGraphic(binaryItem, PamSettingsMenuPane.ICON_BINARY);
		binaryItem.setOnAction(e -> {
			if (BinaryStore.findBinaryStoreControl() == null) {
				boolean add = PamDialogFX.showMessageDialog("No Binary Storage Module Found",
						"No binary storage module has been added to the data model. Would you like to add one now?");
				if (add) {
					PamController.getInstance().addModule(PamModuleInfo.findModuleInfo("binaryFileStorage.BinaryStore"), "Binary Store");
					menuBarOpenSettingsDialog(BinaryStore.findBinaryStoreControl());
				}
			} else {
				menuBarOpenSettingsDialog(BinaryStore.findBinaryStoreControl());
			}
		});

		storageMenu.getItems().addAll(storageManager, databaseItem, binaryItem);

		// ── Help menu ─────────────────────────────────────────────────────────
		Menu helpMenu = new Menu("Help");

		MenuItem helpItem = new MenuItem("Help...");
		setMenuItemGraphic(helpItem, PamSettingsMenuPane.ICON_HELP);
		helpItem.setOnAction(e -> HelpManager.getInstance().openHelp());


		MenuItem checkUpdates = new MenuItem("Check for Updates");
		setMenuItemGraphic(checkUpdates, PamSettingsMenuPane.ICON_UPDATES);
		checkUpdates.setOnAction(e -> PamGuiActionsFX.checkForUpdates());

		MenuItem websiteItem = new MenuItem("Website");
		setMenuItemGraphic(websiteItem, PamSettingsMenuPane.ICON_WEBSITE);
		websiteItem.setOnAction(e -> PamGuiActionsFX.openWebsite());

		MenuItem bugItem = new MenuItem("Found a Bug?");
		setMenuItemGraphic(bugItem, PamSettingsMenuPane.ICON_BUG);
		bugItem.setOnAction(e -> PamGuiActionsFX.openContact());

		MenuItem about = new MenuItem("About PAMGuard...");
		setMenuItemGraphic(about, PamSettingsMenuPane.ICON_ABOUT);
		about.setOnAction(e -> PamGuiActionsFX.showAbout(primaryStage));

		helpMenu.getItems().addAll(helpItem, checkUpdates, websiteItem, bugItem, new SeparatorMenuItem(), about);

		menuBar.getMenus().addAll(fileMenu, settingsMenu, storageMenu, helpMenu);
		return menuBar;
	}

	/**
	 * Open the settings dialog for a controlled unit (used from the menu bar).
	 */
	private void menuBarOpenSettingsDialog(PamControlledUnit controlledUnit) {
		showModuleSettings(controlledUnit);
	}

	/**
	 * Open the JavaFX settings dialog for a module and apply the settings if the user presses OK. 
	 * This is the one place module settings dialogs are opened from in the JavaFX GUI.
	 * @param controlledUnit - the module.
	 * @return true if new settings were applied. 
	 */
	public static boolean showModuleSettings(PamControlledUnit controlledUnit) {
		if (controlledUnit == null) return false;
		if (!(controlledUnit.getGUI(PamGUIManager.FX) instanceof PamControlledGUIFX)) {
			// no JavaFX GUI yet, so offer the module's standard (Swing) settings dialogs. 
			SwingSettingsFX.showSwingSettings(controlledUnit, null);
			return false;
		}
		PamControlledGUIFX pamControlledUnit = (PamControlledGUIFX) controlledUnit.getGUI(PamGUIManager.FX);
		SettingsPane<?> settingsPane = pamControlledUnit.getSettingsPane();
		if (settingsPane == null) {
			// some modules, e.g. displays, have no settings dialog. 
			return false;
		}
		SettingsDialog<?> newDialog = new SettingsDialog<>(settingsPane);
		newDialog.setTitle(controlledUnit.getUnitName() + " settings");
		newDialog.setResizable(true);
		newDialog.setOnShown(value -> settingsPane.paneInitialized());
		Optional<?> response = newDialog.showAndWait();
		boolean ok = response.isPresent() && response.get() != null;
		if (ok) {
			pamControlledUnit.updateParams();
		}
		//notify stuff that process settings may have changed. 
		PamController.getInstance().notifyModelChanged(PamControllerInterface.CHANGED_PROCESS_SETTINGS);
		return ok;
	}

	/**
	 * Create font for main titles 
	 * @return font for title 
	 */
	private static Font createTitleFont(){
		Font font= Font.font(null, FontWeight.BOLD, 27);
		return font;
	}

	/**
	 * Create font for sub titles.
	 * @return font for sub title
	 */
	private static Font createTitleFont2(){
		Font font= Font.font(null, FontWeight.BOLD, 14);
		return font;
	}

	/**
	 * Set the label style for titles
	 * @param label
	 */
	public static void titleFont1style(Labeled label) {
		label.setId("label-title1");
	}

	/**
	 * Set the label style for sub titles
	 * @param label - the label style. 
	 */
	public static void titleFont2style(Labeled label) {
		label.setId("label-title2");
	}

	/**
	 * Get a list of the current PamGUIFX frames available. 
	 * @return a list of the current showing PamGUIFX frames. 
	 */
	public ArrayList<PamGuiFX> getPamGuiFXList(){
		return stages;
	}

	/**
	 * Add a controlled unit to the scene. The controlled unit may have a graphical component or no graphical component at all. 
	 * @param controlledUnit - 
	 */
	@Override
	public void addControlledUnit(PamControlledUnit controlledUnit){
		//		System.out.println("PAMGuiManagerFX. Add Controlled Unit: "+controlledUnit.getClass());

		//now set the content for the tab.
		if (controlledUnit.getGUI(PamGUIManager.FX)!=null){
			//if FX content then is handles as an FX Node within GUI manager. 
			PamControlledGUIFX pamControlledUnitFX=(PamControlledGUIFX) controlledUnit.getGUI(PamGUIManager.FX);
			//figure out which tab the display should be added to. 
			//        	  System.out.println("Controlled Unit: "+controlledUnit);
			//        	  System.out.println("Displays: "+pamControlledUnitFX.getDisplays());
			if (pamControlledUnitFX.getDisplays()!=null){
				for (int i=0; i<pamControlledUnitFX.getDisplays().size(); i++){
					addDisplay(pamControlledUnitFX.getDisplays().get(i));
					//call openNode. 
					pamControlledUnitFX.getDisplays().get(i).openNode();
				}
			}

			if (pamControlledUnitFX.getSidePanes()!=null) {
				for (int i=0; i<pamControlledUnitFX.getSidePanes().size(); i++){
					addSidePane(pamControlledUnitFX.getSidePanes().get(i));
				}
			}
		}

		//          //the controlled unit 
		//          if (controlledUnit instanceof PamControlledUnitAWT){
		//        	 //if AWT unit then need to embed swing components in an special FX node. 
		//        	  //TODO
		//          }
		//mainTabPane.getTabs().add(tab);
	}


	/**
	 * Add a side pane e.g. when a new module is added to the data model. 
	 * @param pane - the pane to add. 
	 */
	private void addSidePane(Pane pane) {
		stages.get(0).getSidePane().getChildren().add(pane);
		stages.get(0).showSidePane(true);

	}

	/**
	 * Remove a hiding side pane e.g. when a module is removed from
	 * the data model. 
	 * @param pane - the pane to remove. 
	 */
	private void removeSidePane(Pane pane) {
		stages.get(0).getSidePane().getChildren().remove(pane);
	}

	/**
	 * Remove a controlled unit from the scene. The controlled unit may have a graphical component or no graphical component at all. 
	 * @param controlledUnit - the controlled unit to remove (here we're removing all it's displays)
	 */
	@Override
	public void removeControlledUnit(PamControlledUnit controlledUnit){
		//now set the content for the tab.
		if (controlledUnit.getGUI(PamGUIManager.FX)!=null){
			//if FX content then is handles as an FX Node within GUI manager. 
			PamControlledGUIFX pamControlledUnitFX=(PamControlledGUIFX) controlledUnit.getGUI(PamGUIManager.FX);
			//figure out which tab the display should be added to. 
			if (pamControlledUnitFX.getDisplays()!=null){
				for (int i=0; i<pamControlledUnitFX.getDisplays().size(); i++){
					//call closeNode. 
					pamControlledUnitFX.getDisplays().get(i).closeNode();
					removeDisplay(pamControlledUnitFX.getDisplays().get(i));
				}
			}

			if (pamControlledUnitFX.getSidePanes()!=null) {
				for (int i=0; i<pamControlledUnitFX.getSidePanes().size(); i++){
					removeSidePane(pamControlledUnitFX.getSidePanes().get(i));
				}
			}
		}
	}


	/**
	 * Gte all tabs across all open windows. 
	 * @return all tabs. 
	 */
	private ArrayList<PamGuiTabFX> getAllTabs() {
		ArrayList<PamGuiTabFX> alltabs= new ArrayList<PamGuiTabFX> (); 
		for (int i=0; i<this.getPamGuiFXList().size(); i++) {
			for (int j=0; j<this.getPamGuiFXList().get(i).getNumTabs(); j++) {
				alltabs.add(this.getPamGuiFXList().get(i).getTab(j)); 
			}
		}
		return alltabs;
	}


	/**
	 * Get the correct tab to add a display.
	 * <p>
	 *  If initialising then searches for the correct tab in user display params.
	 *  <p>
	 *  If initialisation is complete then shows a dialog to allow a user to select the tab. 
	 * @return the tab to add the display to. 
	 */
	private PamGuiTabFX getDisplayTab(UserDisplayNodeFX newDisplay) {
		PamGuiTabFX tab;
		if (nextDisplayTabName != null) {
			// a display added by code which should go into a tab of its own. 
			String tabName = nextDisplayTabName;
			nextDisplayTabName = null;
			for (PamGuiTabFX aTab : getAllTabs()) {
				if (aTab.getName().equals(tabName)) {
					return aTab;
				}
			}
			return stages.get(0).addPamTab(new TabInfo(uniqueTabName(tabName, null)), null, true);
		}
		if (PamController.getInstance().isInitializationComplete()){
			//ask the user what tab to add the display to 
			tab=this.showTabSelectDialog();
			return tab; 
		}
		else {
			if (newDisplay.getDisplayParams().tabName!=null) {
				//add the display to the current set of tabs. 
				ArrayList<PamGuiTabFX> allTabs = getAllTabs(); 
				for (int i=0; i<allTabs.size(); i++) {
					if (allTabs.get(i).getName().equals(newDisplay.getDisplayParams().tabName)){
						return allTabs.get(i); 
					}
				}
			}

			//if loop has completed then no tab has found. This should not ordinarily happen...
			String tabName; 
			if (newDisplay.getDisplayParams().tabName!=null) tabName=newDisplay.getDisplayParams().tabName; 
			else tabName=newDisplay.getName(); 

			tab = stages.get(0).addPamTab(new TabInfo(tabName), newDisplay, true);
			return tab; 
		}
	}

	/**
	 * Add a new display to PAMGUARD. 
	 * @param newDisplay - the new Display to add. 
	 */
	private void addDisplay(UserDisplayNodeFX newDisplay){
		PamGuiTabFX tab;


		if (!newDisplay.isStaticDisplay()){
			//if a non static display then add to a selected tab. 
			tab=getDisplayTab(newDisplay); 
			if (tab == null) {
				//the user cancelled the choice of tab. The module has been added, so its display must go somewhere. 
				ArrayList<PamGuiTabFX> displayTabs = getTabs(true);
				if (displayTabs.size() > 0) {
					tab = displayTabs.get(displayTabs.size()-1);
				}
				else {
					tab = stages.get(0).addPamTab(new TabInfo(uniqueTabName("Display 1", null)), null, true);
				}
			}
			tab.addInternalPane(newDisplay);
		}
		else {
			//if a static display create a new tab- name the static display
			tab=stages.get(0).addPamTab(new TabInfo(newDisplay.getName()), newDisplay, true);
		}

		//the tab. 
		if (tab.getInternalPanes().size()>0) {
			PamInternalPane internalFrame =
					tab.getInternalPanes().get(tab.getInternalPanes().size()-1);

			//			System.out.println("PAMGUIManagerFX: Adding a new display " 
			//					+ newDisplay.getDisplayParams().positionX + "  "
			//					+ newDisplay.getDisplayParams().positionY + "  " +
			//					+ newDisplay.getDisplayParams().sizeX + "  "
			//					+ newDisplay.getDisplayParams().sizeY);

			newDisplay.getDisplayParams().tabName=tab.getName(); 
			newDisplay.setFrameHolder(internalFrame); 

			//Have to platform later this as needs some time to intitialise? 11/08/2018
			Platform.runLater(()->{
				internalFrame.setPaneLayout(newDisplay.getDisplayParams().positionX, newDisplay.getDisplayParams().positionY); 
				if (newDisplay.getDisplayParams().sizeX>0) {
					internalFrame.setPaneSize(newDisplay.getDisplayParams().sizeX, newDisplay.getDisplayParams().sizeY); //this meant display was too small? 
				}
			});
		}
		else {
			if (newDisplay.getDisplayParams()!=null) {
				newDisplay.getDisplayParams().tabName=tab.getName(); 
			}
		}


	}


	private void removeDisplay(UserDisplayNodeFX newDisplay){
		//remove from any PamGuiFX
		for (int i=0; i<stages.size() ; i++){
			stages.get(i).removeInternalPane(newDisplay);
		}
	}


	/**
	 * Get all tabs from all stages. 
	 * @param excludestatic displays
	 * @return all tabs from all stages. 
	 */
	private ArrayList<PamGuiTabFX> getTabs(boolean excludeStatic){
		//first create list of tabs. 
		ArrayList<PamGuiTabFX> allTabs=new ArrayList<PamGuiTabFX>();

		ArrayList<PamGuiTabFX> tabs;
		for (int i=0; i<stages.size(); i++){
			tabs=stages.get(i).getTabs();
			for (int j=0; j<tabs.size(); j++){
				//the data model is never a tab for displays. 
				if (TabInfo.DATA_MODEL_TAB_NAME.equals(tabs.get(j).getName())) continue;
				if (excludeStatic && tabs.get(j).isStaticDisplay()) continue;
				allTabs.add(tabs.get(j));
				//System.out.println(" Tab name: "+tabs.get(j).getLabel().getText());
			}
		}

		return allTabs; 
	}

	/**
	 * Show a dialog to allow users to select which tab display should be added to. 
	 * @return the tab number to add display to. 
	 */
	private PamGuiTabFX showTabSelectDialog(){

		//first create list of tabs. 
		ArrayList<PamGuiTabFX> allTabs=getTabs(true);

		int tabIndex=0; 

		//no tab available, so add a new tab.
		if (allTabs.size()==0){
			return stages.get(0).addPamTab(new TabInfo(uniqueTabName("Display 1", null)), null, true);
		}
		else if (allTabs.size()==1){
			// only one place it can go, so don't ask. 
			return allTabs.get(0);
		}
		else{

			TabSelectionPane tabSelectionPane=new TabSelectionPane(stages.get(0));

			PamSettingsDialogFX<?> settingsDialog=new PamSettingsDialogFX(tabSelectionPane); 
			settingsDialog.getDialogPane().getStylesheets().addAll(PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
			settingsDialog.initStyle(StageStyle.UNDECORATED);

			//			ChoiceDialog<String> dialog = new ChoiceDialog<>(tabStrings.get(1), tabStrings);
			//			dialog.setTitle("Select Tab");
			//			dialog.setContentText("Select tab to add display to");

			//Get response
			Optional<PamGuiTabFX> result = (Optional<PamGuiTabFX>) settingsDialog.showAndWait();
			allTabs=getTabs(true);

			//can create 
			if (result.isPresent()){
				tabIndex=allTabs.indexOf(result.get());
				if (tabIndex < 0) return null;
			}
			else return null;
		}

		//now create
		return allTabs.get(tabIndex); 
	}


	/**
	 * Get CSS for PAMGUARD setting 'look and feel' for sliding dialogs
	 * @return the CSS for settings feels. 
	 */
	public ArrayList<String> getPamSettingsCSS() {//return new PrimerDark().getUserAgentStylesheet();
		//		return getClass().getResource("/Resources/css/pamSettingsCSS.css").toExternalForm();
		return PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getSlidingDialogCSS();
	}

	/**
	 * Get CSS for PAMGUARD GUI standard 'look and feel'
	 * @return the standard CSS fro PAMGUARD. 
	 */
	public ArrayList<String> getPamCSS() {
		//return new PrimerLight().getUserAgentStylesheet();
		//		return getClass().getResource("/Resources/css/pamCSS.css").toExternalForm();
		return PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getGUICSS();
	}

	/**
	 * Get CSS for PAMGUARD GUI standard 'look and feel' for regular dialogs
	 * @return the standard CSS fro PAMGUARD. 
	 */
	public ArrayList<String> getPamDialogCSS() {//return new PrimerDark().getUserAgentStylesheet();

		return PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS();
	}

	/**
	 * Refresh the scene and all known pane stylesheets from the current PamDefaultStyle.
	 * Call this after switching colour schemes so that the new CSS takes effect immediately.
	 */
	public void refreshSceneCSS() {
		PamStylesManagerFX.getPamStylesManagerFX().updateStyles();
	}


	/**
	 * Set the GUI to show PAMGUARD has started or stopped. 
	 */
	public void setPamRunning(boolean running){
		//		Glyph graphic;
		//		for (int i=0; i<this.stages.size(); i++){
		//			ArrayList<Button> recordButtons=stages.get(i).getRecordButtons();
		//			for (int j=0; j<recordButtons.size(); j++){
		//				if (running){
		//					graphic=Glyph.create("FontAwesome|SQUARE").size(22).color(Color.BLACK);
		//				}
		//				else {
		//					graphic=Glyph.create("FontAwesome|CIRCLE").size(22).color(Color.LIMEGREEN);
		//				}
		//				//now set all run buttons to correct format. 
		//				recordButtons.get(j).setGraphic(graphic);
		//			}
		//		}
	}

	public void pamStop(WindowEvent e) {
		if (this.prepareToClose(true)) { 
			Platform.exit();
			System.exit(0);
		}
		else e.consume();//dont close. 

	}

	@Override
	public void notifyModelChanged(int changeType) {
		//some notifications come from Swing dialogs, e.g. module ordering. 
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> notifyModelChanged(changeType));
			return;
		}
		// pass to data model
		if (dataModelFX!=null) {
			dataModelFX.notifyModelChanged(changeType);
		}

		if (changeType == PamControllerInterface.INITIALIZATION_COMPLETE) {
			nFFTModules = countFFTModules();
			nLtsaModules = countLtsaModules();
			Platform.runLater(() -> reportModulesWithoutFXGUI());
		}
		else if (changeType == PamControllerInterface.ADD_CONTROLLEDUNIT && pamController.isInitializationComplete()) {
			checkNewFFTModule();
			checkNewLtsaModule();
		}
		else if (changeType == PamControllerInterface.REMOVE_CONTROLLEDUNIT) {
			nFFTModules = countFFTModules();
			nLtsaModules = countLtsaModules();
		}

		//System.out.println("PAMGUIMANAGERFX: Notify model changed: "+changeType);	

	}

	/**
	 * The number of FFT modules in the configuration the last time it was checked. 
	 */
	private int nFFTModules = 0;

	private static final String FFT_CLASS = "fftManager.PamFFTControl";

	private static final String TIME_DISPLAY_CLASS = "dataPlotsFX.TDDisplayController";

	private int countFFTModules() {
		return pamController.findControlledUnits(fftManager.PamFFTControl.class).size();
	}

	/**
	 * @return true if the configuration has a Time Display module. 
	 */
	private boolean hasTimeDisplay() {
		return pamController.findControlledUnits(dataPlotsFX.TDDisplayController.class).size() > 0;
	}

	/**
	 * Called when a module has been added by the user. The first time an FFT module is added to a 
	 * configuration with no Time Display, add a Time Display showing a spectrogram so that there is 
	 * something to look at straight away. 
	 */
	private void checkNewFFTModule() {
		int n = countFFTModules();
		boolean newFFT = n > nFFTModules;
		nFFTModules = n;
		if (!newFFT || hasTimeDisplay()) {
			return;
		}
		// wait a moment, because automatic configuration tools add their own displays after the FFT module. 
		javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(750));
		wait.setOnFinished(e -> {
			if (!hasTimeDisplay() && countFFTModules() > 0) {
				addSpectrogramDisplay();
			}
		});
		wait.play();
	}

	/**
	 * The number of LTSA modules the last time it was checked. 
	 */
	private int nLtsaModules = 0;

	private int countLtsaModules() {
		return pamController.findControlledUnits(ltsa.LtsaControl.class).size();
	}

	/**
	 * When the user adds an LTSA module, add a Time Display which shows the LTSA over a long time range. 
	 */
	private void checkNewLtsaModule() {
		ArrayList<PamControlledUnit> ltsas = pamController.findControlledUnits(ltsa.LtsaControl.class);
		boolean newLtsa = ltsas.size() > nLtsaModules;
		nLtsaModules = ltsas.size();
		if (!newLtsa) {
			return;
		}
		ltsa.LtsaControl ltsaControl = (ltsa.LtsaControl) ltsas.get(ltsas.size() - 1);
		javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(750));
		wait.setOnFinished(e -> addLtsaDisplay(ltsaControl));
		wait.play();
	}

	/**
	 * Visible time range of the LTSA display. 
	 */
	private static final long LTSA_VISIBLE_MILLIS = 6L * 3600L * 1000L;

	/**
	 * If not null, the next display added goes into a tab with this name, which is made if needed, 
	 * rather than asking the user. 
	 */
	private String nextDisplayTabName;

	/**
	 * Add a Time Display which shows an LTSA. 
	 * @param ltsaControl - the LTSA module. 
	 * @return true if a display was added. 
	 */
	public boolean addLtsaDisplay(ltsa.LtsaControl ltsaControl) {
		try {
			String name = ltsaControl.getUnitName() + " Display";
			int i = 2;
			while (pamController.findControlledUnit(dataPlotsFX.TDDisplayController.class, name) != null) {
				name = ltsaControl.getUnitName() + " Display " + (i++);
			}
			// a new LTSA measures nothing until channels are chosen, so start with all channels of its FFT source. 
			ltsa.LtsaParameters ltsaParams = ltsaControl.getLtsaParameters();
			if (ltsaParams.channelMap == 0) {
				PamguardMVC.PamDataBlock<?> fftSource = ltsaParams.dataSource == null ? null : 
					pamController.getFFTDataBlock(ltsaParams.dataSource);
				if (fftSource == null) {
					for (PamguardMVC.PamDataBlock<?> block : pamController.getFFTDataBlocks()) {
						if (!(block instanceof ltsa.LtsaDataBlock)) {
							fftSource = block;
							break;
						}
					}
				}
				if (fftSource != null && !(fftSource instanceof ltsa.LtsaDataBlock)) {
					ltsa.LtsaParameters newParams = ltsaParams.clone();
					newParams.dataSource = fftSource.getLongDataName();
					newParams.channelMap = fftSource.getSequenceMap();
					ltsaControl.setLtsaParameters(newParams);
				}
			}
			// the LTSA display gets a tab of its own. 
			nextDisplayTabName = ltsaControl.getUnitName();
			java.util.function.Supplier<dataPlotsFX.layout.TDDisplayFX> display = PamAutoConfigUtils.addTimeDisplay(name);
			PamAutoConfigUtils.whenReady(display, null, tdDisplay -> {
				nextDisplayTabName = null;
				tdDisplay.addDataBlock(ltsaControl.getLtsaProcess().getLtsaDataBlock(), null);
				// an LTSA is made over hours, so show hours rather than seconds. 
				tdDisplay.getTDParams().visibleTimeRange = LTSA_VISIBLE_MILLIS;
				tdDisplay.getTDParams().scrollableTimeRange = LTSA_VISIBLE_MILLIS * 4;
				if (tdDisplay.getTimeScroller() != null) {
					if (!tdDisplay.isViewer()) {
						long start = (long) tdDisplay.getTDParams().startMillis;
						tdDisplay.getTimeScroller().setRangeMillis(start, start + LTSA_VISIBLE_MILLIS * 4, false);
					}
					tdDisplay.getTimeScroller().setVisibleMillis(LTSA_VISIBLE_MILLIS);
				}
			});
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Add a Time Display to the configuration. If the configuration has FFT data then the display 
	 * shows a spectrogram of the first FFT data found. 
	 * @return true if a display was added. 
	 */
	public boolean addSpectrogramDisplay() {
		try {
			String name = "Time Display";
			int i = 2;
			while (pamController.findControlledUnit(dataPlotsFX.TDDisplayController.class, name) != null) {
				name = "Time Display " + (i++);
			}
			java.util.function.Supplier<dataPlotsFX.layout.TDDisplayFX> display = 
					PamAutoConfigUtils.addTimeDisplay(name);
			PamAutoConfigUtils.whenReady(display, null, tdDisplay -> {
				ArrayList<PamguardMVC.PamDataBlock> fftBlocks = pamController.getFFTDataBlocks();
				if (fftBlocks != null && fftBlocks.size() > 0) {
					tdDisplay.addDataBlock(fftBlocks.get(0), null);
				}
			});
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Tell the user about any modules in the configuration which have no JavaFX user interface, e.g. 
	 * when a configuration made with the Swing GUI is opened. The modules still run, but have no 
	 * displays or settings here. 
	 */
	private void reportModulesWithoutFXGUI() {
		ArrayList<String> names = new ArrayList<String>();
		int n = pamController.getNumControlledUnits();
		for (int i = 0; i < n; i++) {
			PamControlledUnit unit = pamController.getControlledUnit(i);
			if (unit == null) continue;
			Object gui = null;
			try {
				gui = unit.getGUI(PamGUIManager.FX);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			if (!(gui instanceof PamControlledGUIFX)) {
				names.add(unit.getUnitName() + " (" + unit.getUnitType() + ")");
			}
		}
		if (names.size() == 0) return;
		boolean hasDisplay = getTabs(false).size() > 0;
		StringBuilder msg = new StringBuilder();
		msg.append("This configuration contains modules which do not have a JavaFX user interface yet:\n\n");
		for (String name : names) {
			msg.append("    ").append(name).append("\n");
		}
		msg.append("\nThese modules will still process and store data. Their settings open in the standard (Swing) "
				+ "PAMGuard dialogs from the settings button on the module in the Data Model, but their displays are "
				+ "only available in the standard PAMGuard user interface.");
		if (!hasDisplay) {
			// offer to add a display so that there is something to look at. 
			msg.append("\n\nThere are no displays in this configuration. Would you like to add a Time Display now? "
					+ "More displays can be added from the Displays group in the Data Model.");
			javafx.scene.control.ButtonType addDisplay = new javafx.scene.control.ButtonType("Add a Time Display", 
					javafx.scene.control.ButtonBar.ButtonData.YES);
			javafx.scene.control.ButtonType notNow = new javafx.scene.control.ButtonType("Not now", 
					javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
			javafx.scene.control.Alert alert = new javafx.scene.control.Alert(AlertType.INFORMATION, msg.toString(), addDisplay, notNow);
			alert.setTitle("Modules without a JavaFX interface");
			alert.setHeaderText(null);
			alert.initOwner(primaryStage);
			alert.getDialogPane().getStylesheets().addAll(PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
			alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
			if (alert.showAndWait().orElse(notNow) == addDisplay) {
				addSpectrogramDisplay();
			}
			return;
		}
		PamView.dialog.warn.WarnOnce.showWarningFX(primaryStage, "Modules without a JavaFX interface", msg.toString(), AlertType.INFORMATION);
	}

	@Override
	public void addView(PamViewInterface newView) {
		// TODO Auto-generated method stub
	}


	@Override
	public void showControlledUnit(PamControlledUnit unit) {
		showDisplays(unit);
	}

	/**
	 * Show the displays of a module. Any display which is not in a tab, e.g. because the user closed
	 * its tab, is added to a tab again. Then the tab holding the first display is selected. 
	 * @param unit - the module.
	 * @return true if the module has a display. 
	 */
	public boolean showDisplays(PamControlledUnit unit) {
		if (unit == null || !(unit.getGUI(PamGUIManager.FX) instanceof PamControlledGUIFX)) return false;
		ArrayList<UserDisplayNodeFX> displays = ((PamControlledGUIFX) unit.getGUI(PamGUIManager.FX)).getDisplays();
		if (displays == null || displays.size() == 0) return false;
		for (UserDisplayNodeFX display : displays) {
			if (!isDisplayShown(display)) {
				addDisplay(display);
			}
		}
		switchToTab(displays.get(0));
		return true;
	}

	/**
	 * Check whether a display is in one of the tabs. 
	 */
	private boolean isDisplayShown(UserDisplayNodeFX display) {
		for (PamGuiTabFX tab : getAllTabs()) {
			if (tab.getContentHolder().getCenter() == display.getNode()) return true;
			for (int k=0; k<tab.getInternalPanes().size(); k++) {
				if (tab.getInternalPanes().get(k).getUserDisplayNode() == display) return true;
			}
		}
		return false;
	}

	/**
	 * Get a tab name which is not used by any other tab. Displays find their tab by name when a 
	 * configuration is loaded so every tab needs a different name. 
	 * @param name - the name wanted. 
	 * @param ignore - a tab to ignore, e.g. the tab being renamed. Can be null. 
	 * @return the name, with a number added or changed if another tab already has it. 
	 */
	public String uniqueTabName(String name, PamGuiTabFX ignore) {
		String base = name;
		int n = 2;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*\\S)\\s+(\\d+)$").matcher(name);
		if (m.matches()) {
			base = m.group(1);
			n = Integer.parseInt(m.group(2)) + 1;
		}
		String unique = name;
		while (tabNameUsed(unique, ignore)) {
			unique = base + " " + n++;
		}
		return unique;
	}

	private boolean tabNameUsed(String name, PamGuiTabFX ignore) {
		for (PamGuiTabFX tab : getAllTabs()) {
			if (tab != ignore && name.equals(tab.getName())) return true;
		}
		return false;
	}

	@Override
	public void destroyModel() {
		// TODO Auto-generated method stub

	}


	@Override
	public void enableGUIControl(boolean enable) {
		// TODO Auto-generated method stub

	}


	public Stage getMainScene() {
		return primaryStage;
	}


	/**
	 * Set the title of the main window from the run mode and the name of the 
	 * configuration file or database. 
	 */
	public void sortFrameTitles() {
		if (primaryStage == null) return;
		PamController pamController = PamController.getInstance();
		String tit = "PAMGuard";
		switch (pamController.getRunMode()) {
		case PamController.RUN_NETWORKRECEIVER:
			tit += " Network Receiver";
			break;
		case PamController.RUN_MIXEDMODE:
			tit += " Mixed Mode";
			break;
		case PamController.RUN_PAMVIEW:
			tit += " Viewer";
			break;
		case PamController.RUN_REMOTE:
			tit += " Remote";
			break;
		}
		String ctrlName = pamController.getPSFName();
		if (ctrlName != null) {
			tit += " - " + ctrlName;
		}
		final String title = tit;
		if (Platform.isFxApplicationThread()) {
			primaryStage.setTitle(title);
		}
		else {
			Platform.runLater(() -> primaryStage.setTitle(title));
		}
	}


	public void getAllFrameParameters() {
		// TODO Auto-generated method stub

	}


	/**
	 * Get the number of stages 
	 * @return the number of stages (windows) currently open. 
	 */
	public int getNumFrames() {
		return stages.size();
	}

	/**
	 * Get a stage.
	 * @param i - frame number
	 * @return the stage
	 */
	public PamGuiFX getFrame(int i) {
		return stages.get(i);
	}

	/**
	 * Create a dialog to name a new module. 
	 * @param moduleInfo
	 * @return the new name of the module. 
	 */
	public String getModuleName(PamModuleInfo moduleInfo) {

		Dialog<String> dialog = new Dialog<String>();
		dialog.initStyle(StageStyle.UNDECORATED);
		dialog.setHeaderText(null);
		dialog.setTitle("Name controlled Unit");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		TextField nameField = new TextField();
		nameField.setPromptText("Module Name");
		nameField.setPrefColumnCount(20);
		//now need to set name which hasn't been set before- add numbers to end of default name;
		String name=moduleInfo.getDefaultName();
		boolean hasName=PamController.getInstance().isControlledUnit(name); 
		int n=2;
		while (hasName){
			name=(moduleInfo.getDefaultName()+" "+n);
			hasName=PamController.getInstance().isControlledUnit(name); 
			n++;
		}
		nameField.setText(name);

		PamHBox namePane=new PamHBox();
		//namePane.setPadding(new Insets(5)); 
		namePane.setSpacing(5); 
		namePane.setAlignment(Pos.CENTER_RIGHT);
		namePane.getChildren().addAll(new Label("Module name"), nameField);

		PamVBox mainPane=new PamVBox();
		mainPane.setSpacing(5);
		mainPane.setPadding(new Insets(5,5,5,5));
		Label title=new Label("Name New Module");
		titleFont2style(title); 
		//		title.setFont(titleFontSize2);
		mainPane.getChildren().addAll(title, namePane);

		dialog.getDialogPane().setContent(mainPane);
		dialog.getDialogPane().getStylesheets().addAll(this.getPamDialogCSS());

		//add listener to prevent close request if the dialog
		final Button btOk = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		//System.out.println("btOK "+btOk);
		if (btOk!=null) btOk.addEventFilter(ActionEvent.ACTION, event -> {
			boolean checkName=PamController.getInstance().isControlledUnit(nameField.getText()); 
			if (checkName){
				PamDialogFX.showWarning(null, "Invalid Module Name", "Another module has this name. Modules must have different names. Please choose another name");
				event.consume();
			}
		});

		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return nameField.getText();
			}
			return null;
		});

		// open dialog. 
		Optional<String> result = dialog.showAndWait();

		if (result.isPresent()){
			return result.get();
		}
		else return null;

	}

	/**
	 * Switch to the tab which contains a certain display within it's content area. 
	 * @param userDisplayNodeFX - the display to show. 
	 */
	public void switchToTab(UserDisplayNodeFX userDisplayNodeFX) {
		//System.out.println("Switch to tab: "+userDisplayNodeFX.getName());
		/**
		 * Need to iterate through each open window, then each tab, then all internal panes within each tab. Phew. 
		 */
		PamInternalPane internalPane;
		for (int i=0; i<this.getNumFrames(); i++){
			for (int j=0; j<this.getFrame(i).getNumTabs(); j++){
				//if a static display will be center of tab
				if (this.getFrame(i).getTab(j).getContentHolder().getCenter()==userDisplayNodeFX.getNode()){
					this.getFrame(i).selectTab(j);
					return;
				}
				//otherwise look through internal frames.
				for (int k=0; k<this.getFrame(i).getTab(j).getInternalPanes().size(); k++){
					internalPane=this.getFrame(i).getTab(j).getInternalPanes().get(k);
					//check if internal pane is the node. 
					if (internalPane.getInternalRegion()==userDisplayNodeFX.getNode()){
						this.getFrame(i).selectTab(j);
						return;
					}
					//also, might be embedded as a child of the internal pane. 
					if (internalPane.getInternalRegion().getChildrenUnmodifiable().indexOf(userDisplayNodeFX.getNode())>=0){
						this.getFrame(i).selectTab(j);
						return; 
					}
				}
			}
		}
	}


	/***
	 * Get the data model pane. 
	 * @return the data model pane. 
	 */
	public DataModelPaneFX getDataModelFX() {
		return dataModelFX;
	}

	/**
	 * Show GUI in load mode. SHows load bars etc. 
	 * @param loadMode- shoe the GUI in loadMode;
	 */
	public void showLoadMode(boolean loadMode) {
		for (int i=0; i<this.stages.size(); i++){
			stages.get(i).showLoadMode(loadMode); 
		}

	}

	/**
	 * Notify the GUI that progress has been made in an external task. 
	 * @param pamTaskUpdate - class which holds information on the Task and progress. 
	 */
	@Override
	public void notifyLoadProgress(PamTaskUpdate pamTaskUpdate) {
		for (int i=0; i<this.stages.size(); i++){
			stages.get(i).notifyLoadProgress(pamTaskUpdate); 
		}	
	}

	@Override
	public PamSettings getInitialSettings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init() {




		//		System.out.println("Initialising FX Toolbox");
		//		//start the application		
		//		new Thread(() -> {
		//			Application.launch(PamguardFXApplication.class); 
		//		}).start();
		//		// wait for toolkit to start:
		//		try {
		//			PamguardFXApplication.awaitFXToolkit();
		//		} catch (InterruptedException e) {
		//			// TODO Auto-generated catch block
		//			e.printStackTrace();
		//		}
		//	
		//		System.out.println("FX Toolbox has been initialised: "+ PamguardFXApplication.getPrimaryStage());

		//		//set the main stage.
		//		this.primaryStage=PamguardFXApplication.getPrimaryStage();
	}

	@Override
	public boolean hasCallBack() {
		// the FX application calls the rest of the GUI. 
		return true;
	}

	@Override
	public PamViewInterface initPrimaryView(PamController pamController, PamModel pamModel) {
		//start everything

		//now restore the settings for the display - this has to happen here as too early everywhere else. 
		PamSettingManager.getInstance().registerSettings(this);
		restoreWindowBounds();
		sortFrameTitles();

		primaryStage.show();

		return primaryView;
	}

	/**
	 * Put the main window where it was last time, but always on a screen which is 
	 * currently connected and never larger than that screen. 
	 */
	private void restoreWindowBounds() {
		double w = pamGuiSettings.width;
		double h = pamGuiSettings.height;
		double x = pamGuiSettings.x;
		double y = pamGuiSettings.y;
		javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
		if (!Double.isNaN(x) && !Double.isNaN(y)) {
			// use the screen the window was on, if it's still there. 
			java.util.List<javafx.stage.Screen> screens = javafx.stage.Screen.getScreensForRectangle(x, y, Math.max(w, 1), Math.max(h, 1));
			if (screens.size() > 0) {
				bounds = screens.get(0).getVisualBounds();
			}
			else {
				x = y = Double.NaN;
			}
		}
		if (Double.isNaN(w) || w < 400) w = 1400;
		if (Double.isNaN(h) || h < 300) h = 900;
		w = Math.min(w, bounds.getWidth());
		h = Math.min(h, bounds.getHeight());
		if (Double.isNaN(x) || Double.isNaN(y)) {
			x = bounds.getMinX() + (bounds.getWidth() - w) / 2;
			y = bounds.getMinY() + (bounds.getHeight() - h) / 2;
		}
		x = Math.max(bounds.getMinX(), Math.min(x, bounds.getMaxX() - w));
		y = Math.max(bounds.getMinY(), Math.min(y, bounds.getMaxY() - h));
		primaryStage.setX(x);
		primaryStage.setY(y);
		primaryStage.setWidth(w);
		primaryStage.setHeight(h);
		primaryStage.setMaximized(pamGuiSettings.maximized);
		primaryStage.setFullScreen(pamGuiSettings.fullscreen);

		// remember the size of the window when it isn't maximised, so it can be restored to that. 
		javafx.beans.value.ChangeListener<Number> boundsListener = (obs, oldVal, newVal) -> {
			if (!primaryStage.isMaximized() && !primaryStage.isFullScreen() && !primaryStage.isIconified()) {
				pamGuiSettings.width = primaryStage.getWidth();
				pamGuiSettings.height = primaryStage.getHeight();
				pamGuiSettings.x = primaryStage.getX();
				pamGuiSettings.y = primaryStage.getY();
			}
		};
		primaryStage.widthProperty().addListener(boundsListener);
		primaryStage.heightProperty().addListener(boundsListener);
		primaryStage.xProperty().addListener(boundsListener);
		primaryStage.yProperty().addListener(boundsListener);
	}

	public Stage setPrimaryStage(Stage primaryStage2) {
		return this.primaryStage=primaryStage2;
	}

	@Override
	public String getModuleName(Object parentFrame, PamModuleInfo moduleInfo) {
		return 	getModuleName(moduleInfo); 
	}

	/**
	 * Show the alert dialog. 
	 * @param strheader
	 * @param confirm
	 * @return
	 */
	public static ButtonType showAlertDialog(String strheader, String confirm) {
		//create alert dialog. 
		Alert alert = new Alert(AlertType.WARNING);
		alert.setTitle("Confirmation Dialog");
		alert.setHeaderText(strheader);
		alert.setContentText(confirm);

		Optional<ButtonType> result = alert.showAndWait();
		return result.get();
	}

	class BombThread implements Runnable {

		volatile boolean waitingStop = true;
		@Override
		public void run() {
			pamController.pamStop();
			waitingStop = false;
		}

	}

	/**
	 * 
	 * This get's called whenever the main window closes - 
	 * ideally, I'd like to stop this happening when the system
	 * is running, but since that's not possible, just make sure
	 * everything has stopped.
	 * 
	 * @param weShouldSave true if the settings should get saved to the psf file, false if otherwise
	 * @return true if ok to close, false otherwise. 
	 */
	public boolean prepareToClose(boolean weShouldSave) {
		//		System.out.println("Preparing to close PAMGUARD");

		if (!pamController.canClose()) {
			return false;
		}

		int pamStatus = pamController.getPamStatus();
		if (pamStatus != PamController.PAM_IDLE) {
			boolean ans = PamDialogFX.showMessageDialog(this.getMainScene().getOwner(),  
					"PAMGUARD stop", "Are you sure you want to stop and exit. " +
							"PAMGUARD is busy",
							ButtonType.YES,
							ButtonType.CANCEL, AlertType.CONFIRMATION);
			if (!ans) {
				return false;
			}
		}

		/*
		 * Sometimes have trouble stopping in which case we're 
		 * going to have to bomb. If it can't stop in 5s, then die ! 
		 */
		BombThread bombThread = new BombThread();
		Thread t = new Thread(bombThread);
		t.start();
		for (int i = 6; i >= 0 && bombThread.waitingStop; i--) {
			for (int j = 0; j<10 && bombThread.waitingStop; j++) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			if (!bombThread.waitingStop) break;
			System.out.println(String.format("Stopping PAMGuard. Will force exit in %d seconds", i));
		}

		//save the viewer data in viewer mode. 
		if (pamController.getRunMode() == PamController.RUN_PAMVIEW) {
			pamController.saveViewerData();
		}

		//		// deal with anything that needs sorting out in the realm of UID's.
		// move this to pamController.pamClose()
		//		pamController.getUidManager().runShutDownOps();

		// if the user doesn't want to save the config file, make sure they know
		// that they'll lose any changes to the settings
		if (!weShouldSave) {
			boolean ans = PamDialogFX.showMessageDialog(this.getMainScene().getOwner(),  
					"<html><body><p style='width: 300px;'>Are you sure you want to exit without saving your current configuration?  "
							+ "Any changes that have been made to the current configuration will be lost</p></body></html>",
							"Exit without Save",
							ButtonType.YES,
							ButtonType.CANCEL, AlertType.CONFIRMATION);
			if (!ans) {	// Hitting Cancel returns a No value
				return false;
			}
		}

		// finally save all settings just before PAMGUARD closes. 
		if (weShouldSave) {
			PamSettingManager.getInstance().saveFinalSettings();
		}

		pamController.pamClose();

		return true;
	}


	public boolean getShowLoadMode() {
		// TODO Auto-generated method stub
		return this.getFrame(0).isShowLoadMode(); 
	}

	@Override
	public void pamStarted() {
		Platform.runLater(()->{
			for (int i=0; i<this.stages.size(); i++){
				stages.get(i).pamStarted(); 
			}
		});

	}

	@Override
	public void pamEnded() {
		Platform.runLater(()->{
			for (int i=0; i<this.stages.size(); i++){
				stages.get(i).pamEnded(); 
			}
		});
	}

	@Override
	public void modelChanged(int changeType) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTitle(String title) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getFrameNumber() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public JFrame getGuiFrame() {
		// TODO Auto-generated method stub
		return null;
	}

	/***Settings Manager***/

	@Override
	public String getUnitName() {
		return "GUI_Manager_FX"; //<-only ever one GUIManagerFX
	}

	@Override
	public String getUnitType() {
		return "GUIManagerFX";
	}

	@Override
	public Serializable getSettingsReference() {
		Serializable set = prepareSerialisedSettings();
		return set;
	}

	@Override
	public long getSettingsVersion() {
		return PAMGuiFXSettings.serialVersionUID;
	}

	private Serializable prepareSerialisedSettings(){
		ArrayList<TabInfo> tabInfos = new ArrayList<TabInfo>();
		for (int i=0; i<this.getPamGuiFXList().size(); i++) {
			PamGuiFX stage = this.getPamGuiFXList().get(i);
			for (int j=0; j<stage.getNumTabs(); j++) {
				PamGuiTabFX tab = stage.getTab(j);
				//the Data Model tab is always recreated on startup, so don't persist it.
				if (TabInfo.DATA_MODEL_TAB_NAME.equals(tab.getName())) continue;
				tabInfos.add(tab.getTabInfo());
			}
		}
		pamGuiSettings.tabInfos=tabInfos;
		// the size and position of the window are kept up to date by listeners on the stage.
		pamGuiSettings.maximized = primaryStage.isMaximized();
		pamGuiSettings.fullscreen = primaryStage.isFullScreen();

		return pamGuiSettings;
	}

	@Override
	public boolean restoreSettings(PamControlledUnitSettings settings) {
		//		System.out.println("Settings: "+settings);
		if (settings == null) {
			return false;
		}
		PAMGuiFXSettings pamGuiSettings = ((PAMGuiFXSettings) settings.getSettings()); 
		this.pamGuiSettings=pamGuiSettings.clone(); 
		this.setParams(pamGuiSettings); 
		//		tdParams.scrollableTimeRange=300000L;
		//		System.out.println("Settings: "+settings.graphParameters.size());
		return true;
	}

	/**
	 * Set GUI params. This should only be set during start up. 
	 * @param pamGuiSettings - the GUI parameters. 
	 */
	private void setParams(PAMGuiFXSettings pamGuiSettings2) {
		//Recreate the saved tabs (empty) before any displays are added. This preserves
		//tab names (including user renames), tab order and any tabs that have no displays.
		//Displays loaded later are matched back to these tabs by name in getDisplayTab().
		//
		//This runs early in startup (from restoreSettings, via initPrimaryView) at which
		//point only the Data Model tab exists, so we simply add any other saved tabs.
		if (pamGuiSettings2 == null || pamGuiSettings2.tabInfos == null) return;

		PamGuiFX primaryStageFX = stages.get(0);
		for (TabInfo tabInfo : pamGuiSettings2.tabInfos) {
			if (tabInfo == null || tabInfo.tabName == null) continue;
			//the Data Model tab already exists - never duplicate it.
			if (TabInfo.DATA_MODEL_TAB_NAME.equals(tabInfo.tabName)) continue;
			//don't create a tab that already exists (matched by name).
			if (tabExists(tabInfo.tabName)) continue;
			//recreate the tab empty; displays are added to it later by name.
			primaryStageFX.addPamTab(new TabInfo(tabInfo.tabName), null, true);
		}
	}

	/**
	 * Check whether a tab with the given name already exists in any open window.
	 * @param tabName - the tab name to look for.
	 * @return true if a tab with this name exists.
	 */
	private boolean tabExists(String tabName) {
		if (tabName == null) return false;
		for (PamGuiTabFX tab : getAllTabs()) {
			if (tabName.equals(tab.getName())) return true;
		}
		return false;
	}

	public Window getPrimaryStage() {
		return this.primaryStage;
	}

	/**
	 * Add a new pane to the status bar. The status bar is located across the GUI and so 
	 * a pane factory is used to create the pane in each window.
	 * <p>
	 * This will add the pane to all current windows and any future windows. If you wish to add to a specific window 
	 * get a reference to the PamGuiFX stage and use stage.addStatusBarPane(PaneFactory); 
	 * @param statusPaneFactory - the pane factory which creates the pane. 
	 */
	public void addStatusBarPane(PaneFactory statusPaneFactory) {
		if (!toolBarPanes.contains(statusPaneFactory)){
			toolBarPanes.add(statusPaneFactory); 
			
		}
		//add to all stages - this will add to all tabs in each stage but will not duplicate if already exists.
		for (int i=0; i<this.stages.size(); i++) {
			this.stages.get(i).addStatusBarPane(statusPaneFactory); 
		}
		
//		else {
//			System.err.println("PamGuiManagerFX: Tool bar pane already exists");
//		}
		
		
	}
	
	
	/**
	 * Remove a status bar pane from all windows.
	 * @param statusPaneFactory - the pane factory which creates the pane. 
	 * @return true of the pane was removed, false if it was not found.
	 */
	public boolean removeStatusBarPane(PaneFactory statusPaneFactory) {
		if (toolBarPanes.remove(statusPaneFactory)){
			//remove from all stages
			for (int i=0; i<this.stages.size(); i++) {
				this.stages.get(i).removeStatusBarPane(statusPaneFactory); 
			}
			return true;
		}
		else {
			return false;
		}
	}


}
