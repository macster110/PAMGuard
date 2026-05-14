package soundtrapSensor;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;

import javax.swing.JMenu;
import javax.swing.JMenuItem;


import PamController.PamControlledUnit;
import PamController.PamControlledUnitSettings;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamView.GeneralProjector.ParameterType;
import PamView.GeneralProjector.ParameterUnits;
import dataPlotsFX.data.TDDataProviderRegisterFX;
import soundtrapSensor.plot.SudSensorPlotProviderFX;

/**
 * Main PAMGuard controlled unit for the SudSensor module. This module
 * subscribes to SWV chunks from SoundTrap SUD files and extracts
 * magnetometer/accelerometer data, computes heading, pitch and roll via a
 * Mahony sensor-fusion filter, and stores the results to binary files.
 *
 * @author PAMGuard
 */
public class SudSensorControl extends PamControlledUnit implements PamSettings {

    public static final String UNIT_TYPE = "SoundTrap Sensor";

    private SudSensorParams params = new SudSensorParams();
    private final SudSensorProcess sensorProcess;
    private final SudSensorSWVHandler swvHandler;

    public SudSensorControl(String unitName) {
        super(UNIT_TYPE, unitName);

        addPamProcess(sensorProcess = new SudSensorProcess(this));
        swvHandler = new SudSensorSWVHandler(this);

        TDDataProviderRegisterFX.getInstance().registerDataInfo(
                new SudSensorPlotProviderFX(this, sensorProcess.getSensorDataBlock()));

        // Subscribe immediately so that the handler is in place before
        // acquisition starts
        swvHandler.subscribeSUD();

        if (!isViewer) {
            setSidePanel(new SudSensorSidePanel(this));
        }

        PamSettingManager.getInstance().registerSettings(this);
    }

    public SudSensorParams getParams() {
        return params;
    }

    public SudSensorProcess getSensorProcess() {
        return sensorProcess;
    }

    public SudSensorSWVHandler getSWVHandler() {
        return swvHandler;
    }

    // -----------------------------------------------------------------
    // Menu
    // -----------------------------------------------------------------

    @Override
    public JMenuItem createDetectionMenu(Frame parentFrame) {
    	JMenuItem settingsMenu;
		settingsMenu = new JMenuItem(getUnitName() + " ...");
		
		settingsMenu.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSettings(parentFrame);
            }
        });
        return settingsMenu;
    }

    private void showSettings(Frame parentFrame) {
        SudSensorParams newParams = SudSensorDialog.showDialog(parentFrame, this);
        if (newParams != null) {
            params = newParams;
        }
    }

    // -----------------------------------------------------------------
    // PamSettings implementation
    // -----------------------------------------------------------------

    @Override
    public Serializable getSettingsReference() {
        return params;
    }

    @Override
    public long getSettingsVersion() {
        return SudSensorParams.serialVersionUID;
    }

    @Override
    public boolean restoreSettings(PamControlledUnitSettings pamControlledUnitSettings) {
        Object obj = pamControlledUnitSettings.getSettings();
        if (obj instanceof SudSensorParams) {
            params = ((SudSensorParams) obj).clone();
            return true;
        }
        return false;
    }
    

}
