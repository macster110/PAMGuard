package IshmaelLocator.layoutFX;

import IshmaelLocator.IshLocControl;
import IshmaelLocator.IshLocParams;
import PamController.SettingsPane;
import pamViewFX.PamControlledGUIFX;

/**
 * JavaFX GUI for the Ishmael locator.
 * 
 * @author Jamie Macaulay
 *
 */
public class IshLocGUIFX extends PamControlledGUIFX {

	private IshLocControl ishLocControl;

	private IshLocPaneFX settingsPane;

	public IshLocGUIFX(IshLocControl ishLocControl) {
		this.ishLocControl = ishLocControl;
	}

	@Override
	public SettingsPane<IshLocParams> getSettingsPane() {
		if (settingsPane == null) {
			settingsPane = new IshLocPaneFX(ishLocControl);
		}
		settingsPane.setParams(ishLocControl.getIshLocParams());
		return settingsPane;
	}

	@Override
	public void updateParams() {
		if (settingsPane == null) return;
		IshLocParams newParams = settingsPane.getParams(ishLocControl.getIshLocParams());
		if (newParams != null) {
			ishLocControl.setIshLocParams(newParams);
		}
	}

}
