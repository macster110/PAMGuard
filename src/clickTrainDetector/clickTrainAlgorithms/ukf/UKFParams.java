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

	/** Duration of the detection batching window ("frame") in seconds. */
	public double frameDuration = 0.05;

	/** Maximum number of consecutive missed clicks before a track is closed. */
	public int maxCoast = 4;

	/** Minimum number of clicks for a track to be saved. */
	public int minTrackLength = 3;

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
	/** Measurement noise variance of bearing (rad^2). */
	public double bearingMeasNoise = Math.toRadians(3) * Math.toRadians(3);

	/**
	 * Minimum affinity (0-1) for a track-detection pair to be associated. Pairs
	 * below this, or beyond the ICI gate, are forbidden.
	 */
	public double minAffinity = 0.05;

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
