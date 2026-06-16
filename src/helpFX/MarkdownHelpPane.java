package helpFX;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import one.jpro.platform.mdfx.MarkdownView;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import PamView.ColourScheme;
import PamView.PamColors;
import pamViewFX.fxStyles.PamStylesManagerFX;

/**
 * A JavaFX pane that renders a Markdown help file using {@link MarkdownView}
 * from the jpro-mdfx library.
 *
 * <p>Markdown files are loaded from the classpath using an absolute path
 * (e.g. {@code /clickDetector/click_detector_help.md}).  The PAMGuard FX
 * stylesheet is applied so the content inherits the current CSA theme.
 * Images referenced relative to the markdown file's package directory are
 * resolved via the classpath.</p>
 *
 * @author PAMGuard team
 */
public class MarkdownHelpPane extends VBox {

	/** Classpath root for the help index page (in the helpFX package). */
	static final String HELP_FX_ROOT = "/helpFX/";

	/** Our custom mdfx variable-override CSS. */
	private static final String HELPFX_CSS = "/helpFX/helpfx.css";

	private final ScrollPane scrollPane;
	private PamMarkdownView mdView;

	/** The classpath directory prefix for the currently displayed file, e.g. {@code /rawDeepLearningClassifier/} */
	private String currentBaseDir = "/helpFX/";

	public MarkdownHelpPane() {
		scrollPane = new ScrollPane();
		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);
		VBox.setVgrow(scrollPane, Priority.ALWAYS);

		// Apply PAMGuard dialog CSS to the outer ScrollPane so container colours match the theme
		applyPamStylesheets(scrollPane);

		getChildren().add(scrollPane);
	}

	/**
	 * Load and display the given {@link HelpPoint}.
	 *
	 * @param helpPoint the page (and optional anchor) to display
	 */
	public void display(HelpPoint helpPoint) {
		if (helpPoint == null) {
			displayIndex();
			return;
		}
		displayMarkdown(helpPoint.getMarkdownFile());
	}

	/** Display the help index / overview page. */
	public void displayIndex() {
		displayMarkdown(HELP_FX_ROOT + "index.md");
	}

	/**
	 * Load and display a Markdown file by its absolute classpath path.
	 *
	 * @param classpathPath absolute classpath path, e.g. {@code /clickDetector/click_detector_help.md}
	 */
	public void displayMarkdown(String classpathPath) {
		String path = classpathPath.startsWith("/") ? classpathPath : "/" + classpathPath;

		// Derive the base directory for resolving relative image paths
		int lastSlash = path.lastIndexOf('/');
		currentBaseDir = (lastSlash > 0) ? path.substring(0, lastSlash + 1) : "/";

		String markdownText = loadResource(path);
		if (markdownText == null) {
			markdownText = "# Help not found\n\nCould not load `" + path + "`.";
		}

		if (mdView == null) {
			mdView = new PamMarkdownView(markdownText);
			mdView.setMaxWidth(Double.MAX_VALUE);
			scrollPane.setContent(mdView);
		} else {
			mdView.setMdString(markdownText);
		}

		// Apply background / foreground inline style so mdfx picks up the correct
		// base colours in both day and night mode.
		applyThemeStyle(mdView);

		scrollPane.setVvalue(0);
	}

	/**
	 * Apply PAMGuard dialog stylesheets to a node's stylesheet list.
	 * This is used for the outer ScrollPane container.
	 */
	private void applyPamStylesheets(javafx.scene.Node node) {
		try {
			PamStylesManagerFX sm = PamStylesManagerFX.getPamStylesManagerFX();
			if (sm != null && sm.getCurStyle() != null) {
				List<String> css = sm.getCurStyle().getDialogCSS();
				if (css != null) {
					if (node instanceof javafx.scene.Parent) {
						((javafx.scene.Parent) node).getStylesheets().addAll(css);
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Set an inline style on the MarkdownView so that {@code -fx-base} and
	 * {@code -fx-text-base-color} resolve to the correct values for the
	 * current day/night colour scheme.  The {@code helpfx.css} stylesheet then
	 * maps these to the {@code -mdfx-*} variables used internally by mdfx.
	 */
	private void applyThemeStyle(PamMarkdownView view) {
		boolean isNight = false;
		try {
			isNight = ColourScheme.NIGHTSCHEME.equals(
					PamColors.getInstance().getColourScheme().getName());
		} catch (Exception ignored) {
		}

		if (isNight) {
			// Dark background, light text
			view.setStyle(
				"-fx-base: #2b2b2b;" +
				"-fx-background: #2b2b2b;" +
				"-fx-text-base-color: #e0e0e0;" +
				"-fx-background-color: #2b2b2b;"
			);
		} else {
			// Light background, dark text (PAMGuard default: rgb(240,240,240))
			view.setStyle(
				"-fx-base: #f0f0f0;" +
				"-fx-background: #f0f0f0;" +
				"-fx-text-base-color: #1a1a1a;" +
				"-fx-background-color: #f0f0f0;"
			);
		}
	}

	// -------------------------------------------------------------------------
	// Inner subclass that fixes CSS injection and image loading
	// -------------------------------------------------------------------------

	/**
	 * Subclass of {@link MarkdownView} that:
	 * <ul>
	 *   <li>Injects the {@code helpfx.css} stylesheet (overrides mdfx colour variables)
	 *       via {@link #getDefaultStylesheets()}</li>
	 *   <li>Loads relative image paths from the classpath via {@link #generateImage(String)}</li>
	 * </ul>
	 */
	private class PamMarkdownView extends MarkdownView {

		PamMarkdownView(String mdString) {
			super(mdString);
			getStyleClass().add("markdown-view");
		}

		/**
		 * Called by mdfx once in the constructor to build the full stylesheet list.
		 * We return the mdfx defaults PLUS our {@code helpfx.css} which overrides
		 * the {@code -mdfx-*} colour variables to use PAMGuard's CSS inherited values.
		 */
		@Override
		protected List<String> getDefaultStylesheets() {
			List<String> sheets = new ArrayList<>(super.getDefaultStylesheets());
			URL css = MarkdownHelpPane.class.getResource(HELPFX_CSS);
			if (css != null) {
				sheets.add(css.toExternalForm());
			}
			return sheets;
		}

		/**
		 * Called by mdfx for every {@code ![alt](src)} Markdown image tag.
		 * Resolves {@code src} as a classpath resource relative to the directory
		 * of the currently loaded {@code .md} file.
		 */
		@Override
		public Node generateImage(String src) {
			URL url = resolveUrl(src);

			if (url != null) {
				try {
					ImageView iv = new ImageView(new Image(url.toExternalForm(), true));
					iv.setPreserveRatio(true);
					// Bind width to the view so images scale with the pane
					iv.fitWidthProperty().bind(widthProperty().subtract(32));
					iv.setSmooth(true);
					return iv;
				} catch (Exception ignored) {
				}
			}

			// Fallback to mdfx default behaviour
			return super.generateImage(src);
		}

		/**
		 * Resolve an image {@code src} to a classpath URL.
		 * Resolution order:
		 * <ol>
		 *   <li>Absolute URL (http/https/file)</li>
		 *   <li>Absolute classpath path (starts with {@code /})</li>
		 *   <li>Relative to {@link #currentBaseDir} (e.g. {@code resources/foo.png}
		 *       resolved against {@code /rawDeepLearningClassifier/})</li>
		 * </ol>
		 */
		private URL resolveUrl(String src) {
			if (src == null || src.isEmpty()) return null;
			try {
				// 1. Absolute HTTP/file URL
				if (src.contains("://")) return new URL(src);
				// 2. Absolute classpath path
				if (src.startsWith("/")) return MarkdownHelpPane.class.getResource(src);
				// 3. Relative to the current markdown file's package directory
				return MarkdownHelpPane.class.getResource(currentBaseDir + src);
			} catch (Exception e) {
				return null;
			}
		}
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	/** Load a classpath resource as a UTF-8 string; returns {@code null} if not found. */
	private static String loadResource(String path) {
		try (InputStream is = MarkdownHelpPane.class.getResourceAsStream(path)) {
			if (is == null) return null;
			try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				StringBuilder sb = new StringBuilder();
				char[] buf = new char[4096];
				int n;
				while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
				return sb.toString();
			}
		} catch (IOException e) {
			return null;
		}
	}
}
