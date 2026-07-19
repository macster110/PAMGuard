package PamController.pamWizard;

/**
 * The result of a {@link PamFileTypeScanner} examining a set of dropped files:
 * which file type was looked for, how many matching files were found, and an
 * optional type-specific payload (for {@link PamImportFileType#SOUND} this is the
 * {@code FileListData<WavFileType>} of scanned audio files).
 *
 * @author Jamie Macaulay
 */
public class PamFileTypeResult {

	private final PamImportFileType fileType;

	private final int fileCount;

	private final Object data;

	public PamFileTypeResult(PamImportFileType fileType, int fileCount, Object data) {
		this.fileType = fileType;
		this.fileCount = fileCount;
		this.data = data;
	}

	/**
	 * The file type this result is for.
	 * @return the file type.
	 */
	public PamImportFileType getFileType() {
		return fileType;
	}

	/**
	 * The number of matching files found.
	 * @return the file count.
	 */
	public int getFileCount() {
		return fileCount;
	}

	/**
	 * Type-specific payload describing the matched files. For
	 * {@link PamImportFileType#SOUND} this is a {@code FileListData<WavFileType>}.
	 * @return the payload, or null.
	 */
	public Object getData() {
		return data;
	}

	/**
	 * @return true if any matching files of this type were found.
	 */
	public boolean hasFiles() {
		return fileCount > 0;
	}
}
