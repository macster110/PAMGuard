package PamController.pamWizard;

/**
 * The types of data file that can be recognised when files are dragged into a
 * blank PAMGuard configuration. Only {@link #SOUND} is currently scanned for;
 * the others are placeholders so that auto-configurations which view detection
 * data (e.g. FPOD/CPOD echolocation clicks) alongside sound data can be added
 * later without changing the framework.
 *
 * @author Jamie Macaulay
 */
public enum PamImportFileType {

	/** Raw audio / sound files (wav, etc.). */
	SOUND("Sound files"),

	/** FPOD detection files. */
	FPOD("FPOD files"),

	/** CPOD detection files. */
	CPOD("CPOD files"),

	/** PAMGuard binary data files. */
	BINARY("PAMGuard binary files");

	private final String name;

	PamImportFileType(String name) {
		this.name = name;
	}

	/**
	 * A human readable name for the file type.
	 * @return the name of the file type.
	 */
	public String getName() {
		return name;
	}
}
