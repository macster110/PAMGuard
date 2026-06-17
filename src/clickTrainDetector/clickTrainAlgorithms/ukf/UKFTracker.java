package clickTrainDetector.clickTrainAlgorithms.ukf;

import java.util.ArrayList;
import java.util.Iterator;

import PamguardMVC.PamDataUnit;

/**
 * The core UKF tracking pipeline, independent of the PAMGuard plumbing so it can
 * be unit tested. Clicks are batched into time "frames"; each frame runs:
 * <p>
 * UKF prediction → learned affinity → two-stage Hungarian assignment → UKF
 * update. Finished tracks are reported to a {@link Listener}.
 *
 * @author Jamie Macaulay
 */
public class UKFTracker {

	/** Length of the affinity feature vector. */
	public static final int FEATURE_DIM = 5;

	/** A large cost used for forbidden (gated-out) track-detection pairs. */
	private static final double FORBIDDEN_COST = 1e6;

	/** Notified when a track finishes. */
	public interface Listener {
		void trackFinished(ClickTrack track);
	}

	private final UKFParams params;
	private final boolean bearingAvailable;
	private final TrackStateModel model;
	private final CTAffinity affinity;
	private final Listener listener;

	private final ArrayList<ClickTrack> tracks = new ArrayList<>();
	private final ArrayList<PamDataUnit<?, ?>> frameClicks = new ArrayList<>();
	private double frameEndTime = Double.NaN;
	private int nextTrackId = 0;

	public UKFTracker(UKFParams params, boolean bearingAvailable, Listener listener) {
		this.params = params;
		this.bearingAvailable = bearingAvailable;
		this.model = new TrackStateModel(params, params.useAmplitude, params.useBearing && bearingAvailable);
		this.affinity = buildAffinity(params);
		this.listener = listener;
	}

	/**
	 * Build the affinity metric: a custom network loaded from file if the user has
	 * configured one, otherwise the built-in default (Gaussian-gating) network. Any
	 * problem loading the custom network falls back to the default with a warning.
	 */
	private static CTAffinity buildAffinity(UKFParams params) {
		if (params.useCustomAffinity && params.affinityNetworkFile != null
				&& !params.affinityNetworkFile.trim().isEmpty()) {
			try {
				return AffinityNN.fromFile(new java.io.File(params.affinityNetworkFile), FEATURE_DIM);
			} catch (Exception e) {
				System.err.println("UKFTracker: could not load custom affinity network from "
						+ params.affinityNetworkFile + " - falling back to default. " + e.getMessage());
			}
		}
		return AffinityNN.defaultGate(FEATURE_DIM);
	}

	/** Reset the tracker, discarding all active tracks and buffered clicks. */
	public void reset() {
		frameClicks.clear();
		frameEndTime = Double.NaN;
		tracks.clear();
	}

	/** Add a click; the frame is flushed automatically when its window closes. */
	public void addClick(PamDataUnit<?, ?> click) {
		double t = timeSeconds(click);
		if (frameClicks.isEmpty()) {
			frameEndTime = t + params.frameDuration;
		} else if (t >= frameEndTime) {
			flushFrame();
			frameEndTime = t + params.frameDuration;
		}
		frameClicks.add(click);
	}

	/** Process and clear the current frame of clicks. */
	public void flushFrame() {
		if (frameClicks.isEmpty()) {
			return;
		}
		ArrayList<PamDataUnit<?, ?>> dets = new ArrayList<>(frameClicks);
		frameClicks.clear();
		double now = 0;
		for (PamDataUnit<?, ?> d : dets) {
			now = Math.max(now, timeSeconds(d));
		}
		process(dets, now);
	}

	/** Finalise all remaining tracks (e.g. at the end of processing). */
	public void finaliseAll() {
		flushFrame();
		for (ClickTrack track : tracks) {
			listener.trackFinished(track);
		}
		tracks.clear();
	}

	/**
	 * Coast active tracks forward to the given time, bridging missed clicks. A track
	 * that cannot reach {@code now} within {@code maxCoast} missed clicks is closed.
	 */
	public void closeOverdueTracks(double now) {
		Iterator<ClickTrack> it = tracks.iterator();
		while (it.hasNext()) {
			ClickTrack track = it.next();
			if (track.size() < 2) {
				// no measured ICI yet: do not coast (a coasted prior could absorb a click
				// that is really too far away). Close if no second click arrived within the
				// maximum ICI, so a too-slow train never bootstraps.
				if (now - track.getLastTimeSeconds() > params.maxICI) {
					listener.trackFinished(track);
					it.remove();
				}
				continue;
			}
			// coast forward through whole missed clicks while overdue.
			while ((now - track.getLastTimeSeconds()) > 1.5 * track.expectedICI()
					&& track.getCoast() < params.maxCoast) {
				track.coastForward();
			}
			// still overdue after the maximum number of coasts -> the track has ended.
			if ((now - track.getLastTimeSeconds()) > 1.5 * track.expectedICI()) {
				listener.trackFinished(track);
				it.remove();
			}
		}
	}

	/** Run the full pipeline for one frame of detections. */
	private void process(ArrayList<PamDataUnit<?, ?>> dets, double now) {
		// coast tracks forward to the frame time so a click resuming after a gap
		// associates at a normal ICI (and tracks that have ended are closed).
		closeOverdueTracks(now);

		// split detections into high / low confidence (ByteTrack two-stage).
		ArrayList<PamDataUnit<?, ?>> high = new ArrayList<>();
		ArrayList<PamDataUnit<?, ?>> low = new ArrayList<>();
		for (PamDataUnit<?, ?> d : dets) {
			if (!params.twoStage || d.getAmplitudeDB() >= params.confidenceAmplitude) {
				high.add(d);
			} else {
				low.add(d);
			}
		}

		boolean[] trackMatched = new boolean[tracks.size()];

		// stage 1: high-confidence detections against all tracks.
		boolean[] highMatched = associate(high, trackMatched);

		// stage 2: low-confidence detections against still-unmatched tracks.
		if (params.twoStage && !low.isEmpty()) {
			associate(low, trackMatched);
		}

		// unmatched high-confidence detections start new tracks.
		for (int d = 0; d < high.size(); d++) {
			if (!highMatched[d]) {
				PamDataUnit<?, ?> click = high.get(d);
				tracks.add(new ClickTrack(nextTrackId++, model, params, click, timeSeconds(click),
						click.getAmplitudeDB(), bearingOf(click)));
			}
		}
	}

	/**
	 * Associate a set of detections to the active tracks via Hungarian assignment,
	 * updating matched tracks. Tracks already matched (in {@code trackMatched}) are
	 * excluded.
	 *
	 * @return a boolean array, one per detection, true if it was matched.
	 */
	private boolean[] associate(ArrayList<PamDataUnit<?, ?>> dets, boolean[] trackMatched) {
		boolean[] detMatched = new boolean[dets.size()];
		if (dets.isEmpty()) {
			return detMatched;
		}

		ArrayList<Integer> candTracks = new ArrayList<>();
		for (int i = 0; i < tracks.size(); i++) {
			if (!trackMatched[i]) {
				candTracks.add(i);
			}
		}
		if (candTracks.isEmpty()) {
			return detMatched;
		}

		double[][] cost = new double[candTracks.size()][dets.size()];
		double[][][] measCache = new double[candTracks.size()][dets.size()][];
		for (int r = 0; r < candTracks.size(); r++) {
			ClickTrack track = tracks.get(candTracks.get(r));
			for (int c = 0; c < dets.size(); c++) {
				PamDataUnit<?, ?> det = dets.get(c);
				double t = timeSeconds(det);
				double ici = t - track.getLastTimeSeconds();
				if (ici <= 0 || ici > params.maxICI) {
					cost[r][c] = FORBIDDEN_COST;
					continue;
				}
				double[] z = track.measurement(t, det.getAmplitudeDB(), bearingOf(det));
				measCache[r][c] = z;
				double aff = affinity.affinity(features(track, z, t));
				cost[r][c] = aff < params.minAffinity ? FORBIDDEN_COST : -Math.log(aff);
			}
		}

		int[] rowToCol = HungarianAlgorithm.solve(cost);
		for (int r = 0; r < rowToCol.length; r++) {
			int c = rowToCol[r];
			if (c < 0 || cost[r][c] >= FORBIDDEN_COST) {
				continue;
			}
			int trackIndex = candTracks.get(r);
			ClickTrack track = tracks.get(trackIndex);
			PamDataUnit<?, ?> det = dets.get(c);
			track.predict();
			track.update(measCache[r][c], det, timeSeconds(det));
			trackMatched[trackIndex] = true;
			detMatched[c] = true;
		}
		return detMatched;
	}

	/**
	 * Build the affinity feature vector for a (track, detection) pairing. Only
	 * feature 0 (Mahalanobis distance) is used by the default network; the rest
	 * exist for a trained network.
	 */
	private double[] features(ClickTrack track, double[] z, double t) {
		double[] f = new double[FEATURE_DIM];
		f[0] = track.mahalanobis(z);
		double[] innov = track.getModel().measResidual(z, track.measurementPrediction());
		f[1] = Math.abs(innov[0]) / Math.sqrt(params.iciMeasNoise);
		if (track.getModel().usesAmplitude()) {
			f[2] = Math.abs(innov[track.getModel().ampMeasIndex()]) / Math.sqrt(params.ampMeasNoise);
		}
		if (track.getModel().usesBearing()) {
			f[3] = Math.abs(innov[track.getModel().bearingMeasIndex()]) / Math.sqrt(params.bearingMeasNoise);
		}
		f[4] = (t - track.getLastTimeSeconds()) / Math.max(track.expectedICI(), 1e-3);
		return f;
	}

	/** Number of currently active tracks (for diagnostics/tests). */
	public int activeTrackCount() {
		return tracks.size();
	}

	/* -------------------- feature extraction from a data unit -------------------- */

	private static double timeSeconds(PamDataUnit<?, ?> dataUnit) {
		return dataUnit.getTimeMilliseconds() / 1000.0;
	}

	private static double bearingOf(PamDataUnit<?, ?> dataUnit) {
		if (dataUnit.getLocalisation() != null && dataUnit.getLocalisation().getAngles() != null
				&& dataUnit.getLocalisation().getAngles().length > 0) {
			return dataUnit.getLocalisation().getAngles()[0];
		}
		return 0;
	}

}
