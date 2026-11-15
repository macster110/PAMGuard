package GPS.layoutFX;

import GPS.GPSControl;
import GPS.GPSParameters;
import PamController.SettingsPane;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for GPS processing. GPS data are drawn on the FX map. 
 */
public class GPSGUIFX extends PamControlledGUIFX {

	private GPSControl gpsControl;

	private GPSSettingsPaneFX settingsPane;

	public GPSGUIFX(GPSControl gpsControl) {
		this.gpsControl = gpsControl;
	}

	@Override
	public SettingsPane<GPSParameters> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new GPSSettingsPaneFX(gpsControl);
		}
		settingsPane.setParams(gpsControl.getGpsParameters());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		GPSParameters newParams = settingsPane.getParams(gpsControl.getGpsParameters());
		if (newParams != null) {
			gpsControl.setGpsParameters(newParams);
		}
	}

}
