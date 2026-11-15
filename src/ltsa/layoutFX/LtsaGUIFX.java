package ltsa.layoutFX;

import PamController.SettingsPane;
import ltsa.LtsaControl;
import ltsa.LtsaParameters;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for the Long Term Spectral Average. The LTSA data are shown in the FX 
 * Time Display as a spectrogram. 
 */
public class LtsaGUIFX extends PamControlledGUIFX {

	private LtsaControl ltsaControl;

	private LtsaSettingsPaneFX settingsPane;

	public LtsaGUIFX(LtsaControl ltsaControl) {
		this.ltsaControl = ltsaControl;
	}

	@Override
	public SettingsPane<LtsaParameters> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new LtsaSettingsPaneFX(ltsaControl);
		}
		settingsPane.setParams(ltsaControl.getLtsaParameters());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		LtsaParameters newParams = settingsPane.getParams(ltsaControl.getLtsaParameters());
		if (newParams != null) {
			ltsaControl.setLtsaParameters(newParams);
		}
	}

}
