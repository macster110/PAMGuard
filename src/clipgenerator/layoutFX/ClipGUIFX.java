package clipgenerator.layoutFX;

import java.util.ArrayList;

import PamController.PamControllerInterface;
import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import clipgenerator.ClipControl;
import clipgenerator.ClipSettings;
import pamViewFX.PamControlledGUIFX;
import userDisplayFX.UserDisplayNodeFX;

/**
 * JavaFX GUI for the clip generator. Provides the settings pane and a JavaFX clip
 * display which shows the clips on a large zoomable canvas.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipGUIFX extends PamControlledGUIFX {

	private ClipControl clipControl;

	private ClipSettingsPaneFX settingsPane;

	private ArrayList<UserDisplayNodeFX> displays;

	private ClipDisplayFX clipDisplay;

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
	public ArrayList<UserDisplayNodeFX> getDisplays() {
		if (displays == null) {
			clipDisplay = new ClipDisplayFX(clipControl);
			displays = new ArrayList<UserDisplayNodeFX>();
			displays.add(clipDisplay);
		}
		return displays;
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
		if (clipDisplay != null) {
			clipDisplay.notifyModelChanged(changeType);
		}
	}

	/**
	 * @return the clip display, or null if it has not been created.
	 */
	public ClipDisplayFX getClipDisplay() {
		return clipDisplay;
	}

}
