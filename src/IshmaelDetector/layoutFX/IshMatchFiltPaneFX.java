package IshmaelDetector.layoutFX;

import IshmaelDetector.IshDetParams;
import IshmaelDetector.MatchFiltControl;
import PamController.SettingsPane;
import PamDetection.RawDataUnit;

/**
 * JavaFX settings pane for the Ishmael matched filter detector. The matched
 * filter works on raw data rather than FFT data.
 * 
 * @author Jamie Macaulay
 *
 */
public class IshMatchFiltPaneFX extends IshPaneFX {

	/**
	 * The matched filter specific settings.
	 */
	private MatchFiltPane matchFiltPane;

	public IshMatchFiltPaneFX(MatchFiltControl matchFiltControl) {
		super(matchFiltControl, RawDataUnit.class, "Raw Data Source");
	}

	@Override
	public SettingsPane<IshDetParams> getDetectorPane() {
		if (matchFiltPane == null) {
			matchFiltPane = new MatchFiltPane();
		}
		return matchFiltPane;
	}

	@Override
	public String getHelpPoint() {
		return "detectors.ishmael.docs.ishmael_matchedfilter";
	}

	@Override
	public String getName() {
		return "Matched Filter Parameters";
	}

}
