package Map.layoutFX;

import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

import GPS.GpsData;
import Map.MapController;
import Map.MapDetectionData;
import Map.MapFileManager;
import Map.MapParameters;
import Map.SimpleMap;
import PamController.PamController;
import PamController.PamControllerInterface;
import PamController.PamControlledUnitSettings;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamController.masterReference.MasterReferencePoint;
import PamUtils.LatLong;
import PamUtils.PamCalendar;
import PamView.PamSymbol;
import PamView.symbol.PamSymbolChooser;
import PamView.symbol.PamSymbolManager;
import PamView.symbol.SymbolData;
import PamguardMVC.PamDataBlock;
import geoMag.MagneticVariation;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.hidingPane.HidingPane;
import pamViewFX.fxNodes.internalNode.PamInternalPane;
import pamViewFX.fxNodes.pamScrollers.acousticScroller.AcousticScrollerFX;
import pamViewFX.fxStyles.PamStylesManagerFX;
import pamViewFX.fxgraphics2d.FXGraphics2D;
import pamScrollSystem.AbstractPamScroller;
import pamScrollSystem.PamScrollObserver;
import userDisplayFX.UserDisplayControlFX;
import userDisplayFX.UserDisplayNodeFX;
import userDisplayFX.UserDisplayNodeParams;

/**
 * JavaFX map display. Shows the ship, its track, hydrophones and detection
 * overlays on a map with a latitude and longitude grid, optional Gebco
 * contours and raster bathymetry. Has pan and zoom controls, a measuring
 * tool, GPS and cursor position readouts, a key, and a sliding settings pane
 * for the map options and the data overlays. In viewer mode a scroll bar
 * controls which data are loaded and shown.
 *
 * @author Jamie Macaulay
 *
 */
public class MapDisplayFX extends PamBorderPane implements UserDisplayNodeFX, PamSettings {

	private static final double SETTINGS_WIDTH = 440;

	private MapController mapController;

	private SimpleMap simpleMap;

	private MapCanvasFX mapCanvas;

	private StackPane holder;

	private HidingPane hidingPane;

	private MapSettingsPaneFX settingsPane;

	private MapOverlayPaneFX overlayPane;

	/**
	 * GPS and cursor info.
	 */
	private Label gpsTitle, latLabel, lonLabel, timeLabel, lastFixLabel, courseLabel, headingLabel, speedLabel,
			magVarLabel;

	private Label cursorLatLabel, cursorLonLabel, cursorRangeLabel, cursorBearingLabel;

	private Label measureLabel;

	private PamVBox infoPane;

	private PamVBox keyPane;

	private PamVBox measurePane;

	private ToggleButton measureButton;

	private AcousticScrollerFX viewerScroller;

	private javafx.animation.Timeline infoTimer;

	/**
	 * Position and tab of the display.
	 */
	private UserDisplayNodeParams displayParams = new UserDisplayNodeParams();

	public MapDisplayFX(MapController mapController) {
		this.mapController = mapController;
		this.simpleMap = mapController.getMapTabPanelControl().getSimpleMap();
		createPane();
		PamSettingManager.getInstance().registerSettings(this);
	}

	private boolean isViewer() {
		return PamController.getInstance().getRunMode() == PamController.RUN_PAMVIEW;
	}

	private void createPane() {
		mapCanvas = new MapCanvasFX(mapController, simpleMap);

		holder = new StackPane();
		holder.getChildren().add(mapCanvas);

		// controls on the top left
		PamVBox controls = createMapControls();
		StackPane.setAlignment(controls, Pos.TOP_LEFT);
		StackPane.setMargin(controls, new Insets(10));
		controls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
		controls.setPickOnBounds(false);

		// gps and cursor info on the bottom right.
		infoPane = createInfoPane();
		StackPane.setAlignment(infoPane, Pos.BOTTOM_RIGHT);
		StackPane.setMargin(infoPane, new Insets(10));
		infoPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

		// key in the bottom left above the scale bar.
		keyPane = new PamVBox();
		keyPane.setSpacing(2);
		keyPane.setPadding(new Insets(5));
		keyPane.setStyle("-fx-background-color: rgba(255,255,255,0.8); -fx-background-radius: 5;");
		StackPane.setAlignment(keyPane, Pos.BOTTOM_LEFT);
		StackPane.setMargin(keyPane, new Insets(10, 10, 64, 10));
		keyPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
		keyPane.setMouseTransparent(true);

		measurePane = new PamVBox();
		measurePane.setPadding(new Insets(5));
		measurePane.setStyle("-fx-background-color: rgba(255,255,255,0.85); -fx-background-radius: 5;");
		measurePane.getChildren().add(measureLabel = new Label());
		measureLabel.setStyle("-fx-text-fill: black;");
		StackPane.setAlignment(measurePane, Pos.TOP_CENTER);
		StackPane.setMargin(measurePane, new Insets(10));
		measurePane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
		measurePane.setVisible(false);
		measurePane.setMouseTransparent(true);

		holder.getChildren().addAll(keyPane, infoPane, measurePane, controls);

		// settings
		createSettingsPane();

		mapCanvas.addMapCanvasListener(new MapCanvasFX.MapCanvasListener() {
			@Override
			public void mouseMoved(LatLong latLong) {
				updateCursorInfo(latLong);
			}

			@Override
			public void measure(LatLong start, LatLong end) {
				showMeasurement(start, end);
			}

			@Override
			public void viewChanged() {
			}
		});

		createContextMenu();

		this.setCenter(holder);

		if (isViewer()) {
			createViewerScroller();
		}

		// update the info once a second.
		infoTimer = new javafx.animation.Timeline(
				new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateGpsInfo()));
		infoTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
		infoTimer.play();

		showMapObjects();
	}

	/**
	 * Create the zoom and measure buttons.
	 */
	private PamVBox createMapControls() {
		PamButton zoomIn = createMapButton("mdi2p-plus", "Zoom in");
		zoomIn.setOnAction(e -> mapCanvas.zoom(1 / 1.5));
		PamButton zoomOut = createMapButton("mdi2m-minus", "Zoom out");
		zoomOut.setOnAction(e -> mapCanvas.zoom(1.5));
		PamButton centre = createMapButton("mdi2c-crosshairs-gps", "Centre map on the vessel or reference position");
		centre.setOnAction(e -> mapCanvas.centreOnShip());
		PamButton north = createMapButton("mdi2c-compass-outline", "Rotate map to north up");
		north.setOnAction(e -> mapCanvas.northUp());

		measureButton = new ToggleButton();
		measureButton.setGraphic(PamGlyphDude.createPamIcon("mdi2r-ruler", PamGuiManagerFX.iconSize));
		measureButton.setTooltip(new Tooltip("Measure the range and bearing between two points. Drag on the map to measure. "
				+ "\nYou can also hold the Ctrl key and drag."));
		measureButton.selectedProperty().addListener((obs, o, n) -> mapCanvas.setMeasureMode(n));
		measureButton.setPrefSize(32, 32);

		PamVBox vBox = new PamVBox(zoomIn, zoomOut, centre, north, measureButton);
		vBox.setSpacing(4);
		return vBox;
	}

	private PamButton createMapButton(String icon, String tip) {
		PamButton button = new PamButton();
		button.setGraphic(PamGlyphDude.createPamIcon(icon, PamGuiManagerFX.iconSize));
		button.setTooltip(new Tooltip(tip));
		button.setPrefSize(32, 32);
		return button;
	}

	private Label infoLabel(String text) {
		Label label = new Label(text);
		label.setStyle("-fx-text-fill: #202020;");
		return label;
	}

	private Label infoTitle(String text) {
		Label label = new Label(text);
		label.setStyle("-fx-text-fill: #202020; -fx-font-weight: bold;");
		return label;
	}

	/**
	 * Create the pane which shows GPS and cursor information.
	 */
	private PamVBox createInfoPane() {
		PamGridPane gps = new PamGridPane();
		gps.setHgap(8);
		gps.setVgap(1);
		int row = 0;
		gps.add(gpsTitle = infoTitle("GPS"), 0, row++, 2, 1);
		gps.add(infoLabel("Latitude"), 0, row);
		gps.add(latLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Longitude"), 0, row);
		gps.add(lonLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Time"), 0, row);
		gps.add(timeLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Last fix"), 0, row);
		gps.add(lastFixLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Course"), 0, row);
		gps.add(courseLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Heading"), 0, row);
		gps.add(headingLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Speed"), 0, row);
		gps.add(speedLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Mag. var."), 0, row);
		gps.add(magVarLabel = infoLabel(""), 1, row++);

		gps.add(infoTitle("Cursor"), 0, row++, 2, 1);
		gps.add(infoLabel("Latitude"), 0, row);
		gps.add(cursorLatLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Longitude"), 0, row);
		gps.add(cursorLonLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Bearing"), 0, row);
		gps.add(cursorBearingLabel = infoLabel(""), 1, row++);
		gps.add(infoLabel("Range"), 0, row);
		gps.add(cursorRangeLabel = infoLabel(""), 1, row++);

		PamVBox vBox = new PamVBox(gps);
		vBox.setPadding(new Insets(6));
		vBox.setStyle("-fx-background-color: rgba(255,255,255,0.8); -fx-background-radius: 5;");
		vBox.setMouseTransparent(true);
		return vBox;
	}

	private void createSettingsPane() {
		settingsPane = new MapSettingsPaneFX(simpleMap);
		settingsPane.setMapCanvas(mapCanvas);

		overlayPane = new MapOverlayPaneFX(simpleMap, mapCanvas);
		overlayPane.addOverlayListener(() -> {
			createKey();
			checkViewerData();
		});
		overlayPane.setPadding(new Insets(10));

		PamButton applyButton = new PamButton("Apply");
		applyButton.setTooltip(new Tooltip("Apply the map options"));
		applyButton.setOnAction(e -> applySettings());
		PamButton resetButton = new PamButton("Reset");
		resetButton.setTooltip(new Tooltip("Reset the controls to the current map options"));
		resetButton.setOnAction(e -> settingsPane.setParams(simpleMap.getMapParameters()));
		PamHBox buttons = new PamHBox(resetButton, applyButton);
		buttons.setSpacing(5);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		buttons.setPadding(new Insets(5, 10, 5, 10));

		PamBorderPane optionsHolder = new PamBorderPane();
		optionsHolder.setCenter(settingsPane.getContentNode());
		optionsHolder.setBottom(buttons);

		TabPane tabPane = new TabPane();
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabPane.getTabs().add(new Tab("Overlays", overlayPane));
		tabPane.getTabs().add(new Tab("Map Options", optionsHolder));
		tabPane.setPrefWidth(SETTINGS_WIDTH);
		tabPane.setMaxWidth(SETTINGS_WIDTH);
		tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
			if (n.intValue() == 0) {
				overlayPane.refresh();
			}
			else {
				settingsPane.setParams(simpleMap.getMapParameters());
			}
		});

		PamBorderPane settingsHolder = new PamBorderPane(tabPane);
		settingsHolder.setPadding(new Insets(35, 0, 0, 0));
		settingsHolder.setPrefWidth(SETTINGS_WIDTH);
		settingsHolder.setMaxWidth(SETTINGS_WIDTH);

		hidingPane = new HidingPane(Side.RIGHT, settingsHolder, holder, true);
		hidingPane.setVisibleImmediatly(false);
		hidingPane.showHidePane(false);
		hidingPane.getStyleClass().add("pane-trans");
		PamStylesManagerFX.getPamStylesManagerFX().styleNode(hidingPane, PamStylesManagerFX.STYLE_SLIDING);
		StackPane.setAlignment(hidingPane, Pos.TOP_RIGHT);
		hidingPane.setPrefWidth(SETTINGS_WIDTH);
		hidingPane.setMaxWidth(SETTINGS_WIDTH);

		PamButton showButton = hidingPane.getShowButton();
		showButton.getStyleClass().add("close-button-left");
		PamStylesManagerFX.getPamStylesManagerFX().styleNode(showButton, PamStylesManagerFX.STYLE_SLIDING);
		showButton.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog", PamGuiManagerFX.iconSize));
		showButton.getStyleClass().add("icon-button");
		showButton.setPrefHeight(60);
		showButton.setTooltip(new Tooltip("Show the map overlays and options"));
		showButton.setOnMouseClicked(e -> {
			overlayPane.refresh();
			settingsPane.setParams(simpleMap.getMapParameters());
		});
		StackPane.setAlignment(showButton, Pos.CENTER_RIGHT);

		holder.getChildren().addAll(hidingPane, showButton);

		// the settings pane is translucent so hide the GPS info and key whilst it is showing.
		hidingPane.showingProperty().addListener((obs, o, n) -> showMapObjects());
	}

	/**
	 * Apply the settings in the map options pane.
	 */
	private void applySettings() {
		MapParameters newParams = settingsPane.getParams(simpleMap.getMapParameters());
		if (newParams == null) {
			return;
		}
		setMapParameters(newParams);
	}

	/**
	 * Set new map parameters.
	 * @param newParams - the new parameters.
	 */
	public void setMapParameters(MapParameters newParams) {
		File oldFile = simpleMap.getMapParameters().getMapFile();
		simpleMap.setMapParameters(newParams);
		// make sure the contour file is loaded.
		File newFile = newParams.getMapFile();
		MapFileManager mapFileManager = simpleMap.getMapFileManager();
		if (mapFileManager != null && newFile != null && (oldFile == null || !oldFile.equals(newFile)
				|| mapFileManager.getContourCount() == 0)) {
			mapFileManager.readFileData(newFile);
		}
		mapCanvas.newParameters();
		createKey();
		showMapObjects();
		checkViewerData();
	}

	private void createContextMenu() {
		ContextMenu contextMenu = new ContextMenu();
		mapCanvas.setOnContextMenuRequested(e -> {
			if (mapController.getMapTabPanelControl() == null) {
				return;
			}
			populateContextMenu(contextMenu);
			contextMenu.show(mapCanvas, e.getScreenX(), e.getScreenY());
		});
		mapCanvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
			if (e.getButton() == MouseButton.PRIMARY) {
				contextMenu.hide();
			}
		});
	}

	private void populateContextMenu(ContextMenu contextMenu) {
		contextMenu.getItems().clear();

		MenuItem options = new MenuItem("Map options...");
		options.setOnAction(e -> {
			showSettings(1);
		});
		MenuItem overlays = new MenuItem("Plot overlay options...");
		overlays.setOnAction(e -> {
			showSettings(0);
		});
		contextMenu.getItems().addAll(options, overlays, new SeparatorMenuItem());

		simpleMap.getMapDetectionsManager().createBlockList();
		ArrayList<MapDetectionData> mddList = simpleMap.getMapDetectionsManager().getMapDetectionDatas();
		for (MapDetectionData md : new ArrayList<MapDetectionData>(mddList)) {
			if (md.dataBlock == null) {
				continue;
			}
			CheckMenuItem item = new CheckMenuItem(md.dataName);
			item.setSelected(md.select);
			item.setOnAction(e -> {
				md.select = item.isSelected();
				simpleMap.getMapPanel().updateObservers();
				createKey();
				checkViewerData();
				mapCanvas.repaint(false);
			});
			contextMenu.getItems().add(item);
		}
		contextMenu.getItems().add(new SeparatorMenuItem());

		MapParameters params = simpleMap.getMapParameters();
		CheckMenuItem showKey = new CheckMenuItem("Show key");
		showKey.setSelected(params.isShowKey());
		showKey.setOnAction(e -> {
			params.setShowKey(showKey.isSelected());
			createKey();
		});
		CheckMenuItem showPanZoom = new CheckMenuItem("Show pan zoom controls");
		showPanZoom.setSelected(params.isShowPanZoom());
		showPanZoom.setOnAction(e -> {
			params.setShowPanZoom(showPanZoom.isSelected());
			showMapObjects();
		});
		CheckMenuItem showGps = new CheckMenuItem("Show GPS and cursor data");
		showGps.setSelected(params.isShowGpsData());
		showGps.setOnAction(e -> {
			params.setShowGpsData(showGps.isSelected());
			showMapObjects();
		});
		MenuItem centre = new MenuItem("Centre map on vessel");
		centre.setOnAction(e -> mapCanvas.centreOnShip());
		contextMenu.getItems().addAll(showKey, showPanZoom, showGps, new SeparatorMenuItem(), centre);

		contextMenu.getItems().add(new SeparatorMenuItem());
		MenuItem copy = new MenuItem("Copy map to clipboard");
		copy.setOnAction(e -> {
			ClipboardContent content = new ClipboardContent();
			content.putImage(mapCanvas.getSnapshot());
			Clipboard.getSystemClipboard().setContent(content);
		});
		MenuItem save = new MenuItem("Save map image...");
		save.setOnAction(e -> saveImage());
		contextMenu.getItems().addAll(copy, save);
	}

	/**
	 * Save the map as an image.
	 */
	private void saveImage() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save map image");
		chooser.getExtensionFilters().add(new ExtensionFilter("PNG image", "*.png"));
		chooser.setInitialFileName("PAMGuardMap.png");
		File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
		if (file == null) {
			return;
		}
		try {
			javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(mapCanvas.getSnapshot(), null), "png", file);
		}
		catch (Exception ex) {
			pamViewFX.fxNodes.pamDialogFX.PamDialogFX.showWarning("Unable to save the map image: " + ex.getMessage());
		}
	}

	/**
	 * Show the settings pane.
	 * @param tab - the tab to show (0 for overlays, 1 for map options).
	 */
	public void showSettings(int tab) {
		overlayPane.refresh();
		settingsPane.setParams(simpleMap.getMapParameters());
		Region content = (Region) hidingPane.getChildren().get(0);
		if (content instanceof PamBorderPane && ((PamBorderPane) content).getCenter() instanceof TabPane) {
			((TabPane) ((PamBorderPane) content).getCenter()).getSelectionModel().select(tab);
		}
		hidingPane.showHidePane(true);
	}

	/**
	 * Show or hide the controls, GPS info and key according to the parameters.
	 */
	private void showMapObjects() {
		MapParameters params = simpleMap.getMapParameters();
		boolean settingsShowing = hidingPane != null && hidingPane.showingProperty().get();
		infoPane.setVisible(params.isShowGpsData() && !settingsShowing);
		for (javafx.scene.Node n : holder.getChildren()) {
			if (n instanceof PamVBox && ((PamVBox) n).getChildren().contains(measureButton)) {
				n.setVisible(params.isShowPanZoom());
			}
		}
		createKey();
	}

	/**
	 * Create the key showing the symbols of the plotted data and the contours.
	 */
	@SuppressWarnings("rawtypes")
	public void createKey() {
		keyPane.getChildren().clear();
		MapParameters params = simpleMap.getMapParameters();
		if (!params.isShowKey()) {
			keyPane.setVisible(false);
			return;
		}
		int nUsed = 0;
		ArrayList<PamDataBlock> blocks = simpleMap.getMapDetectionsManager().getPlottableBlocks();
		if (blocks != null) {
			for (PamDataBlock dataBlock : new ArrayList<PamDataBlock>(blocks)) {
				if (!simpleMap.getMapDetectionsManager().isShouldPlot(dataBlock)) {
					continue;
				}
				SymbolData symbolData = null;
				PamSymbolManager symbolManager = dataBlock.getPamSymbolManager();
				if (symbolManager != null) {
					try {
						PamSymbolChooser symbolChooser = symbolManager.getSymbolChooser(
								simpleMap.getMapDetectionsManager().getUnitName(), mapCanvas.getProjector());
						symbolData = symbolChooser.getSymbolChoice(mapCanvas.getProjector(), null);
					}
					catch (Exception e) {
						symbolData = null;
					}
				}
				Canvas icon = new Canvas(14, 14);
				if (symbolData != null) {
					PamSymbol symbol = new PamSymbol(PamSymbolChooser.adaptToColourScheme(symbolData));
					symbol.setWidth(10);
					symbol.setHeight(10);
					FXGraphics2D g2 = new FXGraphics2D(icon.getGraphicsContext2D());
					symbol.draw(g2, new Point(7, 7));
				}
				keyPane.getChildren().add(keyItem(icon, dataBlock.getDataName()));
				nUsed++;
			}
		}
		// contours
		MapFileManager mapFileManager = simpleMap.getMapFileManager();
		boolean[] wanted = params.getMapContours();
		Vector<Integer> available = mapFileManager == null ? null : mapFileManager.getAvailableContours();
		if (wanted != null && available != null) {
			int nPossible = Math.min(wanted.length, available.size());
			int nWanted = 0;
			for (int i = 0; i < nPossible; i++) {
				if (wanted[i]) {
					nWanted++;
				}
			}
			int iUsed = 0;
			for (int i = 0; i < nPossible; i++) {
				if (!wanted[i]) {
					continue;
				}
				Color c = MapCanvasFX.getContourColour(iUsed++, nWanted);
				Canvas icon = new Canvas(14, 14);
				icon.getGraphicsContext2D().setStroke(javafx.scene.paint.Color.rgb(c.getRed(), c.getGreen(), c.getBlue()));
				icon.getGraphicsContext2D().setLineWidth(2);
				icon.getGraphicsContext2D().strokeLine(1, 7, 13, 7);
				String name = available.get(i) == 0 ? "Coast" : String.format("%d m", available.get(i));
				keyPane.getChildren().add(keyItem(icon, name));
				nUsed++;
			}
		}
		keyPane.setVisible(nUsed > 0 && !(hidingPane != null && hidingPane.showingProperty().get()));
	}

	private PamHBox keyItem(Canvas icon, String text) {
		Label label = new Label(text);
		label.setStyle("-fx-text-fill: #202020;");
		PamHBox hBox = new PamHBox(icon, label);
		hBox.setSpacing(5);
		hBox.setAlignment(Pos.CENTER_LEFT);
		return hBox;
	}

	/**
	 * Update the GPS information.
	 */
	private void updateGpsInfo() {
		if (!infoPane.isVisible()) {
			return;
		}
		String name = MasterReferencePoint.getName();
		gpsTitle.setText(name == null ? "GPS" : name);
		LatLong latLong = MasterReferencePoint.getLatLong();
		GpsData shipGps = mapCanvas.getShipGps();
		if (isViewer() && shipGps != null) {
			latLong = shipGps;
		}
		if (latLong == null) {
			latLabel.setText("-");
			lonLabel.setText("-");
		}
		else {
			latLabel.setText(LatLong.formatLatitude(latLong.getLatitude()));
			lonLabel.setText(LatLong.formatLongitude(latLong.getLongitude()));
		}
		long now = mapCanvas.getMapTime();
		timeLabel.setText(PamCalendar.formatDateTime(now));
		Long lf = MasterReferencePoint.getFixTime();
		if (isViewer() && shipGps != null) {
			lf = shipGps.getTimeInMillis();
		}
		String fixText = lf == null ? "-" : PamCalendar.formatTime(lf, true);
		if (latLong instanceof GpsData && ((GpsData) latLong).getFixType() != null) {
			fixText += String.format(" (%s)", ((GpsData) latLong).getFixType());
		}
		lastFixLabel.setText(fixText);
		Double course = MasterReferencePoint.getCourse();
		courseLabel.setText(course == null ? "-" : String.format("%.1f°T", course));
		Double head = MasterReferencePoint.getHeading();
		headingLabel.setText(head == null ? "-" : String.format("%.1f°T", head));
		Double speed = MasterReferencePoint.getSpeed();
		speedLabel.setText(speed == null ? "-" : String.format("%.1f kn", speed));
		Double magVar = null;
		if (latLong != null) {
			try {
				magVar = MagneticVariation.getInstance().getVariation(now, latLong.getLatitude(), latLong.getLongitude());
			}
			catch (Exception e) {
				magVar = null;
			}
		}
		magVarLabel.setText(magVar == null ? "-" : String.format("%.1f°", magVar));
		String err = MasterReferencePoint.getError();
		if (err != null) {
			infoPane.setStyle("-fx-background-color: rgba(255,200,120,0.9); -fx-background-radius: 5;");
			Tooltip.install(infoPane, new Tooltip(err));
		}
		else {
			infoPane.setStyle("-fx-background-color: rgba(255,255,255,0.8); -fx-background-radius: 5;");
		}
		updateCursorInfo(mapCanvas.getLastMouseLatLong());
	}

	/**
	 * Show the position of the mouse and its range and bearing from the reference.
	 */
	private void updateCursorInfo(LatLong mouseLatLong) {
		if (mouseLatLong == null) {
			cursorLatLabel.setText("-");
			cursorLonLabel.setText("-");
			cursorBearingLabel.setText("-");
			cursorRangeLabel.setText("-");
			return;
		}
		cursorLatLabel.setText(LatLong.formatLatitude(mouseLatLong.getLatitude()));
		cursorLonLabel.setText(LatLong.formatLongitude(mouseLatLong.getLongitude()));
		LatLong ref = mapCanvas.getShipGps();
		if (ref == null) {
			ref = MasterReferencePoint.getLatLong();
		}
		if (ref == null) {
			cursorBearingLabel.setText("-");
			cursorRangeLabel.setText("-");
			return;
		}
		double range = ref.distanceToMetres(mouseLatLong);
		double bearing = ref.bearingTo(mouseLatLong);
		cursorBearingLabel.setText(String.format("%.1f°T", bearing));
		cursorRangeLabel.setText(formatRange(range));
	}

	private String formatRange(double range) {
		if (range >= 10000) {
			return String.format("%.2f km (%.2f nmi)", range / 1000., range / LatLong.MetersPerMile);
		}
		return String.format("%.0f m (%.2f nmi)", range, range / LatLong.MetersPerMile);
	}

	private void showMeasurement(LatLong start, LatLong end) {
		if (start == null || end == null) {
			measurePane.setVisible(false);
			return;
		}
		double range = start.distanceToMetres(end);
		double bearing = start.bearingTo(end);
		measureLabel.setText(String.format("Range %s, bearing %.1f°T", formatRange(range), bearing));
		measurePane.setVisible(true);
	}

	/*
	 * Viewer mode
	 */

	private void createViewerScroller() {
		viewerScroller = new AcousticScrollerFX(mapController.getUnitName() + " FX", Orientation.HORIZONTAL, 100,
				3600L * 1000L, true);
		viewerScroller.addObserver(new PamScrollObserver() {
			@Override
			public void scrollValueChanged(AbstractPamScroller abstractPamScroller) {
				MasterReferencePoint.setDisplayTime(viewerScroller.getValueMillis());
				mapCanvas.repaint(true);
			}

			@Override
			public void scrollRangeChanged(AbstractPamScroller absPamScroller) {
				mapCanvas.repaint(true);
			}
		});
		mapCanvas.setTimeRangeSupplier(new MapCanvasFX.TimeRangeSupplier() {
			@Override
			public long getMinimumMillis() {
				return viewerScroller.getMinimumMillis();
			}

			@Override
			public long getMaximumMillis() {
				return viewerScroller.getMaximumMillis();
			}

			@Override
			public long getValueMillis() {
				return viewerScroller.getValueMillis();
			}
		});
		this.setBottom(viewerScroller.getNode());
	}

	/**
	 * In viewer mode, make sure the plotted data are loaded by the scroll bar.
	 */
	@SuppressWarnings("rawtypes")
	private void checkViewerData() {
		if (viewerScroller == null) {
			return;
		}
		boolean changes = false;
		if (simpleMap.getGpsDataBlock() != null && !viewerScroller.isDataBlockUsed(simpleMap.getGpsDataBlock())) {
			viewerScroller.addDataBlock(simpleMap.getGpsDataBlock());
			changes = true;
		}
		ArrayList<PamDataBlock> blocks = simpleMap.getMapDetectionsManager().getPlottableBlocks();
		for (PamDataBlock dataBlock : new ArrayList<PamDataBlock>(blocks)) {
			boolean plot = simpleMap.getMapDetectionsManager().isShouldPlot(dataBlock);
			if (plot && !viewerScroller.isDataBlockUsed(dataBlock)) {
				viewerScroller.addDataBlock(dataBlock);
				changes = true;
			}
			else if (!plot && viewerScroller.isDataBlockUsed(dataBlock)) {
				viewerScroller.removeDataBlock(dataBlock);
				changes = true;
			}
		}
		PamDataBlock effortBlock = simpleMap.getEffortDataBlock();
		if (effortBlock != null && simpleMap.getMapParameters().colourByEffort && effortBlock.getEffortProvider() != null) {
			PamDataBlock parent = effortBlock.getEffortProvider().getParentDataBlock();
			if (parent != null && !viewerScroller.isDataBlockUsed(parent)) {
				viewerScroller.addDataBlock(parent);
				changes = true;
			}
		}
		if (changes) {
			viewerScroller.reLoad();
		}
	}

	/*
	 * UserDisplayNodeFX
	 */

	@Override
	public UserDisplayControlFX getUserDisplayControl() {
		return null;
	}

	@Override
	public String getName() {
		return mapController.getUnitName();
	}

	@Override
	public Region getNode() {
		return this;
	}

	@Override
	public void openNode() {
		mapCanvas.resetMapCentre();
		createKey();
	}

	@Override
	public boolean isStaticDisplay() {
		return false;
	}

	@Override
	public boolean isResizeableDisplay() {
		return true;
	}

	@Override
	public boolean isMinorDisplay() {
		return false;
	}

	@Override
	public boolean requestNodeSettingsPane() {
		Platform.runLater(() -> showSettings(1));
		return true;
	}

	@Override
	public void closeNode() {
		mapCanvas.stop();
		infoTimer.stop();
	}

	@Override
	public void notifyModelChanged(int changeType) {
		switch (changeType) {
		case PamControllerInterface.INITIALIZATION_COMPLETE:
			Platform.runLater(() -> {
				mapCanvas.resetMapCentre();
				overlayPane.refresh();
				createKey();
				checkViewerData();
			});
			break;
		case PamControllerInterface.ADD_CONTROLLEDUNIT:
		case PamControllerInterface.REMOVE_CONTROLLEDUNIT:
		case PamControllerInterface.ADD_DATABLOCK:
		case PamControllerInterface.REMOVE_DATABLOCK:
			if (PamController.getInstance().isInitializationComplete()) {
				Platform.runLater(() -> {
					overlayPane.refresh();
					createKey();
				});
			}
			break;
		case PamControllerInterface.CHANGED_OFFLINE_DATASTORE:
		case PamControllerInterface.INITIALIZE_LOADDATA:
			if (PamController.getInstance().isInitializationComplete()) {
				Platform.runLater(() -> checkViewerData());
			}
			break;
		case PamControllerInterface.MASTER_REFERENCE_CHANGED:
		case PamControllerInterface.HYDROPHONE_ARRAY_CHANGED:
			mapCanvas.repaint(true);
			break;
		}
	}

	@Override
	public UserDisplayNodeParams getDisplayParams() {
		return displayParams;
	}

	@Override
	public String getUnitName() {
		return mapController.getUnitName();
	}

	@Override
	public String getUnitType() {
		return "Map Display FX";
	}

	@Override
	public java.io.Serializable getSettingsReference() {
		return displayParams;
	}

	@Override
	public long getSettingsVersion() {
		return 2L;
	}

	@Override
	public boolean restoreSettings(PamControlledUnitSettings pamControlledUnitSettings) {
		try {
			displayParams = (UserDisplayNodeParams) pamControlledUnitSettings.getSettings();
		}
		catch (Exception e) {
			displayParams = new UserDisplayNodeParams();
		}
		if (displayParams == null) {
			displayParams = new UserDisplayNodeParams();
		}
		return true;
	}

	@Override
	public void setFrameHolder(PamInternalPane internalFrame) {
	}

	public MapCanvasFX getMapCanvas() {
		return mapCanvas;
	}

}
