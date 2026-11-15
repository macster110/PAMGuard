package clipgenerator.layoutFX;

import PamController.PamControllerInterface;
import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import clipgenerator.ClipControl;
import clipgenerator.ClipSettings;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for the clip generator. Provides the settings pane. Clips are shown
 * with the separate Clip Display module ({@link clipDisplayFX.ClipDisplayControlFX})
 * which can also show any other data that holds raw sound.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipGUIFX extends PamControlledGUIFX {

	private ClipControl clipControl;

	private ClipSettingsPaneFX settingsPane;

	public ClipGUIFX(ClipControl clipControl) {
		this.clipControl = clipControl;
	}

	@Override
	public SettingsPane<ClipSettings> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new ClipSettingsPaneFX(clipControl);
		}
		// detectors may have been added since the clip generator was created.
		clipControl.refreshTriggerBlocks();
		settingsPane.setParams(clipControl.getClipSettings());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) {
			return;
		}
		ClipSettings newSettings = settingsPane.getParams(clipControl.getClipSettings());
		if (newSettings != null) {
			clipControl.setClipSettings(newSettings);
		}
	}

	@Override
	public void notifyGUIChange(int changeType) {
		switch (changeType) {
		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			// the data source may have been changed by the JavaFX data model.
			PamDataBlock<?> parent = clipControl.getClipProcess().getParentDataBlock();
			ClipSettings settings = clipControl.getClipSettings();
			if (parent != null && settings != null && !parent.getLongDataName().equals(settings.dataSourceName)) {
				settings.dataSourceName = parent.getLongDataName();
			}
			break;
		}
	}

}
