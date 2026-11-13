package IshmaelDetector.layoutFX;

import IshmaelDetector.IshDetControl;
import IshmaelDetector.IshDetParams;
import PamController.PamControllerInterface;
import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for the Ishmael detectors (energy sum, spectrogram correlation
 * and matched filter). Each detector supplies its own settings pane via
 * {@link IshDetControl#createSettingsPaneFX()}.
 *
 * @author Jamie Macaulay
 *
 */
public class IshGUIFX extends PamControlledGUIFX {

	/**
	 * Reference to the Ishmael detector control.
	 */
	private IshDetControl ishDetControl;

	/**
	 * The settings pane.
	 */
	private IshPaneFX settingsPane;

	public IshGUIFX(IshDetControl ishDetControl) {
		this.ishDetControl = ishDetControl;
	}

	@Override
	public SettingsPane<IshDetParams> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = ishDetControl.createSettingsPaneFX();
		}
		// make sure any data blocks added since the pane was created are in the source list.
		settingsPane.getSourcePane().setSourceList();
		settingsPane.setParams(ishDetControl.getIshDetectionParams());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		IshDetParams newParams = settingsPane.getParams(ishDetControl.getIshDetectionParams());
		if (newParams != null) {
			ishDetControl.setIshDetParams(newParams);
		}
	}

	@Override
	public void notifyGUIChange(int changeType) {
		switch (changeType) {
		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			// the data source may have been changed by the JavaFX data model, so make sure the parameters match.
			PamDataBlock<?> parent = ishDetControl.getIshDetFnProcess().getParentDataBlock();
			if (parent != null && ishDetControl.getIshDetectionParams() != null) {
				ishDetControl.getIshDetectionParams().groupedSourceParmas.setDataSource(parent.getLongDataName());
			}
			break;
		}
	}

}
