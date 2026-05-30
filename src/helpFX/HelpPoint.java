package helpFX;

/**
 * Identifies a location in the PAMGuardFX help system: a Markdown file path
 * (relative to the helpFX/docs resource root) and an optional heading anchor
 * within that file.
 *
 * <p>Example usage:</p>
 * <pre>
 *     // Open the Sound Acquisition overview
 *     HelpPoint hp = new HelpPoint("sound_acquisition.md");
 *
 *     // Open directly at a heading anchor
 *     HelpPoint hp2 = new HelpPoint("click_detector.md", "configuration");
 * </pre>
 *
 * @author PAMGuard team
 */
public class HelpPoint {

	/** Path to the Markdown file, relative to the helpFX/docs classpath root. */
	private final String markdownFile;

	/**
	 * Optional anchor (heading id) to scroll to inside the file.
	 * May be {@code null} if no specific heading is targeted.
	 */
	private final String anchor;

	/**
	 * Create a help point pointing at the top of a help file.
	 *
	 * @param markdownFile classpath-relative path inside {@code helpFX/docs/},
	 *                     e.g. {@code "sound_acquisition.md"}
	 */
	public HelpPoint(String markdownFile) {
		this(markdownFile, null);
	}

	/**
	 * Create a help point pointing at a specific heading anchor inside a help file.
	 *
	 * @param markdownFile classpath-relative path inside {@code helpFX/docs/},
	 *                     e.g. {@code "click_detector.md"}
	 * @param anchor       heading anchor (lower-case, hyphen-separated), e.g.
	 *                     {@code "configuration"} or {@code null} for the top of the page
	 */
	public HelpPoint(String markdownFile, String anchor) {
		this.markdownFile = markdownFile;
		this.anchor = anchor;
	}

	/** @return the Markdown file path (relative to helpFX/docs resource root) */
	public String getMarkdownFile() {
		return markdownFile;
	}

	/**
	 * @return the optional heading anchor, or {@code null} if the whole page
	 *         should be shown from the top
	 */
	public String getAnchor() {
		return anchor;
	}

	@Override
	public String toString() {
		return anchor == null ? markdownFile : markdownFile + "#" + anchor;
	}
}
