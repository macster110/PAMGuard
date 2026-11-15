package NMEA.layoutFX;

import java.util.ArrayList;

import NMEA.NMEAControl;
import NMEA.NMEAParameters;
import PamController.SettingsPane;
import pamViewFX.PamControlledGUIFX;
import userDisplayFX.UserDisplayNodeFX;

/**
 * JavaFX GUI for NMEA data collection: settings for the NMEA source and a display of 
 * the NMEA strings being received. 
 */
public class NMEAGUIFX extends PamControlledGUIFX {

	private NMEAControl nmeaControl;

	private NMEASettingsPaneFX settingsPane;

	private ArrayList<UserDisplayNodeFX> displays;

	public NMEAGUIFX(NMEAControl nmeaControl) {
		this.nmeaControl = nmeaControl;
	}

	@Override
	public SettingsPane<NMEAParameters> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new NMEASettingsPaneFX(nmeaControl);
		}
		settingsPane.setParams(nmeaControl.getNmeaParameters());
		return settingsPane;
	}

	@Override
	public ArrayList<UserDisplayNodeFX> getDisplays() {
		if (displays == null) {
			displays = new ArrayList<UserDisplayNodeFX>();
			displays.add(new NMEAStringsDisplayFX(nmeaControl));
		}
		return displays;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		NMEAParameters newParams = settingsPane.getParams(nmeaControl.getNmeaParameters());
		if (newParams != null) {
			nmeaControl.setNmeaParameters(newParams);
			// restart the NMEA source with the new settings. 
			nmeaControl.getAcquireNmeaData().startNmeaSource();
		}
	}

}
