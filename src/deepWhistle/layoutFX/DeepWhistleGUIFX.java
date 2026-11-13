package deepWhistle.layoutFX;

import PamController.PamControllerInterface;
import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import deepWhistle.DeepWhistleControl;
import deepWhistle.MaskedFFTParamters;
import pamViewFX.PamControlledGUIFX;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;

/**
 * JavaFX GUI for the Deep Whistle module. The settings pane is the same JavaFX
 * pane the Swing GUI shows inside its settings dialog.
 * 
 * @author Jamie Macaulay
 *
 */
public class DeepWhistleGUIFX extends PamControlledGUIFX {

	private DeepWhistleControl deepWhistleControl;

	public DeepWhistleGUIFX(DeepWhistleControl deepWhistleControl) {
		this.deepWhistleControl = deepWhistleControl;
	}

	@Override
	public SettingsPane<MaskedFFTParamters> getSettingsPane() {
		SettingsPane<MaskedFFTParamters> settingsPane = deepWhistleControl.getSettingsPane();
		settingsPane.setParams(deepWhistleControl.getParameters());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		MaskedFFTParamters newParams = deepWhistleControl.getSettingsPane().getParams(deepWhistleControl.getParameters());
		if (newParams != null) {
			try {
				deepWhistleControl.setParameters(newParams);
			}
			catch (Exception e) {
				// e.g. the deep learning engine could not be loaded. 
				e.printStackTrace();
				PamDialogFX.showWarning("Deep Whistle could not load its model: " + e.getMessage());
			}
		}
	}

	@Override
	public void notifyGUIChange(int changeType) {
		switch (changeType) {
		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			// the data source may have been changed by the JavaFX data model, so make sure the parameters match.
			PamDataBlock<?> parent = deepWhistleControl.getMaskedFFTProcess().getParentDataBlock();
			MaskedFFTParamters params = deepWhistleControl.getParameters();
			if (parent != null && params != null && !parent.getLongDataName().equals(params.dataSourceName)) {
				params.dataSourceName = parent.getLongDataName();
				deepWhistleControl.getMaskedFFTProcess().prepareProcess();
			}
			break;
		}
	}

}
