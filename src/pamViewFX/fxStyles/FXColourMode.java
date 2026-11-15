package pamViewFX.fxStyles;

import PamView.ColourScheme;
import PamView.PamColors;

/**
 * The colour modes offered by the JavaFX GUI. Each is one of the PAMGuard colour
 * schemes, which set the colours the displays draw with, plus, for the Day scheme,
 * whether the dialogs and sliding settings panes are light or dark.
 *
 * @author Jamie Macaulay
 */
public enum FXColourMode {

	/**
	 * Everything light, including the dialogs and settings panes.
	 */
	LIGHT("Light", "Light displays, dialogs and settings panes", ColourScheme.DAYSCHEME, true),

	/**
	 * Light displays with dark dialogs and settings panes. The original JavaFX look.
	 */
	LIGHT_DARK("Light/Dark", "Light displays with dark dialogs and settings panes", ColourScheme.DAYSCHEME, false),

	/**
	 * Everything dark.
	 */
	DARK("Dark", "Dark displays, dialogs and settings panes", ColourScheme.DARKSCHEME, false),

	/**
	 * Dim red on black, to keep night vision.
	 */
	NIGHT("Night", "Dim red on black, to keep your night vision", ColourScheme.NIGHTSCHEME, false);

	/**
	 * The mode last set from the JavaFX GUI, so that the controls showing it can keep
	 * in step with each other.
	 */
	private static javafx.beans.property.ObjectProperty<FXColourMode> modeProperty;

	private final String label;

	private final String description;

	private final String schemeName;

	private final boolean allLight;

	private FXColourMode(String label, String description, String schemeName, boolean allLight) {
		this.label = label;
		this.description = description;
		this.schemeName = schemeName;
		this.allLight = allLight;
	}

	/**
	 * @return name to show the user.
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @return a sentence describing the mode, e.g. for a tool tip.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return the name of the PAMGuard colour scheme used by the mode.
	 */
	public String getSchemeName() {
		return schemeName;
	}

	@Override
	public String toString() {
		return label;
	}

	/**
	 * Get the colour mode in use, from the current colour scheme.
	 * @return the colour mode.
	 */
	public static FXColourMode getCurrent() {
		ColourScheme scheme = PamColors.getInstance().getColourScheme();
		String name = scheme == null ? null : scheme.getName();
		if (ColourScheme.NIGHTSCHEME.equalsIgnoreCase(name)) {
			return NIGHT;
		}
		if (ColourScheme.DARKSCHEME.equalsIgnoreCase(name)) {
			return DARK;
		}
		return PamColors.getInstance().isAllLightFX() ? LIGHT : LIGHT_DARK;
	}

	/**
	 * Change the colour mode. Restyles every JavaFX window and display, and is saved
	 * with the other colour settings so it is used the next time PAMGuard starts.
	 * @param mode the new mode.
	 */
	public static void setCurrent(FXColourMode mode) {
		if (mode == null) {
			return;
		}
		PamColors.getInstance().setAllLightFX(mode.allLight);
		PamColors.getInstance().setColourScheme(mode.schemeName);
		modeProperty().set(mode);
	}

	/**
	 * A property holding the colour mode, updated whenever the mode is changed with
	 * {@link #setCurrent(FXColourMode)}. Use on the JavaFX thread only.
	 * @return the colour mode property.
	 */
	public static javafx.beans.property.ObjectProperty<FXColourMode> modeProperty() {
		if (modeProperty == null) {
			modeProperty = new javafx.beans.property.SimpleObjectProperty<FXColourMode>(getCurrent());
		}
		return modeProperty;
	}

	/**
	 * @return true if the dialogs and settings panes should be light.
	 */
	public static boolean isAllLight() {
		return getCurrent() == LIGHT;
	}

}
