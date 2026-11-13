package Map.layoutFX;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

import Map.MapFileManager;
import Map.MapParameters;
import Map.SimpleMap;
import Map.gridbaselayer.GebcoNETCDF;
import Map.gridbaselayer.GeoTiffFile;
import Map.gridbaselayer.GridbaseControl;
import Map.gridbaselayer.GridbaseParameters;
import Map.gridbaselayer.RasterFileTypes;
import Map.gridbaselayer.RasterFileTypes.RASTERTYPES;
import PamController.SettingsPane;
import PamUtils.LatLong;
import PamguardMVC.PamDataBlock;
import effort.EffortManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.PamToggleSwitch;

/**
 * JavaFX settings for the map. Has the same options as the Swing map options
 * dialog: data times, ship and hydrophone options, 3D rotation, locked (static)
 * map, track colouring by effort, latitude/longitude grid and range rings,
 * Gebco contour file and raster bathymetry file.
 *
 * @author Jamie Macaulay
 *
 */
public class MapSettingsPaneFX extends SettingsPane<MapParameters> {

	private static final double LABEL_WIDTH = 150;

	private SimpleMap simpleMap;

	private TabPane mainPane;

	private PamSpinner<Integer> trackShowTime, dataKeepTime, dataShowTime;

	private PamToggleSwitch showShip, keepShipOnMap, keepShipCentred, headingUp, showSurface;

	private PamToggleSwitch colourByEffort;

	private ComboBox<String> effortSource;

	private PamToggleSwitch showHydrophones, colourByChannel;

	private PamSpinner<Integer> symbolSize;

	private PamToggleSwitch allow3D;

	private PamToggleSwitch lockMap;

	private TextField lockedLat, lockedLon, lockedRange;

	private PamToggleSwitch showGrid;

	private ChoiceBox<String> ringsType;

	private PamSpinner<Double> ringsRange;

	private TextField contourFile;

	private PamVBox contourList;

	private ArrayList<CheckBox> contourChecks = new ArrayList<CheckBox>();

	private TextField rasterFile;

	private Label rasterLat, rasterLon, rasterHeight, rasterDepth;

	private MapParameters currentParams;

	/**
	 * Supplies the current map centre and range for the locked map.
	 */
	private MapCanvasFX mapCanvas;

	public MapSettingsPaneFX(SimpleMap simpleMap) {
		super(null);
		this.simpleMap = simpleMap;
		mainPane = new TabPane();
		mainPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		mainPane.getTabs().add(new Tab("Options", wrap(createOptionsPane())));
		mainPane.getTabs().add(new Tab("Grid", wrap(createGridPane())));
		mainPane.getTabs().add(new Tab("Contours", wrap(createContourPane())));
		mainPane.getTabs().add(new Tab("Raster", wrap(createRasterPane())));
		mainPane.setPrefWidth(420);
	}

	private Node wrap(Node node) {
		ScrollPane scrollPane = new ScrollPane(node);
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.getStyleClass().add("edge-to-edge");
		return scrollPane;
	}

	private Label titleLabel(String title) {
		Label label = new Label(title);
		PamGuiManagerFX.titleFont2style(label);
		return label;
	}

	private Label label(String text) {
		Label label = new Label(text);
		label.setPrefWidth(LABEL_WIDTH);
		label.setMinWidth(LABEL_WIDTH);
		return label;
	}

	private PamVBox createOptionsPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(10));

		// data times
		PamGridPane timeGrid = new PamGridPane();
		timeGrid.setHgap(5);
		timeGrid.setVgap(5);
		int row = 0;
		timeGrid.add(label("Track length"), 0, row);
		timeGrid.add(trackShowTime = createIntSpinner(0, Integer.MAX_VALUE, 60), 1, row);
		timeGrid.add(new Label("s"), 2, row++);
		trackShowTime.setTooltip(new Tooltip("How long a period of the ship's track to show"));
		timeGrid.add(label("Data storage time"), 0, row);
		timeGrid.add(dataKeepTime = createIntSpinner(0, Integer.MAX_VALUE, 60), 1, row);
		timeGrid.add(new Label("s"), 2, row++);
		dataKeepTime.setTooltip(new Tooltip("How long to keep GPS and other data for, even if it's not being displayed"));
		timeGrid.add(label("Data display time"), 0, row);
		timeGrid.add(dataShowTime = createIntSpinner(0, Integer.MAX_VALUE, 60), 1, row);
		timeGrid.add(new Label("s"), 2, row++);
		dataShowTime.setTooltip(new Tooltip("Default time to show detection data for. Each overlay can have its own time"));

		showShip = new PamToggleSwitch("Show ship");
		keepShipOnMap = new PamToggleSwitch("Keep ship on map");
		keepShipCentred = new PamToggleSwitch("Keep ship centred");
		headingUp = new PamToggleSwitch("Ship heading always up");
		showSurface = new PamToggleSwitch("Show sea surface");

		// effort
		colourByEffort = new PamToggleSwitch("Colour track-line by effort");
		colourByEffort.selectedProperty().addListener((obs, oldV, newV) -> enableControls());
		effortSource = new ComboBox<String>();
		effortSource.setMaxWidth(Double.MAX_VALUE);
		effortSource.setTooltip(new Tooltip("Data used to colour the ship's track by effort"));
		HBox.setHgrow(effortSource, Priority.ALWAYS);
		PamHBox effortBox = new PamHBox(label("Effort data"), effortSource);
		effortBox.setSpacing(5);
		effortBox.setAlignment(Pos.CENTER_LEFT);

		// hydrophones
		showHydrophones = new PamToggleSwitch("Show hydrophones");
		showHydrophones.selectedProperty().addListener((obs, oldV, newV) -> enableControls());
		colourByChannel = new PamToggleSwitch("Colour by channel number");
		symbolSize = new PamSpinner<Integer>(2, 100, 8, 2);
		symbolSize.setEditable(true);
		symbolSize.setPrefWidth(90);
		symbolSize.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		PamHBox symbolBox = new PamHBox(label("Symbol size"), symbolSize);
		symbolBox.setSpacing(5);
		symbolBox.setAlignment(Pos.CENTER_LEFT);

		// 3D
		allow3D = new PamToggleSwitch("Enable 3D rotation");
		allow3D.setTooltip(new Tooltip("Allow the map to be rotated in 3D. Hold shift and drag on the map to rotate it"));

		// static map
		lockMap = new PamToggleSwitch("Lock map");
		lockMap.setTooltip(new Tooltip("Lock the map position and scale, e.g. for static mooring mitigation projects"));
		lockMap.selectedProperty().addListener((obs, oldV, newV) -> {
			if (newV && (lockedLat.getText() == null || lockedLat.getText().length() == 0)) {
				fillLockedFields();
			}
			enableControls();
		});
		PamGridPane lockGrid = new PamGridPane();
		lockGrid.setHgap(5);
		lockGrid.setVgap(5);
		lockGrid.add(label("Map height (m)"), 0, 0);
		lockGrid.add(lockedRange = new TextField(), 1, 0);
		lockGrid.add(label("Centre latitude (\u00B0)"), 0, 1);
		lockGrid.add(lockedLat = new TextField(), 1, 1);
		lockGrid.add(label("Centre longitude (\u00B0)"), 0, 2);
		lockGrid.add(lockedLon = new TextField(), 1, 2);

		vBox.getChildren().addAll(titleLabel("Data Options"), timeGrid, titleLabel("Ship"), showShip, keepShipOnMap,
				keepShipCentred, headingUp, showSurface, colourByEffort, effortBox, titleLabel("Hydrophones"),
				showHydrophones, colourByChannel, symbolBox, titleLabel("3D Rotation"), allow3D,
				titleLabel("Static Map"), lockMap, lockGrid);
		return vBox;
	}

	private PamSpinner<Integer> createIntSpinner(int min, int max, int step) {
		PamSpinner<Integer> spinner = new PamSpinner<Integer>(min, max, 0, step);
		spinner.setEditable(true);
		spinner.setPrefWidth(110);
		spinner.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		return spinner;
	}

	private PamVBox createGridPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(10));

		showGrid = new PamToggleSwitch("Show latitude and longitude lines");

		ringsType = new ChoiceBox<String>();
		ringsType.getItems().addAll("None", "Metres", "Kilometres", "Nautical Miles");
		ringsType.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> enableControls());
		ringsRange = new PamSpinner<Double>(0.001, 1000000., 1000., 100.);
		ringsRange.setEditable(true);
		ringsRange.setPrefWidth(120);
		ringsRange.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

		PamGridPane ringGrid = new PamGridPane();
		ringGrid.setHgap(5);
		ringGrid.setVgap(5);
		ringGrid.add(label("Show"), 0, 0);
		ringGrid.add(ringsType, 1, 0);
		ringGrid.add(label("Separation"), 0, 1);
		ringGrid.add(ringsRange, 1, 1);

		vBox.getChildren().addAll(titleLabel("Grid"), showGrid, titleLabel("Range Rings"), ringGrid);
		return vBox;
	}

	private PamVBox createContourPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(10));

		contourFile = new TextField();
		contourFile.setEditable(false);
		HBox.setHgrow(contourFile, Priority.ALWAYS);

		PamButton browseButton = new PamButton();
		browseButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-folder-open-outline", PamGuiManagerFX.iconSize));
		browseButton.setTooltip(new Tooltip("Select a Gebco ASCII (.asc) contour file"));
		browseButton.setOnAction(e -> browseContourFile());

		PamButton clearButton = new PamButton();
		clearButton.setGraphic(PamGlyphDude.createPamIcon("mdi2c-close", PamGuiManagerFX.iconSize));
		clearButton.setTooltip(new Tooltip("Clear the contour file"));
		clearButton.setOnAction(e -> {
			contourFile.setText("");
			simpleMap.getMapFileManager().clearFileData();
			fillContourList(null);
		});

		PamHBox fileBox = new PamHBox(contourFile, browseButton, clearButton);
		fileBox.setSpacing(5);
		fileBox.setAlignment(Pos.CENTER_LEFT);

		contourList = new PamVBox();
		contourList.setSpacing(3);

		PamButton allButton = new PamButton("Select all");
		allButton.setOnAction(e -> contourChecks.forEach(c -> c.setSelected(true)));
		PamButton noneButton = new PamButton("Select none");
		noneButton.setOnAction(e -> contourChecks.forEach(c -> c.setSelected(false)));
		PamHBox selButtons = new PamHBox(allButton, noneButton);
		selButtons.setSpacing(5);

		vBox.getChildren().addAll(titleLabel("Gebco ASCII File"), fileBox, titleLabel("Contours"), selButtons, contourList);
		return vBox;
	}

	private PamVBox createRasterPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(10));

		rasterFile = new TextField();
		rasterFile.setEditable(false);
		HBox.setHgrow(rasterFile, Priority.ALWAYS);

		PamButton browseButton = new PamButton();
		browseButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-folder-open-outline", PamGuiManagerFX.iconSize));
		browseButton.setTooltip(new Tooltip("Select a NETCDF or GeoTIFF bathymetry file"));
		browseButton.setOnAction(e -> browseRasterFile());

		PamButton clearButton = new PamButton();
		clearButton.setGraphic(PamGlyphDude.createPamIcon("mdi2c-close", PamGuiManagerFX.iconSize));
		clearButton.setTooltip(new Tooltip("Clear the raster file"));
		clearButton.setOnAction(e -> {
			rasterFile.setText("");
			sayRasterRange();
		});

		PamHBox fileBox = new PamHBox(rasterFile, browseButton, clearButton);
		fileBox.setSpacing(5);
		fileBox.setAlignment(Pos.CENTER_LEFT);

		PamGridPane infoGrid = new PamGridPane();
		infoGrid.setHgap(5);
		infoGrid.setVgap(3);
		infoGrid.add(label("Latitude"), 0, 0);
		infoGrid.add(rasterLat = new Label("-"), 1, 0);
		infoGrid.add(label("Longitude"), 0, 1);
		infoGrid.add(rasterLon = new Label("-"), 1, 1);
		infoGrid.add(label("Max land height"), 0, 2);
		infoGrid.add(rasterHeight = new Label("-"), 1, 2);
		infoGrid.add(label("Max water depth"), 0, 3);
		infoGrid.add(rasterDepth = new Label("-"), 1, 3);

		Hyperlink link = new Hyperlink("Suitable files can be created at the BODC website");
		link.setTooltip(new Tooltip(GridbaseControl.bodcURL));
		link.setOnAction(e -> {
			try {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(GridbaseControl.bodcURL));
			}
			catch (Exception ex) {
				System.err.println("Unable to open " + GridbaseControl.bodcURL);
			}
		});

		vBox.getChildren().addAll(titleLabel("NETCDF or GeoTIFF File"), fileBox, infoGrid, link);
		return vBox;
	}

	private void browseContourFile() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Map File Selection...");
		chooser.getExtensionFilters().addAll(new ExtensionFilter("Gebco ASCII map files", "*.asc", "*.ASC"),
				new ExtensionFilter("All files", "*.*"));
		setInitialDirectory(chooser, contourFile.getText());
		File file = chooser.showOpenDialog(findWindow());
		if (file == null) {
			return;
		}
		contourFile.setText(file.getAbsolutePath());
		simpleMap.getMapFileManager().readFileData(file);
		fillContourList(null);
	}

	private void browseRasterFile() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Bathymetry File Selection...");
		chooser.getExtensionFilters().addAll(new ExtensionFilter("NETCDF or GeoTIFF files", "*.nc", "*.tif", "*.tiff"),
				new ExtensionFilter("All files", "*.*"));
		setInitialDirectory(chooser, rasterFile.getText());
		File file = chooser.showOpenDialog(findWindow());
		if (file == null) {
			return;
		}
		rasterFile.setText(file.getAbsolutePath());
		sayRasterRange();
	}

	private void setInitialDirectory(FileChooser chooser, String currentFile) {
		if (currentFile == null || currentFile.length() == 0) {
			return;
		}
		File parent = new File(currentFile).getParentFile();
		if (parent != null && parent.exists()) {
			chooser.setInitialDirectory(parent);
		}
	}

	private javafx.stage.Window findWindow() {
		if (mainPane.getScene() == null) {
			return null;
		}
		return mainPane.getScene().getWindow();
	}

	/**
	 * Fill the list of contour check boxes from the map file.
	 * @param selected - which contours are selected. Can be null.
	 */
	private void fillContourList(boolean[] selected) {
		boolean[] oldSelection = selected;
		if (oldSelection == null) {
			oldSelection = getContourSelection();
		}
		contourList.getChildren().clear();
		contourChecks.clear();
		MapFileManager mapFileManager = simpleMap.getMapFileManager();
		Vector<Integer> availableContours = mapFileManager == null ? null : mapFileManager.getAvailableContours();
		if (availableContours == null || availableContours.size() == 0) {
			contourList.getChildren().add(new Label("No contours available"));
			return;
		}
		for (int i = 0; i < availableContours.size(); i++) {
			String name = String.format("%d m", availableContours.get(i));
			if (availableContours.get(i) == 0) {
				name += " (coast)";
			}
			if (availableContours.get(i) == -1) {
				name += " (ice edge)";
			}
			CheckBox checkBox = new CheckBox(name);
			if (oldSelection != null && i < oldSelection.length) {
				checkBox.setSelected(oldSelection[i]);
			}
			contourChecks.add(checkBox);
			contourList.getChildren().add(checkBox);
		}
	}

	private boolean[] getContourSelection() {
		if (contourChecks.size() == 0) {
			return null;
		}
		boolean[] sel = new boolean[contourChecks.size()];
		for (int i = 0; i < sel.length; i++) {
			sel[i] = contourChecks.get(i).isSelected();
		}
		return sel;
	}

	/**
	 * Show the extent of the raster file.
	 */
	private void sayRasterRange() {
		String fn = rasterFile.getText();
		rasterLat.setText("-");
		rasterLon.setText("-");
		rasterHeight.setText("-");
		rasterDepth.setText("-");
		if (fn == null || fn.length() == 0) {
			rasterLat.setText("No file selected");
			return;
		}
		RASTERTYPES rasterType = RasterFileTypes.getFileType(fn);
		if (rasterType == null) {
			rasterLat.setText("Unknown file type");
			return;
		}
		try {
			switch (rasterType) {
			case GEOTIFF:
				File g = new File(fn);
				if (!g.exists()) {
					rasterLat.setText(g.getName() + " doesn't exist");
					return;
				}
				GeoTiffFile gtf = GeoTiffFile.makeFile(g);
				if (gtf == null) {
					rasterLat.setText("Can't use file");
					return;
				}
				LatLong minLL = gtf.getMinLatLong();
				LatLong maxLL = gtf.getMaxLatLong();
				rasterLat.setText(String.format("%s - %s", LatLong.formatLatitude(minLL.getLatitude()),
						LatLong.formatLatitude(maxLL.getLatitude())));
				rasterLon.setText(String.format("%s - %s", LatLong.formatLongitude(minLL.getLongitude()),
						LatLong.formatLongitude(maxLL.getLongitude())));
				break;
			case NETCDF:
				GebcoNETCDF gebfile = GebcoNETCDF.makeGebcoNCDFFile(fn);
				if (gebfile == null) {
					rasterLat.setText("Can't use file");
					return;
				}
				double[] lats = gebfile.getLatRange();
				double[] lons = gebfile.getLonRange();
				double[] ele = gebfile.getElevationRange();
				rasterLat.setText(String.format("%s - %s (%d points)", LatLong.formatLatitude(lats[0]),
						LatLong.formatLatitude(lats[1]), gebfile.getnLat()));
				rasterLon.setText(String.format("%s - %s (%d points)", LatLong.formatLongitude(lons[0]),
						LatLong.formatLongitude(lons[1]), gebfile.getnLon()));
				rasterDepth.setText(String.format("%3.0f m", -ele[0]));
				rasterHeight.setText(String.format("%3.0f m", ele[1]));
				break;
			}
		}
		catch (Exception e) {
			rasterLat.setText("Error in file");
		}
	}

	private void fillLockedFields() {
		if (mapCanvas != null) {
			LatLong centre = mapCanvas.getMapCentre();
			lockedLat.setText(Double.toString(centre.getLatitude()));
			lockedLon.setText(Double.toString(centre.getLongitude()));
			lockedRange.setText(Integer.toString((int) Math.round(mapCanvas.getMapRangeMetres())));
		}
		else if (currentParams != null) {
			lockedLat.setText(Double.toString(currentParams.lockedMapCenter.getLatitude()));
			lockedLon.setText(Double.toString(currentParams.lockedMapCenter.getLongitude()));
			lockedRange.setText(Integer.toString(currentParams.getMapRangeMetres()));
		}
	}

	private void enableControls() {
		colourByChannel.setDisable(!showHydrophones.isSelected());
		symbolSize.setDisable(!showHydrophones.isSelected());
		ringsRange.setDisable(ringsType.getSelectionModel().getSelectedIndex() <= 0);
		effortSource.setDisable(!colourByEffort.isSelected());
		boolean lock = lockMap.isSelected();
		lockedLat.setDisable(!lock);
		lockedLon.setDisable(!lock);
		lockedRange.setDisable(!lock);
	}

	@Override
	public MapParameters getParams(MapParameters currParams) {
		if (currParams == null) {
			currParams = currentParams;
		}
		if (currParams == null) {
			return null;
		}
		MapParameters params = currParams.copy();
		try {
			params.setTrackShowTime(trackShowTime.getValue());
			params.setDataKeepTime(dataKeepTime.getValue());
			params.setDataShowTime(dataShowTime.getValue());
		}
		catch (Exception e) {
			PamDialogFX.showWarning("Invalid data time");
			return null;
		}
		params.setHideShip(!showShip.isSelected());
		params.setKeepShipOnMap(keepShipOnMap.isSelected());
		params.setKeepShipCentred(keepShipCentred.isSelected());
		params.setHeadingUp(headingUp.isSelected());
		params.hideSurface = !showSurface.isSelected();
		params.colourByEffort = colourByEffort.isSelected();
		params.effortDataSource = effortSource.getValue();
		params.setShowHydrophones(showHydrophones.isSelected());
		params.setColourHydrophonesByChannel(colourByChannel.isSelected());
		int sz = symbolSize.getValue();
		if (sz % 2 == 1) {
			sz++;
		}
		params.setSymbolSize(sz);
		params.setAllow3D(allow3D.isSelected());
		params.lockMap = lockMap.isSelected();
		if (params.lockMap) {
			try {
				params.lockedMapCenter = new LatLong(Double.parseDouble(lockedLat.getText()),
						Double.parseDouble(lockedLon.getText()));
				params.lockedMapScale = Integer.parseInt(lockedRange.getText().trim());
			}
			catch (Exception e) {
				PamDialogFX.showWarning("The locked map centre and height must be numbers");
				return null;
			}
		}
		params.setHideGrid(!showGrid.isSelected());
		params.setShowRangeRings(Math.max(0, ringsType.getSelectionModel().getSelectedIndex()));
		if (params.getShowRangeRings() > 0) {
			Double r = ringsRange.getValue();
			if (r == null || r <= 0) {
				PamDialogFX.showWarning("Range rings separation must be > 0");
				return null;
			}
			params.setRangeRingDistance(r);
		}

		String cf = contourFile.getText();
		params.setMapFile(cf == null || cf.length() == 0 ? null : new File(cf));
		params.setMapContours(getContourSelection());

		// raster file
		GridbaseControl gridbaseControl = simpleMap.getGridBaseControl();
		if (gridbaseControl != null) {
			String fn = rasterFile.getText();
			if (fn != null && fn.length() > 0 && !new File(fn).exists()) {
				PamDialogFX.showWarning("The map raster file " + fn + " does not exist");
				return null;
			}
			GridbaseParameters gridParams = gridbaseControl.getGridbaseParameters();
			String oldFile = gridParams == null ? null : gridParams.netCDFFile;
			boolean changed = (oldFile == null) ? (fn != null && fn.length() > 0) : !oldFile.equals(fn);
			if (changed) {
				GridbaseParameters newGridParams = gridParams == null ? new GridbaseParameters() : gridParams.clone();
				newGridParams.netCDFFile = fn;
				gridbaseControl.setGridbaseParameters(newGridParams);
			}
		}

		currentParams = params;
		return params;
	}

	@Override
	public void setParams(MapParameters input) {
		currentParams = input;
		trackShowTime.getValueFactory().setValue(input.getTrackShowTime());
		dataKeepTime.getValueFactory().setValue(input.getDataKeepTime());
		dataShowTime.getValueFactory().setValue(input.getDataShowTime());
		showShip.setSelected(!input.isHideShip());
		keepShipOnMap.setSelected(input.isKeepShipOnMap());
		keepShipCentred.setSelected(input.isKeepShipCentred());
		headingUp.setSelected(input.isHeadingUp());
		showSurface.setSelected(!input.hideSurface);
		colourByEffort.setSelected(input.colourByEffort);

		effortSource.getItems().clear();
		ArrayList<PamDataBlock> effortBlocks = EffortManager.getEffortManager().getEffortDataBlocks();
		if (effortBlocks != null) {
			for (PamDataBlock block : effortBlocks) {
				effortSource.getItems().add(block.getLongDataName());
			}
		}
		effortSource.setValue(input.effortDataSource);

		showHydrophones.setSelected(input.isShowHydrophones());
		colourByChannel.setSelected(input.isColourHydrophonesByChannel());
		symbolSize.getValueFactory().setValue(Math.max(2, input.getSymbolSize()));
		allow3D.setSelected(input.isAllow3D());

		lockMap.setSelected(input.lockMap);
		if (input.lockMap && input.lockedMapCenter != null) {
			lockedLat.setText(Double.toString(input.lockedMapCenter.getLatitude()));
			lockedLon.setText(Double.toString(input.lockedMapCenter.getLongitude()));
			lockedRange.setText(Integer.toString(input.lockedMapScale));
		}
		else {
			lockedLat.setText("");
			lockedLon.setText("");
			lockedRange.setText("");
		}

		showGrid.setSelected(!input.isHideGrid());
		ringsType.getSelectionModel().select(Math.max(0, Math.min(3, input.getShowRangeRings())));
		ringsRange.getValueFactory().setValue(input.getRangeRingDistance());

		File mapFile = input.getMapFile();
		contourFile.setText(mapFile == null ? "" : mapFile.getAbsolutePath());
		fillContourList(input.getMapContours());

		GridbaseControl gridbaseControl = simpleMap.getGridBaseControl();
		if (gridbaseControl != null && gridbaseControl.getGridbaseParameters() != null) {
			String fn = gridbaseControl.getGridbaseParameters().netCDFFile;
			rasterFile.setText(fn == null ? "" : fn);
		}
		else {
			rasterFile.setText("");
		}
		sayRasterRange();

		enableControls();
	}

	/**
	 * Set the map canvas, used to fill in the locked map position.
	 * @param mapCanvas - the map canvas.
	 */
	public void setMapCanvas(MapCanvasFX mapCanvas) {
		this.mapCanvas = mapCanvas;
	}

	@Override
	public String getName() {
		return "Map Options";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

	@Override
	public String getHelpPoint() {
		return "mapping.mapHelp.docs.overview";
	}

}
