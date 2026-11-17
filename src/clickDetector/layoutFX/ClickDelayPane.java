package clickDetector.layoutFX;

import Localiser.DelayMeasurementParams;
import Localiser.algorithms.timeDelayLocalisers.bearingLoc.DelayOptionsPaneFX;
import PamController.SettingsPane;
import PamView.PamSymbol;
import clickDetector.ClickControl;
import clickDetector.ClickParameters;
import clickDetector.ClickClassifiers.ClickIdentifier;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSymbolFX;
import pamViewFX.fxNodes.PamVBox;


/**
 * Pane to change click delay settings. The default settings and each click type are listed in a
 * table. Selecting a row shows its delay measurement settings to the side. Click types can use the
 * default settings or their own, as in the Swing click delay panel.
 *
 * @author Jamie Macaulay
 *
 */
public class ClickDelayPane extends SettingsPane<ClickParameters> {

	/**
	 * Reference click control.
	 */
	private ClickControl clickControl;

	/**
	 * Delay measurement parameters for each type. Index 0 is the default. Null for a type
	 * which uses the default.
	 */
	private DelayMeasurementParams[] delayParams;

	/**
	 * Click type codes. Index 0 is the default.
	 */
	private int[] typesList;

	/**
	 * Whether each type uses the default settings.
	 */
	private boolean[] useDefault;

	/**
	 * Names of each type.
	 */
	private String[] typeNames;

	/**
	 * Symbols for each type (null for the default).
	 */
	private PamSymbol[] typeSymbols;

	/**
	 * Table of the default and click types.
	 */
	private TableView<Integer> typeTable;

	/**
	 * Title of the settings for the selected type.
	 */
	private Label selectedTitle;

	/**
	 * Use the default settings for the selected type.
	 */
	private CheckBox useDefaultCheck;

	/**
	 * Delay measurement settings for the selected type.
	 */
	private DelayOptionsPaneFX delayOptionsPane;

	/**
	 * The row shown in the settings at the side.
	 */
	private int shownIndex = -1;

	/**
	 * True while the controls are being set up, so changes are not copied back.
	 */
	private boolean settingUp = false;

	private PamBorderPane mainPane;

	public ClickDelayPane(ClickControl clickControl){
		super(null);
		this.clickControl = clickControl;
		mainPane= new PamBorderPane();
		createPane();
	}

	/**
	 * Create the pane.
	 */
	private void createPane(){
		typeTable = new TableView<Integer>();
		typeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		typeTable.setPrefWidth(330);
		typeTable.setMinWidth(300);
		typeTable.setPrefHeight(250);
		typeTable.setPlaceholder(new Label("No click types"));

		TableColumn<Integer, Integer> symbolCol = new TableColumn<Integer, Integer>("");
		symbolCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<Integer>(cell.getValue()));
		symbolCol.setCellFactory(col -> new javafx.scene.control.TableCell<Integer, Integer>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || typeSymbols == null || item >= typeSymbols.length || typeSymbols[item] == null) {
					setGraphic(null);
				}
				else {
					setGraphic(new PamSymbolFX(typeSymbols[item]).getNode(PamGuiManagerFX.iconSize));
				}
			}
		});
		symbolCol.setMinWidth(30);
		symbolCol.setPrefWidth(30);
		symbolCol.setMaxWidth(30);
		symbolCol.setSortable(false);

		TableColumn<Integer, String> nameCol = new TableColumn<Integer, String>("Click type");
		nameCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(typeNames[cell.getValue()]));
		nameCol.setSortable(false);
		nameCol.setMinWidth(150);
		nameCol.setPrefWidth(190);

		TableColumn<Integer, String> settingsCol = new TableColumn<Integer, String>("Settings");
		settingsCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getSettingsName(cell.getValue())));
		settingsCol.setSortable(false);
		settingsCol.setMinWidth(80);

		typeTable.getColumns().add(symbolCol);
		typeTable.getColumns().add(nameCol);
		typeTable.getColumns().add(settingsCol);

		typeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal)->{
			if (settingUp) return;
			copySettingsBack();
			showSettings(newVal);
		});

		//the settings for the selected type
		selectedTitle = new Label(" ");
		PamGuiManagerFX.titleFont2style(selectedTitle);

		useDefaultCheck = new CheckBox("Use default settings");
		useDefaultCheck.setOnAction((action)->{
			if (settingUp || shownIndex <= 0) return;
			useDefault[shownIndex] = useDefaultCheck.isSelected();
			if (!useDefault[shownIndex] && delayParams[shownIndex] == null && delayParams[0] != null) {
				// start from the default settings
				delayParams[shownIndex] = delayParams[0].clone();
			}
			showSettings(shownIndex);
			typeTable.refresh();
		});

		delayOptionsPane = new DelayOptionsPaneFX();

		PamVBox settingsHolder = new PamVBox();
		settingsHolder.setSpacing(5);
		settingsHolder.setPadding(new Insets(0,0,0,10));
		settingsHolder.getChildren().addAll(selectedTitle, useDefaultCheck, delayOptionsPane.getContentNode());

		PamHBox holder = new PamHBox();
		holder.setSpacing(5);
		holder.getChildren().addAll(typeTable, settingsHolder);
		PamHBox.setHgrow(settingsHolder, Priority.ALWAYS);

		mainPane.setCenter(holder);
	}

	/**
	 * Short description of the settings used for a row.
	 */
	private String getSettingsName(int index) {
		if (index > 0 && useDefault[index]) {
			return "Default";
		}
		if (index == 0) {
			return "Default";
		}
		return "Custom";
	}

	/**
	 * Copy the settings in the side pane back to the row they were shown for.
	 * @return false if the settings are invalid.
	 */
	private boolean copySettingsBack() {
		if (shownIndex < 0 || delayParams == null || shownIndex >= delayParams.length) {
			return true;
		}
		if (shownIndex > 0 && useDefault[shownIndex]) {
			return true;
		}
		DelayMeasurementParams dmp = delayParams[shownIndex];
		if (dmp == null) {
			dmp = new DelayMeasurementParams();
		}
		DelayMeasurementParams newParams = delayOptionsPane.getParams(dmp.clone());
		if (newParams == null) {
			return false;
		}
		delayParams[shownIndex] = newParams;
		typeTable.refresh();
		return true;
	}

	/**
	 * Show the settings for a row at the side.
	 * @param index - the row.
	 */
	private void showSettings(Integer index) {
		if (index == null || delayParams == null || index < 0 || index >= delayParams.length) {
			shownIndex = -1;
			return;
		}
		settingUp = true;
		shownIndex = index;
		selectedTitle.setText(typeNames[index]);
		useDefaultCheck.setVisible(index > 0);
		useDefaultCheck.setManaged(index > 0);
		useDefaultCheck.setSelected(index > 0 && useDefault[index]);
		DelayMeasurementParams dmp = delayParams[index];
		if (dmp == null || (index > 0 && useDefault[index])) {
			dmp = delayParams[0];
		}
		if (dmp == null) {
			dmp = new DelayMeasurementParams();
		}
		if (clickControl.getClickDetector() != null && clickControl.getClickDetector().getSampleRate() > 0) {
			delayOptionsPane.setSampleRate(clickControl.getClickDetector().getSampleRate());
		}
		delayOptionsPane.setParams(dmp);
		// settings of a type which uses the default can't be changed here.
		delayOptionsPane.getContentNode().setDisable(index > 0 && useDefault[index]);
		settingUp = false;
	}

	/**
	 * Set up the rows for the current click types.
	 * @param clickParameters - the click parameters
	 */
	private void createPanelControls(ClickParameters clickParameters) {
		ClickIdentifier clickIdentifier = clickControl.getClickIdentifier();
		int nTypes = 1; // always a default.
		int[] codeList = null;
		if (clickIdentifier != null) {
			codeList = clickIdentifier.getCodeList();
			if (codeList != null) {
				nTypes += codeList.length;
			}
		}
		PamSymbol[] symbols = null;
		if (clickIdentifier != null) {
			symbols = clickIdentifier.getSymbols();
		}

		typesList = new int[nTypes];
		typeNames = new String[nTypes];
		typeSymbols = new PamSymbol[nTypes];
		useDefault = new boolean[nTypes];
		delayParams = new DelayMeasurementParams[nTypes];

		delayParams[0] = clickParameters.getDelayMeasurementParams(0, true);
		typeNames[0] = "Unclassified clicks / default";
		for (int i = 1; i < nTypes; i++) {
			typesList[i] = codeList[i-1];
			typeNames[i] = clickIdentifier.getSpeciesName(typesList[i]);
			if (symbols != null && i-1 < symbols.length) {
				typeSymbols[i] = symbols[i-1];
			}
			delayParams[i] = clickParameters.getDelayMeasurementParams(typesList[i], false);
			// types without their own settings are given the default object. 
			useDefault[i] = delayParams[i] == null || delayParams[i] == delayParams[0];
			if (useDefault[i]) {
				delayParams[i] = null;
			}
		}
	}

	public ClickParameters getParams() {
		return getParams(null);
	}

	/**
	 * Copies the local list back into the click settings, removing
	 * old entries as necessary.
	 * @param clickParameters
	 * @return the click parameters, or null if the settings are invalid.
	 */
	public ClickParameters getParams(ClickParameters clickParameters) {
		if (clickParameters==null) return null;
		if (delayParams==null || delayParams[0] == null) {
			return clickParameters;
		}
		if (!copySettingsBack()) {
			return null;
		}
		clickParameters.setDelayMeasurementParams(0, delayParams[0]);
		for (int i = 1; i < typesList.length; i++) {
			if (useDefault[i]) {
				clickParameters.setDelayMeasurementParams(typesList[i], null);
			}
			else {
				clickParameters.setDelayMeasurementParams(typesList[i], delayParams[i]);
			}
		}
		return clickParameters;
	}

	@Override
	public void setParams(ClickParameters clickParameters) {
		settingUp = true;
		createPanelControls(clickParameters);
		typeTable.getItems().clear();
		for (int i = 0; i < typesList.length; i++) {
			typeTable.getItems().add(i);
		}
		int select = shownIndex;
		if (select < 0 || select >= typesList.length) {
			select = 0;
		}
		typeTable.getSelectionModel().select(select);
		settingUp = false;
		shownIndex = -1;
		showSettings(select);
		typeTable.refresh();
	}

	@Override
	public String getName() {
		return "Click Delay Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
