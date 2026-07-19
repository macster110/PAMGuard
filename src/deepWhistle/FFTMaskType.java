package deepWhistle;

/**
 * The available {@link PamFFTMask} implementations that the user can choose
 * between. Each mask type has a human readable name, an optional URL to a
 * downloadable model and is able to create its own settings pane.
 * <p>
 * Currently only {@link #DEEP_WHISTLE} is implemented but more masks will be
 * added in the future - they simply need to be added to this enum along with a
 * case in {@link #createSettingsPane()}.
 *
 * @author Jamie Macaulay
 */
public enum FFTMaskType {

	/**
	 * The DeepWhistle mask which removes noise from a spectrogram using a deep
	 * learning model.
	 */
	DEEP_WHISTLE("Deep Whistle", DeepWhistleMask.MODEL_URL, new String[] {DeepWhistleMask.MODEL_FILE_NAME});

	/**
	 * Human readable name shown to the user in the selection control.
	 */
	private final String name;

	/**
	 * URL to the downloadable model used by the mask. May be null if the mask
	 * does not require a model.
	 */
	private final String modelURL;

	/**
	 * Candidate model file names to locate within a downloaded archive (e.g. the
	 * <code>.pt</code> file inside a zip). May be null if the model URL points
	 * directly at the model file rather than an archive.
	 */
	private final String[] modelFileNames;

	FFTMaskType(String name, String modelURL, String[] modelFileNames) {
		this.name = name;
		this.modelURL = modelURL;
		this.modelFileNames = modelFileNames;
	}

	/**
	 * Get the human readable name of the mask.
	 *
	 * @return the name of the mask.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the URL to the downloadable model used by the mask.
	 *
	 * @return the model URL or null if the mask does not require a downloadable
	 *         model.
	 */
	public String getModelURL() {
		return modelURL;
	}

	/**
	 * Get the candidate model file names to locate within a downloaded archive.
	 *
	 * @return the model file names, or null if the URL points directly at the
	 *         model file.
	 */
	public String[] getModelFileNames() {
		return modelFileNames;
	}

	@Override
	public String toString() {
		return name;
	}

	/**
	 * Create the settings pane associated with this mask. The settings pane holds
	 * any mask specific controls (e.g. the confidence spinner for the DeepWhistle
	 * mask).
	 *
	 * @return a new settings pane for this mask or null if the mask has no
	 *         specific settings.
	 */
	public FFTMaskSettingsPane createSettingsPane() {
		switch (this) {
		case DEEP_WHISTLE:
			return new DeepWhistleMaskPane();
		default:
			return null;
		}
	}

	/**
	 * Create a new instance of the {@link PamFFTMask} for this mask type.
	 *
	 * @param process - the process the mask will be used in.
	 * @return a new mask instance or null if the mask type is unknown.
	 */
	public PamFFTMask createMask(MaskedFFTProcess process) {
		switch (this) {
		case DEEP_WHISTLE:
			return new DeepWhistleMask(process);
		default:
			return null;
		}
	}

}
