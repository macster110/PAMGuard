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
 * the segment, e.g. a 60 second running median. The window is trailing i.e. it uses
 * the timewindow seconds of data before (and including) the segment. Older versions
 * of PAMGuard ignore contparams and calculate the transform on each segment.
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
	 * The length of the trailing window of data used to calculate the transform in seconds.
	 */
	public final double timeWindow;

	/**
	 * The length of data after the segment used to calculate the transform in seconds.
	 * Look ahead models are not currently supported by PAMGuard so this is always treated
	 * as zero but is kept so the JSON format can support it in future.
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

	@Override
	public String toString() {
		return "ContTransformParams [transform " + transformIndex + " " + transformType + ", timewindow=" + timeWindow
				+ " s, lookahead=" + lookahead + " s]";
	}

}
