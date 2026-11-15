package SoundRecorder.layoutFX;

import java.util.ArrayList;

import PamController.SettingsPane;
import SoundRecorder.RecorderControl;
import SoundRecorder.RecorderSettings;
import javafx.scene.layout.Pane;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for the sound recorder: a settings pane and recording controls in the side pane. 
 */
public class RecorderGUIFX extends PamControlledGUIFX {

	private RecorderControl recorderControl;

	private RecorderSettingsPaneFX settingsPane;

	private RecorderSidePaneFX sidePane;

	public RecorderGUIFX(RecorderControl recorderControl) {
		this.recorderControl = recorderControl;
	}

	@Override
	public SettingsPane<RecorderSettings> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new RecorderSettingsPaneFX(recorderControl);
		}
		settingsPane.setParams(recorderControl.getRecorderSettings());
		return settingsPane;
	}

	@Override
	public ArrayList<Pane> getSidePanes() {
		if (sidePane == null) {
			sidePane = new RecorderSidePaneFX(recorderControl);
		}
		ArrayList<Pane> panes = new ArrayList<Pane>();
		panes.add(sidePane);
		return panes;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		RecorderSettings newSettings = settingsPane.getParams(recorderControl.getRecorderSettings());
		if (newSettings != null) {
			recorderControl.setRecorderSettings(newSettings);
		}
	}

}
