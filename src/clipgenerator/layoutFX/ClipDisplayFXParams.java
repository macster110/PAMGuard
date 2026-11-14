package clipgenerator.layoutFX;

import java.io.Serializable;

import clipgenerator.clipDisplay.ClipDisplayParameters;
import userDisplayFX.UserDisplayNodeParams;

/**
 * Settings for the JavaFX clip display. Holds the same clip parameters as the Swing
 * display plus the zoom level and the position of the display.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDisplayFXParams implements Serializable, Cloneable {

	public static final long serialVersionUID = 1L;

	/**
	 * Clip image settings, the same as the Swing display.
	 */
	public ClipDisplayParameters clipParams = new ClipDisplayParameters();

	/**
	 * Zoom of the clip canvas. 1 is the same size as the Swing display.
	 */
	public double zoom = 1;

	/**
	 * Channel map of clips to show. 0 shows all channels.
	 */
	public int displayChannels = 0;

	/**
	 * Position of the display.
	 */
	public UserDisplayNodeParams displayParams = new UserDisplayNodeParams();

	public ClipDisplayFXParams() {
		// the zoomable canvas can show many more clips than the Swing display.
		clipParams.setMaxClips(5000);
		clipParams.setMaxMinutes(60);
	}

	@Override
	public ClipDisplayFXParams clone() {
		try {
			ClipDisplayFXParams p = (ClipDisplayFXParams) super.clone();
			p.clipParams = clipParams == null ? new ClipDisplayParameters() : clipParams.copy();
			return p;
		}
		catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

}
