package clickTrainDetector.clickTrainAlgorithms.ukf.test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import clickTrainDetector.clickTrainAlgorithms.mht.mhtMAT.SimpleClick;
import clickTrainDetector.clickTrainAlgorithms.ukf.AffinityNN;
import clickTrainDetector.clickTrainAlgorithms.ukf.ClickTrack;
import clickTrainDetector.clickTrainAlgorithms.ukf.HungarianAlgorithm;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFModel;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFParams;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFTracker;
import clickTrainDetector.clickTrainAlgorithms.ukf.UnscentedKalmanFilter;

/**
 * Tests for the UKF click train tracker and its components (Hungarian assignment
 * and the unscented Kalman filter). Plain main() program; non-zero exit on
 * failure.
 *
 * @author Jamie Macaulay
 */
public class TestUKFTracker {

	private static final float SR = 500000;

	private static int failures = 0;

	public static void main(String[] args) {

		testHungarian();
		testUKFConverges();
		testCustomAffinity();

		testSingleRegularTrain();
		testTwoInterleavedTrains();
		testBearingSeparatedTrains();
		testCoastingGap();
		testTwoStageRecovery();
		testRejectTooSlow();

		System.out.println("\n==================================");
		if (failures == 0) {
			System.out.println("ALL UKF TRACKER TESTS PASSED");
		} else {
			System.out.println(failures + " UKF TRACKER TEST(S) FAILED");
		}
		System.out.println("==================================");
		System.exit(failures == 0 ? 0 : 1);
	}

	/* ---------------- component tests ---------------- */

	/**
	 * Hungarian assignment on a matrix with a known optimal assignment.
	 */
	private static void testHungarian() {
		// optimal assignment is (0->1, 1->0, 2->2) with cost 1+2+3 = 6.
		double[][] cost = { { 9, 1, 9 }, { 2, 9, 9 }, { 9, 9, 3 } };
		int[] a = HungarianAlgorithm.solve(cost);
		boolean ok = a[0] == 1 && a[1] == 0 && a[2] == 2;
		assertTrue("Hungarian finds the optimal assignment (got [" + a[0] + "," + a[1] + "," + a[2] + "])", ok);
	}

	/**
	 * A UKF tracking a constant 1-D signal should converge to it from noisy
	 * measurements.
	 */
	private static void testUKFConverges() {
		UKFModel model = new UKFModel() {
			public int stateDim() {
				return 1;
			}

			public int measDim() {
				return 1;
			}

			public double[] f(double[] x) {
				return x.clone();
			}

			public double[] h(double[] x) {
				return x.clone();
			}

			public double[][] processNoise() {
				return new double[][] { { 1e-4 } };
			}

			public double[][] measurementNoise() {
				return new double[][] { { 1.0 } };
			}

			public double[] stateResidual(double[] a, double[] b) {
				return new double[] { a[0] - b[0] };
			}

			public double[] measResidual(double[] a, double[] b) {
				return new double[] { a[0] - b[0] };
			}

			public double[] stateMean(double[][] s, double[] w) {
				double m = 0;
				for (int i = 0; i < s.length; i++) {
					m += w[i] * s[i][0];
				}
				return new double[] { m };
			}

			public double[] measMean(double[][] s, double[] w) {
				return stateMean(s, w);
			}
		};

		UnscentedKalmanFilter ukf = new UnscentedKalmanFilter(model, new double[] { 0 }, new double[][] { { 10 } });
		Random r = new Random(0);
		double truth = 5.0;
		for (int i = 0; i < 50; i++) {
			ukf.predict();
			ukf.update(new double[] { truth + r.nextGaussian() * 1.0 });
		}
		double est = ukf.getState()[0];
		assertTrue("UKF converges to the constant (estimate " + String.format("%.2f", est) + ")",
				Math.abs(est - truth) < 0.5);
	}

	/**
	 * A custom affinity network loaded from a JSON file should (a) reproduce the
	 * default network when given the default weights, (b) reject a wrong-shaped
	 * file, and (c) drive the tracker when configured in the params.
	 */
	private static void testCustomAffinity() {
		try {
			// default-gate weights written to a file.
			File good = File.createTempFile("affinity", ".json");
			good.deleteOnExit();
			try (FileWriter w = new FileWriter(good)) {
				w.write("{ \"weights\": [ [[-0.5, 0, 0, 0, 0]], [[4.0]] ], \"biases\": [ [3.0], [0.0] ] }");
			}

			AffinityNN custom = AffinityNN.fromFile(good, UKFTracker.FEATURE_DIM);
			AffinityNN deflt = AffinityNN.defaultGate(UKFTracker.FEATURE_DIM);
			double[] near = { 0, 0, 0, 0, 0 };
			double[] far = { 12, 0, 0, 0, 0 };
			boolean matches = Math.abs(custom.affinity(near) - deflt.affinity(near)) < 1e-9
					&& Math.abs(custom.affinity(far) - deflt.affinity(far)) < 1e-9;
			assertTrue("Custom affinity loaded from file reproduces the default network", matches);

			// wrong input dimension should be rejected.
			File bad = File.createTempFile("affinitybad", ".json");
			bad.deleteOnExit();
			try (FileWriter w = new FileWriter(bad)) {
				w.write("{ \"weights\": [ [[-0.5, 0, 0]], [[4.0]] ], \"biases\": [ [3.0], [0.0] ] }");
			}
			boolean threw = false;
			try {
				AffinityNN.fromFile(bad, UKFTracker.FEATURE_DIM);
			} catch (Exception e) {
				threw = true;
			}
			assertTrue("Wrong-shaped affinity network file is rejected", threw);

			// the tracker uses the custom network end to end.
			List<SimpleClick> clicks = new ArrayList<>();
			generateTrain(clicks, 0, 1.0, 0.1, 0.003, 120, -0.15, 1.0, null, 30, new Random(1));
			UKFParams params = defaultParams();
			params.useCustomAffinity = true;
			params.affinityNetworkFile = good.getAbsolutePath();
			List<Integer> trains = runTracker(clicks, params, false);
			assertTrue("Tracker runs with a custom affinity network (got " + trains.size() + " sizes " + trains + ")",
					trains.size() == 1 && trains.get(0) >= 25);
		} catch (Exception e) {
			assertTrue("Custom affinity test threw: " + e, false);
		}
	}

	/* ---------------- tracker scenarios ---------------- */

	private static void testSingleRegularTrain() {
		List<SimpleClick> clicks = new ArrayList<>();
		generateTrain(clicks, 0, 1.0, 0.1, 0.003, 120, -0.15, 1.0, null, 30, new Random(1));
		List<Integer> trains = runTracker(clicks, defaultParams(), false);
		assertTrue("Single regular train: one train (got " + trains.size() + " sizes " + trains + ")",
				trains.size() == 1);
		if (trains.size() == 1) {
			assertTrue("Single regular train: recovers most clicks (" + trains.get(0) + "/30)", trains.get(0) >= 25);
		}
	}

	private static void testTwoInterleavedTrains() {
		List<SimpleClick> clicks = new ArrayList<>();
		generateTrain(clicks, 0, 1.00, 0.10, 0.003, 122, -0.10, 1.0, null, 25, new Random(2));
		generateTrain(clicks, 100, 1.04, 0.13, 0.003, 108, 0.10, 1.0, null, 25, new Random(3));
		sortByTime(clicks);
		List<Integer> trains = runTracker(clicks, defaultParams(), false);
		assertTrue("Two interleaved trains: two trains (got " + trains.size() + " sizes " + trains + ")",
				trains.size() == 2);
	}

	private static void testBearingSeparatedTrains() {
		List<SimpleClick> clicks = new ArrayList<>();
		generateTrain(clicks, 0, 1.00, 0.10, 0.003, 118, 0.0, 1.0, 30.0, 25, new Random(6));
		generateTrain(clicks, 100, 1.05, 0.10, 0.003, 118, 0.0, 1.0, 120.0, 25, new Random(7));
		sortByTime(clicks);
		List<Integer> trains = runTracker(clicks, defaultParams(), true);
		assertTrue("Bearing-separated trains: two trains (got " + trains.size() + " sizes " + trains + ")",
				trains.size() == 2);
	}

	private static void testCoastingGap() {
		List<SimpleClick> clicks = new ArrayList<>();
		generateTrain(clicks, 0, 1.0, 0.1, 0.003, 120, -0.1, 1.0, null, 15, new Random(4));
		// gap of 2 missed clicks then continue.
		generateTrain(clicks, 50, 1.0 + 15 * 0.1 + 2 * 0.1, 0.1, 0.003, 118.5, -0.1, 1.0, null, 15, new Random(5));
		sortByTime(clicks);
		List<Integer> trains = runTracker(clicks, defaultParams(), false);
		assertTrue("Coasting gap: one bridged train (got " + trains.size() + " sizes " + trains + ")",
				trains.size() == 1);
	}

	private static void testTwoStageRecovery() {
		// a single train where every other click is quiet (below the confidence
		// threshold); the two-stage association should recover the quiet clicks.
		List<SimpleClick> clicks = new ArrayList<>();
		Random r = new Random(9);
		double t = 1.0;
		for (int i = 0; i < 24; i++) {
			double amp = (i % 2 == 0) ? 120 : 90; // loud / quiet alternating
			clicks.add(new SimpleClick(i, t, amp, SR));
			t += 0.1 + r.nextGaussian() * 0.003;
		}
		UKFParams params = defaultParams();
		params.twoStage = true;
		params.confidenceAmplitude = 100; // quiet (90) clicks are low-confidence
		// track on ICI only: amplitude is the confidence signal here, so using it as a
		// tracking gate would itself reject the quiet clicks the second stage recovers.
		params.useAmplitude = false;
		List<Integer> trains = runTracker(clicks, params, false);
		assertTrue("Two-stage recovery: one train recovering quiet clicks (got " + trains.size() + " sizes " + trains
				+ ")", trains.size() == 1 && trains.get(0) >= 20);
	}

	private static void testRejectTooSlow() {
		List<SimpleClick> clicks = new ArrayList<>();
		generateTrain(clicks, 0, 1.0, 0.6, 0.005, 120, 0.0, 1.0, null, 20, new Random(8));
		List<Integer> trains = runTracker(clicks, defaultParams(), false);
		assertTrue("Too-slow train rejected (got " + trains.size() + " sizes " + trains + ")", trains.isEmpty());
	}

	/* ---------------- helpers ---------------- */

	private static UKFParams defaultParams() {
		UKFParams params = new UKFParams();
		params.maxICI = 0.4;
		params.frameDuration = 0.05;
		params.maxCoast = 4;
		params.minTrackLength = 3;
		params.useAmplitude = true;
		params.useBearing = true;
		params.twoStage = true;
		params.confidenceAmplitude = 0.0;
		return params;
	}

	/**
	 * Run clicks through the tracker and return the sizes of the finished trains
	 * (>= minTrackLength).
	 */
	private static List<Integer> runTracker(List<SimpleClick> clicks, UKFParams params, boolean bearingAvailable) {
		List<Integer> sizes = new ArrayList<>();
		UKFTracker tracker = new UKFTracker(params, bearingAvailable, (ClickTrack track) -> {
			if (track.size() >= params.minTrackLength) {
				sizes.add(track.size());
			}
		});
		for (SimpleClick click : clicks) {
			tracker.addClick(click);
		}
		tracker.finaliseAll();
		return sizes;
	}

	private static void generateTrain(List<SimpleClick> clicks, int uidStart, double t0, double ici, double iciJitter,
			double amp, double ampDrift, double ampJitter, Double bearing, int n, Random random) {
		double t = t0;
		for (int i = 0; i < n; i++) {
			double a = amp + i * ampDrift + random.nextGaussian() * ampJitter;
			if (bearing == null) {
				clicks.add(new SimpleClick(uidStart + i, t, a, SR));
			} else {
				double bear = bearing + random.nextGaussian() * 1.5;
				clicks.add(new SimpleClick(uidStart + i, Double.valueOf(t), Double.valueOf(a), Double.valueOf(bear), SR));
			}
			t += ici + random.nextGaussian() * iciJitter;
		}
	}

	private static void sortByTime(List<SimpleClick> clicks) {
		clicks.sort((a, b) -> Double.compare(a.timeSeconds, b.timeSeconds));
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
