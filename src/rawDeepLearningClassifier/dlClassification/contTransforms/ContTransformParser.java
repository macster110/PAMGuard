package rawDeepLearningClassifier.dlClassification.contTransforms;

import java.util.ArrayList;
import java.util.List;

import org.jamdev.jdl4pam.transforms.DLTransform;
import org.jamdev.jdl4pam.transforms.DLTransform.DLTransformType;
import org.jamdev.jdl4pam.transforms.jsonfile.DLTransformsParser;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads the optional "contparams" of each transform in a deep learning model's JSON
 * metadata and matches them to the model's transforms.
 * <p>
 * This is done in PAMGuard, not in jdl4pam, because the jdl4pam parsers ignore any
 * key in a transform apart from "name" and "params". This means older versions
 * of PAMGuard can still load models which use contparams.
 *
 * @author Jamie Macaulay
 */
public class ContTransformParser {

	/**
	 * Parse the continuous transform parameters from a model's JSON metadata.
	 *
	 * @param jsonString - the JSON metadata string.
	 * @param transforms - the transforms used by the model, created from the same metadata.
	 * @return a list of continuous parameters, in transform order. Empty if the model has no contparams.
	 */
	public static List<ContTransformParams> parse(String jsonString, List<DLTransform> transforms) {

		ArrayList<ContTransformParams> contParams = new ArrayList<ContTransformParams>();
		if (jsonString == null || transforms == null) return contParams;

		JSONArray jsonTransforms;
		try {
			jsonTransforms = findTransformsArray(new JSONObject(jsonString));
		}
		catch (Exception e) {
			e.printStackTrace();
			return contParams;
		}
		if (jsonTransforms == null) return contParams;

		for (int i = 0; i < jsonTransforms.length(); i++) {

			JSONObject jsonTransform = jsonTransforms.optJSONObject(i);
			if (jsonTransform == null || !jsonTransform.has(ContTransformParams.CONT_PARAMS_KEY)) continue;

			String name = jsonTransform.optString("name", null);
			DLTransformType type = name == null ? null : DLTransformsParser.getTransformType(name);
			if (type == null) {
				System.err.println("ContTransformParser: unknown transform with contparams: " + name);
				continue;
			}

			// find which occurrence of this transform type this is so it can be matched to the model's transforms.
			int occurrence = 0;
			for (int j = 0; j < i; j++) {
				JSONObject prev = jsonTransforms.optJSONObject(j);
				if (prev != null && type == DLTransformsParser.getTransformType(prev.optString("name", ""))) occurrence++;
			}

			int transformIndex = findTransform(transforms, type, occurrence);
			if (transformIndex < 0) {
				System.err.println("ContTransformParser: could not find the transform " + name + " in the model transforms - contparams are ignored.");
				continue;
			}

			JSONObject jsonContParams = jsonTransform.getJSONObject(ContTransformParams.CONT_PARAMS_KEY);

			double timeWindow = jsonContParams.optDouble(ContTransformParams.TIME_WINDOW_KEY, Double.NaN);
			if (Double.isNaN(timeWindow)) {
				// default to the transform's own window if it has one.
				JSONObject params = jsonTransform.optJSONObject("params");
				if (params != null) timeWindow = params.optDouble("window_duration", Double.NaN);
			}
			if (Double.isNaN(timeWindow) || timeWindow <= 0) {
				System.err.println("ContTransformParser: contparams for " + name + " must have a positive " + ContTransformParams.TIME_WINDOW_KEY + " - contparams are ignored.");
				continue;
			}

			double lookahead = jsonContParams.optDouble(ContTransformParams.LOOKAHEAD_KEY, 0.0);
			if (Double.isNaN(lookahead) || lookahead < 0 || lookahead > timeWindow) {
				System.err.println("ContTransformParser: " + name + " has an invalid lookahead of " + lookahead
						+ " s. It must be between 0 and the " + ContTransformParams.TIME_WINDOW_KEY + " - a trailing window is used.");
				lookahead = 0;
			}

			contParams.add(new ContTransformParams(transformIndex, type, timeWindow, lookahead, jsonContParams));
		}

		return contParams;
	}

	/**
	 * Find the transforms array in a model's JSON metadata. This is either at the top
	 * level or inside one of the objects in the top level (e.g. some Ketos models).
	 *
	 * @param jsonObject - the JSON metadata.
	 * @return the transforms array or null if there is none.
	 */
	private static JSONArray findTransformsArray(JSONObject jsonObject) {
		JSONArray array = jsonObject.optJSONArray("transforms");
		if (array != null) return array;
		for (String key : jsonObject.keySet()) {
			JSONObject child = jsonObject.optJSONObject(key);
			if (child != null) {
				array = child.optJSONArray("transforms");
				if (array != null) return array;
			}
		}
		return null;
	}

	/**
	 * Find the nth transform of a type in a list of transforms.
	 *
	 * @param transforms - the transforms.
	 * @param type       - the transform type.
	 * @param occurrence - the occurrence (zero indexed).
	 * @return the index of the transform or -1 if not found.
	 */
	private static int findTransform(List<DLTransform> transforms, DLTransformType type, int occurrence) {
		int count = 0;
		for (int i = 0; i < transforms.size(); i++) {
			if (transforms.get(i).getDLTransformType() == type) {
				if (count == occurrence) return i;
				count++;
			}
		}
		return -1;
	}

}
