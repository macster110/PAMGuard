package clickTrainDetector.clickTrainAlgorithms.ukf;

import java.io.Serializable;

import PamModel.parametermanager.ManagedParameters;
import PamModel.parametermanager.PamParameterSet;
import PamModel.parametermanager.PamParameterSet.ParameterSetType;

/**
 * Parameters for the UKF click train algorithm.
 *
 * @author Jamie Macaulay
 */
public class UKFParams implements Serializable, Cloneable, ManagedParameters {

	public static final long serialVersionUID = 1L;

	/* ---- features the tracker uses ---- */

	/** Track amplitude (always tracks ICI). */
	public boolean useAmplitude = true;

	/** Track bearing. Automatically ignored when no bearing is available. */
	public boolean useBearing = true;

	/* ---- gating / lifecycle ---- */

	/** The absolute maximum inter-click interval allowed within a train (seconds). */
	public double maxICI = 0.4;

	/**
	 * Duration of the detection batching window ("frame") in seconds. Clicks inside
	 * one frame are associated together, and each track can take at most one click
	 * per frame - so this must be shorter than the smallest inter-click interval to
	 * be resolved, otherwise a fast train (e.g. a porpoise buzz, ICI down to a few
	 * tens of ms) loses every other click to a spurious new track. Kept well below a
	 * typical minimum ICI.
	 */
	public double frameDuration = 0.02;

	/** Maximum number of consecutive missed clicks before a track is closed. */
	public int maxCoast = 4;

	/** Minimum number of clicks for a track to be saved. */
	public int minTrackLength = 3;

	/**
	 * Number of clicks a track must accumulate before it is "confirmed". Until then
	 * it is tentative and is only offered detections after every confirmed track has
	 * claimed one, so a freshly-started (possibly spurious) track cannot steal a
	 * click from an established train. Should be &gt;= 2 and no greater than
	 * {@link #minTrackLength}.
	 */
	public int confirmHits = 3;

	/* ---- two-stage (ByteTrack) association ---- */

	/** Use the two-stage high/low-confidence association. */
	public boolean twoStage = true;

	/**
	 * Amplitude (dB) threshold separating high-confidence from low-confidence
	 * clicks. Clicks at or above this are associated in the first stage; quieter
	 * clicks only in the second stage. Set to 0 to treat all clicks as
	 * high-confidence.
	 */
	public double confidenceAmplitude = 0.0;

	/* ---- UKF process noise (random-walk variances per click step) ---- */

	/** Process noise variance of log-ICI. */
	public double iciProcessNoise = 0.01;
	/** Process noise variance of the log-ICI rate (drift). */
	public double iciRateProcessNoise = 1e-4;
	/** Process noise variance of amplitude (dB^2). */
	public double ampProcessNoise = 1.0;
	/** Process noise variance of bearing (rad^2). */
	public double bearingProcessNoise = Math.toRadians(3) * Math.toRadians(3);

	/* ---- UKF measurement noise ---- */

	/** Measurement noise variance of log-ICI. */
	public double iciMeasNoise = 0.02;
	/** Measurement noise variance of amplitude (dB^2). */
	public double ampMeasNoise = 4.0;

	/**
	 * The bearing "jump floor" in degrees - the minimum bearing change tolerated
	 * between consecutive clicks. This is the bearing measurement-noise floor: the
	 * bearing innovation always includes at least this much uncertainty, so the
	 * association gate is never tighter than this however confident the track is.
	 * Bearings are normally smooth so this is small (a few degrees); raise it for
	 * species whose bearings are inherently noisy (e.g. harbour porpoise, whose
	 * narrowband clicks give poor bearing measurements) so genuine trains are not
	 * broken up.
	 */
	public double bearingFloorDeg = 3.0;

	/**
	 * The bearing measurement-noise variance (rad^2) derived from
	 * {@link #bearingFloorDeg}. This is the floor added to the predicted bearing
	 * variance when forming the innovation covariance.
	 *
	 * @return the bearing measurement-noise variance in rad^2.
	 */
	public double bearingMeasNoiseRad2() {
		double rad = Math.toRadians(bearingFloorDeg);
		return rad * rad;
	}

	/**
	 * Minimum affinity (0-1) for a track-detection pair to be associated. Pairs
	 * below this, or beyond the ICI gate, are forbidden.
	 */
	public double minAffinity = 0.05;

	/* ---- N-scan multi-hypothesis deferral ---- */

	/**
	 * Association deferral depth, in frames. 0 (the default) uses the single-frame
	 * greedy nearest-neighbour association, which commits every frame's assignment
	 * immediately. A positive value turns on a fixed-lag multi-hypothesis search: the
	 * best few global assignments are kept for up to this many frames before the
	 * winner is committed, so an ambiguous association (e.g. two trains crossing) can
	 * be corrected by later evidence instead of being locked in. 4-8 is a reasonable
	 * range; larger values defer longer (more robust, more memory/CPU).
	 */
	public int nScanDepth = 7;

	/**
	 * Number of global hypotheses kept in the N-scan beam. Only used when
	 * {@link #nScanDepth} &gt; 0. Larger explores more association possibilities at
	 * proportionally more cost.
	 */
	public int beamWidth = 3;

	/**
	 * Number of best assignments (Murty k-best) generated per hypothesis per frame in
	 * the N-scan search. Only used when {@link #nScanDepth} &gt; 0.
	 */
	public int nScanKBest = 3;

	/**
	 * Cost (nats) charged in the N-scan search when an active, currently-due track is
	 * left without a detection in a frame (a missed click). Only used when
	 * {@link #nScanDepth} &gt; 0. Should be cheaper than accepting a clearly wrong
	 * match but dear enough that a good match is preferred.
	 */
	public double nScanCoastCost = 1.5;

	/**
	 * Cost (nats) charged in the N-scan search when a high-confidence detection is
	 * not associated to any existing track and instead starts a new one. Only used
	 * when {@link #nScanDepth} &gt; 0.
	 */
	public double nScanNewTrackCost = 2.5;

	/* ---- learned affinity network ---- */

	/**
	 * Use a custom affinity network loaded from {@link #affinityNetworkFile} instead
	 * of the built-in default (Gaussian-gating) network.
	 */
	public boolean useCustomAffinity = false;

	/**
	 * Path to a JSON file describing a custom affinity network (see
	 * {@code AffinityNN.fromFile}). Only used when {@link #useCustomAffinity} is set.
	 */
	public String affinityNetworkFile = null;

	@Override
	public UKFParams clone() {
		try {
			return (UKFParams) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public PamParameterSet getParameterSet() {
		return PamParameterSet.autoGenerate(this, ParameterSetType.DETECTOR);
	}

}
