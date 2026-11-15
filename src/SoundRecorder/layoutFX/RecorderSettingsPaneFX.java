package SoundRecorder.layoutFX;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import Acquisition.AcquisitionProcess;
import Acquisition.DaqSystem;
import PamController.PamController;
import PamController.SettingsPane;
import PamModel.SMRUEnable;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamRawDataBlock;
import PamDetection.RawDataUnit;
import SoundRecorder.RecorderControl;
import SoundRecorder.RecorderSettings;
import SoundRecorder.RecorderView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.SelectFolderFX;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;

/**
 * JavaFX settings for the sound recorder. Holds the same options as the Swing 
 * {@link SoundRecorder.RecorderSettingsDialog}. The channels to record are also 
 * chosen here, rather than on the recorder's display as in the Swing GUI. 
 */
public class RecorderSettingsPaneFX extends SettingsPane<RecorderSettings> {

	private RecorderControl recorderControl;

	private PamBorderPane mainPane;

	private SourcePaneFX sourcePane;

	private RadioButton startIdle, startRecording, startCycle, startAsLast;

	private CheckBox enableBuffer;

	private TextField bufferLength;

	private TextField autoDuration, autoInterval;

	private SelectFolderFX selectFolder;

	private TextField fileInitials;

	private ComboBox<AudioFileFormat.Type> fileType;

	private ComboBox<Integer> bitDepth;

	private CheckBox limitSeconds, limitMegaBytes, roundFileStarts;

	private TextField maxSeconds, maxMegaBytes;

	public RecorderSettingsPaneFX(RecorderControl recorderControl) {
		super(null);
		this.recorderControl = recorderControl;
		TabPane tabPane = new TabPane();
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabPane.getTabs().add(new Tab("Control", createControlPane()));
		tabPane.getTabs().add(new Tab("Files and Folders", createFilesPane()));
		mainPane = new PamBorderPane(tabPane);
	}

	private static Label title(String text) {
		Label label = new Label(text);
		PamGuiManagerFX.titleFont2style(label);
		label.setPadding(new Insets(8, 0, 0, 0));
		return label;
	}

	private static TextField numberField(String tip) {
		TextField field = new TextField();
		field.setPrefColumnCount(5);
		field.setTooltip(new Tooltip(tip));
		return field;
	}

	private Node createControlPane() {
		PamVBox holder = new PamVBox();
		holder.setSpacing(5);
		holder.setPadding(new Insets(5));

		sourcePane = new SourcePaneFX(RawDataUnit.class, true, true);

		enableBuffer = new CheckBox("Enable buffer, length");
		bufferLength = numberField("Seconds of data kept in memory, which can be added to the start of a recording");
		HBox bufferBox = new HBox(5, enableBuffer, bufferLength, new Label("s"));
		bufferBox.setAlignment(Pos.CENTER_LEFT);
		bufferLength.disableProperty().bind(enableBuffer.selectedProperty().not());

		startIdle = new RadioButton("Remain idle");
		startRecording = new RadioButton("Start recording");
		startCycle = new RadioButton("Start recording cycle");
		startAsLast = new RadioButton("Return to the state the recorder was in when PAMGuard last stopped");
		ToggleGroup group = new ToggleGroup();
		for (RadioButton b : new RadioButton[] { startIdle, startRecording, startCycle, startAsLast }) {
			b.setToggleGroup(group);
		}

		autoDuration = numberField("The duration of automatic recordings in seconds");
		autoInterval = numberField("The time from the start of one recording to the start of the next, "
				+ "including the recording itself");
		GridPane autoGrid = new GridPane();
		autoGrid.setHgap(5);
		autoGrid.setVgap(5);
		autoGrid.add(new Label("Recording length"), 0, 0);
		autoGrid.add(autoDuration, 1, 0);
		autoGrid.add(new Label("s"), 2, 0);
		autoGrid.add(new Label("Total cycle time"), 0, 1);
		autoGrid.add(autoInterval, 1, 1);
		autoGrid.add(new Label("s"), 2, 1);

		holder.getChildren().addAll(title("Raw data source"), sourcePane, bufferBox,
				title("When PAMGuard starts"), startIdle, startRecording, startCycle, startAsLast,
				title("Automatic recording cycle"), autoGrid);
		return holder;
	}

	private Node createFilesPane() {
		PamVBox holder = new PamVBox();
		holder.setSpacing(5);
		holder.setPadding(new Insets(5));

		selectFolder = new SelectFolderFX("Output folder", 30, true);

		fileInitials = new TextField();
		fileInitials.setPrefColumnCount(6);
		fileInitials.setTooltip(new Tooltip("Characters at the start of each file name"));
		fileType = new ComboBox<AudioFileFormat.Type>();
		fileType.setTooltip(new Tooltip("Recording format. SUD writes X3 compressed SoundTrap files (always 16 bit) "
				+ "which PAMGuard can read back."));
		bitDepth = new ComboBox<Integer>();
		for (int depth : RecorderSettings.BITDEPTHS) {
			bitDepth.getItems().add(depth);
		}
		bitDepth.setTooltip(new Tooltip("There is no point making 24 bit files from 16 bit sound card input"));

		GridPane formatGrid = new GridPane();
		formatGrid.setHgap(5);
		formatGrid.setVgap(5);
		formatGrid.add(new Label("File name prefix"), 0, 0);
		formatGrid.add(fileInitials, 1, 0);
		formatGrid.add(new Label("File type"), 0, 1);
		formatGrid.add(fileType, 1, 1);
		formatGrid.add(new Label("Bit depth"), 0, 2);
		formatGrid.add(bitDepth, 1, 2);
		Label nameInfo = new Label("File names automatically contain the date as YYYYMMDD_HHMMSS_mmm");
		nameInfo.setWrapText(true);

		limitSeconds = new CheckBox("Limit file length to");
		maxSeconds = numberField("Short files are easier to process, but many short files can slow archiving");
		roundFileStarts = new CheckBox("Round file start times");
		roundFileStarts.setTooltip(new Tooltip("File start times will be rounded to fixed times"));
		limitMegaBytes = new CheckBox("Limit file size to");
		maxMegaBytes = numberField("Files should always be smaller than 2000 megabytes");
		maxSeconds.disableProperty().bind(limitSeconds.selectedProperty().not());
		roundFileStarts.disableProperty().bind(limitSeconds.selectedProperty().not());
		maxMegaBytes.disableProperty().bind(limitMegaBytes.selectedProperty().not());
		GridPane lengthGrid = new GridPane();
		lengthGrid.setHgap(5);
		lengthGrid.setVgap(5);
		lengthGrid.add(limitSeconds, 0, 0);
		lengthGrid.add(maxSeconds, 1, 0);
		lengthGrid.add(new Label("seconds"), 2, 0);
		lengthGrid.add(roundFileStarts, 3, 0);
		lengthGrid.add(limitMegaBytes, 0, 1);
		lengthGrid.add(maxMegaBytes, 1, 1);
		lengthGrid.add(new Label("megabytes"), 2, 1);

		holder.getChildren().addAll(title("Output folder"), selectFolder, title("File names and format"), formatGrid, nameInfo, 
				title("Maximum file lengths"), lengthGrid);
		return holder;
	}

	@Override
	public void setParams(RecorderSettings input) {
		sourcePane.setSourceList();
		sourcePane.setSource(input.rawDataSource);
		PamDataBlock<?> source = sourcePane.getSource();
		sourcePane.setChannelList(input.getChannelBitmap(source == null ? 0xFFFFFFFF : source.getChannelMap()));

		enableBuffer.setSelected(input.enableBuffer);
		bufferLength.setText(String.format("%d", input.bufferLength));
		if (input.isAutoStart()) {
			startAsLast.setSelected(true);
		}
		else if (input.startStatus == RecorderView.BUTTON_START) {
			startRecording.setSelected(true);
		}
		else if (input.startStatus == RecorderView.BUTTON_AUTO) {
			startCycle.setSelected(true);
		}
		else {
			startIdle.setSelected(true);
		}
		autoDuration.setText(String.format("%d", input.autoDuration));
		autoInterval.setText(String.format("%d", input.autoInterval));

		selectFolder.setFolderName(input.outputFolder);
		selectFolder.setIncludeSubFolders(input.isDatedSubFolders());
		fileInitials.setText(input.fileInitials);
		fileType.getItems().clear();
		fileType.getItems().addAll(AudioSystem.getAudioFileTypes());
		fileType.getItems().add(RecorderControl.SUD);
		if (SMRUEnable.isEnableDecimus()) {
			fileType.getItems().add(RecorderControl.X3);
		}
		AudioFileFormat.Type type = input.getFileType();
		for (AudioFileFormat.Type t : fileType.getItems()) {
			if (type != null && t.toString().equals(type.toString())) {
				fileType.getSelectionModel().select(t);
			}
		}
		bitDepth.getSelectionModel().select(Integer.valueOf(input.getBitDepth()));
		limitSeconds.setSelected(input.isLimitLengthSeconds());
		limitMegaBytes.setSelected(input.isLimitLengthMegaBytes());
		maxSeconds.setText(String.format("%d", input.maxLengthSeconds));
		maxMegaBytes.setText(String.format("%d", input.maxLengthMegaBytes));
		roundFileStarts.setSelected(input.isRoundFileStarts());
	}

	private boolean warn(String message) {
		PamDialogFX.showWarning(null, getName(), message);
		return false;
	}

	@Override
	public RecorderSettings getParams(RecorderSettings currParams) {
		RecorderSettings settings = (currParams == null ? recorderControl.getRecorderSettings() : currParams).clone();

		PamDataBlock<?> source = sourcePane.getSource();
		if (source == null) {
			warn("Select a raw data source for the recorder.");
			return null;
		}
		settings.rawDataSource = source.getDataName();
		int channels = sourcePane.getChannelList();
		if (channels == 0) {
			warn("Select at least one channel to record.");
			return null;
		}
		settings.setChannelBitmap(channels);

		try {
			settings.enableBuffer = enableBuffer.isSelected();
			settings.bufferLength = Integer.valueOf(bufferLength.getText().trim());
			settings.autoDuration = Integer.valueOf(autoDuration.getText().trim());
			settings.autoInterval = Integer.valueOf(autoInterval.getText().trim());
			settings.maxLengthSeconds = Integer.valueOf(maxSeconds.getText().trim());
			settings.maxLengthMegaBytes = Integer.valueOf(maxMegaBytes.getText().trim());
		}
		catch (NumberFormatException e) {
			warn("The buffer length, recording cycle and file length values must be whole numbers.");
			return null;
		}
		if (settings.autoDuration <= 0) {
			warn("The duration of automatic recordings must be greater than 0 s.");
			return null;
		}
		if (settings.autoInterval <= settings.autoDuration) {
			warn("The total cycle time of automatic recordings must be longer than the recording length.");
			return null;
		}

		settings.setAutoStart(startAsLast.isSelected());
		if (startAsLast.isSelected()) {
			settings.startStatus = 0;
		}
		else if (startRecording.isSelected()) {
			settings.startStatus = RecorderView.BUTTON_START;
		}
		else if (startCycle.isSelected()) {
			settings.startStatus = RecorderView.BUTTON_AUTO;
		}
		else {
			settings.startStatus = RecorderView.BUTTON_OFF;
		}

		String folder = selectFolder.getFolderName(true);
		if (folder == null || folder.isEmpty()) {
			warn("Select a folder for the recordings.");
			return null;
		}
		settings.outputFolder = folder;
		settings.setDatedSubFolders(selectFolder.isIncludeSubFolders());
		settings.fileInitials = fileInitials.getText();
		AudioFileFormat.Type type = fileType.getSelectionModel().getSelectedItem();
		if (type == null) {
			warn("Select a file type for the recordings.");
			return null;
		}
		settings.setFileType(type);
		Integer depth = bitDepth.getSelectionModel().getSelectedItem();
		if (depth == null) {
			warn("Select a bit depth for the recordings.");
			return null;
		}
		settings.setBitDepth(depth);
		settings.setLimitLengthSeconds(limitSeconds.isSelected());
		settings.setLimitLengthMegaBytes(limitMegaBytes.isSelected());
		settings.setRoundFileStarts(roundFileStarts.isSelected());

		if (!checkBitDepth(settings)) {
			return null;
		}
		return settings;
	}

	/**
	 * Check the bit depth of the recordings against the bit depth of the acquisition system, 
	 * and ask the user if they want to carry on if they are different. 
	 */
	private boolean checkBitDepth(RecorderSettings settings) {
		try {
			PamRawDataBlock rawBlock = PamController.getInstance().getRawDataBlock(settings.rawDataSource);
			if (rawBlock == null || rawBlock.getSourceDataBlock() == null) return true;
			if (!(rawBlock.getSourceDataBlock().getParentProcess() instanceof AcquisitionProcess)) return true;
			AcquisitionProcess daqProcess = (AcquisitionProcess) rawBlock.getSourceDataBlock().getParentProcess();
			DaqSystem daqSystem = daqProcess.getAcquisitionControl().findDaqSystem(null);
			if (daqSystem == null) return true;
			int daqBits = daqSystem.getSampleBits();
			if (daqBits == settings.getBitDepth() || daqBits <= 0) return true;
			String msg = daqBits > settings.getBitDepth() ?
					String.format("The sound acquisition system has %d bits but the recorder is set to %d bits, "
							+ "so the recordings will lose quality. Do you want to use these settings?", daqBits, settings.getBitDepth()) :
					String.format("The sound acquisition system has %d bits and the recorder is set to %d bits. This makes "
							+ "larger files with no better quality. Do you want to use these settings?", daqBits, settings.getBitDepth());
			Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
			alert.setTitle(getName());
			alert.setHeaderText(null);
			return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
		}
		catch (Exception e) {
			e.printStackTrace();
			return true;
		}
	}

	@Override
	public String getName() {
		return "Sound Recorder Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
