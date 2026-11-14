package clipgenerator.layoutFX;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import PamUtils.FrequencyFormat;
import PamUtils.PamCalendar;
import PamUtils.PamUtils;
import PamView.PamColors;
import PamView.PamColors.PamColor;
import PamView.PamSymbol;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import clipgenerator.ClipDataUnit;
import clipgenerator.clipDisplay.ClipDisplayParameters;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import pamViewFX.fxNodes.utilsFX.PamUtilsFX;
import pamViewFX.fxgraphics2d.FXGraphics2D;

/**
 * A zoomable canvas which shows clip spectrograms in a grid. The grid flows to the
 * width of the display and only the clips which are visible are drawn, so thousands
 * of clips can be shown and scrolled through smoothly. Clip images are made on a
 * background thread, visible clips first, and a limited number are kept in memory.
 * <p>
 * Ctrl (or Cmd) + scroll wheel, a trackpad pinch or the + and - keys zoom in and out about
 * the mouse. Scroll or drag to move up and down. Click to select, Ctrl+click to add to the
 * selection, Shift+click to select a range and double click to play a clip.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipCanvasFX extends Region {

	/**
	 * Gap between clips in display units.
	 */
	private static final double GAP = 4;

	/**
	 * Width of the coloured border around each clip.
	 */
	private static final double BORDER = 2;

	/**
	 * Height of the text areas around each clip image.
	 */
	private static final double TEXT_BORDER = 13;

	private static final double MIN_ZOOM = 0.02;

	private static final double MAX_ZOOM = 16;

	/**
	 * Maximum number of image pixels kept in memory.
	 */
	private static final long MAX_CACHE_PIXELS = 40_000_000L;

	/**
	 * Height in pixels of the thumbnail images used when the display is zoomed out.
	 * Thumbnails are small so one can be kept for every clip.
	 */
	private static final int THUMB_HEIGHT = 40;

	private static final Color HIGHLIGHT = Color.rgb(255, 255, 0, 0.25);

	private ClipDisplayFX clipDisplay;

	private Canvas canvas;

	private ScrollBar vBar;

	/**
	 * All clips in display order.
	 */
	private ArrayList<ClipItem> allItems = new ArrayList<ClipItem>();

	/**
	 * Clips which pass the data selector and channel selection, in display order.
	 */
	private ArrayList<ClipItem> shownItems = new ArrayList<ClipItem>();

	/**
	 * Index of the first clip in each row of the layout.
	 */
	private int[] rowStarts = new int[0];

	private double rowPitch = 100;

	private double contentHeight = 0;

	private double zoom = 1;

	private double targetZoom = 1;

	private double zoomAnchorX, zoomAnchorY;

	private boolean needLayout = true;

	private boolean needPaint = true;

	private Font font = Font.font("Arial", 10);

	private FXGraphics2D g2;

	/**
	 * Clip images currently held, in order of last use.
	 */
	private LinkedHashMap<ClipItem, Boolean> imageCache = new LinkedHashMap<>(256, 0.75f, true);

	private long cachePixels = 0;

	private ImageMaker imageMaker;

	private ClipItem lastClickedItem;

	private ClipItem hoverItem;

	private Tooltip tooltip;

	private double pressX, pressY, pressScroll;

	private boolean dragging;

	/**
	 * Settings which affect the clip images. When these change all images are remade.
	 */
	private String imageSettingsKey = "";

	private Consumer<ClipCanvasFX> layoutListener;

	public ClipCanvasFX(ClipDisplayFX clipDisplay) {
		this.clipDisplay = clipDisplay;
		canvas = new Canvas(100, 100);
		g2 = new FXGraphics2D(canvas.getGraphicsContext2D());
		vBar = new ScrollBar();
		vBar.setOrientation(Orientation.VERTICAL);
		vBar.valueProperty().addListener((obs, o, n) -> repaint());
		getChildren().addAll(canvas, vBar);
		setFocusTraversable(true);

		tooltip = new Tooltip();
		tooltip.setShowDelay(Duration.millis(400));
		tooltip.setShowDuration(Duration.seconds(30));
		tooltip.setWrapText(true);
		tooltip.setMaxWidth(500);

		imageMaker = new ImageMaker();

		setOnScroll(this::onScroll);
		setOnZoom(this::onZoom);
		canvas.setOnMousePressed(this::onPressed);
		canvas.setOnMouseDragged(this::onDragged);
		canvas.setOnMouseReleased(this::onReleased);
		canvas.setOnMouseMoved(this::onMoved);
		canvas.setOnMouseExited(e -> {
			hoverItem = null;
			tooltip.hide();
		});
		setOnKeyPressed(e -> {
			KeyCode k = e.getCode();
			if (k == KeyCode.PLUS || k == KeyCode.EQUALS || k == KeyCode.ADD) {
				zoomAbout(1.25, getCanvasWidth() / 2, canvas.getHeight() / 2);
			}
			else if (k == KeyCode.MINUS || k == KeyCode.SUBTRACT) {
				zoomAbout(0.8, getCanvasWidth() / 2, canvas.getHeight() / 2);
			}
			else if (k == KeyCode.HOME) {
				vBar.setValue(0);
			}
			else if (k == KeyCode.END) {
				vBar.setValue(vBar.getMax());
			}
			else if (k == KeyCode.PAGE_DOWN) {
				vBar.setValue(Math.min(vBar.getMax(), vBar.getValue() + canvas.getHeight() * 0.9));
			}
			else if (k == KeyCode.PAGE_UP) {
				vBar.setValue(Math.max(0, vBar.getValue() - canvas.getHeight() * 0.9));
			}
			else if (k == KeyCode.A && e.isShortcutDown()) {
				selectAll();
			}
			else if (k == KeyCode.ESCAPE) {
				clearSelection();
			}
			else {
				return;
			}
			e.consume();
		});

		new AnimationTimer() {
			@Override
			public void handle(long now) {
				animate();
			}
		}.start();
	}

	/*
	 * Clip management
	 */

	/**
	 * Add a clip to the display.
	 * @param clip - the clip.
	 * @param atStart - true to put it at the start (top left), false to put it at the end.
	 */
	public void addClip(ClipDataUnit clip, boolean atStart) {
		ClipItem item = new ClipItem(clip);
		if (atStart) {
			allItems.add(0, item);
		}
		else {
			allItems.add(item);
		}
		item.shown = clipDisplay.shouldShow(clip);
		rebuildShown();
	}

	/**
	 * Remove all clips.
	 */
	public void clearClips() {
		allItems.clear();
		shownItems.clear();
		imageCache.clear();
		cachePixels = 0;
		lastClickedItem = null;
		hoverItem = null;
		imageMaker.setWanted(new ArrayList<ClipItem>());
		needLayout = true;
		repaint();
	}

	/**
	 * Replace all clips, e.g. when new data are loaded in viewer mode.
	 * @param clips - the clips in display order.
	 */
	public void setClips(Collection<ClipDataUnit> clips) {
		clearClips();
		for (ClipDataUnit clip : clips) {
			ClipItem item = new ClipItem(clip);
			item.shown = clipDisplay.shouldShow(clip);
			allItems.add(item);
		}
		rebuildShown();
	}

	/**
	 * Remove old clips so there are no more than maxClips and none older than minTime.
	 * @param maxClips - maximum number of clips.
	 * @param minTime - minimum clip time in milliseconds.
	 */
	public void trimClips(int maxClips, long minTime) {
		boolean changed = false;
		// remove the oldest clips, which may be at the start or the end of the list.
		while (allItems.size() > maxClips) {
			ClipItem first = allItems.get(0);
			ClipItem last = allItems.get(allItems.size() - 1);
			ClipItem oldest = first.clip.getTimeMilliseconds() < last.clip.getTimeMilliseconds() ? first : last;
			removeItem(oldest);
			changed = true;
		}
		Iterator<ClipItem> it = allItems.iterator();
		while (it.hasNext()) {
			ClipItem item = it.next();
			if (item.clip.getTimeMilliseconds() < minTime) {
				it.remove();
				forgetImage(item);
				changed = true;
			}
		}
		if (changed) {
			rebuildShown();
		}
	}

	private void removeItem(ClipItem item) {
		allItems.remove(item);
		forgetImage(item);
		if (item == lastClickedItem) {
			lastClickedItem = null;
		}
	}

	/**
	 * Remove a clip from the display.
	 * @param clip - the clip to remove.
	 */
	public void removeClip(PamDataUnit clip) {
		for (ClipItem item : new ArrayList<ClipItem>(allItems)) {
			if (item.clip == clip) {
				removeItem(item);
			}
		}
		rebuildShown();
	}

	/**
	 * Check which clips should be shown, e.g. after the data selector has changed.
	 */
	public void updateShownClips() {
		for (ClipItem item : allItems) {
			item.shown = clipDisplay.shouldShow(item.clip);
		}
		rebuildShown();
	}

	private void rebuildShown() {
		shownItems.clear();
		for (ClipItem item : allItems) {
			if (item.shown) {
				shownItems.add(item);
			}
		}
		needLayout = true;
		repaint();
	}

	/**
	 * Called when display settings change. Images are remade if the settings which
	 * affect them have changed.
	 */
	public void settingsChanged() {
		ClipDisplayParameters p = clipDisplay.getClipDisplayParameters();
		String key = p.getLogFFTLength() + "_" + p.amlitudeMinVal + "_" + p.amplitudeRangeVal + "_" + p.getColourMap();
		if (!key.equals(imageSettingsKey)) {
			imageSettingsKey = key;
			for (ClipItem item : allItems) {
				item.imageKey = null;
			}
		}
		for (ClipItem item : allItems) {
			item.estimateSize();
		}
		needLayout = true;
		repaint();
	}

	/**
	 * Redraw the clip borders, e.g. after the symbol options have changed.
	 */
	public void repaint() {
		needPaint = true;
	}

	public int getNumClips() {
		return allItems.size();
	}

	public int getNumShownClips() {
		return shownItems.size();
	}

	/**
	 * Get the selected clips in display order.
	 * @return list of selected clips.
	 */
	public List<ClipDataUnit> getSelectedClips() {
		ArrayList<ClipDataUnit> sel = new ArrayList<ClipDataUnit>();
		for (ClipItem item : shownItems) {
			if (item.highlight) {
				sel.add(item.clip);
			}
		}
		return sel;
	}

	public void selectAll() {
		for (ClipItem item : shownItems) {
			item.highlight = true;
		}
		selectionChanged();
	}

	public void clearSelection() {
		for (ClipItem item : allItems) {
			item.highlight = false;
		}
		lastClickedItem = null;
		selectionChanged();
	}

	private void selectionChanged() {
		repaint();
		if (layoutListener != null) {
			layoutListener.accept(this);
		}
	}

	/**
	 * Set a listener which is called when the zoom, layout or selection change.
	 * @param layoutListener - the listener.
	 */
	public void setLayoutListener(Consumer<ClipCanvasFX> layoutListener) {
		this.layoutListener = layoutListener;
	}

	/*
	 * Zooming
	 */

	public double getZoom() {
		return targetZoom;
	}

	/**
	 * Set the zoom straight away (no animation) keeping the top of the view in place.
	 * @param zoom - the zoom factor. 1 is the same scale as the Swing display.
	 */
	public void setZoom(double zoom) {
		zoom = clampZoom(zoom);
		ClipItem anchor = itemAt(getCanvasWidth() / 2, 1);
		this.zoom = this.targetZoom = zoom;
		doLayout();
		if (anchor != null) {
			vBar.setValue(clampScroll(anchor.y * zoom));
		}
		repaint();
		if (layoutListener != null) {
			layoutListener.accept(this);
		}
	}

	/**
	 * Zoom in or out by a factor about a point on the screen. The zoom is animated.
	 * @param factor - zoom factor (> 1 zooms in).
	 * @param x - x position on the canvas.
	 * @param y - y position on the canvas.
	 */
	public void zoomAbout(double factor, double x, double y) {
		targetZoom = clampZoom(targetZoom * factor);
		zoomAnchorX = x;
		zoomAnchorY = y;
	}

	/**
	 * Zoom so that all clips fit on the screen, if possible.
	 */
	public void zoomToFit() {
		double w = getCanvasWidth();
		double h = canvas.getHeight();
		if (w <= 0 || h <= 0 || shownItems.isEmpty()) {
			return;
		}
		// binary search for the largest zoom at which everything fits.
		double lo = MIN_ZOOM, hi = MAX_ZOOM;
		for (int i = 0; i < 40; i++) {
			double mid = Math.sqrt(lo * hi);
			if (layoutHeight(w / mid) * mid <= h) {
				lo = mid;
			}
			else {
				hi = mid;
			}
		}
		setZoom(lo);
		vBar.setValue(0);
	}

	private double clampZoom(double z) {
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
	}

	private void animate() {
		if (Math.abs(targetZoom - zoom) > 1e-4 * zoom) {
			// zoom about the anchor point, keeping the clip under the mouse where it is.
			ClipItem anchor = itemAt(zoomAnchorX, zoomAnchorY);
			double ry = 0;
			if (anchor != null) {
				double sy = anchor.y * zoom - vBar.getValue();
				ry = (zoomAnchorY - sy) / (anchor.h * zoom);
			}
			double newZoom = zoom + (targetZoom - zoom) * 0.35;
			if (Math.abs(targetZoom - newZoom) < 1e-3 * targetZoom) {
				newZoom = targetZoom;
			}
			zoom = newZoom;
			doLayout();
			if (anchor != null) {
				vBar.setValue(clampScroll(anchor.y * zoom + ry * anchor.h * zoom - zoomAnchorY));
			}
			else {
				vBar.setValue(clampScroll(vBar.getValue()));
			}
			needPaint = true;
			if (layoutListener != null) {
				layoutListener.accept(this);
			}
		}
		if (needLayout) {
			double oldTop = vBar.getValue();
			doLayout();
			vBar.setValue(clampScroll(oldTop));
			needPaint = true;
			if (layoutListener != null) {
				layoutListener.accept(this);
			}
		}
		if (needPaint) {
			needPaint = false;
			paint();
		}
	}

	/*
	 * Layout
	 */

	@Override
	protected void layoutChildren() {
		double w = getWidth();
		double h = getHeight();
		double barW = vBar.prefWidth(-1);
		vBar.resizeRelocate(w - barW, 0, barW, h);
		double cw = Math.max(1, w - barW);
		if (canvas.getWidth() != cw || canvas.getHeight() != h) {
			canvas.setWidth(cw);
			canvas.setHeight(Math.max(1, h));
			needLayout = true;
		}
	}

	private double getCanvasWidth() {
		return canvas.getWidth();
	}

	private double clampScroll(double value) {
		return Math.max(0, Math.min(vBar.getMax(), value));
	}

	/**
	 * Height of the layout in display units for a given width.
	 */
	private double layoutHeight(double worldWidth) {
		double x = GAP;
		int nRows = shownItems.isEmpty() ? 0 : 1;
		double pitch = GAP;
		for (ClipItem item : shownItems) {
			pitch = item.h + GAP;
			if (x + item.w > worldWidth && x > GAP) {
				nRows++;
				x = GAP;
			}
			x += item.w + GAP;
		}
		return nRows * pitch + GAP;
	}

	private void doLayout() {
		needLayout = false;
		double worldW = Math.max(10, getCanvasWidth() / zoom);
		ArrayList<Integer> starts = new ArrayList<Integer>();
		double x = GAP, y = GAP;
		rowPitch = 50;
		for (int i = 0; i < shownItems.size(); i++) {
			ClipItem item = shownItems.get(i);
			rowPitch = item.h + GAP;
			if (i == 0) {
				starts.add(0);
			}
			else if (x + item.w > worldW && x > GAP) {
				x = GAP;
				y += rowPitch;
				starts.add(i);
			}
			item.x = x;
			item.y = y;
			x += item.w + GAP;
		}
		rowStarts = new int[starts.size()];
		for (int i = 0; i < rowStarts.length; i++) {
			rowStarts[i] = starts.get(i);
		}
		contentHeight = shownItems.isEmpty() ? 0 : y + rowPitch + GAP;
		double viewH = canvas.getHeight();
		double max = Math.max(0, contentHeight * zoom - viewH);
		vBar.setMax(max);
		vBar.setVisibleAmount(max == 0 ? 1 : viewH * max / (contentHeight * zoom));
		vBar.setUnitIncrement(rowPitch * zoom / 3);
		vBar.setBlockIncrement(viewH * 0.9);
		vBar.setDisable(max == 0);
	}

	/**
	 * Find the clip at a screen position.
	 * @param sx - canvas x.
	 * @param sy - canvas y.
	 * @return the clip item or null.
	 */
	private ClipItem itemAt(double sx, double sy) {
		if (rowStarts.length == 0) {
			return null;
		}
		double wx = sx / zoom;
		double wy = (sy + vBar.getValue()) / zoom;
		int row = (int) Math.floor((wy - GAP) / rowPitch);
		if (row < 0 || row >= rowStarts.length) {
			return null;
		}
		int end = row + 1 < rowStarts.length ? rowStarts[row + 1] : shownItems.size();
		for (int i = rowStarts[row]; i < end; i++) {
			ClipItem item = shownItems.get(i);
			if (wx >= item.x && wx <= item.x + item.w && wy >= item.y && wy <= item.y + item.h) {
				return item;
			}
		}
		return null;
	}

	/*
	 * Painting
	 */

	private void paint() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		gc.setTransform(1, 0, 0, 1, 0, 0);
		gc.setFill(clipDisplay.getBackgroundColour());
		gc.fillRect(0, 0, w, h);
		if (shownItems.isEmpty()) {
			gc.setFill(clipDisplay.getTextColour());
			gc.setFont(Font.font(13));
			gc.setTextAlign(TextAlignment.CENTER);
			gc.setTextBaseline(VPos.CENTER);
			String msg = allItems.isEmpty() ? "No clips" : "No clips match the current data selection";
			gc.fillText(msg, w / 2, h / 2);
			gc.setTextAlign(TextAlignment.LEFT);
			gc.setTextBaseline(VPos.BASELINE);
			return;
		}
		double scroll = vBar.getValue();
		int firstRow = (int) Math.max(0, Math.floor((scroll / zoom - GAP) / rowPitch));
		int lastRow = (int) Math.min(rowStarts.length - 1, Math.floor(((scroll + h) / zoom - GAP) / rowPitch));
		ArrayList<ClipItem> visible = new ArrayList<ClipItem>();
		ArrayList<ClipItem> wanted = new ArrayList<ClipItem>();
		ArrayList<ClipItem> wantedLater = new ArrayList<ClipItem>();
		for (int row = firstRow; row <= lastRow; row++) {
			int end = row + 1 < rowStarts.length ? rowStarts[row + 1] : shownItems.size();
			for (int i = rowStarts[row]; i < end; i++) {
				ClipItem item = shownItems.get(i);
				visible.add(item);
				item.wantFull = needFullImage(item);
				if (!item.hasThumb()) {
					// clips with nothing to show come first.
					wanted.add(item);
				}
				else if (item.wantFull && !item.hasFullImage()) {
					wantedLater.add(item);
				}
			}
		}
		wanted.addAll(wantedLater);
		// ask for images for the next rows to make scrolling smooth.
		int extra = 0;
		for (int row = lastRow + 1; row < rowStarts.length && extra < 60; row++) {
			int end = row + 1 < rowStarts.length ? rowStarts[row + 1] : shownItems.size();
			for (int i = rowStarts[row]; i < end; i++, extra++) {
				ClipItem item = shownItems.get(i);
				item.wantFull = needFullImage(item);
				if (!item.hasThumb() || (item.wantFull && !item.hasFullImage())) {
					wanted.add(item);
				}
			}
		}
		imageMaker.setWanted(wanted);

		boolean drawText = zoom * font.getSize() >= 6;
		boolean drawTrigger = clipDisplay.getClipDisplayParameters().showTriggerOverlay && zoom > 0.25;
		for (ClipItem item : visible) {
			drawItem(gc, item, scroll, drawText, drawTrigger);
		}
		gc.setTransform(1, 0, 0, 1, 0, 0);
	}

	private void drawItem(GraphicsContext gc, ClipItem item, double scroll, boolean drawText, boolean drawTrigger) {
		ClipDisplayParameters params = clipDisplay.getClipDisplayParameters();
		double sx = item.x * zoom;
		double sy = item.y * zoom - scroll;

		PamSymbol symbol = clipDisplay.getBorderSymbol(item.clip);
		Color lineCol = Color.GREY;
		Color axisCol = PamUtilsFX.awtToFXColor(PamColors.getInstance().getBorderColour());
		Color textCol = PamUtilsFX.awtToFXColor(PamColors.getInstance().getForegroudColor(PamColor.AXIS));
		if (symbol != null) {
			lineCol = PamUtilsFX.awtToFXColor(symbol.getLineColor());
			if (symbol.isFill()) {
				axisCol = PamUtilsFX.awtToFXColor(symbol.getFillColor());
				textCol = axisCol.getBrightness() > 0.55 ? Color.BLACK : Color.WHITE;
			}
		}

		gc.setTransform(zoom, 0, 0, zoom, sx, sy);
		// outer border and axis area
		gc.setFill(lineCol);
		gc.fillRect(0, 0, item.w, item.h);
		gc.setFill(axisCol);
		gc.fillRect(BORDER, BORDER, item.w - 2 * BORDER, item.h - 2 * BORDER);

		double ix = BORDER + TEXT_BORDER;
		double iy = BORDER + TEXT_BORDER;
		double iw = item.imageW;
		double ih = item.imageH;

		Image image = item.image;
		if (image != null && !item.wantFull && item.thumb != null) {
			// zoomed out, so the thumbnail looks the same and is quicker to draw.
			image = item.thumb;
		}
		else if (image != null) {
			imageCache.get(item); // mark as recently used
		}
		else {
			image = item.thumb;
		}
		if (image != null) {
			double miss = 1. - params.frequencyScale;
			double srcY = Math.max(0, image.getHeight() * miss);
			gc.drawImage(image, 0, srcY, image.getWidth(), image.getHeight() - srcY, ix, iy, iw, ih);
		}
		else {
			gc.setFill(Color.gray(0.2));
			gc.fillRect(ix, iy, iw, ih);
			if (drawText && item.noImage) {
				gc.setFill(Color.gray(0.8));
				gc.setFont(font);
				gc.fillText("no image", ix + 3, iy + ih / 2);
			}
		}

		if (drawText) {
			gc.setFill(textCol);
			gc.setFont(font);
			gc.setTextBaseline(VPos.TOP);
			gc.fillText(PamCalendar.formatTime(item.clip.getTimeMilliseconds(), true), ix, BORDER);
			gc.setTextBaseline(VPos.BOTTOM);
			double bottom = item.h - BORDER;
			gc.fillText(String.format("Ch%d", item.channel), BORDER + 2, bottom);
			gc.setTextAlign(TextAlignment.RIGHT);
			gc.fillText(String.format("%3.2fs", item.durationSeconds()), item.w - BORDER - 2, bottom);
			gc.setTextAlign(TextAlignment.LEFT);
			// rotated maximum frequency on the left
			double f = item.clip.getDisplaySampleRate() / 2. * params.frequencyScale;
			gc.save();
			gc.translate(BORDER, iy);
			gc.rotate(-90);
			gc.setTextBaseline(VPos.TOP);
			gc.setTextAlign(TextAlignment.RIGHT);
			gc.fillText(FrequencyFormat.formatFrequency(f, true), 0, 0);
			gc.restore();
			gc.setTextAlign(TextAlignment.LEFT);
			gc.setTextBaseline(VPos.BASELINE);
		}

		if (item.highlight) {
			gc.setFill(HIGHLIGHT);
			gc.fillRect(0, 0, item.w, item.h);
			gc.setStroke(Color.YELLOW);
			gc.setLineWidth(Math.max(1.5, 2 / zoom));
			gc.strokeRect(0.5, 0.5, item.w - 1, item.h - 1);
		}
		gc.setTransform(1, 0, 0, 1, 0, 0);

		if (drawTrigger && image != null) {
			drawTriggerData(item, sx + ix * zoom, sy + iy * zoom, iw * zoom, ih * zoom);
		}
	}

	/**
	 * Draw the data unit which triggered the clip (e.g. a whistle contour) on the clip.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void drawTriggerData(ClipItem item, double x, double y, double w, double h) {
		PamDataUnit trigger = item.getTrigger();
		if (trigger == null) {
			return;
		}
		PamDataBlock dataBlock = trigger.getParentDataBlock();
		ClipDataProjectorFX proj = clipDisplay.getProjector();
		if (dataBlock == null || !dataBlock.canDraw(proj)) {
			return;
		}
		ClipDisplayParameters params = clipDisplay.getClipDisplayParameters();
		double fMax = item.clip.getDisplaySampleRate() / 2. * params.frequencyScale;
		proj.setClip(item.clip.getTimeMilliseconds(), item.durationSeconds() * 1000., fMax, x, y, w, h);
		proj.clearHoverList();
		try {
			g2.setClip(new java.awt.Rectangle((int) x, (int) y, (int) Math.ceil(w), (int) Math.ceil(h)));
			if (dataBlock.getOverlayDraw() != null) {
				dataBlock.getOverlayDraw().preDrawAnything(g2, dataBlock, proj);
			}
			dataBlock.drawDataUnit(g2, trigger, proj);
		}
		catch (Exception e) {
			// overlay drawing is not critical
		}
		finally {
			g2.setClip(null);
		}
	}

	/*
	 * Mouse
	 */

	private void onScroll(ScrollEvent e) {
		if (e.getTouchCount() > 1 || e.isDirect()) {
			// let trackpad zoom events handle pinches.
		}
		if (e.isShortcutDown() || e.isControlDown()) {
			double factor = Math.pow(1.0025, e.getDeltaY());
			zoomAbout(factor, e.getX(), e.getY());
		}
		else {
			vBar.setValue(clampScroll(vBar.getValue() - e.getDeltaY()));
		}
		e.consume();
	}

	private void onZoom(ZoomEvent e) {
		zoomAbout(e.getZoomFactor(), e.getX(), e.getY());
		e.consume();
	}

	private void onPressed(MouseEvent e) {
		requestFocus();
		tooltip.hide();
		pressX = e.getX();
		pressY = e.getY();
		pressScroll = vBar.getValue();
		dragging = false;
		if (e.isPopupTrigger()) {
			showMenu(e);
		}
	}

	private void onDragged(MouseEvent e) {
		if (!dragging && Math.hypot(e.getX() - pressX, e.getY() - pressY) > 4) {
			dragging = true;
		}
		if (dragging) {
			vBar.setValue(clampScroll(pressScroll - (e.getY() - pressY)));
		}
	}

	private void onReleased(MouseEvent e) {
		if (e.isPopupTrigger()) {
			showMenu(e);
			return;
		}
		if (dragging || e.getButton() != MouseButton.PRIMARY) {
			dragging = false;
			return;
		}
		ClipItem item = itemAt(e.getX(), e.getY());
		if (item == null) {
			if (!e.isShortcutDown() && !e.isShiftDown()) {
				clearSelection();
			}
			return;
		}
		clipDisplay.getProjector().setClipStart(item.clip.getTimeMilliseconds());
		if (e.getClickCount() == 2) {
			clipDisplay.playClip(item.clip);
			return;
		}
		selectItem(item, e.isShortcutDown(), e.isShiftDown());
	}

	/**
	 * Select a clip in the same way as the Swing display.
	 */
	private void selectItem(ClipItem item, boolean toggle, boolean range) {
		if (!toggle && !range) {
			boolean was = item.highlight;
			for (ClipItem it : allItems) {
				it.highlight = false;
			}
			item.highlight = !was;
			lastClickedItem = item.highlight ? item : null;
		}
		else if (toggle) {
			item.highlight = !item.highlight;
			lastClickedItem = item;
		}
		else {
			int i1 = lastClickedItem == null ? -1 : shownItems.indexOf(lastClickedItem);
			int i2 = shownItems.indexOf(item);
			if (i1 < 0) {
				item.highlight = true;
				lastClickedItem = item;
			}
			else {
				int a = Math.min(i1, i2), b = Math.max(i1, i2);
				for (int i = 0; i < shownItems.size(); i++) {
					shownItems.get(i).highlight = i >= a && i <= b;
				}
			}
		}
		selectionChanged();
	}

	private void showMenu(MouseEvent e) {
		ClipItem item = itemAt(e.getX(), e.getY());
		if (item != null && !item.highlight) {
			selectItem(item, false, false);
		}
		clipDisplay.showContextMenu(item == null ? null : item.clip, e);
	}

	private void onMoved(MouseEvent e) {
		ClipItem item = itemAt(e.getX(), e.getY());
		if (item == hoverItem) {
			return;
		}
		hoverItem = item;
		if (item == null) {
			tooltip.hide();
			Tooltip.uninstall(canvas, tooltip);
			return;
		}
		clipDisplay.getProjector().setClipStart(item.clip.getTimeMilliseconds());
		tooltip.setText(clipDisplay.getClipSummary(item.clip));
		Tooltip.install(canvas, tooltip);
	}

	/*
	 * Images
	 */

	private void imageReady(ClipItem item, Image[] images, String key) {
		Image image = images == null ? null : images[0];
		Image thumb = images == null ? null : images[1];
		item.noImage = image == null;
		item.thumb = thumb;
		item.thumbKey = key;
		if (image != null) {
			// the clip may be a different length to the estimate.
			double oldW = item.w;
			item.setImageSize(image.getWidth(), image.getHeight());
			if (oldW != item.w) {
				needLayout = true;
			}
		}
		if (image != null && (item.wantFull || thumb == null)) {
			forgetImage(item);
			item.image = image;
			item.imageKey = key;
			imageCache.put(item, Boolean.TRUE);
			cachePixels += (long) (image.getWidth() * image.getHeight());
			trimCache();
		}
		else if (item.imageKey == null || !item.imageKey.equals(key)) {
			// only the thumbnail is needed. Old images are out of date.
			forgetImage(item);
		}
		repaint();
	}

	/**
	 * Check whether a clip is shown big enough on the screen to need its full
	 * resolution image rather than its thumbnail.
	 */
	private boolean needFullImage(ClipItem item) {
		double screenH = item.imageH * zoom;
		double thumbH = THUMB_HEIGHT * clipDisplay.getClipDisplayParameters().frequencyScale;
		return screenH > thumbH * 1.25;
	}

	private void forgetImage(ClipItem item) {
		if (item.image != null) {
			cachePixels -= (long) (item.image.getWidth() * item.image.getHeight());
			item.image = null;
		}
		imageCache.remove(item);
	}

	private void trimCache() {
		Iterator<ClipItem> it = imageCache.keySet().iterator();
		while (cachePixels > MAX_CACHE_PIXELS && it.hasNext()) {
			ClipItem item = it.next();
			if (item.image != null) {
				cachePixels -= (long) (item.image.getWidth() * item.image.getHeight());
				item.image = null;
				item.imageKey = null;
			}
			it.remove();
		}
	}

	/**
	 * Stop making images, e.g. when the display closes.
	 */
	public void dispose() {
		imageMaker.stop();
	}

	/**
	 * Makes clip images on a background thread. The list of wanted images is replaced
	 * each time the display is painted so the visible clips are always made first.
	 */
	private class ImageMaker implements Runnable {

		private ArrayDeque<ClipItem> queue = new ArrayDeque<ClipItem>();

		private volatile boolean running = true;

		private Thread thread;

		ImageMaker() {
			thread = new Thread(this, "Clip display images");
			thread.setDaemon(true);
			thread.start();
		}

		synchronized void setWanted(List<ClipItem> items) {
			queue.clear();
			queue.addAll(items);
			notifyAll();
		}

		void stop() {
			running = false;
			synchronized (this) {
				notifyAll();
			}
		}

		private synchronized ClipItem next() throws InterruptedException {
			while (running && queue.isEmpty()) {
				wait();
			}
			return queue.pollFirst();
		}

		@Override
		public void run() {
			while (running) {
				ClipItem item;
				try {
					item = next();
				}
				catch (InterruptedException e) {
					return;
				}
				if (item == null) {
					continue;
				}
				String key = imageSettingsKey;
				if (item.hasThumb() && (!item.wantFull || item.hasFullImage())) {
					continue;
				}
				Image[] images = null;
				try {
					images = makeImage(item);
				}
				catch (Exception e) {
					images = null;
				}
				final Image[] img = images;
				Platform.runLater(() -> {
					// settings may have changed whilst making the image.
					if (key.equals(imageSettingsKey)) {
						imageReady(item, img, key);
					}
				});
			}
		}

		private Image[] makeImage(ClipItem item) {
			ClipDisplayParameters params = clipDisplay.getClipDisplayParameters();
			int fftLen = 1 << params.getLogFFTLength();
			java.awt.Color[] colours = clipDisplay.getColourArray().getColours();
			BufferedImage bi = item.clip.getClipImage(item.channel, fftLen, fftLen / 2, params.amlitudeMinVal,
					params.amlitudeMinVal + params.amplitudeRangeVal, colours);
			// the image is all we need so free the spectrogram data held by the clip.
			item.clip.clearClipSpecData();
			if (bi == null) {
				return null;
			}
			return new Image[] { SwingFXUtils.toFXImage(bi, null), makeThumb(bi) };
		}

		/**
		 * Make a small version of the clip image for when the display is zoomed out.
		 */
		private Image makeThumb(BufferedImage bi) {
			int h = bi.getHeight();
			if (h <= THUMB_HEIGHT) {
				return null;
			}
			double scale = (double) THUMB_HEIGHT / h;
			int w = Math.max(2, (int) Math.round(bi.getWidth() * scale));
			// average the pixels so that thin features such as whistles are not lost.
			java.awt.Image scaled = bi.getScaledInstance(w, THUMB_HEIGHT, java.awt.Image.SCALE_AREA_AVERAGING);
			BufferedImage small = new BufferedImage(w, THUMB_HEIGHT, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = small.createGraphics();
			g.drawImage(scaled, 0, 0, null);
			g.dispose();
			return SwingFXUtils.toFXImage(small, null);
		}
	}

	/**
	 * A clip in the display.
	 */
	private class ClipItem {

		final ClipDataUnit clip;

		final int channel;

		/**
		 * Position and size in display units (zoom = 1).
		 */
		double x, y, w, h;

		/**
		 * Size of the image area in display units.
		 */
		double imageW, imageH;

		Image image;

		String imageKey;

		/**
		 * Small image used when zoomed out.
		 */
		Image thumb;

		String thumbKey;

		/**
		 * True if the clip is shown big enough to need the full image.
		 */
		volatile boolean wantFull = true;

		boolean noImage;

		boolean highlight;

		boolean shown = true;

		private boolean triggerSearched;

		private PamDataUnit trigger;

		boolean hasThumb() {
			String key = imageSettingsKey;
			return (thumb != null && key.equals(thumbKey)) || (image != null && key.equals(imageKey))
					|| (noImage && key.equals(thumbKey));
		}

		boolean hasFullImage() {
			return image != null && imageSettingsKey.equals(imageKey);
		}

		ClipItem(ClipDataUnit clip) {
			this.clip = clip;
			this.channel = Math.max(0, PamUtils.getLowestChannel(clip.getChannelBitmap()));
			estimateSize();
		}

		/**
		 * Work out the size of the clip from its duration and the FFT settings
		 * before the image has been made.
		 */
		void estimateSize() {
			ClipDisplayParameters params = clipDisplay.getClipDisplayParameters();
			int fftLen = 1 << params.getLogFFTLength();
			int hop = fftLen / 2;
			double nSamples = durationSeconds() * clip.getDisplaySampleRate();
			double nT = Math.max(1, Math.floor((nSamples - (fftLen - hop)) / hop));
			if (image != null && imageSettingsKey.equals(imageKey)) {
				nT = image.getWidth();
			}
			setImageSize(nT, fftLen / 2);
		}

		void setImageSize(double nT, double nF) {
			ClipDisplayParameters params = clipDisplay.getClipDisplayParameters();
			imageW = Math.max(4, nT * params.imageHScale);
			imageH = Math.max(4, nF * params.imageVScale);
			w = imageW + 2 * BORDER + TEXT_BORDER + 2;
			h = imageH + 2 * BORDER + 2 * TEXT_BORDER;
		}

		double durationSeconds() {
			Long dur = clip.getSampleDuration();
			float fs = clip.getSourceSampleRate();
			if (dur == null || fs <= 0) {
				return 0.1;
			}
			return dur / (double) fs;
		}

		PamDataUnit getTrigger() {
			if (!triggerSearched) {
				triggerSearched = true;
				trigger = clip.getTriggerDataUnit();
				if (trigger == null) {
					trigger = clip.findTriggerDataUnit();
				}
			}
			return trigger;
		}
	}

}
