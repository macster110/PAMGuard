package clickTrainDetector.clickTrainAlgorithms.ukf.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import PamguardMVC.PamDataUnit;
import clickTrainDetector.clickTrainAlgorithms.mht.mhtMAT.SimpleClick;
import clickTrainDetector.clickTrainAlgorithms.ukf.AffinityNN;
import clickTrainDetector.clickTrainAlgorithms.ukf.AffinityTrainer;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFParams;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFTracker;

/**
 * Tests for the UKF affinity-network trainer. Synthesises a few well-separated
 * annotated "events", generates training examples, trains the MLP, and checks
 * that it learns to tell true continuations from distractors and that the saved
 * JSON round-trips through {@link AffinityNN#fromFile}.
 *
 * @author Jamie Macaulay
 */
@SuppressWarnings("rawtypes")
public class TestAffinityTrainer {

	private static final float SR = 500000;

	private static int failures = 0;

	public static void main(String[] args) throws Exception {

		// three amplitude-separated trains, interleaved in time, act as annotated events.
		List<List<PamDataUnit>> events = new ArrayList<>();
		events.add(makeTrain(0, 1.00, 0.10, 130, 25, new Random(1)));
		events.add(makeTrain(100, 1.04, 0.11, 110, 25, new Random(2)));
		events.add(makeTrain(200, 1.08, 0.09, 92, 25, new Random(3)));

		UKFParams params = new UKFParams();
		params.maxICI = 0.4;
		params.useAmplitude = true;
		params.useBearing = false;

		AffinityTrainer.Dataset data = AffinityTrainer.generateExamples(events, params, false);
		assertTrue("Examples generated (" + data.nPositive + " positive, " + data.nNegative + " negative)",
				data.nPositive > 20 && data.nNegative > 20);

		File out = File.createTempFile("affinity_trained", ".json");
		out.deleteOnExit();
		AffinityTrainer.Result result = AffinityTrainer.trainAndExport(events, params, false,
				AffinityTrainer.DEFAULT_HIDDEN, AffinityTrainer.DEFAULT_EPOCHS, null, out);

		assertTrue("Trained network separates true vs distractor (accuracy " + pct(result.accuracy) + ")",
				result.accuracy > 0.8);

		// the saved file round-trips and behaves like the trained network.
		AffinityNN loaded = AffinityNN.fromFile(out, UKFTracker.FEATURE_DIM);
		double meanPos = 0;
		double meanNeg = 0;
		int nP = 0;
		int nN = 0;
		for (int i = 0; i < data.x.length; i++) {
			if (data.y[i] > 0.5) {
				meanPos += loaded.affinity(data.x[i]);
				nP++;
			} else {
				meanNeg += loaded.affinity(data.x[i]);
				nN++;
			}
		}
		meanPos /= nP;
		meanNeg /= nN;
		assertTrue("Loaded network scores true continuations above distractors (pos " + fmt(meanPos) + " > neg "
				+ fmt(meanNeg) + ")", meanPos > meanNeg + 0.2);

		System.out.println("\n==================================");
		if (failures == 0) {
			System.out.println("ALL AFFINITY TRAINER TESTS PASSED");
		} else {
			System.out.println(failures + " AFFINITY TRAINER TEST(S) FAILED");
		}
		System.out.println("==================================");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static List<PamDataUnit> makeTrain(int uidStart, double t0, double ici, double amp, int n, Random rnd) {
		List<PamDataUnit> clicks = new ArrayList<>();
		double t = t0;
		for (int i = 0; i < n; i++) {
			double a = amp + rnd.nextGaussian() * 1.0;
			clicks.add(new SimpleClick(uidStart + i, t, a, SR));
			t += ici + rnd.nextGaussian() * 0.003;
		}
		return clicks;
	}

	private static String pct(double v) {
		return String.format("%.1f%%", v * 100);
	}

	private static String fmt(double v) {
		return String.format("%.3f", v);
	}

	private static void assertTrue(String message, boolean condition) {
		if (condition) {
			System.out.println("[PASS] " + message);
		} else {
			System.out.println("[FAIL] " + message);
			failures++;
		}
	}

}
