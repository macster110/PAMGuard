package rawDeepLearningClassifier.dlClassification.contTransforms;

import org.jamdev.jdl4pam.transforms.DLTransform.DLTransformType;
import org.json.JSONObject;

/**
 * The continuous parameters for one transform in a deep learning model. These are
 * read from the optional "contparams" object alongside "params" in a model's JSON
 * metadata, e.g.
 *
 * <pre>
 * {"name": "median_equalizer", "params": {"window_duration": 60.0}, "contparams": {"timewindow": 60.0}}
 * </pre>
 *
 * A transform with contparams is calculated using a longer time window of data than
 * the segment, e.g. a 60 second running median. The window is timewindow seconds long
 * and ends lookahead seconds after the segment, i.e. it uses (timewindow - lookahead)
 * seconds of data before the segment and lookahead seconds after it. A lookahead means
 * results are delayed by that amount. Older versions of PAMGuard ignore contparams and
 * calculate the transform on each segment.
 *
 * @author Jamie Macaulay
 */
public class ContTransformParams {

	/**
	 * The JSON key for continuous parameters.
	 */
	public static final String CONT_PARAMS_KEY = "contparams";

	/**
	 * The JSON key for the time window in seconds.
	 */
	public static final String TIME_WINDOW_KEY = "timewindow";

	/**
	 * The JSON key for the look ahead in seconds.
	 */
	public static final String LOOKAHEAD_KEY = "lookahead";

	/**
	 * The index of the transform in the model's transform list.
	 */
	public final int transformIndex;

	/**
	 * The transform type.
	 */
	public final DLTransformType transformType;

	/**
	 * The length of the window of data used to calculate the transform in seconds, including the lookahead.
	 */
	public final double timeWindow;

	/**
	 * The length of data after the segment used to calculate the transform in seconds.
	 * Results are delayed by this amount. Zero for a trailing window.
	 */
	public final double lookahead;

	/**
	 * The raw contparams JSON object (may contain transform specific settings).
	 */
	public final JSONObject json;

	public ContTransformParams(int transformIndex, DLTransformType transformType, double timeWindow, double lookahead, JSONObject json) {
		this.transformIndex = transformIndex;
		this.transformType = transformType;
		this.timeWindow = timeWindow;
		this.lookahead = lookahead;
		this.json = json;
	}

	/**
	 * Get the length of data before the segment used to calculate the transform.
	 *
	 * @return the time window minus the lookahead in seconds.
	 */
	public double getPreWindow() {
		return Math.max(0, timeWindow - lookahead);
	}

	@Override
	public String toString() {
		return "ContTransformParams [transform " + transformIndex + " " + transformType + ", timewindow=" + timeWindow
				+ " s, lookahead=" + lookahead + " s]";
	}

}
