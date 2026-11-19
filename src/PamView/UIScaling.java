package PamView;

import com.formdev.flatlaf.util.UIScale;

/**
 * Scaling for things which are sized in pixels, such as icons and fixed size fonts,
 * so that they match the size of the text.
 * <p>
 * When Java isn't scaling the whole display (e.g. on Linux with
 * -Dsun.java2d.uiScale=1 on a high resolution screen), FlatLaf still makes the text
 * bigger to match the system font size, but anything PAMGuard sizes in pixels stays
 * small. This applies the same factor FlatLaf uses for its own text and controls.
 * Where Java scales the whole display (Windows, macOS, or Linux with a uiScale
 * of 2), the factor is normally 1, so nothing changes.
 *
 * @author Jamie Macaulay
 */
public class UIScaling {

	private UIScaling() {
	}

	/**
	 * @return the factor FlatLaf scales text and controls by, or 1 if it can't be found.
	 */
	public static float getScaleFactor() {
		try {
			float f = UIScale.getUserScaleFactor();
			return f > 0 ? f : 1f;
		}
		catch (Throwable e) {
			return 1f;
		}
	}

	/**
	 * Scale a size in pixels.
	 * @param size unscaled size, e.g. an icon size.
	 * @return the scaled size.
	 */
	public static int scale(int size) {
		float f = getScaleFactor();
		if (f == 1f) {
			return size;
		}
		return Math.round(size * f);
	}

	/**
	 * Scale a size in pixels.
	 * @param size unscaled size, e.g. a font size.
	 * @return the scaled size.
	 */
	public static float scale(float size) {
		return size * getScaleFactor();
	}
}
