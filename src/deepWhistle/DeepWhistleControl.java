package deepWhistle;

import java.awt.Frame;
import java.io.Serializable;

import javax.swing.JMenuItem;

import PamController.PamControlledUnitGUI;
import PamController.PamControlledUnitSettings;
import PamController.PamGUIManager;
import deepWhistle.layoutFX.DeepWhistleGUIFX;
import PamController.PamSettingManager;
import PamController.SettingsPane;
import dataPlots.data.TDDataProviderRegister;
import dataPlotsFX.data.TDDataProviderRegisterFX;
import dataPlotsFX.spectrogramPlotFX.FFTPlotProvider;
import fftManager.newSpectrogram.SpectrogramPlotProvider;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX2AWT;


/**
 * Control class for DeepWhistle module 
 * 
 * @author Jamie Macaulay
 */
public class DeepWhistleControl extends  MaskedFFTControl {

	public static String unitType = "Deep Whistle Mask";

	private MaskedFFTProcess process;

	private PamDialogFX2AWT<MaskedFFTParamters> settingsDialog;

	private DeepWhistleSettingsPane settingsPane;

	private DeepWhistleParamters parameters = new DeepWhistleParamters();

	public DeepWhistleControl(String unitName) {
		super(unitType, unitName);
		process = new DeepWhistleProcess(this);
		addPamProcess(process);
		PamSettingManager.getInstance().registerSettings(this);
		updateParams( parameters);
		
		TDDataProviderRegisterFX.getInstance().registerDataInfo(new FFTPlotProvider(this, process.getMaskedFFTDataBlock()));

	}


	@Override
	public Serializable getSettingsReference() {
		return parameters;
	}

	@Override
	public long getSettingsVersion() {
		return MaskedFFTParamters.serialVersionUID;
	}

	@Override
	public boolean restoreSettings(PamControlledUnitSettings pamControlledUnitSettings) {
		parameters = (DeepWhistleParamters) pamControlledUnitSettings.getSettings();
		return true;
	}

	@Override
	public JMenuItem createDetectionMenu(Frame parentFrame) {
		JMenuItem menuItem = new JMenuItem(this.getUnitName() + " settings ...");
		menuItem.addActionListener((e) -> showSettings(parentFrame));
		return menuItem;
	}

	protected void showSettings(Frame parentFrame) {
		// For the initial implementation we don't provide an AWT dialog.
		// A JavaFX settings pane will be implemented separately; this method can
		// be updated later to open an AWT dialog that wraps the FX pane if needed.
		showSettingsDialog( parentFrame);
	}

	public MaskedFFTParamters getDeepWhistleParameters() {
		return parameters;
	}

	//----------------- GUI Stuff ----------------//

	/**
	 * Get the settings pane.
	 * 
	 * @return the settings pane.
	 */
	public DeepWhistleSettingsPane getSettingsPane() {

		if (this.settingsPane == null) {
			settingsPane = new DeepWhistleSettingsPane(this);
		}

		return settingsPane;
	}

	/**
	 * Show settings dialog.
	 * 
	 * @param parentFrame - the frame.
	 */
	public void showSettingsDialog(Frame parentFrame) {
		if (settingsDialog == null || parentFrame != settingsDialog.getOwner()) {
			SettingsPane<MaskedFFTParamters> setPane = (SettingsPane<MaskedFFTParamters>) getSettingsPane();
			//setPane.setParams(this.rawDLParmas);
			settingsDialog = new PamDialogFX2AWT<MaskedFFTParamters>(parentFrame, setPane, false);
			settingsDialog.setHelpPoint("detectors.deepWhistleHelp.docs.deepWhistle");
			settingsDialog.setResizable(true);
		}
		MaskedFFTParamters newParams = settingsDialog.showDialog(parameters); 

		System.out.println("DeepWhistleControl: showSettingsDialog: newParams = " + newParams);

		// if cancel button is pressed then new params will be null.
		if (newParams != null) {
			updateParams(newParams);
		}
	}

	/**
	 * Called whenever there are new parameters. 
	 * @param newParams
	 */
	private void updateParams(MaskedFFTParamters newParams) {
		System.out.println("DeepWhistleControl: update params = " + newParams);

		this.process.prepareProcess();
	}


	@Override
	public MaskedFFTParamters getParameters() {
		return parameters;
	}


	@Override
	public void setParameters(MaskedFFTParamters parameters) {
		if (parameters instanceof DeepWhistleParamters) {
			this.parameters = (DeepWhistleParamters) parameters;
		}
		updateParams(this.parameters);
	}

	/**
	 * Get the process which applies the mask to FFT data. 
	 * @return the masked FFT process. 
	 */
	public MaskedFFTProcess getMaskedFFTProcess() {
		return process;
	}

	@Override
	public PamControlledUnitGUI getGUI(int flag) {
		if (flag == PamGUIManager.FX) {
			if (deepWhistleGUIFX == null) {
				deepWhistleGUIFX = new DeepWhistleGUIFX(this);
			}
			return deepWhistleGUIFX;
		}
		return null;
	}

	@Override
	public void notifyModelChanged(int changeType) {
		super.notifyModelChanged(changeType);
		if (deepWhistleGUIFX != null) deepWhistleGUIFX.notifyGUIChange(changeType);
	}

	/**
	 * The JavaFX GUI. 
	 */
	private DeepWhistleGUIFX deepWhistleGUIFX;


}
