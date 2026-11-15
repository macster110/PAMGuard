package SoundRecorder.layoutFX;

import java.io.File;

import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import SoundRecorder.RecorderControl;
import SoundRecorder.RecorderView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamVBox;

/**
 * JavaFX controls for the sound recorder, shown in the side pane. The same buttons as the 
 * Swing side panel: Off, Auto (duty cycle), Continuous, and Continuous with the buffer 
 * added to the start, plus the name of the file being written. 
 */
public class RecorderSidePaneFX extends PamVBox implements RecorderView {

	private RecorderControl recorderControl;

	private Label title;

	private Label status;

	private ToggleButton buttonOff, buttonAuto, buttonStart, buttonStartBuffered;

	public RecorderSidePaneFX(RecorderControl recorderControl) {
		this.recorderControl = recorderControl;
		this.setSpacing(5);
		this.setPadding(new Insets(5));

		title = new Label(recorderControl.getUnitName());
		PamGuiManagerFX.titleFont2style(title);

		status = new Label("Recorder idle");
		status.setMaxWidth(Double.MAX_VALUE);
		status.setGraphic(PamGlyphDude.createPamIcon("mdi2r-record-circle-outline", 16));

		buttonOff = makeButton("Off", "Stop recording", BUTTON_OFF);
		buttonAuto = makeButton("Auto", "Record on the automatic duty cycle set in the settings", BUTTON_AUTO);
		buttonStart = makeButton("Continuous", "Record continuously", BUTTON_START);
		buttonStartBuffered = makeButton("Cont. + buffer", "Record continuously, starting with the data in the buffer", 
				BUTTON_START_BUFFERED);
		ToggleGroup group = new ToggleGroup();
		for (ToggleButton b : new ToggleButton[] { buttonOff, buttonAuto, buttonStart, buttonStartBuffered }) {
			b.setToggleGroup(group);
		}
		// the recorder is always in one of the states, so don't let the user unselect a button. 
		group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal == null && oldVal != null) {
				oldVal.setSelected(true);
			}
		});

		GridPane grid = new GridPane();
		grid.setHgap(5);
		grid.setVgap(5);
		grid.add(buttonOff, 0, 0);
		grid.add(buttonStart, 1, 0);
		grid.add(buttonAuto, 0, 1);
		grid.add(buttonStartBuffered, 1, 1);
		for (ToggleButton b : new ToggleButton[] { buttonOff, buttonAuto, buttonStart, buttonStartBuffered }) {
			b.setMaxWidth(Double.MAX_VALUE);
			GridPane.setHgrow(b, Priority.ALWAYS);
		}

		this.getChildren().addAll(title, status, grid);
		setButtonStates(recorderControl.getPressedButton());
		enableRecording(recorderControl.isRecordingEnabled());
		recorderControl.addRecorderView(this);
	}

	private ToggleButton makeButton(String text, String tip, int command) {
		ToggleButton button = new ToggleButton(text);
		button.setTooltip(new Tooltip(tip));
		button.setOnAction(e -> recorderControl.pressButton(command));
		return button;
	}

	/**
	 * Run on the JavaFX thread. The recorder calls its views from its Swing timer. 
	 */
	private static void onFX(Runnable r) {
		if (Platform.isFxApplicationThread()) {
			r.run();
		}
		else {
			Platform.runLater(r);
		}
	}

	@Override
	public void newParams() {
		onFX(() -> title.setText(recorderControl.getUnitName()));
	}

	@Override
	public void newData(PamDataBlock dataBlock, PamDataUnit dataUnit) {
	}

	@Override
	public void setButtonStates(int command) {
		onFX(() -> {
			buttonOff.setSelected(command == BUTTON_OFF);
			buttonAuto.setSelected(command == BUTTON_AUTO);
			buttonStart.setSelected(command == BUTTON_START);
			buttonStartBuffered.setSelected(command == BUTTON_START_BUFFERED);
		});
	}

	@Override
	public void sayStatus(String text) {
		String str;
		if (recorderControl.getRecorderStatus() == RecorderControl.RECORDING) {
			String fileName = recorderControl.getRecordingFileName();
			str = fileName == null ? "File closed" : "Recording " + new File(fileName).getName();
		}
		else {
			str = "Recorder idle";
		}
		boolean recording = recorderControl.getRecorderStatus() == RecorderControl.RECORDING;
		onFX(() -> {
			if (!str.equals(status.getText())) {
				status.setText(str);
				status.setStyle(recording ? "-fx-text-fill: #ff5050;" : "");
			}
		});
	}

	@Override
	public void enableRecording(boolean enable) {
		onFX(() -> {
			buttonAuto.setDisable(!enable);
			buttonStart.setDisable(!enable);
			buttonStartBuffered.setDisable(!enable);
		});
	}

	@Override
	public void enableRecordingControl(boolean enable) {
	}

}
