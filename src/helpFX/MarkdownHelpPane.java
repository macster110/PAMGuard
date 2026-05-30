package helpFX;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javafx.concurrent.Worker;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * A JavaFX pane that renders a Markdown help file inside a {@link WebView}.
 *
 * <p>Markdown is converted to HTML using the CommonMark parser
 * ({@code org.commonmark}) and then displayed with PAMGuard-themed CSS that is
 * bundled inside the {@code helpFX/docs/} resource directory.</p>
 *
 * <p>Navigation to a named heading anchor is performed automatically after the
 * page finishes loading.</p>
 *
 * @author PAMGuard team
 */
public class MarkdownHelpPane extends VBox {

	/** Resource root used for all help Markdown files. */
	static final String DOCS_ROOT = "/helpFX/docs/";

	/** CSS resource bundled alongside the Markdown files. */
	private static final String CSS_RESOURCE = DOCS_ROOT + "helpFX_style.css";

	/** CommonMark parser (shared, thread-confined to FX thread). */
	private static final Parser PARSER;

	/** CommonMark HTML renderer (shared). */
	private static final HtmlRenderer RENDERER;

	static {
		List<org.commonmark.Extension> extensions =
				List.of(HeadingAnchorExtension.create());
		PARSER = Parser.builder()
				.extensions(extensions)
				.build();
		RENDERER = HtmlRenderer.builder()
				.extensions(extensions)
				.build();
	}

	private final WebView webView;
	private final WebEngine engine;

	/** Anchor to scroll to after the page finishes loading; may be null. */
	private String pendingAnchor;

	public MarkdownHelpPane() {
		webView = new WebView();
		engine = webView.getEngine();
		VBox.setVgrow(webView, Priority.ALWAYS);
		getChildren().add(webView);

		// Scroll to anchor once the page has finished loading
		engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
			if (newState == Worker.State.SUCCEEDED && pendingAnchor != null) {
				scrollToAnchor(pendingAnchor);
				pendingAnchor = null;
			}
		});
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
		displayMarkdown(helpPoint.getMarkdownFile(), helpPoint.getAnchor());
	}

	/**
	 * Display the help index / overview page.
	 */
	public void displayIndex() {
		displayMarkdown("index.md", null);
	}

	/**
	 * Load a Markdown file (relative to the docs resource root) and display it,
	 * optionally scrolling to the specified heading anchor.
	 *
	 * @param markdownFile file name or relative path, e.g. {@code "click_detector.md"}
	 * @param anchor       optional heading anchor id, or {@code null}
	 */
	public void displayMarkdown(String markdownFile, String anchor) {
		String markdownText = loadResource(DOCS_ROOT + markdownFile);
		if (markdownText == null) {
			markdownText = "# Help not found\n\nCould not load `" + markdownFile + "`.";
		}
		String css = loadResource(CSS_RESOURCE);
		String html = buildHtmlPage(markdownText, css);
		pendingAnchor = anchor;
		engine.loadContent(html, "text/html");
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	/**
	 * Convert Markdown to a complete HTML document with embedded CSS.
	 */
	private static String buildHtmlPage(String markdownText, String css) {
		org.commonmark.node.Node document = PARSER.parse(markdownText);
		String body = RENDERER.render(document);
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
		sb.append("<style>\n");
		if (css != null) {
			sb.append(css);
		} else {
			sb.append(defaultCss());
		}
		sb.append("\n</style>\n</head>\n<body>\n");
		sb.append(body);
		sb.append("\n</body>\n</html>\n");
		return sb.toString();
	}

	/** Scroll the WebView to the element with the given id. */
	private void scrollToAnchor(String anchor) {
		try {
			engine.executeScript(
					"var el = document.getElementById('" + anchor + "');" +
					"if (el) { el.scrollIntoView(true); }");
		} catch (Exception e) {
			// Non-fatal: anchor may not exist in the page
		}
	}

	/**
	 * Load a classpath resource as a UTF-8 string.
	 *
	 * @return the resource content, or {@code null} if not found
	 */
	private static String loadResource(String path) {
		try (InputStream is = MarkdownHelpPane.class.getResourceAsStream(path)) {
			if (is == null) return null;
			try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				StringBuilder sb = new StringBuilder();
				char[] buf = new char[4096];
				int n;
				while ((n = reader.read(buf)) != -1) {
					sb.append(buf, 0, n);
				}
				return sb.toString();
			}
		} catch (IOException e) {
			return null;
		}
	}

	/** Minimal fallback CSS if the bundled stylesheet cannot be found. */
	private static String defaultCss() {
		return "body { font-family: sans-serif; margin: 20px; line-height: 1.6; }\n"
			 + "h1, h2, h3 { color: #2c3e50; }\n"
			 + "code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }\n"
			 + "pre { background: #f4f4f4; padding: 10px; border-radius: 3px; overflow-x: auto; }\n"
			 + "a { color: #3498db; }\n"
			 + "table { border-collapse: collapse; width: 100%; }\n"
			 + "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n"
			 + "th { background-color: #f2f2f2; }\n";
	}

	/** @return the underlying {@link WebView} */
	public WebView getWebView() {
		return webView;
	}
}
