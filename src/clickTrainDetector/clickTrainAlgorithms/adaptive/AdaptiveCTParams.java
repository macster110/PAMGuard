package clickTrainDetector.clickTrainAlgorithms.adaptive;

import java.io.Serializable;

import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterSet;
import PamModel.parametermanager.PamParameterSet.ParameterSetType;
import clickTrainDetector.clickTrainAlgorithms.mht.MHTChi2Params;
import clickTrainDetector.clickTrainAlgorithms.mht.MHTKernelParams;

/**
 * Parameters for the adaptive click train algorithm.
 * <p>
 * Unlike the standard MHT chi^2 algorithm, the adaptive algorithm does not
 * require a variance coefficient and minimum error value for every feature.
 * Instead it self-calibrates the expected jitter of each feature from the track
 * itself. As such only a handful of intuitive parameters are exposed to the
 * user.
 * <p>
 * Extends {@link MHTChi2Params} (which provides the {@code maxICI} field) so the
 * adaptive scorer can plug into the reused {@code MHTChi2Provider} machinery.
 *
 * @author Jamie Macaulay
 */
public class AdaptiveCTParams extends MHTChi2Params implements Serializable, Cloneable, ManagedParameters {

	public static final long serialVersionUID = 1L;

	/**
	 * Sensitivity of the algorithm between 0 (loose) and 1 (tight). Higher values
	 * make the algorithm stricter, i.e. clicks must conform more closely to the
	 * predicted feature values to be added to a train. This scales the minimum
	 * residual "floor" used for every feature.
	 */
	public double sensitivity = 0.5;

	/**
	 * The expected probability that a click in a train is detected (0-1). This
	 * controls how readily the algorithm bridges missed clicks (coasts). A high
	 * value (e.g. 0.9) assumes few clicks are missed and so coasting is penalised
	 * more heavily.
	 */
	public double detectionProb = 0.9;

	/* ---- which features the detector uses to group clicks ---- */

	/**
	 * Use inter-click-interval (ICI) consistency to group clicks. ICI is always
	 * used internally for coasting and rejecting over-long trains; this flag only
	 * controls whether ICI consistency contributes to the matching score.
	 */
	public boolean useICI = true;

	/**
	 * Use amplitude consistency to group clicks.
	 */
	public boolean useAmplitude = true;

	/**
	 * Use bearing consistency to group clicks. Automatically ignored when no
	 * bearing information is available.
	 */
	public boolean useBearing = true;

	/**
	 * Use waveform cross-correlation (similarity) to group clicks - and to refine
	 * the ICI measurement. Automatically ignored when no waveform data is
	 * available. More accurate but more processor intensive.
	 */
	public boolean useCorrelation = false;

	/**
	 * Parameters for the underlying MHT kernel (track search). These have sensible
	 * defaults and are not usually changed by the user.
	 */
	public MHTKernelParams mhtKernel = new MHTKernelParams();

	@Override
	public AdaptiveCTParams clone() {
		// MHTKernelParams.clone() is not visible from this package; the kernel params
		// are not edited via the settings pane so the shallow copy from super.clone()
		// is sufficient.
		return (AdaptiveCTParams) super.clone();
	}

	@Override
	public PamParameterSet getParameterSet() {
		return PamParameterSet.autoGenerate(this, ParameterSetType.DETECTOR);
	}

}
