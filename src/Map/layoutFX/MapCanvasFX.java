package Map.layoutFX;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

import Array.ArrayManager;
import Array.PamArray;
import Array.SnapshotGeometry;
import GPS.GPSControl;
import GPS.GPSDataBlock;
import GPS.GPSParameters;
import GPS.GpsData;
import GPS.GpsDataUnit;
import Map.MapContour;
import Map.MapController;
import Map.MapDetectionData;
import Map.MapDrawingOptions;
import Map.MapFileManager;
import Map.MapPanel;
import Map.MapParameters;
import Map.SimpleMap;
import Map.Vessel;
import Map.gridbaselayer.GridbaseControl;
import Map.gridbaselayer.MapRasterImage;
import PamController.PamController;
import PamController.masterReference.MasterReferencePoint;
import PamUtils.Coordinate3d;
import PamUtils.LatLong;
import PamUtils.PamCalendar;
import PamUtils.PamUtils;
import PamView.ColourArray;
import PamView.PamColors;
import PamView.PamColors.PamColor;
import PamView.PamSymbol;
import PamView.symbol.PamSymbolChooser;
import PamView.symbol.PamSymbolManager;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamDataUnit;
import PamguardMVC.dataSelector.DataSelector;
import effort.EffortDataUnit;
import effort.EffortProvider;
import javafx.animation.AnimationTimer;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import pamMaths.PamVector;
import pamViewFX.fxgraphics2d.FXGraphics2D;

/**
 * The drawing area of the JavaFX map. Draws the sea surface, latitude and
 * longitude grid, contours, raster bathymetry, the ship and its track,
 * hydrophones and detection overlays using the same projector and overlay
 * graphics as the Swing map. Swing drawing code is reused through
 * {@link FXGraphics2D}.
 * <p>
 * The map uses the parameters and the overlay manager of the module's main
 * Swing map so that both GUIs share the same settings.
 *
 * @author Jamie Macaulay
 *
 */
public class MapCanvasFX extends Pane {

	/**
	 * The name used for data selectors.
	 */
	public static final String DATASELECTNAME = MapPanel.DATASELECTNAME;

	/**
	 * Minimum and maximum map ranges in meters.
	 */
	public static final double MIN_RANGE = 10;

	public static final double MAX_RANGE = 2e7;

	private MapController mapController;

	private SimpleMap simpleMap;

	/**
	 * Canvas for the background (grid, contours, track).
	 */
	private Canvas baseCanvas;

	/**
	 * Canvas for the ship, detections etc.
	 */
	private Canvas dataCanvas;

	private FXGraphics2D baseGraphics;

	private FXGraphics2D dataGraphics;

	private MapProjectorFX projector;

	private LatLong mapCentre = new LatLong();

	private double mapRangeMetres = 10000;

	private Vessel ship;

	private LatLong minCornerLatLong, maxCornerLatLong;

	/**
	 * Flags to redraw the map.
	 */
	private volatile boolean repaintBase = true, repaintData = true;

	private long lastDataRepaint = 0;

	private long lastBaseRepaint = 0;

	private int lastGpsCount = -1;

	/**
	 * Mouse handling
	 */
	private double mousePressX, mousePressY;

	private LatLong mousePressLatLong;

	private double mouseDragX, mouseDragY;

	private boolean measureMode = false;

	private boolean measuring = false;

	private double pressRotation, pressVerticalRotation;

	private boolean rotating;

	private Tooltip hoverTooltip;

	/**
	 * Listeners for changes to the map (e.g. mouse position)
	 */
	private ArrayList<MapCanvasListener> listeners = new ArrayList<MapCanvasListener>();

	/**
	 * Supplies the current map time in viewer mode.
	 */
	private TimeRangeSupplier timeRangeSupplier;

	private boolean centredOnFirstGps = false;

	private AnimationTimer animationTimer;

	private LatLong lastMouseLatLong;

	/**
	 * Supplies the start, current and end time of the viewer scroll bar.
	 */
	public interface TimeRangeSupplier {

		long getMinimumMillis();

		long getMaximumMillis();

		long getValueMillis();
	}

	/**
	 * Listener for map events.
	 */
	public interface MapCanvasListener {

		/**
		 * Called when the mouse moves over the map
		 * @param latLong - the lat long of the mouse. Null if mouse left the map.
		 */
		public void mouseMoved(LatLong latLong);

		/**
		 * Called whenever the map is measuring a distance.
		 * @param start - the start of the measurement. Null to clear.
		 * @param end - the end of the measurement.
		 */
		public void measure(LatLong start, LatLong end);

		/**
		 * Called whenever the map view (centre, zoom, rotation) changes.
		 */
		public void viewChanged();
	}

	public MapCanvasFX(MapController mapController, SimpleMap simpleMap) {
		this.mapController = mapController;
		this.simpleMap = simpleMap;

		projector = new MapProjectorFX();
		// some overlay graphics need a reference to the Swing map panel.
		projector.setMapPanelRef(simpleMap.getMapPanel());

		ship = new Vessel(Color.RED);

		baseCanvas = new Canvas(100, 100);
		dataCanvas = new Canvas(100, 100);
		baseGraphics = new FXGraphics2D(baseCanvas.getGraphicsContext2D());
		dataGraphics = new FXGraphics2D(dataCanvas.getGraphicsContext2D());

		this.getChildren().addAll(baseCanvas, dataCanvas);
		this.setMinSize(50, 50);

		widthProperty().addListener((obs, oldVal, newVal) -> resizeCanvas());
		heightProperty().addListener((obs, oldVal, newVal) -> resizeCanvas());

		hoverTooltip = new Tooltip();
		hoverTooltip.setShowDelay(Duration.millis(200));
		hoverTooltip.setHideDelay(Duration.millis(100));

		addMouseHandlers();

		this.setCursor(Cursor.CROSSHAIR);

		mapRangeMetres = getMapParameters().getMapRangeMetres();
		resetMapCentre();

		animationTimer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				animate();
			}
		};
		animationTimer.start();
	}

	/**
	 * Called on every animation frame. Repaints the map if needed but no more than
	 * four times a second, and at least once a second so that the predicted ship
	 * position moves smoothly.
	 */
	private void animate() {
		long now = System.currentTimeMillis();
		if (!repaintBase && !repaintData && now - lastDataRepaint < 1000) {
			return;
		}
		if (now - lastDataRepaint < 250 && !repaintBase) {
			return;
		}
		paintMap();
	}

	/**
	 * Stop the animation timer (e.g. when the display is closed).
	 */
	public void stop() {
		animationTimer.stop();
	}

	private void resizeCanvas() {
		baseCanvas.setWidth(Math.max(1, getWidth()));
		baseCanvas.setHeight(Math.max(1, getHeight()));
		dataCanvas.setWidth(Math.max(1, getWidth()));
		dataCanvas.setHeight(Math.max(1, getHeight()));
		repaint(true);
	}

	/**
	 * Request a repaint of the map.
	 * @param base - true to also redraw the base map (grid, contours, track).
	 */
	public void repaint(boolean base) {
		if (base) {
			repaintBase = true;
		}
		repaintData = true;
	}

	public MapParameters getMapParameters() {
		return simpleMap.getMapParameters();
	}

	/**
	 * Centre the map on the locked map centre, the ship or the master reference
	 * point.
	 */
	public void resetMapCentre() {
		MapParameters params = getMapParameters();
		if (params.lockMap && params.lockedMapCenter != null) {
			mapCentre = params.lockedMapCenter.clone();
			mapRangeMetres = params.lockedMapScale;
		}
		else {
			GpsData shipGps = getShipGps();
			if (shipGps != null) {
				mapCentre = new LatLong(shipGps.getLatitude(), shipGps.getLongitude());
			}
			else {
				LatLong ref = MasterReferencePoint.getLatLong();
				if (ref != null) {
					mapCentre = ref.clone();
				}
			}
		}
		repaint(true);
		notifyViewChanged();
	}

	/**
	 * Centre the map on the ship or reference position.
	 */
	public void centreOnShip() {
		GpsData shipGps = getShipGps();
		if (shipGps != null) {
			mapCentre = new LatLong(shipGps.getLatitude(), shipGps.getLongitude());
		}
		else {
			LatLong ref = MasterReferencePoint.getLatLong();
			if (ref != null) {
				mapCentre = ref.clone();
			}
		}
		repaint(true);
		notifyViewChanged();
	}

	/*
	 * Projection
	 */

	private void setupProjector() {
		double w = getWidth();
		double h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		double pixelsPerMetre = h / mapRangeMetres;
		projector.setMapCentreDegrees(mapCentre);
		projector.setMapRangeMetres(mapRangeMetres);
		projector.setPanelHeight(h);
		projector.setPanelWidth(w);
		projector.setPixelsPerMetre(pixelsPerMetre);
		ship.setPixelsPerMetre(pixelsPerMetre);
	}

	/**
	 * Paint the map.
	 */
	public void paintMap() {
		if (getWidth() <= 1 || getHeight() <= 1) {
			return;
		}
		if (PamController.getInstance().getPamStatus() == PamController.PAM_LOADINGDATA) {
			return;
		}
		try {
			updateShip();
			setupProjector();
			GPSDataBlock gpsDataBlock = simpleMap.getGpsDataBlock();
			int gpsCount = gpsDataBlock == null ? 0 : gpsDataBlock.getUnitsCount();
			long now = System.currentTimeMillis();
			// the track is drawn on the base map so redraw that regularly if new GPS data has arrived.
			if (gpsCount != lastGpsCount && now - lastBaseRepaint > 1000) {
				repaintBase = true;
			}
			if (repaintBase) {
				lastGpsCount = gpsCount;
				lastBaseRepaint = now;
				repaintBase = false;
				paintBase(baseGraphics);
			}
			repaintData = false;
			lastDataRepaint = now;
			paintData(dataGraphics);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get the time of the map
	 * @return the current time or the time of the viewer scroll bar.
	 */
	public long getMapTime() {
		if (timeRangeSupplier != null) {
			return timeRangeSupplier.getValueMillis();
		}
		return PamCalendar.getTimeInMillis();
	}

	private boolean isViewer() {
		return PamController.getInstance().getRunMode() == PamController.RUN_PAMVIEW;
	}

	/**
	 * Find the current ship GPS position.
	 * @return the ship position or null if there are no GPS data.
	 */
	public GpsData getShipGps() {
		GPSDataBlock gpsDataBlock = simpleMap.getGpsDataBlock();
		if (gpsDataBlock == null || gpsDataBlock.getUnitsCount() == 0) {
			return null;
		}
		GpsDataUnit gpsDataUnit;
		if (timeRangeSupplier != null) {
			gpsDataUnit = gpsDataBlock.getClosestUnitMillis(timeRangeSupplier.getValueMillis());
		}
		else {
			gpsDataUnit = gpsDataBlock.getLastUnit();
		}
		if (gpsDataUnit == null) {
			return null;
		}
		return gpsDataUnit.getGpsData();
	}

	/**
	 * Update the ship position, keep it on the map, and rotate the map if heading up.
	 */
	private void updateShip() {
		GpsData shipGps = getShipGps();
		ship.setShipGps(shipGps);
		if (shipGps == null) {
			return;
		}
		MapParameters params = getMapParameters();
		if (!centredOnFirstGps && !params.lockMap) {
			centredOnFirstGps = true;
			mapCentre = new LatLong(shipGps.getLatitude(), shipGps.getLongitude());
			repaintBase = true;
		}
		if (params.isHeadingUp()) {
			double rot = -shipGps.getCourseOverGround();
			if (rot != projector.getMapRotationDegrees()) {
				projector.setMapRotationDegrees(rot);
				repaintBase = true;
			}
		}
		if (params.lockMap) {
			return;
		}
		if (params.isKeepShipCentred()) {
			if (mapCentre.getLatitude() != shipGps.getLatitude() || mapCentre.getLongitude() != shipGps.getLongitude()) {
				mapCentre = new LatLong(shipGps.getLatitude(), shipGps.getLongitude());
				repaintBase = true;
			}
		}
		else if (params.isKeepShipOnMap() && getHeight() > 0) {
			setupProjector();
			Coordinate3d sc = projector.getCoord3d(shipGps.getLatitude(), shipGps.getLongitude(), 0);
			if (sc.x <= 0 || sc.x >= getWidth()) {
				mapCentre.setLongitude((shipGps.getLongitude() + mapCentre.getLongitude()) / 2.);
				repaintBase = true;
			}
			if (sc.y <= 0 || sc.y >= getHeight()) {
				mapCentre.setLatitude((shipGps.getLatitude() + mapCentre.getLatitude()) / 2.);
				repaintBase = true;
			}
		}
	}

	/*
	 * Base map drawing
	 */

	private void paintBase(FXGraphics2D g) {
		int w = (int) getWidth();
		int h = (int) getHeight();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1));
		calculatePlotLimits();

		GraphicsContext gc = baseCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, w, h);

		if (isFillSurface()) {
			g.setColor(PamColors.getInstance().getColor(PamColor.MAP));
			g.fillRect(0, 0, w, h);
		}
		else {
			g.setColor(PamColors.getInstance().getColor(PamColor.PlOTWINDOW));
			g.fillRect(0, 0, w, h);
			drawRotatedSurface(g);
		}

		// raster bathymetry
		GridbaseControl gridbaseControl = simpleMap.getGridBaseControl();
		if (gridbaseControl != null) {
			try {
				MapRasterImage rasterImage = gridbaseControl.getImage(null, null, false);
				if (rasterImage != null) {
					gridbaseControl.getSwingPainter().paintMapImage(g, projector, rasterImage);
				}
			}
			catch (Exception e) {
				System.err.println("Map: unable to draw raster map: " + e.getMessage());
			}
		}

		if (!getMapParameters().isHideGrid()) {
			paintGrid(g);
		}

		paintContours(g);

		PamArray currentArray = ArrayManager.getArrayManager().getCurrentArray();
		if (currentArray == null || !currentArray.getHydrophoneLocator().isStatic()) {
			paintTrack(g);
		}
	}

	private Color getSurfaceColour() {
		Color currentColor = PamColors.getInstance().getColor(PamColor.MAP);
		if (Math.cos(Math.toRadians(projector.getMapVerticalRotationDegrees())) < 0) {
			float uwScale = 255.f * 1.1f; // scale for seeing the surface from below ....
			return new Color(currentColor.getRed() / uwScale, currentColor.getGreen() / uwScale,
					currentColor.getBlue() / uwScale, 1.f);
		}
		else {
			return currentColor;
		}
	}

	private boolean isFillSurface() {
		return (projector.getMapVerticalRotationDegrees() == 0. && !getMapParameters().hideSurface);
	}

	private void drawRotatedSurface(Graphics2D g) {
		int[] px = new int[4];
		int[] py = new int[4];
		Coordinate3d p;
		p = projector.getCoord3d(minCornerLatLong.getLatitude(), minCornerLatLong.getLongitude(), 0);
		px[0] = (int) p.x;
		py[0] = (int) p.y;
		p = projector.getCoord3d(minCornerLatLong.getLatitude(), maxCornerLatLong.getLongitude(), 0);
		px[1] = (int) p.x;
		py[1] = (int) p.y;
		p = projector.getCoord3d(maxCornerLatLong.getLatitude(), maxCornerLatLong.getLongitude(), 0);
		px[2] = (int) p.x;
		py[2] = (int) p.y;
		p = projector.getCoord3d(maxCornerLatLong.getLatitude(), minCornerLatLong.getLongitude(), 0);
		px[3] = (int) p.x;
		py[3] = (int) p.y;
		g.setColor(getSurfaceColour());
		if (getMapParameters().hideSurface) {
			g.drawPolygon(px, py, 4);
		}
		else {
			g.fillPolygon(px, py, 4);
		}
	}

	/**
	 * Calculate the lat long limits of the visible map.
	 */
	private void calculatePlotLimits() {
		double w = getWidth();
		double h = getHeight();
		LatLong[] cornerLatLong = new LatLong[4];
		if (isFillSurface()) {
			cornerLatLong[0] = projector.panel2LL(0, 0);
			cornerLatLong[1] = projector.panel2LL(0, h);
			cornerLatLong[2] = projector.panel2LL(w, h);
			cornerLatLong[3] = projector.panel2LL(w, 0);
		}
		else {
			double mapScale = projector.getPixelsPerMetre();
			double halfAngle = Math.toRadians(15);
			double correction = Math.tan(halfAngle) / halfAngle;
			double hLatLong = h / mapScale / 2. * correction;
			halfAngle *= w / h;
			correction = Math.tan(halfAngle) / halfAngle;
			double widLatLong = w / mapScale / 2. * correction;
			LatLong centLatLong = projector.getMapCentreDegrees();
			cornerLatLong[0] = centLatLong.addDistanceMeters(-widLatLong, -hLatLong);
			cornerLatLong[1] = centLatLong.addDistanceMeters(-widLatLong, hLatLong);
			cornerLatLong[2] = centLatLong.addDistanceMeters(widLatLong, hLatLong);
			cornerLatLong[3] = centLatLong.addDistanceMeters(widLatLong, -hLatLong);
		}
		double centreLong = projector.getMapCentreDegrees().getLongitude();
		for (int i = 1; i < 4; i++) {
			double cl = PamUtils.constrainedAngle(cornerLatLong[i].getLongitude(), 180 + centreLong);
			cornerLatLong[i].setLongitude(cl);
		}
		minCornerLatLong = cornerLatLong[0].clone();
		maxCornerLatLong = cornerLatLong[0].clone();
		for (int i = 1; i < 4; i++) {
			LatLong ll = cornerLatLong[i];
			minCornerLatLong.setLatitude(Math.min(minCornerLatLong.getLatitude(), ll.getLatitude()));
			minCornerLatLong.setLongitude(Math.min(minCornerLatLong.getLongitude(), ll.getLongitude()));
			maxCornerLatLong.setLatitude(Math.max(maxCornerLatLong.getLatitude(), ll.getLatitude()));
			maxCornerLatLong.setLongitude(Math.max(maxCornerLatLong.getLongitude(), ll.getLongitude()));
		}
		minCornerLatLong.setLatitude(Math.max(minCornerLatLong.getLatitude(), -89.9));
		maxCornerLatLong.setLatitude(Math.min(maxCornerLatLong.getLatitude(), 89.9));
	}

	/**
	 * Nice grid intervals in minutes of arc.
	 */
	private static final double[] gridSteps = { 1. / 600., 1. / 300., 1. / 120., 1. / 60., 1. / 30., 1. / 12., 1. / 6., 1. / 4., 1. / 2., 1., 2., 5., 10., 15., 30., 60., 120.,
			300., 600., 900., 1200., 1800., 2700. };

	/**
	 * Pick a grid step in minutes so that there are a sensible number of grid
	 * lines across the map.
	 * @param rangeDegrees - the range of the map in degrees.
	 * @return the grid step in degrees.
	 */
	private double pickGridStep(double rangeDegrees, int maxLines) {
		double rangeMinutes = rangeDegrees * 60;
		for (int i = 0; i < gridSteps.length; i++) {
			if (rangeMinutes / gridSteps[i] <= maxLines) {
				return gridSteps[i] / 60.;
			}
		}
		return gridSteps[gridSteps.length - 1] / 60.;
	}

	/**
	 * Paint the latitude and longitude grid with labels.
	 */
	private void paintGrid(FXGraphics2D g) {
		if (minCornerLatLong == null || maxCornerLatLong == null) {
			return;
		}
		double minLat = minCornerLatLong.getLatitude();
		double maxLat = maxCornerLatLong.getLatitude();
		double minLon = minCornerLatLong.getLongitude();
		double maxLon = maxCornerLatLong.getLongitude();
		int maxLines = (int) Math.max(3, Math.min(12, getHeight() / 70));
		double latStep = pickGridStep(maxLat - minLat, maxLines);
		int maxLonLines = (int) Math.max(3, Math.min(12, getWidth() / 90));
		double lonStep = pickGridStep(maxLon - minLon, maxLonLines);

		Color lineColour = PamColors.getInstance().getColor(PamColor.LATLINE);
		g.setStroke(new BasicStroke(1f));
		g.setFont(new Font("Arial", Font.PLAIN, 11));
		int nSeg = 20;

		// latitude lines.
		double lat = Math.ceil(minLat / latStep) * latStep;
		for (; lat <= maxLat; lat += latStep) {
			g.setColor(lineColour);
			Coordinate3d prev = null;
			for (int i = 0; i <= nSeg; i++) {
				double lon = minLon + (maxLon - minLon) * i / nSeg;
				Coordinate3d c = projector.getCoord3d(lat, lon, 0);
				if (prev != null) {
					g.drawLine((int) prev.x, (int) prev.y, (int) c.x, (int) c.y);
				}
				prev = c;
			}
			drawGridLabel(g, LatLong.formatLatitude(lat), lat, minLon, maxLon, true, lineColour);
		}

		// longitude lines
		double lon = Math.ceil(minLon / lonStep) * lonStep;
		for (; lon <= maxLon; lon += lonStep) {
			g.setColor(lineColour);
			Coordinate3d prev = null;
			for (int i = 0; i <= nSeg; i++) {
				double la = minLat + (maxLat - minLat) * i / nSeg;
				Coordinate3d c = projector.getCoord3d(la, lon, 0);
				if (prev != null) {
					g.drawLine((int) prev.x, (int) prev.y, (int) c.x, (int) c.y);
				}
				prev = c;
			}
			drawGridLabel(g, LatLong.formatLongitude(PamUtils.constrainedAngle(lon, 180)), lon, minLat, maxLat, false, lineColour);
		}
	}

	/**
	 * Draw a grid label at the point where the grid line enters the visible map.
	 */
	private void drawGridLabel(FXGraphics2D g, String text, double fixedValue, double min, double max, boolean isLat,
			Color colour) {
		int nTest = 200;
		double w = getWidth();
		double h = getHeight();
		for (int i = 0; i <= nTest; i++) {
			double v = min + (max - min) * i / nTest;
			Coordinate3d c = isLat ? projector.getCoord3d(fixedValue, v, 0) : projector.getCoord3d(v, fixedValue, 0);
			if (c.x >= 0 && c.x < w && c.y >= 0 && c.y < h) {
				int textWidth = g.getFontMetrics().stringWidth(text);
				// keep latitude labels clear of the map controls on the left.
				int minX = isLat ? 52 : 2;
				int x = (int) Math.min(Math.max(c.x + 2, minX), w - textWidth - 2);
				int y = (int) Math.min(Math.max(c.y - 3, 12), h - 3);
				g.setColor(colour.darker());
				g.drawString(text, x, y);
				return;
			}
		}
	}

	private boolean[] wantedContours;

	private Color[] contourColours;

	private boolean prepareContourData() {
		MapFileManager mapManager = simpleMap.getMapFileManager();
		if (mapManager == null) {
			return false;
		}
		Vector<Integer> availableContours = mapManager.getAvailableContours();
		boolean[] mapContours = getMapParameters().getMapContours();
		if (availableContours == null || availableContours.size() == 0 || mapContours == null) {
			return false;
		}
		int nPossibles = Math.min(availableContours.size(), mapContours.length);
		if (nPossibles == 0) {
			return false;
		}
		wantedContours = new boolean[nPossibles];
		contourColours = new Color[nPossibles];
		int nUsed = 0;
		for (int i = 0; i < nPossibles; i++) {
			if (mapContours[i]) {
				nUsed++;
			}
		}
		int iUsed = 0;
		for (int i = 0; i < nPossibles; i++) {
			wantedContours[i] = mapContours[i];
			if (wantedContours[i]) {
				contourColours[i] = getContourColour(iUsed++, nUsed);
			}
		}
		return nUsed > 0;
	}

	/**
	 * Same contour colours as the Swing map.
	 */
	public static Color getContourColour(int listPos, int nContours) {
		if (listPos == 0) {
			return Color.BLACK;
		}
		if (listPos >= nContours) {
			return Color.WHITE;
		}
		ColourArray colourArray = ColourArray.createMergedArray(nContours - 1, Color.GREEN, Color.BLUE);
		return colourArray.getColours()[listPos - 1];
	}

	private void paintContours(Graphics2D g) {
		if (!prepareContourData()) {
			return;
		}
		MapFileManager mapManager = simpleMap.getMapFileManager();
		int nSegments = mapManager.getContourCount();
		g.setStroke(new BasicStroke(1));
		for (int i = 0; i < nSegments; i++) {
			MapContour mapContour = mapManager.getMapContour(i);
			int ci = mapManager.getContourIndex(mapContour.getDepth());
			if (ci < 0 || ci >= contourColours.length || contourColours[ci] == null) {
				continue;
			}
			g.setColor(contourColours[ci]);
			paintContour(g, mapContour);
		}
	}

	private void paintContour(Graphics2D g2d, MapContour mapContour) {
		Vector<LatLong> latLongs = mapContour.getLatLongs();
		if (latLongs == null || latLongs.size() < 2) {
			return;
		}
		LatLong latLong = latLongs.get(0);
		LatLong prevLatLong = latLong;
		Coordinate3d c1 = projector.getCoord3d(latLong.getLatitude(), latLong.getLongitude(), -latLong.getHeight());
		Coordinate3d c2;
		for (int i = 1; i < latLongs.size(); i++) {
			latLong = latLongs.get(i);
			c2 = projector.getCoord3d(latLong.getLatitude(), latLong.getLongitude(), -latLong.getHeight());
			if (wantContour(prevLatLong, latLong)) {
				if ((int) c1.x != (int) c2.x || (int) c1.y != (int) c2.y) {
					g2d.drawLine((int) c1.x, (int) c1.y, (int) c2.x, (int) c2.y);
				}
			}
			c1 = c2;
			prevLatLong = latLong;
		}
	}

	private boolean wantContour(LatLong prevLatLong, LatLong latLong) {
		if (minCornerLatLong == null || maxCornerLatLong == null) {
			return false;
		}
		double centralLong = projector.getMapCentreDegrees().getLongitude();
		double prevLong = PamUtils.constrainedAngle(prevLatLong.getLongitude(), 180 + centralLong);
		double thisLong = PamUtils.constrainedAngle(latLong.getLongitude(), 180 + centralLong);
		double maxCornerLong = PamUtils.constrainedAngle(maxCornerLatLong.getLongitude(), 180 + centralLong);
		double minCornerLong = PamUtils.constrainedAngle(minCornerLatLong.getLongitude(), 180 + centralLong);
		if ((Math.abs(thisLong - centralLong) > 170) && (Math.abs(prevLong) - centralLong > 170)) {
			return false;
		}
		if ((prevLatLong.getLatitude() >= minCornerLatLong.getLatitude()
				&& prevLatLong.getLatitude() <= maxCornerLatLong.getLatitude() && prevLong >= minCornerLong
				&& prevLong <= maxCornerLong)
				|| (latLong.getLatitude() >= minCornerLatLong.getLatitude()
						&& latLong.getLatitude() <= maxCornerLatLong.getLatitude() && thisLong >= minCornerLong
						&& thisLong <= maxCornerLong)) {
			return true;
		}
		return PamUtils.doesLineIntersectRect(prevLong, prevLatLong.getLatitude(), thisLong, latLong.getLatitude(),
				minCornerLong, minCornerLatLong.getLatitude(), maxCornerLong, maxCornerLatLong.getLatitude());
	}

	private long getMapStartTime() {
		switch (PamController.getInstance().getRunMode()) {
		case PamController.RUN_NORMAL:
		case PamController.RUN_MIXEDMODE:
			return PamCalendar.getTimeInMillis() - getMapParameters().getTrackShowTime() * 1000L;
		case PamController.RUN_PAMVIEW:
			if (timeRangeSupplier != null) {
				return timeRangeSupplier.getMinimumMillis();
			}
			return PamCalendar.getSessionStartTime();
		}
		return PamCalendar.getTimeInMillis();
	}

	private EffortProvider findEffortProvider() {
		if (!getMapParameters().colourByEffort) {
			return null;
		}
		PamDataBlock effortBlock = simpleMap.getEffortDataBlock();
		if (effortBlock == null) {
			return null;
		}
		return effortBlock.getEffortProvider();
	}

	/**
	 * Paint the ship's track, coloured by effort if required.
	 */
	private void paintTrack(Graphics2D g) {
		GPSDataBlock gpsDataBlock = simpleMap.getGpsDataBlock();
		if (gpsDataBlock == null || gpsDataBlock.getUnitsCount() < 2) {
			return;
		}
		PamSymbolChooser symbolModifier = null;
		EffortDataUnit currentEffortThing = null;
		EffortProvider effortProvider = findEffortProvider();
		PamSymbol effortSymbol = null;
		Iterator<EffortDataUnit> effortIterator = null;
		if (effortProvider != null) {
			symbolModifier = effortProvider.getSymbolChooser(simpleMap.getSelectorName(), projector);
			List<EffortDataUnit> effortThings = effortProvider.getAllEffortThings();
			if (effortThings != null && effortThings.size() > 0) {
				effortIterator = effortThings.iterator();
			}
			if (effortIterator != null && effortIterator.hasNext()) {
				currentEffortThing = effortIterator.next();
				if (symbolModifier != null) {
					effortSymbol = effortProvider.getPamSymbol(symbolModifier, currentEffortThing);
				}
			}
		}
		Color defaultColour = PamColors.getInstance().getColor(PamColor.GPSTRACK);
		PamSymbol gpsSymbol = null;
		try {
			PamSymbolChooser sChooser = gpsDataBlock.getPamSymbolManager().getSymbolChooser(simpleMap.getSelectorName(),
					projector);
			gpsSymbol = sChooser.getPamSymbol(projector, null);
		}
		catch (Exception e) {
		}
		if (gpsSymbol != null) {
			defaultColour = gpsSymbol.getLineColor();
		}
		if (effortSymbol != null) {
			g.setStroke(new BasicStroke(effortSymbol.getLineThickness()));
		}
		else if (gpsSymbol != null) {
			g.setStroke(new BasicStroke(gpsSymbol.getLineThickness()));
		}
		else {
			g.setStroke(new BasicStroke(1));
		}
		g.setColor(defaultColour);

		long t1 = getMapStartTime(), t2 = Long.MAX_VALUE;
		if (isViewer() && timeRangeSupplier != null) {
			t1 = timeRangeSupplier.getMinimumMillis();
			t2 = timeRangeSupplier.getMaximumMillis();
		}
		long maxInterpTime = mapController.getMaxInterpolationTime() * 1000L;
		long lastFixTime, thisFixTime;
		synchronized (gpsDataBlock.getSynchLock()) {
			ListIterator<GpsDataUnit> gpsIterator = gpsDataBlock.getListIterator(0);
			GpsDataUnit dataUnit = gpsIterator.next();
			GpsData gpsData = dataUnit.getGpsData();
			lastFixTime = dataUnit.getTimeMilliseconds();
			Coordinate3d c1 = projector.getCoord3d(gpsData.getLatitude(), gpsData.getLongitude(), 0.);
			Coordinate3d c2;
			while (gpsIterator.hasNext()) {
				dataUnit = gpsIterator.next();
				while (currentEffortThing != null && dataUnit.getTimeMilliseconds() > currentEffortThing.getEffortEnd()) {
					if (effortIterator.hasNext()) {
						currentEffortThing = effortIterator.next();
						if (symbolModifier != null) {
							effortSymbol = effortProvider.getPamSymbol(symbolModifier, currentEffortThing);
						}
					}
					else {
						break;
					}
				}
				if (effortSymbol != null && currentEffortThing.inEffort(dataUnit.getTimeMilliseconds())) {
					g.setColor(effortSymbol.getLineColor());
				}
				else {
					g.setColor(defaultColour);
				}
				gpsData = dataUnit.getGpsData();
				if (!gpsData.isDataOk()) {
					continue;
				}
				c2 = projector.getCoord3d(gpsData.getLatitude(), gpsData.getLongitude(), 0.);
				thisFixTime = dataUnit.getTimeMilliseconds();
				if (thisFixTime >= t1 && thisFixTime <= t2 && thisFixTime - lastFixTime < maxInterpTime) {
					g.drawLine((int) c1.x, (int) c1.y, (int) c2.x, (int) c2.y);
				}
				lastFixTime = thisFixTime;
				c1 = c2;
			}
		}
	}

	/*
	 * Data layer drawing
	 */

	private void paintData(FXGraphics2D g) {
		int w = (int) getWidth();
		int h = (int) getHeight();
		GraphicsContext gc = dataCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, w, h);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1));

		projector.clearHoverList();

		paintRangeRings(g);

		MapParameters params = getMapParameters();
		if (!params.isHideShip()) {
			paintPredictedGPS(g);
			paintShip(g);
		}

		paintDetectorData(g);

		if (params.isShowHydrophones()) {
			paintHydrophones(g);
		}

		paintCompass(gc);

		paintScaleBar(gc);

		if (measuring) {
			paintMeasure(gc);
		}
	}

	private void paintRangeRings(Graphics2D g) {
		MapParameters params = getMapParameters();
		if (params.showRangeRings == MapParameters.RANGE_RINGS_NONE || minCornerLatLong == null) {
			return;
		}
		g.setColor(PamColors.getInstance().getColor(PamColor.LATLINE));
		g.setFont(new Font("Arial", Font.PLAIN, 11));
		double ringsRange = params.rangeRingDistance;
		String units = "m";
		if (params.showRangeRings == MapParameters.RANGE_RINGS_NMI) {
			ringsRange *= LatLong.MetersPerMile;
			units = "nmi";
		}
		else if (params.showRangeRings == MapParameters.RANGE_RINGS_KM) {
			ringsRange *= 1000;
			units = "km";
		}
		if (ringsRange <= 0) {
			return;
		}
		LatLong masterRef = MasterReferencePoint.getLatLong();
		if (masterRef == null) {
			masterRef = mapCentre;
		}
		double maxRange = masterRef.distanceToMetres(minCornerLatLong);
		maxRange = Math.max(maxRange, masterRef.distanceToMetres(maxCornerLatLong));
		int nRings = (int) Math.min(200, maxRange / ringsRange);
		double vertScale = Math.cos(Math.toRadians(projector.getMapVerticalRotationDegrees()));
		Coordinate3d centre = projector.getCoord3d(masterRef);
		for (int i = 1; i <= nRings; i++) {
			double xR = ringsRange * i * projector.getPixelsPerMetre();
			double yR = xR * vertScale;
			g.drawOval((int) (centre.x - xR), (int) (centre.y - yR), (int) (2 * xR), (int) (2 * yR));
			int textX = (int) (centre.x - xR * .707);
			int textY = (int) (centre.y - yR * .707);
			g.drawString(String.format("%s %s", formatNumber(params.rangeRingDistance * i), units), textX, textY);
		}
	}

	private static String formatNumber(double val) {
		if (val == Math.floor(val)) {
			return String.format("%d", (long) val);
		}
		return String.format("%.2f", val);
	}

	private void paintPredictedGPS(Graphics2D g) {
		if (isViewer()) {
			return;
		}
		GpsData gpsData = ship.getShipGps(false);
		if (gpsData == null) {
			return;
		}
		g.setColor(PamColors.getInstance().getColor(PamColor.GPSTRACK));
		Coordinate3d c1 = projector.getCoord3d(gpsData.getLatitude(), gpsData.getLongitude(), 0.);
		GpsData predicted = gpsData.getPredictedGPSData(PamCalendar.getTimeInMillis());
		Coordinate3d c2 = projector.getCoord3d(predicted.getLatitude(), predicted.getLongitude(), 0.);
		g.drawLine((int) c1.x, (int) c1.y, (int) c2.x, (int) c2.y);
	}

	private void paintShip(Graphics2D g) {
		if (ship.getShipGps(false) == null) {
			return;
		}
		GPSControl gpsControl = GPSControl.getGpsControl();
		if (gpsControl != null) {
			GPSParameters gpsParameters = gpsControl.getGpsParameters();
			ship.setVesselDimension(gpsParameters.dimA, gpsParameters.dimB, gpsParameters.dimC, gpsParameters.dimD);
			ship.setPredictionArrow(gpsParameters.plotPredictedPosition ? gpsParameters.predictionTime : 0);
		}
		ship.drawShip(g, projector);
	}

	private void paintHydrophones(Graphics2D g) {
		PamArray array = ArrayManager.getArrayManager().getCurrentArray();
		if (array == null) {
			return;
		}
		int phoneCount = array.getHydrophoneCount();
		if (phoneCount == 0) {
			return;
		}
		int phoneMap = (1 << phoneCount) - 1;
		if (phoneCount >= 32) {
			phoneMap = PamUtils.makeChannelMap(Math.min(phoneCount, 32));
		}
		SnapshotGeometry geometry = ArrayManager.getArrayManager().getSnapshotGeometry(phoneMap, getMapTime());
		if (geometry == null) {
			return;
		}
		GpsData refLatLong = geometry.getReferenceGPS();
		PamVector[] phoneVectors = geometry.getGeometry();
		if (refLatLong == null || phoneVectors == null) {
			return;
		}
		MapParameters params = getMapParameters();
		for (int i = 0; i < phoneVectors.length; i++) {
			if (phoneVectors[i] == null) {
				continue;
			}
			LatLong phonePos = refLatLong.addDistanceMeters(phoneVectors[i]);
			Coordinate3d c3d = projector.getCoord3d(phonePos.getLatitude(), phonePos.getLongitude(), phonePos.getHeight());
			PamSymbol symbol = array.getHydrophoneSymbol(i);
			double symSize = projector.symbolSizePerpective(params.getSymbolSize(), c3d);
			symbol.setWidth(symSize);
			symbol.setHeight(symSize);
			Color symbolColour = Color.blue;
			if (params.isColourHydrophonesByChannel()) {
				symbolColour = PamColors.getInstance().getChannelColor(i);
			}
			symbol.setLineColor(symbolColour);
			symbol.setFillColor(symbolColour);
			symbol.draw(g, c3d.getXYPoint());
		}
	}

	private void paintDetectorData(Graphics2D g) {
		ArrayList<PamDataBlock> blocks = simpleMap.getMapDetectionsManager().getPlottableBlocks();
		if (blocks == null) {
			return;
		}
		for (PamDataBlock dataBlock : new ArrayList<PamDataBlock>(blocks)) {
			try {
				paintDetectorData(g, dataBlock);
			}
			catch (Exception e) {
				System.err.println("Map: error drawing " + dataBlock.getDataName() + ": " + e.getMessage());
			}
		}
		projector.setProjectorDrawingOptions(null);
	}

	/**
	 * Paint the data from one data block. This follows the Swing map.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void paintDetectorData(Graphics2D g, PamDataBlock dataBlock) {
		MapDetectionData mapDetectionData = simpleMap.getMapDetectionsManager().findDetectionData(dataBlock);
		if (mapDetectionData == null || !mapDetectionData.select) {
			return;
		}
		long now = getMapTime();
		if (PamController.isNetRx()) {
			now = System.currentTimeMillis() + 20 * 1000L;
		}
		MapDrawingOptions mapDrawingOptions = null;
		if (mapDetectionData.fade) {
			mapDrawingOptions = new MapDrawingOptions(1);
			projector.setProjectorDrawingOptions(mapDrawingOptions);
		}
		else {
			projector.setProjectorDrawingOptions(null);
		}
		PamSymbolManager symbolManager = dataBlock.getPamSymbolManager();
		if (symbolManager != null) {
			PamSymbolChooser symbolChooser = symbolManager
					.getSymbolChooser(simpleMap.getMapDetectionsManager().getUnitName(), projector);
			projector.setPamSymbolChooser(symbolChooser);
		}
		else {
			projector.setPamSymbolChooser(null);
		}
		DataSelector ds = dataBlock.getDataSelector(simpleMap.getSelectorName(), false, DATASELECTNAME);
		projector.setDataSelector(ds);
		if (dataBlock.getOverlayDraw() == null) {
			return;
		}
		if (!dataBlock.getOverlayDraw().preDrawAnything(g, dataBlock, projector)) {
			return;
		}
		long earliestToPlot;
		Integer sliderTime = simpleMap.getHiddenSliderTime();
		if (mapDetectionData.allAvailable) {
			earliestToPlot = 0;
		}
		else if (sliderTime != null && !isViewer()) {
			earliestToPlot = now - 1000L * sliderTime;
		}
		else {
			earliestToPlot = now - mapDetectionData.getDisplayMilliseconds();
		}
		long latest = now;
		if (isViewer() && getMapParameters().isViewerShowAll() && timeRangeSupplier != null) {
			earliestToPlot = timeRangeSupplier.getMinimumMillis();
			latest = timeRangeSupplier.getMaximumMillis();
		}
		long dataEnd = latest;
		if (isViewer() && mapDetectionData.lookAhead && !getMapParameters().isViewerShowAll()) {
			dataEnd = now + Math.abs(now - earliestToPlot);
		}
		ArrayList<PamDataUnit> dataCopy = dataBlock.getDataCopy(Math.min(earliestToPlot, now), dataEnd, true, ds);
		for (PamDataUnit dataUnit : dataCopy) {
			if (!shouldPlot(dataUnit, mapDetectionData, earliestToPlot, now)) {
				continue;
			}
			projector.setDataSelector(ds);
			if (mapDrawingOptions != null) {
				mapDrawingOptions.calculateOpacity(now, earliestToPlot, dataUnit.getLastChangeTime());
			}
			dataBlock.drawDataUnit(g, dataUnit, projector);
		}
	}

	/**
	 * Decide whether a data unit should be plotted. Same rules as the Swing map,
	 * but using the JavaFX viewer scroll bar.
	 */
	private boolean shouldPlot(PamDataUnit pamDataUnit, MapDetectionData mapDetectionData, long earliestToPlot,
			long now) {
		if (mapDetectionData.allAvailable) {
			return true;
		}
		long unitTime = pamDataUnit.getTimeMilliseconds();
		if (isViewer()) {
			if (timeRangeSupplier != null && unitTime != 0) {
				if (unitTime < timeRangeSupplier.getMinimumMillis() || unitTime > timeRangeSupplier.getMaximumMillis()) {
					return false;
				}
			}
			if (getMapParameters().isViewerShowAll()) {
				return true;
			}
			if (!mapDetectionData.lookAhead) {
				return !(unitTime > now || unitTime < earliestToPlot);
			}
			else {
				long dispIntval = Math.abs(now - earliestToPlot);
				return !(unitTime < now || unitTime > now + dispIntval);
			}
		}
		long unitEndTime = Math.max(unitTime, pamDataUnit.getLastChangeTime());
		return now - unitEndTime <= mapDetectionData.getDisplayMilliseconds();
	}

	/**
	 * Draw a compass in the top right corner showing the direction of north.
	 */
	private void paintCompass(GraphicsContext gc) {
		double r = 20;
		double cx = getWidth() - r - 12;
		double cy = r + 12;
		gc.save();
		gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.8));
		gc.setStroke(javafx.scene.paint.Color.rgb(60, 60, 60, 0.8));
		gc.setLineWidth(1);
		gc.fillOval(cx - r, cy - r, 2 * r, 2 * r);
		gc.strokeOval(cx - r, cy - r, 2 * r, 2 * r);
		gc.translate(cx, cy);
		gc.rotate(projector.getMapRotationDegrees());
		// north half of the needle in red, south half in grey.
		gc.setFill(javafx.scene.paint.Color.rgb(220, 30, 30));
		gc.fillPolygon(new double[] { 0, -4, 4 }, new double[] { -r + 13, 0, 0 }, 3);
		gc.setFill(javafx.scene.paint.Color.rgb(150, 150, 150));
		gc.fillPolygon(new double[] { 0, -4, 4 }, new double[] { r - 5, 0, 0 }, 3);
		gc.setFill(javafx.scene.paint.Color.rgb(0, 0, 0));
		gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10));
		gc.fillText("N", -3.5, -r + 11);
		gc.restore();
	}

	/**
	 * Draw a scale bar in the bottom left of the map.
	 */
	private void paintScaleBar(GraphicsContext gc) {
		double ppm = projector.getPixelsPerMetre();
		if (ppm <= 0) {
			return;
		}
		double maxWidthPix = Math.min(150, getWidth() / 3);
		double maxMetres = maxWidthPix / ppm;
		double exp = Math.pow(10, Math.floor(Math.log10(maxMetres)));
		double nice = exp;
		for (double m : new double[] { 1, 2, 5 }) {
			if (m * exp <= maxMetres) {
				nice = m * exp;
			}
		}
		double barWidth = nice * ppm;
		String label = nice >= 1000 ? formatNumber(nice / 1000) + " km" : formatNumber(nice) + " m";
		double x = 12;
		double y = getHeight() - 28;
		gc.save();
		gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.75));
		gc.fillRoundRect(x - 6, y - 22, barWidth + 12, 30, 6, 6);
		gc.setStroke(javafx.scene.paint.Color.rgb(30, 30, 30));
		gc.setLineWidth(2);
		gc.strokeLine(x, y, x + barWidth, y);
		gc.strokeLine(x, y - 5, x, y + 3);
		gc.strokeLine(x + barWidth, y - 5, x + barWidth, y + 3);
		gc.setFill(javafx.scene.paint.Color.rgb(30, 30, 30));
		gc.setFont(javafx.scene.text.Font.font("Arial", 11));
		gc.fillText(label, x, y - 8);
		gc.restore();
	}

	private void paintMeasure(GraphicsContext gc) {
		gc.save();
		gc.setStroke(javafx.scene.paint.Color.RED);
		gc.setFill(javafx.scene.paint.Color.RED);
		gc.setLineWidth(1.5);
		gc.strokeLine(mousePressX, mousePressY, mouseDragX, mouseDragY);
		gc.fillOval(mousePressX - 3, mousePressY - 3, 6, 6);
		gc.fillOval(mouseDragX - 3, mouseDragY - 3, 6, 6);
		gc.restore();
	}

	/*
	 * Mouse handling
	 */

	private void addMouseHandlers() {
		this.setOnMousePressed(e -> {
			setupProjector();
			mousePressX = e.getX();
			mousePressY = e.getY();
			mouseDragX = e.getX();
			mouseDragY = e.getY();
			mousePressLatLong = projector.panel2LL(e.getX(), e.getY());
			pressRotation = projector.getMapRotationDegrees();
			pressVerticalRotation = projector.getMapVerticalRotationDegrees();
			rotating = false;
			measuring = false;
			if (e.getButton() == MouseButton.PRIMARY && (measureMode || e.isControlDown())) {
				measuring = true;
			}
			else if (getMapParameters().isAllow3D() && e.getButton() == MouseButton.PRIMARY && e.isShiftDown()) {
				rotating = true;
			}
			hoverTooltip.hide();
		});

		this.setOnMouseDragged(e -> {
			mouseDragX = e.getX();
			mouseDragY = e.getY();
			if (measuring) {
				LatLong end = projector.panel2LL(e.getX(), e.getY());
				for (MapCanvasListener l : listeners) {
					l.measure(mousePressLatLong, end);
				}
				repaint(false);
				return;
			}
			if (rotating) {
				double dx = e.getX() - mousePressX;
				double dy = e.getY() - mousePressY;
				projector.setMapRotationDegrees(pressRotation + dx / 3.);
				double vert = Math.max(-180, Math.min(180, pressVerticalRotation + dy / 3.));
				projector.setMapVerticalRotationDegrees(vert);
				repaint(true);
				notifyViewChanged();
				return;
			}
			if (e.getButton() == MouseButton.PRIMARY && !getMapParameters().lockMap) {
				panTo(e);
			}
		});

		this.setOnMouseReleased(e -> {
			if (measuring) {
				measuring = false;
				repaint(false);
				for (MapCanvasListener l : listeners) {
					l.measure(null, null);
				}
			}
			rotating = false;
		});

		this.setOnMouseMoved(e -> {
			setupProjector();
			lastMouseLatLong = projector.panel2LL(e.getX(), e.getY());
			for (MapCanvasListener l : listeners) {
				l.mouseMoved(lastMouseLatLong);
			}
			showHoverText(e);
		});

		this.setOnMouseExited(e -> {
			lastMouseLatLong = null;
			for (MapCanvasListener l : listeners) {
				l.mouseMoved(null);
			}
			hoverTooltip.hide();
		});

		this.setOnScroll((ScrollEvent e) -> {
			if (getMapParameters().lockMap || e.getDeltaY() == 0) {
				return;
			}
			double factor = e.getDeltaY() > 0 ? 1 / 1.2 : 1.2;
			zoomAbout(factor, e.getX(), e.getY());
		});
	}

	/**
	 * Move the map so that the lat long where the mouse was pressed is under the
	 * mouse.
	 */
	private void panTo(MouseEvent e) {
		if (mousePressLatLong == null) {
			return;
		}
		LatLong nowLL = projector.panel2LL(e.getX(), e.getY());
		double dLat = mousePressLatLong.getLatitude() - nowLL.getLatitude();
		double dLon = mousePressLatLong.getLongitude() - nowLL.getLongitude();
		mapCentre = new LatLong(Math.max(-89, Math.min(89, mapCentre.getLatitude() + dLat)),
				PamUtils.constrainedAngle(mapCentre.getLongitude() + dLon, 180));
		setupProjector();
		repaint(true);
		notifyViewChanged();
	}

	/**
	 * Zoom the map keeping a point on the screen in the same place.
	 * @param factor - zoom factor (< 1 zooms in)
	 * @param x - x pixel to zoom about.
	 * @param y - y pixel to zoom about.
	 */
	public void zoomAbout(double factor, double x, double y) {
		setupProjector();
		LatLong before = projector.panel2LL(x, y);
		setMapRange(mapRangeMetres * factor);
		setupProjector();
		LatLong after = projector.panel2LL(x, y);
		mapCentre = new LatLong(mapCentre.getLatitude() + before.getLatitude() - after.getLatitude(),
				PamUtils.constrainedAngle(mapCentre.getLongitude() + before.getLongitude() - after.getLongitude(), 180));
		repaint(true);
		notifyViewChanged();
	}

	/**
	 * Zoom in or out about the centre of the map.
	 * @param factor - zoom factor (< 1 zooms in).
	 */
	public void zoom(double factor) {
		if (getMapParameters().lockMap) {
			return;
		}
		setMapRange(mapRangeMetres * factor);
		repaint(true);
		notifyViewChanged();
	}

	private void setMapRange(double range) {
		mapRangeMetres = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
		// keep the parameters up to date so that the range is saved.
		getMapParameters().setMapRangeMetres((int) Math.round(mapRangeMetres));
	}

	/**
	 * Reset any map rotation to north up, flat map.
	 */
	public void northUp() {
		getMapParameters().setHeadingUp(false);
		projector.setMapRotationDegrees(0);
		projector.setMapVerticalRotationDegrees(0);
		repaint(true);
		notifyViewChanged();
	}

	private void showHoverText(MouseEvent e) {
		String text = null;
		try {
			text = projector.getHoverText(new Point((int) e.getX(), (int) e.getY()));
		}
		catch (Exception ex) {
			text = null;
		}
		if (text == null || text.length() == 0) {
			hoverTooltip.hide();
			return;
		}
		text = text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)<p>", "\n").replaceAll("<[^>]*>", "");
		hoverTooltip.setText(text.trim());
		if (getScene() != null && getScene().getWindow() != null) {
			hoverTooltip.show(this, e.getScreenX() + 12, e.getScreenY() + 12);
		}
	}

	private void notifyViewChanged() {
		for (MapCanvasListener l : listeners) {
			l.viewChanged();
		}
	}

	public void addMapCanvasListener(MapCanvasListener listener) {
		listeners.add(listener);
	}

	public void setMeasureMode(boolean measureMode) {
		this.measureMode = measureMode;
		this.setCursor(measureMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
	}

	public boolean isMeasureMode() {
		return measureMode;
	}

	public MapProjectorFX getProjector() {
		return projector;
	}

	public LatLong getMapCentre() {
		return mapCentre;
	}

	public void setMapCentre(LatLong mapCentre) {
		this.mapCentre = mapCentre.clone();
		repaint(true);
		notifyViewChanged();
	}

	public double getMapRangeMetres() {
		return mapRangeMetres;
	}

	public void setTimeRangeSupplier(TimeRangeSupplier timeRangeSupplier) {
		this.timeRangeSupplier = timeRangeSupplier;
	}

	public LatLong getLastMouseLatLong() {
		return lastMouseLatLong;
	}

	/**
	 * Called when the map parameters have changed.
	 */
	public void newParameters() {
		MapParameters params = getMapParameters();
		if (params.lockMap && params.lockedMapCenter != null) {
			mapCentre = params.lockedMapCenter.clone();
			mapRangeMetres = params.lockedMapScale;
		}
		if (!params.isHeadingUp() && !params.isAllow3D()) {
			projector.setMapRotationDegrees(0);
		}
		if (!params.isAllow3D()) {
			projector.setMapVerticalRotationDegrees(0);
		}
		repaint(true);
		notifyViewChanged();
	}

	/**
	 * Get a snapshot image of the whole map.
	 * @return image of the map.
	 */
	public javafx.scene.image.WritableImage getSnapshot() {
		return this.snapshot(null, null);
	}

}
