package clipgenerator.layoutFX;

import java.util.ArrayList;

import PamController.PamController;
import PamController.SettingsPane;
import PamDetection.RawDataUnit;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamRawDataBlock;
import clipgenerator.ClipControl;
import clipgenerator.ClipGenSetting;
import clipgenerator.ClipSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.utilityPanes.SelectFolderFX;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;

/**
 * JavaFX settings for the clip generator. Has the same options as the Swing clip
 * generator dialog (audio source, storage and the data triggers with their per trigger
 * settings) plus the map bearing line length from the Swing clip display settings dialog.
 * The per trigger settings, which are a separate dialog in Swing, are shown in an expandable
 * section for each trigger.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipSettingsPaneFX extends SettingsPane<ClipSettings> {

	private ClipControl clipControl;

	private PamBorderPane mainPane;

	private SourcePaneFX sourcePane;

	private CheckBox storeWav, storeBinary, datedSubFolders;

	private SelectFolderFX selectFolder;

	private PamVBox triggerBox;

	private ArrayList<TriggerPane> triggerPanes = new ArrayList<TriggerPane>();

	public ClipSettingsPaneFX(ClipControl clipControl) {
		super(null);
		this.clipControl = clipControl;

		sourcePane = new SourcePaneFX("Audio Data Source", RawDataUnit.class, false, true);
		Label sourceLabel = new Label("Audio Data Source");
		PamGuiManagerFX.titleFont2style(sourceLabel);

		// storage
		Label storageLabel = new Label("Storage Options");
		PamGuiManagerFX.titleFont2style(storageLabel);
		storeWav = new CheckBox("Store in wav files");
		storeBinary = new CheckBox("Store in binary files");
		storeWav.setOnAction(e -> enableControls());
		storeBinary.setOnAction(e -> enableControls());
		PamHBox storeBox = new PamHBox(storeWav, storeBinary);
		storeBox.setSpacing(15);
		selectFolder = new SelectFolderFX("Select Folder", 40, false);
		datedSubFolders = new CheckBox("Store data in sub folders by date");

		// triggers
		Label triggerLabel = new Label("Data Triggers");
		PamGuiManagerFX.titleFont2style(triggerLabel);
		triggerBox = new PamVBox();
		triggerBox.setSpacing(3);

		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(5));
		vBox.getChildren().addAll(sourceLabel, sourcePane, storageLabel, storeBox, selectFolder, datedSubFolders,
				triggerLabel, triggerBox);

		ScrollPane scrollPane = new ScrollPane(vBox);
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setPrefViewportHeight(560);
		scrollPane.setPrefViewportWidth(470);

		mainPane = new PamBorderPane(scrollPane);
	}

	/**
	 * Build a pane for each possible trigger.
	 * @param clipSettings - the settings.
	 */
	private void createTriggerPanes(ClipSettings clipSettings) {
		triggerBox.getChildren().clear();
		triggerPanes.clear();
		int n = clipSettings.getNumClipGenerators();
		if (n == 0) {
			Label l = new Label("There are no detectors which can trigger clips");
			triggerBox.getChildren().add(l);
		}
		for (int i = 0; i < n; i++) {
			TriggerPane tp = new TriggerPane(clipSettings.getClipGenSetting(i), i == 0);
			triggerPanes.add(tp);
			triggerBox.getChildren().add(tp.titledPane);
		}
	}

	private void enableControls() {
		boolean wav = storeWav.isSelected();
		selectFolder.setEnabled(wav);
		datedSubFolders.setDisable(!wav);
		for (TriggerPane tp : triggerPanes) {
			tp.enableControls();
		}
	}

	@Override
	public ClipSettings getParams(ClipSettings currParams) {
		ClipSettings clipSettings = currParams == null ? new ClipSettings() : currParams.clone();
		PamDataBlock source = sourcePane.getSource();
		if (source == null) {
			PamDialogFX.showWarning("You must select an audio data source");
			return null;
		}
		clipSettings.dataSourceName = source.getLongDataName();

		clipSettings.datedSubFolders = datedSubFolders.isSelected();
		clipSettings.outputFolder = selectFolder.getFolderName(false);
		if (!storeWav.isSelected() && !storeBinary.isSelected()) {
			PamDialogFX.showWarning("No storage option is selected");
			return null;
		}
		if (storeWav.isSelected()) {
			if (clipSettings.outputFolder == null || clipSettings.outputFolder.length() == 0) {
				PamDialogFX.showWarning("You must specify an output folder for created clips");
				return null;
			}
			clipSettings.outputFolder = selectFolder.getFolderName(true);
			clipSettings.storageOption = storeBinary.isSelected() ? ClipSettings.STORE_BOTH : ClipSettings.STORE_WAVFILES;
		}
		else {
			clipSettings.storageOption = ClipSettings.STORE_BINARY;
		}

		for (TriggerPane tp : triggerPanes) {
			// the settings are a copy so it's safe to change the trigger settings in them. 
			ClipGenSetting genSet = clipSettings.findClipGenSetting(tp.clipGenSetting.dataName);
			if (genSet != null) {
				tp.getParams(genSet);
			}
		}
		return clipSettings;
	}

	@Override
	public void setParams(ClipSettings clipSettings) {
		if (clipSettings == null) {
			clipSettings = new ClipSettings();
		}
		sourcePane.setSourceList();
		PamRawDataBlock rawDataBlock = (PamRawDataBlock) PamController.getInstance().getDataBlockByLongName(clipSettings.dataSourceName);
		if (rawDataBlock == null) {
			rawDataBlock = PamController.getInstance().getRawDataBlock(clipSettings.dataSourceName);
		}
		if (rawDataBlock != null) {
			sourcePane.setSource(rawDataBlock);
		}

		selectFolder.setFolderName(clipSettings.outputFolder);
		datedSubFolders.setSelected(clipSettings.datedSubFolders);
		storeWav.setSelected(clipSettings.storageOption == ClipSettings.STORE_WAVFILES
				|| clipSettings.storageOption == ClipSettings.STORE_BOTH);
		storeBinary.setSelected(clipSettings.storageOption == ClipSettings.STORE_BINARY
				|| clipSettings.storageOption == ClipSettings.STORE_BOTH);

		createTriggerPanes(clipSettings);
		enableControls();
	}

	@Override
	public String getName() {
		return clipControl.getUnitName() + " settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

	/**
	 * Settings for one trigger data stream.
	 */
	private class TriggerPane {

		private ClipGenSetting clipGenSetting;

		private TitledPane titledPane;

		private CheckBox enable;

		private ComboBox<String> channelSel;

		private PamSpinner<Double> preSeconds, postSeconds;

		private TextField fileInitials;

		private RadioButton budgetOff, budgetOn;

		private PamSpinner<Double> dataBudget, budgetPeriod;

		private PamSpinner<Double> mapLineLength;

		private PamGridPane grid;

		TriggerPane(ClipGenSetting clipGenSetting, boolean isSpectrogramMarks) {
			this.clipGenSetting = clipGenSetting;

			enable = new CheckBox(clipGenSetting.dataName);
			enable.setSelected(clipGenSetting.enable);
			String tip = isSpectrogramMarks ?
					"To use spectrogram marks you will also need to enable marking in the spectrogram display mark settings" :
						"Check to enable clip generation";
			enable.setTooltip(new Tooltip(tip));
			enable.setOnAction(e -> enableControls());

			grid = new PamGridPane();
			grid.setHgap(5);
			grid.setVgap(5);
			int row = 0;

			grid.add(new Label("Channel selection"), 0, row);
			channelSel = new ComboBox<String>();
			channelSel.getItems().addAll(ClipGenSetting.channelSelTypes);
			channelSel.getSelectionModel().select(Math.max(0, clipGenSetting.channelSelection));
			grid.add(channelSel, 1, row, 2, 1);
			row++;

			grid.add(new Label("Time before trigger"), 0, row);
			preSeconds = spinner(0, 3600, clipGenSetting.preSeconds, 0.1);
			grid.add(preSeconds, 1, row);
			grid.add(new Label("s"), 2, row);
			row++;

			grid.add(new Label("Time after trigger"), 0, row);
			postSeconds = spinner(0, 3600, clipGenSetting.postSeconds, 0.1);
			grid.add(postSeconds, 1, row);
			grid.add(new Label("s"), 2, row);
			row++;

			grid.add(new Label("File initials"), 0, row);
			fileInitials = new TextField(clipGenSetting.clipPrefix);
			fileInitials.setPrefColumnCount(7);
			fileInitials.setTooltip(new Tooltip("Prefix for wav file names (wav file storage only)"));
			grid.add(fileInitials, 1, row);
			row++;

			ToggleGroup group = new ToggleGroup();
			budgetOff = new RadioButton("Record everything");
			budgetOn = new RadioButton("Budget data");
			budgetOff.setToggleGroup(group);
			budgetOn.setToggleGroup(group);
			budgetOn.setSelected(clipGenSetting.useDataBudget);
			budgetOff.setSelected(!clipGenSetting.useDataBudget);
			budgetOn.setOnAction(e -> enableControls());
			budgetOff.setOnAction(e -> enableControls());
			PamHBox budgetBox = new PamHBox(budgetOff, budgetOn);
			budgetBox.setSpacing(10);
			grid.add(budgetBox, 0, row, 3, 1);
			row++;

			grid.add(new Label("Data budget"), 0, row);
			dataBudget = spinner(0.1, 1e6, clipGenSetting.dataBudget / 1024., 1);
			grid.add(dataBudget, 1, row);
			grid.add(new Label("MB"), 2, row);
			row++;

			grid.add(new Label("Budget period"), 0, row);
			budgetPeriod = spinner(0.01, 1e5, clipGenSetting.budgetPeriodHours, 1);
			grid.add(budgetPeriod, 1, row);
			grid.add(new Label("hours"), 2, row);
			row++;

			grid.add(new Label("Map bearing line"), 0, row);
			mapLineLength = spinner(0, 1e6, clipGenSetting.mapLineLength == null ? 1000. : clipGenSetting.mapLineLength, 100);
			mapLineLength.setTooltip(new Tooltip("Length of bearing lines drawn on the map for clips with bearings"));
			grid.add(mapLineLength, 1, row);
			grid.add(new Label("m"), 2, row);

			grid.setPadding(new Insets(5, 5, 5, 25));

			titledPane = new TitledPane();
			titledPane.setGraphic(enable);
			titledPane.setContent(grid);
			titledPane.setExpanded(false);
			titledPane.setAlignment(Pos.CENTER_LEFT);
			PamVBox.setVgrow(titledPane, Priority.NEVER);
		}

		private PamSpinner<Double> spinner(double min, double max, double val, double step) {
			PamSpinner<Double> spinner = new PamSpinner<Double>(min, max, val, step);
			spinner.setEditable(true);
			spinner.setPrefWidth(110);
			spinner.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
			return spinner;
		}

		void enableControls() {
			grid.setDisable(!enable.isSelected());
			fileInitials.setDisable(!storeWav.isSelected());
			dataBudget.setDisable(!budgetOn.isSelected());
			budgetPeriod.setDisable(!budgetOn.isSelected());
		}

		ClipGenSetting getParams(ClipGenSetting genSet) {
			genSet.enable = enable.isSelected();
			genSet.channelSelection = channelSel.getSelectionModel().getSelectedIndex();
			genSet.preSeconds = preSeconds.getValue();
			genSet.postSeconds = postSeconds.getValue();
			genSet.clipPrefix = fileInitials.getText();
			genSet.useDataBudget = budgetOn.isSelected();
			if (genSet.useDataBudget) {
				genSet.dataBudget = (int) (dataBudget.getValue() * 1024.);
				genSet.budgetPeriodHours = budgetPeriod.getValue();
			}
			genSet.mapLineLength = mapLineLength.getValue();
			return genSet;
		}
	}

}
