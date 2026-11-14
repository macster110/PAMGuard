package clipgenerator.layoutFX;

import PamUtils.Coordinate3d;
import PamUtils.PamCoordinate;
import PamView.GeneralProjector;
import Spectrogram.TimeFrequencyPoint;

/**
 * Projector for the JavaFX clip display. Converts time and frequency into screen
 * coordinates for the clip currently being drawn, so that the data which triggered a
 * clip (e.g. a whistle contour) can be overlaid on the clip spectrogram. Also used as
 * the projector for the clip display symbol chooser.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDataProjectorFX extends GeneralProjector<TimeFrequencyPoint> {

	/**
	 * Start time of the clip being drawn
	 */
	private long clipStart;

	/**
	 * Screen position of the top left of the clip image.
	 */
	private double originX, originY;

	/**
	 * Screen size of the clip image.
	 */
	private double imageWidth, imageHeight;

	/**
	 * Duration of the clip image in milliseconds.
	 */
	private double imageMillis;

	/**
	 * Maximum frequency shown on the clip image.
	 */
	private double maxFrequency = 1;

	public ClipDataProjectorFX() {
		setParmeterType(0, GeneralProjector.ParameterType.TIME);
		setParmeterType(1, GeneralProjector.ParameterType.FREQUENCY);
	}

	/**
	 * Set the position and scales of the clip currently being drawn.
	 * @param clipStart - clip start time in milliseconds.
	 * @param imageMillis - duration of the clip image in milliseconds.
	 * @param maxFrequency - maximum frequency shown in Hz.
	 * @param originX - screen x of the left of the image.
	 * @param originY - screen y of the top of the image.
	 * @param imageWidth - screen width of the image.
	 * @param imageHeight - screen height of the image.
	 */
	public void setClip(long clipStart, double imageMillis, double maxFrequency,
			double originX, double originY, double imageWidth, double imageHeight) {
		this.clipStart = clipStart;
		this.imageMillis = Math.max(imageMillis, 1e-6);
		this.maxFrequency = Math.max(maxFrequency, 1e-6);
		this.originX = originX;
		this.originY = originY;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
	}

	public void setClipStart(long clipStart) {
		this.clipStart = clipStart;
	}

	@Override
	public Coordinate3d getCoord3d(double timeMillis, double freqHz, double d3) {
		double x = originX + (timeMillis - clipStart) / imageMillis * imageWidth;
		double y = originY + imageHeight - freqHz / maxFrequency * imageHeight;
		return new Coordinate3d(x, y);
	}

	@Override
	public Coordinate3d getCoord3d(TimeFrequencyPoint data) {
		return getCoord3d(data.getTimeMilliseconds(), data.getFrequency(), 0);
	}

	@Override
	public TimeFrequencyPoint getDataPosition(PamCoordinate screenPos) {
		double x = screenPos.getCoordinate(0);
		double y = screenPos.getCoordinate(1);
		double timeMillis = clipStart + (x - originX) / Math.max(imageWidth, 1e-6) * imageMillis;
		double freqHz = (originY + imageHeight - y) / Math.max(imageHeight, 1e-6) * maxFrequency;
		return new TimeFrequencyPoint((long) timeMillis, freqHz);
	}

}
