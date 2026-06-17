package clickTrainDetector.clickTrainAlgorithms.ukf;

import java.util.ArrayList;

import PamguardMVC.PamDataUnit;

/**
 * A single click train track maintained by the UKF tracker.
 *
 * @author Jamie Macaulay
 */
public class ClickTrack {

	private final int id;
	private final TrackStateModel model;
	private final UnscentedKalmanFilter ukf;

	/** Time (seconds) of the last click assigned to this track. */
	private double lastTimeSeconds;

	/** Number of consecutive frames with no assigned click. */
	private int coast = 0;

	/** Clicks belonging to this track, in time order. */
	private final ArrayList<PamDataUnit> clicks = new ArrayList<>();

	public ClickTrack(int id, TrackStateModel model, UKFParams params, PamDataUnit firstClick, double timeSeconds,
			double amp, double bearing) {
		this.id = id;
		this.model = model;
		double logICI0 = Math.log(Math.max(params.maxICI * 0.5, 1e-3));
		double[] x0 = model.initialState(logICI0, amp, bearing);
		double[][] p0 = model.initialCovariance();
		this.ukf = new UnscentedKalmanFilter(model, x0, p0);
		this.lastTimeSeconds = timeSeconds;
		this.clicks.add(firstClick);
	}

	/** UKF prediction step. */
	public void predict() {
		ukf.predict();
	}

	/** The time (seconds) at which the next click is expected. */
	public double predictedNextTime() {
		return lastTimeSeconds + Math.exp(model.logICI(ukf.getState()));
	}

	/** The currently expected ICI (seconds). */
	public double expectedICI() {
		return Math.exp(model.logICI(ukf.getState()));
	}

	/**
	 * Mahalanobis distance of a candidate measurement against this track's current
	 * prediction.
	 */
	public double mahalanobis(double[] z) {
		return ukf.mahalanobis(z);
	}

	/** The measurement the current state expects (for the next click). */
	public double[] measurementPrediction() {
		return ukf.predictedMeasurement();
	}

	/** Build a measurement vector for a candidate click. */
	public double[] measurement(double timeSeconds, double amp, double bearing) {
		double ici = timeSeconds - lastTimeSeconds;
		return model.measurement(ici, amp, bearing);
	}

	/** Associate a click with this track and run the UKF update. */
	public void update(double[] z, PamDataUnit click, double timeSeconds) {
		ukf.update(z);
		lastTimeSeconds = timeSeconds;
		coast = 0;
		clicks.add(click);
	}

	/**
	 * Coast the track forward through one missed click: advance the expected time by
	 * one ICI and run a UKF prediction (which inflates the uncertainty). Used to
	 * bridge gaps so a click resuming after a few missed detections still
	 * associates at a normal ICI.
	 */
	public void coastForward() {
		double ici = expectedICI();
		ukf.predict();
		lastTimeSeconds += ici;
		coast++;
	}

	/** Increment the coast (missed-click) counter. */
	public void coast() {
		coast++;
	}

	public int getCoast() {
		return coast;
	}

	public double getLastTimeSeconds() {
		return lastTimeSeconds;
	}

	public ArrayList<PamDataUnit> getClicks() {
		return clicks;
	}

	public int size() {
		return clicks.size();
	}

	public int getId() {
		return id;
	}

	public TrackStateModel getModel() {
		return model;
	}

}
