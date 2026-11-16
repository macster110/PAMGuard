package clipDisplayFX;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import pamViewFX.fxNodes.flipPane.PamFlipPane;
import javafx.scene.control.MenuButton;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;

import PamController.PamControlledUnitSettings;
import PamController.PamController;
import PamController.PamControllerInterface;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamUtils.FrequencyFormat;
import PamUtils.PamCalendar;
import PamUtils.PamUtils;
import PamView.ColourArray;
import PamView.PamColors;
import PamView.PamColors.PamColor;
import PamView.PamSymbol;
import PamView.paneloverlay.overlaymark.OverlayMarkProviders;
import PamView.symbol.PamSymbolChooser;
import PamView.symbol.PamSymbolManager;
import PamView.symbol.StandardSymbolChooser;
import PamView.symbol.SymbolUpdateMonitor;
import PamView.symbol.modifier.SymbolModType;
import PamguardMVC.PamDataUnit;
import PamguardMVC.PamObservable;
import PamguardMVC.PamObserverAdapter;
import PamguardMVC.dataSelector.DataSelector;
import PamguardMVC.PamDataBlock;
import clipgenerator.ClipDataUnit;
import clipgenerator.ClipProcess;
import clipgenerator.clipDisplay.ClipDisplayParameters;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import pamScrollSystem.AbstractPamScroller;
import pamScrollSystem.PamScrollObserver;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamScrollPane;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.comboBox.ColorComboBox;
import pamViewFX.fxNodes.sliders.ColourRangeSlider;
import pamViewFX.fxNodes.utilsFX.ColourArray.ColourArrayType;
import pamViewFX.fxNodes.hidingPane.HidingPane;
import pamViewFX.fxNodes.internalNode.PamInternalPane;
import pamViewFX.fxNodes.pamScrollers.acousticScroller.AcousticScrollerFX;
import pamViewFX.fxNodes.utilsFX.PamUtilsFX;
import pamViewFX.fxSettingsPanes.DynamicSettingsPane;
import pamViewFX.fxStyles.PamStylesManagerFX;
import pamViewFX.symbol.FXSymbolOptionsPane;
import userDisplayFX.UserDisplayControlFX;
import userDisplayFX.UserDisplayNodeFX;
import userDisplayFX.UserDisplayNodeParams;

/**
 * JavaFX clip display. Shows any data which holds raw sound, e.g. clips from the clip
 * generator, clicks from the click detector or detections from the deep learning
 * classifier. Several data sources can be shown together, in time order. In viewer
 * mode only the data within the time window of the scroll bar are shown and
 * spectrogram images are kept when the window moves, so only new clips are drawn. Clips are drawn as spectrogram tiles on one large canvas which
 * can be smoothly zoomed in and out (ctrl + mouse wheel, pinch, +/- keys or the zoom
 * buttons) so that thousands of clips can be seen at once or individual clips examined
 * in detail. Has all the features of the Swing clip display: spectrogram and colour
 * controls, trigger data overlays, border colours from the symbol manager, data
 * selection, channel filtering, clip selection and marking, playback and tool tips.
 *
 * @author Jamie Macaulay
 *
 */
public class ClipDisplayFX extends PamBorderPane implements UserDisplayNodeFX, PamSettings, SymbolUpdateMonitor {

	private static final double SETTINGS_WIDTH = 330;

	private static final double[] SCALES = { 0.25, 0.5, 1, 2, 4, 8 };

	private ClipDisplayControlFX displayControl;

	/**
	 * The data blocks shown on the display. 
	 */
	private final ArrayList<PamDataBlock<?>> sourceBlocks = new ArrayList<PamDataBlock<?>>();

	/**
	 * Lets the user choose the data shown on the display.
	 */
	private PamVBox sourceList;

	/**
	 * Holds the data selector, which depends on the data source.
	 */

	private Tab symbolTab;

	private ClipDisplayFXParams params = new ClipDisplayFXParams();

	private ClipCanvasFX clipCanvas;

	private ClipDataProjectorFX projector;

	private ClipDisplayMarkerFX marker;

	private ColourArray colourArray;

	private StackPane holder;

	private HidingPane hidingPane;

	private TabPane settingsTabs;

	private Label statusLabel;

	private AcousticScrollerFX viewerScroller;

	private ContextMenu contextMenu;

	/**
	 * Bitmap of channels which have been seen in the clip data.
	 */
	private int knownChannelSources;

	/**
	 * Display controls.
	 */
	private Slider maxFreq;

	/**
	 * Colour map bar with the amplitude range of the colour scale. 
	 */
	private ColourRangeSlider colourSlider;

	private Label ampRangeLabel, maxFreqLabel, fftResLabel;

	private ChoiceBox<Integer> fftLength;

	private ChoiceBox<Double> hScale, vScale;

	private ColorComboBox colourBox;


	private CheckBox showTrigger, newClipsLast, fullSummary;

	private PamSpinner<Integer> maxClips, maxMinutes;

	private ChoiceBox<String> channelChoice;

	private boolean settingControls;

	private final java.util.HashMap<PamDataBlock<?>, DynamicSettingsPane<Boolean>> dataSelectPanes = new java.util.HashMap<PamDataBlock<?>, DynamicSettingsPane<Boolean>>();

	/**
	 * Buttons to add and remove data sources. 
	 */
	private MenuButton addSourceButton, removeSourceButton;

	/**
	 * Flip pane with the data sources on the front and the data selection of one of
	 * them on the back. 
	 */
	private PamFlipPane dataFlipPane;

	private boolean viewerReloadPending;

	public ClipDisplayFX(ClipDisplayControlFX displayControl) {
		this.displayControl = displayControl;
		projector = new ClipDataProjectorFX();
		clipObserver = new ClipObserver();
		PamSettingManager.getInstance().registerSettings(this);
		makeColourArray();
		createPane();

		marker = new ClipDisplayMarkerFX(this);
		OverlayMarkProviders.singleInstance().addProvider(marker);
	}

	private boolean isViewer() {
		return PamController.getInstance().getRunMode() == PamController.RUN_PAMVIEW;
	}

	private void createPane() {
		clipCanvas = new ClipCanvasFX(this);

		holder = new StackPane();
		holder.getChildren().add(clipCanvas);

		PamHBox zoomControls = createZoomControls();
		StackPane.setAlignment(zoomControls, Pos.BOTTOM_RIGHT);
		StackPane.setMargin(zoomControls, new Insets(8, 26, 8, 8));
		zoomControls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
		zoomControls.setPickOnBounds(false);

		statusLabel = new Label();
		statusLabel.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: white; "
				+ "-fx-background-radius: 4; -fx-padding: 2 6 2 6;");
		statusLabel.setMouseTransparent(true);
		StackPane.setAlignment(statusLabel, Pos.BOTTOM_LEFT);
		StackPane.setMargin(statusLabel, new Insets(8));

		holder.getChildren().addAll(statusLabel, zoomControls);

		createSettingsPane();

		this.setCenter(holder);

		if (isViewer()) {
			createViewerScroller();
		}

		clipCanvas.setLayoutListener(c -> updateStatus());
		setControls();
		clipCanvas.setZoom(params.zoom);
		updateStatus();
	}

	/*
	 * Controls
	 */

	private PamHBox createZoomControls() {
		PamButton zoomIn = zoomButton("mdi2m-magnify-plus-outline", "Zoom in (Ctrl + scroll, pinch or +)");
		zoomIn.setOnAction(e -> clipCanvas.zoomAbout(1.5, clipCanvas.getWidth() / 2, clipCanvas.getHeight() / 2));
		PamButton zoomOut = zoomButton("mdi2m-magnify-minus-outline", "Zoom out (Ctrl + scroll, pinch or -)");
		zoomOut.setOnAction(e -> clipCanvas.zoomAbout(1 / 1.5, clipCanvas.getWidth() / 2, clipCanvas.getHeight() / 2));
		PamButton zoomFit = zoomButton("mdi2f-fit-to-page-outline", "Zoom out to show all clips");
		zoomFit.setOnAction(e -> clipCanvas.zoomToFit());
		PamButton zoomOne = zoomButton("mdi2n-numeric-1-box-outline", "Show clips at their normal size");
		zoomOne.setOnAction(e -> clipCanvas.zoomAbout(1. / clipCanvas.getZoom(), clipCanvas.getWidth() / 2, 0));

		PamHBox box = new PamHBox(zoomIn, zoomOut, zoomFit, zoomOne);
		box.setSpacing(4);
		box.setPadding(new Insets(3));
		box.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-background-radius: 5;");
		return box;
	}

	private PamButton zoomButton(String icon, String tip) {
		PamButton button = new PamButton();
		button.setGraphic(PamGlyphDude.createPamIcon(icon, PamGuiManagerFX.iconSize));
		button.setTooltip(new Tooltip(tip));
		button.setFocusTraversable(false);
		return button;
	}

	private void createSettingsPane() {
		settingsTabs = new TabPane();
		settingsTabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

		PamScrollPane displayScroll = new PamScrollPane(createDisplayControls());
		displayScroll.setFitToWidth(true);
		displayScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		settingsTabs.getTabs().add(new Tab("Display", displayScroll));

		symbolTab = new Tab("Symbols");
		updateSymbolTab();

		PamScrollPane dataScroll = new PamScrollPane(createDataPane());
		dataScroll.setFitToWidth(true);
		dataScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		settingsTabs.getTabs().add(new Tab("Data", dataScroll));
		settingsTabs.setPrefWidth(SETTINGS_WIDTH);

		PamBorderPane settingsHolder = new PamBorderPane(settingsTabs);
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
		showButton.setTooltip(new Tooltip("Show the clip display options"));
		showButton.setOnMouseClicked(e -> setControls());
		StackPane.setAlignment(showButton, Pos.CENTER_RIGHT);

		holder.getChildren().addAll(hidingPane, showButton);
	}

	private Label title(String text) {
		Label label = new Label(text);
		PamGuiManagerFX.titleFont2style(label);
		return label;
	}

	private Node createDisplayControls() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(5, 10, 5, 5));

		// spectrogram
		PamGridPane specGrid = new PamGridPane();
		specGrid.setHgap(5);
		specGrid.setVgap(5);
		int row = 0;

		/*
		 * Colour map and amplitude range, using the same colour bar slider as the
		 * spectrogram in the time display: drag the ends to set the amplitudes at
		 * the bottom and top of the colour scale. 
		 */
		colourBox = new ColorComboBox(ColorComboBox.COLOUR_ARRAY_BOX);
		colourBox.setPrefWidth(150);
		specGrid.add(new Label("Colours"), 0, row);
		specGrid.add(colourBox, 1, row++);

		colourSlider = new ColourRangeSlider(Orientation.HORIZONTAL);
		colourSlider.setMin(0);
		colourSlider.setMax(250);
		colourSlider.setLowValue(50);
		colourSlider.setHighValue(120);
		colourSlider.setShowTickMarks(true);
		colourSlider.setShowTickLabels(true);
		colourSlider.setMajorTickUnit(50);
		colourSlider.setPrefWidth(220);
		colourSlider.setTooltip(new Tooltip("Amplitudes (dB) at the bottom and top of the colour scale. "
				+ "Drag either end, or the bar to move both."));
		specGrid.add(new Label("Amplitude"), 0, row);
		specGrid.add(colourSlider, 1, row, 2, 1);
		row++;
		specGrid.add(ampRangeLabel = new Label(), 1, row++, 2, 1);

		fftLength = new ChoiceBox<Integer>();
		for (int i = 4; i <= 15; i++) {
			fftLength.getItems().add(1 << i);
		}
		fftLength.setTooltip(new Tooltip("FFT length used to make the clip spectrograms"));
		specGrid.add(new Label("FFT length"), 0, row);
		specGrid.add(fftLength, 1, row++);
		fftResLabel = new Label(" ");
		fftResLabel.setTooltip(new Tooltip("WARNING: This may vary if some data units have decimated data displayed"));
		specGrid.add(fftResLabel, 0, row++, 3, 1);


		showTrigger = new CheckBox("Overlay trigger data");
		showTrigger.setTooltip(new Tooltip("Draw the detection which triggered each clip on top of the clip, e.g. a whistle contour"));

		// scales
		PamGridPane scaleGrid = new PamGridPane();
		scaleGrid.setHgap(5);
		scaleGrid.setVgap(5);
		row = 0;
		hScale = new ChoiceBox<Double>(FXCollections.observableArrayList(toList(SCALES)));
		hScale.setTooltip(new Tooltip("Horizontal (time) scale of each clip"));
		vScale = new ChoiceBox<Double>(FXCollections.observableArrayList(toList(SCALES)));
		vScale.setTooltip(new Tooltip("Vertical (frequency) scale of each clip"));
		scaleGrid.add(new Label("Horizontal scale"), 0, row);
		scaleGrid.add(hScale, 1, row++);
		scaleGrid.add(new Label("Vertical scale"), 0, row);
		scaleGrid.add(vScale, 1, row++);
		maxFreq = new Slider(0.05, 1, 1);
		maxFreq.setPrefWidth(150);
		maxFreq.setTooltip(new Tooltip("Maximum frequency shown as a fraction of the Nyquist frequency"));
		scaleGrid.add(new Label("Max frequency"), 0, row);
		scaleGrid.add(maxFreq, 1, row++);
		scaleGrid.add(maxFreqLabel = new Label(), 1, row++);

		vBox.getChildren().addAll(title("Spectrogram"), specGrid, showTrigger, title("Scale"), scaleGrid);

		// history (real time only, as in Swing).
		if (!isViewer()) {
			PamGridPane histGrid = new PamGridPane();
			histGrid.setHgap(5);
			histGrid.setVgap(5);
			maxClips = new PamSpinner<Integer>(10, 100000, 100, 10);
			maxClips.setEditable(true);
			maxClips.setPrefWidth(100);
			maxClips.setTooltip(new Tooltip("Maximum number of clips to keep in the display"));
			maxMinutes = new PamSpinner<Integer>(1, 1440, 10, 1);
			maxMinutes.setEditable(true);
			maxMinutes.setPrefWidth(100);
			maxMinutes.setTooltip(new Tooltip("Maximum age of clips kept in the display"));
			histGrid.add(new Label("Max clips"), 0, 0);
			histGrid.add(maxClips, 1, 0);
			histGrid.add(new Label("Max time"), 0, 1);
			histGrid.add(maxMinutes, 1, 1);
			histGrid.add(new Label("minutes"), 2, 1);
			vBox.getChildren().addAll(title("History"), histGrid);
			maxClips.valueProperty().addListener((o, a, b) -> controlChanged(false));
			maxMinutes.valueProperty().addListener((o, a, b) -> controlChanged(false));
		}

		newClipsLast = new CheckBox("Show new clips last");
		newClipsLast.setTooltip(new Tooltip("New clips are added at the end (bottom right) rather than the start (top left)"));
		fullSummary = new CheckBox("Show full tool tip summary");
		fullSummary.setTooltip(new Tooltip("Show the full clip and trigger information in the tool tip, otherwise just the UID and time"));
		vBox.getChildren().addAll(title("Layout"), newClipsLast, fullSummary);

		Label help = new Label("Ctrl + scroll or pinch to zoom, drag to pan. Click to select, Ctrl and Shift click "
				+ "to select several. Double click to play a clip. Right click for more options.");
		help.setWrapText(true);
		help.setStyle("-fx-font-size: 11; -fx-opacity: 0.8;");
		vBox.getChildren().add(help);

		// listeners
		colourSlider.lowValueProperty().addListener((o, a, b) -> controlChanged(true));
		colourSlider.highValueProperty().addListener((o, a, b) -> controlChanged(true));
		fftLength.valueProperty().addListener((o, a, b) -> controlChanged(true));
		colourBox.valueProperty().addListener((o, a, b) -> controlChanged(true));
		hScale.valueProperty().addListener((o, a, b) -> controlChanged(true));
		vScale.valueProperty().addListener((o, a, b) -> controlChanged(true));
		maxFreq.valueProperty().addListener((o, a, b) -> controlChanged(true));
		showTrigger.selectedProperty().addListener((o, a, b) -> controlChanged(false));
		newClipsLast.selectedProperty().addListener((o, a, b) -> controlChanged(false));
		fullSummary.selectedProperty().addListener((o, a, b) -> controlChanged(false));

		return vBox;
	}

	private static List<Double> toList(double[] vals) {
		ArrayList<Double> list = new ArrayList<Double>();
		for (double v : vals) {
			list.add(v);
		}
		return list;
	}

	/**
	 * Show the symbol options for the current data sources, or hide the tab if none have any.
	 */
	private void updateSymbolTab() {
		Node symbolNode = createSymbolPane();
		settingsTabs.getTabs().remove(symbolTab);
		if (symbolNode != null) {
			PamScrollPane symbolScroll = new PamScrollPane(symbolNode);
			symbolScroll.setFitToWidth(true);
			symbolScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
			symbolTab.setContent(symbolScroll);
			settingsTabs.getTabs().add(Math.min(1, settingsTabs.getTabs().size()), symbolTab);
		}
	}

	/**
	 * Make the symbol options, one set for each data source which has a symbol manager.
	 */
	private Node createSymbolPane() {
		PamVBox box = new PamVBox(title("Clip border colours"));
		box.setSpacing(5);
		box.setPadding(new Insets(5));
		int n = 0;
		for (PamDataBlock<?> block : sourceBlocks) {
			PamSymbolManager<?> symbolManager = block.getPamSymbolManager();
			if (symbolManager == null) {
				continue;
			}
			FXSymbolOptionsPane<?> pane = null;
			try {
				pane = symbolManager.getFXOptionsPane(getUniqueName(), projector);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			if (pane == null) {
				continue;
			}
			final FXSymbolOptionsPane<?> fPane = pane;
			fPane.addSettingsListener(() -> {
				fPane.getParams(null);
				clipCanvas.repaint();
			});
			if (sourceBlocks.size() > 1) {
				box.getChildren().add(subTitle(block.getDataName()));
			}
			box.getChildren().add(fPane.getContentNode());
			n++;
		}
		return n == 0 ? null : box;
	}

	private Label subTitle(String text) {
		Label label = new Label(text);
		label.setStyle("-fx-font-weight: bold;");
		return label;
	}

	private Node createDataPane() {
		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new Insets(5));

		// add and remove buttons, as for the plots in the time display. 
		addSourceButton = new MenuButton("Data");
		addSourceButton.setGraphic(PamGlyphDude.createPamIcon("mdi2p-plus", PamGuiManagerFX.iconSize));
		addSourceButton.setTooltip(new Tooltip("Add data to the display"));
		removeSourceButton = new MenuButton("Data");
		removeSourceButton.setGraphic(PamGlyphDude.createPamIcon("mdi2m-minus", PamGuiManagerFX.iconSize));
		removeSourceButton.setTooltip(new Tooltip("Remove data from the display"));
		PamHBox buttons = new PamHBox(addSourceButton, removeSourceButton);
		buttons.setSpacing(5);

		sourceList = new PamVBox();
		sourceList.setSpacing(4);
		Label help = new Label("Anything which holds raw sound can be shown, e.g. clips, clicks or deep learning detections. "
				+ "Use the cog to choose which of the data are shown.");
		help.setWrapText(true);
		help.setStyle("-fx-font-size: 11; -fx-opacity: 0.8;");
		updateSourceChoice();
		box.getChildren().addAll(title("Data sources"), buttons, sourceList, help);

		channelChoice = new ChoiceBox<String>();
		channelChoice.setTooltip(new Tooltip("Only show clips from a single channel"));
		updateChannelChoice();
		channelChoice.setOnShowing(e -> updateChannelChoice());
		channelChoice.valueProperty().addListener((o, a, b) -> {
			if (settingControls || b == null) {
				return;
			}
			int ind = channelChoice.getItems().indexOf(b);
			int[] chans = PamUtils.getChannelArray(knownChannelSources);
			setDisplayChannels(ind <= 0 || chans == null || ind > chans.length ? 0 : 1 << chans[ind - 1]);
		});
		box.getChildren().addAll(title("Channels"), channelChoice);

		// the data selection of a source is shown on the back of a flip pane. 
		dataFlipPane = new PamFlipPane();
		dataFlipPane.setFrontContent(box);
		dataFlipPane.getPreAdvLabel().setText("");
		dataFlipPane.getPostAdvLabel().setText("data selection");
		dataFlipPane.setAdvLabelEditable(false);
		return dataFlipPane;
	}

	/**
	 * Update the list of data sources and the add and remove menus.
	 */
	private void updateSourceChoice() {
		if (sourceList == null) {
			return;
		}
		ArrayList<PamDataBlock<?>> blocks = ClipDisplayControlFX.getRawClipBlocks();

		addSourceButton.getItems().clear();
		for (PamDataBlock<?> block : blocks) {
			if (sourceBlocks.contains(block)) {
				continue;
			}
			MenuItem item = new MenuItem(block.getLongDataName());
			item.setOnAction(e -> {
				ArrayList<PamDataBlock<?>> newList = new ArrayList<PamDataBlock<?>>(sourceBlocks);
				newList.add(block);
				displayControl.setSourceDataBlocks(newList);
			});
			addSourceButton.getItems().add(item);
		}
		addSourceButton.setDisable(addSourceButton.getItems().isEmpty());

		removeSourceButton.getItems().clear();
		for (PamDataBlock<?> block : sourceBlocks) {
			MenuItem item = new MenuItem(block.getLongDataName());
			item.setOnAction(e -> {
				ArrayList<PamDataBlock<?>> newList = new ArrayList<PamDataBlock<?>>(sourceBlocks);
				newList.remove(block);
				displayControl.setSourceDataBlocks(newList);
			});
			removeSourceButton.getItems().add(item);
		}
		removeSourceButton.setDisable(removeSourceButton.getItems().isEmpty());

		sourceList.getChildren().clear();
		if (sourceBlocks.isEmpty()) {
			Label none = new Label(blocks.isEmpty() ? "No data which holds raw sound" : "No data shown. Press + to add data.");
			none.setWrapText(true);
			sourceList.getChildren().add(none);
			return;
		}
		for (PamDataBlock<?> block : sourceBlocks) {
			Label name = new Label(block.getLongDataName());
			name.setWrapText(true);
			name.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(name, Priority.ALWAYS);
			PamHBox row = new PamHBox(name);
			row.setSpacing(5);
			row.setAlignment(Pos.CENTER_LEFT);
			if (getDataSelectPane(block) != null) {
				PamButton cog = new PamButton();
				cog.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog", PamGuiManagerFX.iconSize));
				cog.setTooltip(new Tooltip("Choose which " + block.getDataName() + " data are shown"));
				cog.setOnAction(e -> showDataSelection(block));
				row.getChildren().add(cog);
			}
			sourceList.getChildren().add(row);
		}
	}

	/**
	 * Show the data selection of a data source on the back of the flip pane. 
	 * @param block - the data source.
	 */
	private void showDataSelection(PamDataBlock<?> block) {
		DynamicSettingsPane<Boolean> pane = getDataSelectPane(block);
		if (pane == null || dataFlipPane == null) {
			return;
		}
		PamScrollPane scroll = new PamScrollPane(pane.getContentNode());
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		dataFlipPane.setAdvPaneContent(scroll);
		dataFlipPane.getAdvLabel().setText(block.getDataName() + " ");
		dataFlipPane.flipToBack();
	}

	/**
	 * Get the data selection pane of a data source, making it if needed. 
	 * @param block - the data source.
	 * @return the pane, or null if the data have no data selector.
	 */
	private DynamicSettingsPane<Boolean> getDataSelectPane(PamDataBlock<?> block) {
		DynamicSettingsPane<Boolean> pane = dataSelectPanes.get(block);
		if (pane != null) {
			return pane;
		}
		DataSelector ds = getDataSelector(block);
		if (ds == null) {
			return null;
		}
		try {
			pane = ds.getDialogPaneFX();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		if (pane == null) {
			return null;
		}
		pane.setParams(true);
		final DynamicSettingsPane<Boolean> fPane = pane;
		fPane.addSettingsListener(() -> {
			fPane.getParams(true);
			clipCanvas.updateShownClips();
			updateStatus();
		});
		dataSelectPanes.put(block, fPane);
		return fPane;
	}

	/**
	 * Forget the data selection panes of sources which are no longer shown, and go
	 * back to the list of sources if the one being edited has gone.
	 */
	private void updateDataSelectPane() {
		dataSelectPanes.keySet().removeIf(b -> !sourceBlocks.contains(b));
		if (dataFlipPane != null && !dataFlipPane.isShowingFront()) {
			dataFlipPane.flipToFront();
		}
	}

	/**
	 * Get the first data block shown on the display.
	 * @return the data block, or null.
	 */
	public PamDataBlock<?> getSourceDataBlock() {
		return sourceBlocks.isEmpty() ? null : sourceBlocks.get(0);
	}

	/**
	 * Get all of the data blocks shown on the display.
	 * @return a copy of the list of data blocks.
	 */
	public ArrayList<PamDataBlock<?>> getSourceDataBlocks() {
		return new ArrayList<PamDataBlock<?>>(sourceBlocks);
	}

	/**
	 * Set the data shown on the display. Must be called on the FX thread.
	 * @param dataBlock - any data block whose data units hold raw sound, or null.
	 */
	@SuppressWarnings("rawtypes")
	public void setSourceDataBlock(PamDataBlock dataBlock) {
		ArrayList<PamDataBlock> list = new ArrayList<PamDataBlock>();
		if (dataBlock != null) {
			list.add(dataBlock);
		}
		setSourceDataBlocks(list);
	}

	/**
	 * Set the data shown on the display. Only the clips of sources which are added or
	 * removed change, so the images of the other clips are kept. Must be called on the
	 * FX thread.
	 * @param dataBlocks - data blocks whose data units hold raw sound.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void setSourceDataBlocks(List<? extends PamDataBlock> dataBlocks) {
		ArrayList<PamDataBlock<?>> newBlocks = new ArrayList<PamDataBlock<?>>();
		if (dataBlocks != null) {
			for (PamDataBlock block : dataBlocks) {
				if (block != null && !newBlocks.contains(block)) {
					newBlocks.add(block);
				}
			}
		}
		if (newBlocks.equals(sourceBlocks)) {
			updateSourceChoice();
			return;
		}
		ArrayList<PamDataBlock<?>> removed = new ArrayList<PamDataBlock<?>>(sourceBlocks);
		removed.removeAll(newBlocks);
		ArrayList<PamDataBlock<?>> added = new ArrayList<PamDataBlock<?>>(newBlocks);
		added.removeAll(sourceBlocks);

		for (PamDataBlock block : removed) {
			block.deleteObserver(clipObserver);
			if (viewerScroller != null) {
				viewerScroller.removeDataBlock(block);
			}
			clipCanvas.removeClipsFrom(block);
		}
		sourceBlocks.clear();
		sourceBlocks.addAll(newBlocks);
		params.dataSourceNames = new ArrayList<String>();
		for (PamDataBlock<?> block : sourceBlocks) {
			params.dataSourceNames.add(block.getLongDataName());
		}
		params.dataSourceName = sourceBlocks.isEmpty() ? null : sourceBlocks.get(0).getLongDataName();
		for (PamDataBlock block : added) {
			block.addObserver(clipObserver);
			if (viewerScroller != null) {
				viewerScroller.addDataBlock(block);
			}
		}
		// drop queued clips from sources which are no longer shown.
		newClips.removeIf(u -> !sourceBlocks.contains(u.getParentDataBlock()));
		knownChannelSources = 0;
		for (PamDataUnit u : clipCanvas.getClips()) {
			knownChannelSources |= u.getChannelBitmap();
		}

		updateSourceChoice();
		updateSymbolTab();
		updateDataSelectPane();
		sayValues();
		if (isViewer()) {
			loadViewerClips();
		}
		else {
			for (PamDataBlock block : added) {
				loadExistingClips(block);
			}
			trimClips();
		}
		updateChannelChoice();
		updateStatus();
	}

	/**
	 * Show the data already held by a data source, e.g. when the source is added
	 * whilst PAMGuard is running.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void loadExistingClips(PamDataBlock block) {
		if (block == null) {
			return;
		}
		ArrayList<PamDataUnit> units;
		synchronized (block.getSynchLock()) {
			units = new ArrayList<PamDataUnit>(block.getDataCopy());
		}
		if (units.isEmpty()) {
			return;
		}
		for (PamDataUnit unit : units) {
			knownChannelSources |= unit.getChannelBitmap();
			findTrigger(unit);
		}
		clipCanvas.addClipsInTimeOrder(units, params.clipParams.getNewClipOrder());
	}

	/**
	 * Find the data unit which triggered a clip, so it can be drawn on the clip.
	 */
	private static void findTrigger(PamDataUnit<?, ?> unit) {
		if (unit instanceof ClipDataUnit && ((ClipDataUnit) unit).getTriggerDataUnit() == null) {
			((ClipDataUnit) unit).setTriggerDataUnit(((ClipDataUnit) unit).findTriggerDataUnit());
		}
	}

	private void updateChannelChoice() {
		if (channelChoice == null) {
			return;
		}
		settingControls = true;
		ArrayList<String> items = new ArrayList<String>();
		items.add("Show all channels");
		String selected = items.get(0);
		int[] chans = PamUtils.getChannelArray(knownChannelSources);
		if (chans != null) {
			for (int ch : chans) {
				String str = "Only show channel " + ch;
				items.add(str);
				if (params.displayChannels == 1 << ch) {
					selected = str;
				}
			}
		}
		if (!items.equals(channelChoice.getItems())) {
			channelChoice.getItems().setAll(items);
		}
		channelChoice.setValue(selected);
		settingControls = false;
	}

	private void setDisplayChannels(int channelMap) {
		params.displayChannels = channelMap;
		clipCanvas.updateShownClips();
		updateChannelChoice();
		updateStatus();
	}

	/**
	 * Set the controls from the current parameters.
	 */
	private void setControls() {
		settingControls = true;
		ClipDisplayParameters p = params.clipParams;
		colourSlider.setMax(Math.max(colourSlider.getMax(), p.amlitudeMinVal + p.amplitudeRangeVal));
		colourSlider.setHighValue(p.amlitudeMinVal + p.amplitudeRangeVal);
		colourSlider.setLowValue(p.amlitudeMinVal);
		colourSlider.setHighValue(p.amlitudeMinVal + p.amplitudeRangeVal);
		fftLength.setValue(1 << p.getLogFFTLength());
		colourBox.setValue(p.getColourMap().toString());
		hScale.setValue(nearest(p.imageHScale));
		vScale.setValue(nearest(p.imageVScale));
		maxFreq.setValue(p.frequencyScale);
		showTrigger.setSelected(p.showTriggerOverlay);
		newClipsLast.setSelected(p.getNewClipOrder());
		fullSummary.setSelected(p.isShowFullSummary());
		if (maxClips != null) {
			maxClips.getValueFactory().setValue(p.getMaxClips());
			maxMinutes.getValueFactory().setValue(p.getMaxMinutes());
		}
		settingControls = false;
		sayValues();
		updateChannelChoice();
	}

	private static double nearest(double val) {
		double best = 1;
		for (double s : SCALES) {
			if (Math.abs(Math.log(s / val)) < Math.abs(Math.log(best / val))) {
				best = s;
			}
		}
		return best;
	}

	/**
	 * Read the controls into the parameters.
	 */
	private void getControls() {
		ClipDisplayParameters p = params.clipParams;
		p.amlitudeMinVal = Math.round(colourSlider.getLowValue());
		p.amplitudeRangeVal = Math.max(1, Math.round(colourSlider.getHighValue()) - p.amlitudeMinVal);
		if (fftLength.getValue() != null) {
			p.setLogFFTLength(Integer.numberOfTrailingZeros(fftLength.getValue()));
		}
		ColourArray.ColourArrayType cType = getColourType(colourBox.getValue());
		if (cType != null) {
			p.setColourMap(cType);
		}
		if (hScale.getValue() != null) {
			p.imageHScale = hScale.getValue();
		}
		if (vScale.getValue() != null) {
			p.imageVScale = vScale.getValue();
		}
		p.frequencyScale = maxFreq.getValue();
		p.showTriggerOverlay = showTrigger.isSelected();
		p.setNewClipOrder(newClipsLast.isSelected());
		p.setShowFullSummary(fullSummary.isSelected());
		if (maxClips != null) {
			p.setMaxClips(maxClips.getValue());
			p.setMaxMinutes(maxMinutes.getValue());
		}
	}

	/**
	 * Convert the name shown in the colour combo box to a Swing colour array type.
	 */
	private ColourArray.ColourArrayType getColourType(String name) {
		if (name == null) {
			return null;
		}
		pamViewFX.fxNodes.utilsFX.ColourArray.ColourArrayType fxType = pamViewFX.fxNodes.utilsFX.ColourArray
				.getColourArrayType(name);
		String key = fxType == null ? name : fxType.toString();
		for (ColourArray.ColourArrayType t : ColourArray.ColourArrayType.values()) {
			if (t.toString().equalsIgnoreCase(key)) {
				return t;
			}
		}
		return null;
	}

	private void controlChanged(boolean images) {
		if (settingControls) {
			return;
		}
		getControls();
		makeColourArray();
		sayValues();
		if (images) {
			clipCanvas.settingsChanged();
		}
		else {
			clipCanvas.repaint();
		}
		if (!isViewer()) {
			trimClips();
		}
	}

	private void sayValues() {
		ClipDisplayParameters p = params.clipParams;
		ampRangeLabel.setText(String.format("%3.0f to %3.0f dB", p.amlitudeMinVal, p.amlitudeMinVal + p.amplitudeRangeVal));
		float sr = getSampleRate();
		if (sr > 0) {
			int fftLen = 1 << p.getLogFFTLength();
			double dt = fftLen / sr;
			double df = sr / fftLen;
			fftResLabel.setText(String.format("Δt=%3.1f ms, Δf=%s", dt * 1000.,
					FrequencyFormat.formatFrequency(df, true)));
			maxFreqLabel.setText(FrequencyFormat.formatFrequency(p.frequencyScale * sr / 2, true));
		}
		else {
			fftResLabel.setText(" ");
			maxFreqLabel.setText(String.format("%3.2f × Nyquist", p.frequencyScale));
		}
		// the colour box holds the names of the Swing colour maps, so match on the name. 
		ColourArray.ColourArrayType swingType = getColourType(colourBox.getValue());
		if (swingType != null) {
			for (ColourArrayType fxType : ColourArrayType.values()) {
				if (fxType.toString().equalsIgnoreCase(swingType.toString())) {
					colourSlider.setColourArrayType(fxType);
					break;
				}
			}
		}
	}

	private float getSampleRate() {
		float sr = 0;
		for (PamDataBlock<?> block : sourceBlocks) {
			sr = Math.max(sr, block.getSampleRate());
		}
		return sr;
	}

	private void makeColourArray() {
		colourArray = ColourArray.createStandardColourArray(256, params.clipParams.getColourMap());
	}

	private void updateStatus() {
		int n = clipCanvas.getNumClips();
		int shown = clipCanvas.getNumShownClips();
		int sel = clipCanvas.getSelectedClips().size();
		String str = shown == n ? String.format("%d clips", n) : String.format("%d of %d clips shown", shown, n);
		if (sourceBlocks.isEmpty()) {
			str = "No data source: pick one in the display options";
		}
		if (sel > 0) {
			str += String.format(", %d selected", sel);
		}
		str += String.format(", zoom %d%%", Math.round(clipCanvas.getZoom() * 100));
		statusLabel.setText(str);
		params.zoom = clipCanvas.getZoom();
	}

	/*
	 * Viewer
	 */

	private void createViewerScroller() {
		viewerScroller = new AcousticScrollerFX(getUniqueName(), Orientation.HORIZONTAL, 100, 3600L * 1000L, true);
		for (PamDataBlock<?> block : sourceBlocks) {
			viewerScroller.addDataBlock(block);
		}
		// the window shown can be changed by dragging the edges of the scroll box or typing a 
		// time in it. Only clips within the window are shown.
		viewerScroller.setVisibleMillis(getViewerVisibleMillis());
		viewerScroller.addRangeObserver((oldVal, newVal) -> {
			if (newVal >= 1000) {
				params.viewerVisibleMillis = newVal;
			}
			scheduleViewerReload();
		});
		viewerScroller.addObserver(new PamScrollObserver() {
			@Override
			public void scrollValueChanged(AbstractPamScroller abstractPamScroller) {
				// the scroller can send lots of these whilst being dragged, so only reload once.
				scheduleViewerReload();
			}

			@Override
			public void scrollRangeChanged(AbstractPamScroller absPamScroller) {
				scheduleViewerReload();
			}
		});
		this.setBottom(viewerScroller.getNode());
	}

	/**
	 * Show the clips of all sources within the visible part of the viewer scroll bar.
	 * Clips which were already shown keep their images, so moving the scroller only
	 * makes spectrograms for the clips which have come into view.
	 */
	private void loadViewerClips() {
		viewerReloadPending = false;
		long t1 = Long.MIN_VALUE, t2 = Long.MAX_VALUE;
		if (viewerScroller != null && viewerScroller.getVisibleAmount() > 0) {
			t1 = viewerScroller.getValueMillis();
			t2 = t1 + viewerScroller.getVisibleAmount();
		}
		ArrayList<PamDataUnit> clips = new ArrayList<PamDataUnit>();
		for (PamDataBlock<?> dataBlock : sourceBlocks) {
			synchronized (dataBlock.getSynchLock()) {
				ArrayList<? extends PamDataUnit> all = dataBlock.getDataCopy();
				for (PamDataUnit clip : all) {
					long t = clip.getTimeMilliseconds();
					if (t >= t1 && t <= t2) {
						clips.add(clip);
					}
				}
			}
		}
		clips.sort((a, b) -> Long.compare(a.getTimeMilliseconds(), b.getTimeMilliseconds()));
		if (!params.clipParams.getNewClipOrder()) {
			java.util.Collections.reverse(clips);
		}
		int map = 0;
		for (PamDataUnit clip : clips) {
			map |= clip.getChannelBitmap();
			findTrigger(clip);
		}
		knownChannelSources = map;
		clipCanvas.setClips(clips);
		updateChannelChoice();
		updateStatus();
	}

	private long getViewerVisibleMillis() {
		return params.viewerVisibleMillis > 0 ? params.viewerVisibleMillis : ClipDisplayFXParams.DEFAULT_VIEWER_MILLIS;
	}

	private void scheduleViewerReload() {
		if (viewerReloadPending) {
			return;
		}
		viewerReloadPending = true;
		Platform.runLater(this::loadViewerClips);
	}

	/*
	 * Data
	 */

	private class ClipObserver extends PamObserverAdapter {

		@Override
		public String getObserverName() {
			return getUniqueName();
		}

		@Override
		public long getRequiredDataHistory(PamObservable o, Object arg) {
			return Math.max(10000, params.clipParams.getMaxMinutes() * 60L * 1000L);
		}

		@Override
		public void addData(PamObservable o, PamDataUnit dataUnit) {
			if (!sourceBlocks.contains(o)) {
				return;
			}
			PamDataUnit clip = dataUnit;
			if (isViewer()) {
				scheduleViewerReload();
				return;
			}
			if (clip instanceof ClipDataUnit) {
				((ClipDataUnit) clip).setTriggerDataUnit(((ClipDataUnit) clip).findTriggerDataUnit());
			}
			/*
			 * Clips can arrive in bursts, e.g. from a click detector in a busy period. Queue them and 
			 * add everything that is waiting in one go on the FX thread, rather than one FX task, one 
			 * trim of the old clips and one status update for every clip.
			 */
			newClips.add(clip);
			if (addPending.compareAndSet(false, true)) {
				Platform.runLater(() -> addQueuedClips());
			}
		}

		@Override
		public void updateData(PamObservable observable, PamDataUnit pamDataUnit) {
			if (updatePending.compareAndSet(false, true)) {
				Platform.runLater(() -> {
					updatePending.set(false);
					clipCanvas.updateShownClips();
					updateStatus();
				});
			}
		}

		@Override
		public void removeObservable(PamObservable o) {
		}

	}

	/**
	 * New clips waiting to be added to the display. 
	 */
	private final java.util.concurrent.ConcurrentLinkedQueue<PamDataUnit> newClips = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/**
	 * True when a task to add the queued clips is waiting on the FX thread. 
	 */
	private final java.util.concurrent.atomic.AtomicBoolean addPending = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * True when a task to update the shown clips is waiting on the FX thread. 
	 */
	private final java.util.concurrent.atomic.AtomicBoolean updatePending = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Add all of the queued clips to the display. Called on the FX thread. 
	 */
	private void addQueuedClips() {
		addPending.set(false);
		PamDataUnit clip;
		int map = knownChannelSources;
		java.util.ArrayList<PamDataUnit> clips = new java.util.ArrayList<>();
		while ((clip = newClips.poll()) != null) {
			map |= clip.getChannelBitmap();
			clips.add(clip);
		}
		if (clips.isEmpty()) {
			return;
		}
		clipCanvas.addClips(clips, !params.clipParams.getNewClipOrder());
		if (map != knownChannelSources) {
			knownChannelSources = map;
			updateChannelChoice();
		}
		trimClips();
		updateStatus();
	}

	private void trimClips() {
		long minTime = PamCalendar.getTimeInMillis() - params.clipParams.getMaxMinutes() * 60L * 1000L;
		clipCanvas.trimClips(params.clipParams.getMaxClips(), minTime);
	}

	/**
	 * Check whether a clip should be shown, using the data selector and the channel
	 * filter.
	 * @param clip - the clip.
	 * @return true if the clip should be shown.
	 */
	public boolean shouldShow(PamDataUnit clip) {
		if (clip == null) {
			return false;
		}
		if (params.displayChannels > 0 && (params.displayChannels & clip.getChannelBitmap()) == 0) {
			return false;
		}
		DataSelector ds = getDataSelector(clip.getParentDataBlock());
		if (ds != null && ds.scoreData(clip) <= 0) {
			return false;
		}
		return true;
	}

	private DataSelector getDataSelector(PamDataBlock<?> block) {
		if (block == null) {
			return null;
		}
		return block.getDataSelector(getUniqueName(), false);
	}

	/*
	 * Functions used by the canvas.
	 */

	/**
	 * @return the display settings.
	 */
	public ClipDisplayFXParams getParams() {
		return params;
	}

	public ClipDisplayParameters getClipDisplayParameters() {
		return params.clipParams;
	}

	public Color getBackgroundColour() {
		return PamUtilsFX.awtToFXColor(PamColors.getInstance().getColor(PamColor.PlOTWINDOW));
	}

	public Color getTextColour() {
		return PamUtilsFX.awtToFXColor(PamColors.getInstance().getForegroudColor(PamColor.AXIS));
	}

	public ColourArray getColourArray() {
		return colourArray;
	}

	public ClipDataProjectorFX getProjector() {
		return projector;
	}

	public ClipCanvasFX getClipCanvas() {
		return clipCanvas;
	}

	/**
	 * Get the symbol chooser of the data source of a clip.
	 * @param clip - the clip, or null for the first data source.
	 * @return the symbol chooser, or null.
	 */
	public PamSymbolChooser getSymbolChooser(PamDataUnit clip) {
		PamDataBlock<?> block = clip == null ? getSourceDataBlock() : clip.getParentDataBlock();
		if (block == null) {
			return null;
		}
		PamSymbolManager<?> symbolManager = block.getPamSymbolManager();
		if (symbolManager == null) {
			return null;
		}
		return symbolManager.getSymbolChooser(getUniqueName(), projector);
	}

	/**
	 * Get the border symbol for a clip.
	 * @param clip - the clip.
	 * @return the symbol, or null.
	 */
	public PamSymbol getBorderSymbol(PamDataUnit clip) {
		PamSymbolChooser chooser = getSymbolChooser(clip);
		if (chooser == null) {
			return null;
		}
		try {
			return chooser.getPamSymbol(projector, clip);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get the tool tip text for a clip.
	 * @param clip - the clip.
	 * @return tool tip text.
	 */
	public String getClipSummary(PamDataUnit clip) {
		if (params.clipParams.isShowFullSummary()) {
			try {
				return PamUtilsFX.htmlToNormal(clip.getSummaryString());
			}
			catch (Exception e) {
				// fall through to the short summary.
			}
		}
		String type = clip instanceof ClipDataUnit || clip.getParentDataBlock() == null ? "Clip" : clip.getParentDataBlock().getDataName();
		return type + " UID: " + clip.getUID() + "\n" + type + " UTC: " + PamCalendar.formatTime(clip.getTimeMilliseconds(), 3, false);
	}

	/**
	 * Play a clip, as on the Swing display.
	 * @param clip - the clip.
	 */
	public void playClip(PamDataUnit clip) {
		if (clip == null || clip.getParentDataBlock() == null) {
			return;
		}
		Object process = clip.getParentDataBlock().getParentProcess();
		if (process instanceof ClipProcess && clip instanceof ClipDataUnit) {
			((ClipProcess) process).playClip((ClipDataUnit) clip);
		}
		else {
			soundPlayback.ClipPlayback clipPlayback = soundPlayback.ClipPlayback.getInstance();
			if (clipPlayback.isPlaying()) {
				clipPlayback.stopPlayback();
			}
			else {
				double[][] wave = RawClipHelper.getWaveData(clip);
				if (wave != null) {
					clipPlayback.playClip(wave, RawClipHelper.getSampleRate(clip), true);
				}
			}
		}
	}

	/**
	 * Show the right click menu.
	 * @param clip - the clip under the mouse, or null.
	 * @param e - the mouse event.
	 */
	public void showContextMenu(PamDataUnit clip, MouseEvent e) {
		if (contextMenu != null) {
			contextMenu.hide();
		}
		contextMenu = new ContextMenu();
		List<MenuItem> items = contextMenu.getItems();
		List<PamDataUnit> selected = clipCanvas.getSelectedClips();

		if (clip != null) {
			// clip menu items from the data block, e.g. play, as in the Swing display.
			int nUnitItems = 0;
			try {
				List<JMenuItem> unitItems = clip.getParentDataBlock() == null ? null : clip.getParentDataBlock().getDataUnitMenuItems(null, null, clip);
				if (unitItems != null) {
					for (JMenuItem jItem : unitItems) {
						MenuItem fxItem = PamUtilsFX.swingMenuItemToFX(jItem);
						if (fxItem != null) {
							items.add(fxItem);
							nUnitItems++;
						}
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (nUnitItems == 0) {
				MenuItem play = new MenuItem("Play clip");
				play.setGraphic(PamGlyphDude.createPamIcon("mdi2p-play", PamGuiManagerFX.iconSize));
				play.setOnAction(a -> playClip(clip));
				items.add(play);
			}

			// items from the mark observers, e.g. detection grouping.
			try {
				List<MenuItem> markItems = marker.getSelectionMenuItems(e);
				if (markItems != null && markItems.size() > 0) {
					items.add(new SeparatorMenuItem());
					items.addAll(markItems);
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		// border colours
		PamSymbolChooser chooser = getSymbolChooser(clip);
		if (chooser instanceof StandardSymbolChooser) {
			try {
				ArrayList<JMenuItem> colItems = ((StandardSymbolChooser) chooser).getQuickMenuItems(
						PamController.getMainFrame(), this, "Border colour:", SymbolModType.LINECOLOUR, true);
				if (colItems != null && colItems.size() > 0) {
					if (items.size() > 0) {
						items.add(new SeparatorMenuItem());
					}
					for (JMenuItem jItem : colItems) {
						MenuItem fxItem = PamUtilsFX.swingMenuItemToFX(jItem);
						if (fxItem != null) {
							items.add(fxItem);
						}
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		// data selection
		if (!sourceBlocks.isEmpty()) {
			MenuItem dsItem = new MenuItem("Data selection ...");
			dsItem.setGraphic(PamGlyphDude.createPamIcon("mdi2f-filter-outline", PamGuiManagerFX.iconSize));
			dsItem.setOnAction(a -> showSettings("Data"));
			items.add(new SeparatorMenuItem());
			items.add(dsItem);
		}

		// channels
		int[] chans = PamUtils.getChannelArray(knownChannelSources);
		if (chans != null && chans.length > 1) {
			items.add(new SeparatorMenuItem());
			CheckMenuItem all = new CheckMenuItem("Show all channel groups");
			all.setSelected(params.displayChannels == 0);
			all.setOnAction(a -> setDisplayChannels(0));
			items.add(all);
			for (int ch : chans) {
				CheckMenuItem chItem = new CheckMenuItem("Only show channel " + ch);
				int chMap = 1 << ch;
				chItem.setSelected(params.displayChannels == chMap);
				chItem.setOnAction(a -> setDisplayChannels(chMap));
				items.add(chItem);
			}
		}

		// selection and zoom
		items.add(new SeparatorMenuItem());
		MenuItem selAll = new MenuItem("Select all clips");
		selAll.setOnAction(a -> clipCanvas.selectAll());
		items.add(selAll);
		if (!selected.isEmpty()) {
			MenuItem clearSel = new MenuItem("Clear selection");
			clearSel.setOnAction(a -> clipCanvas.clearSelection());
			items.add(clearSel);
		}
		MenuItem fit = new MenuItem("Zoom to show all clips");
		fit.setGraphic(PamGlyphDude.createPamIcon("mdi2f-fit-to-page-outline", PamGuiManagerFX.iconSize));
		fit.setOnAction(a -> clipCanvas.zoomToFit());
		items.add(fit);
		MenuItem one = new MenuItem("Normal clip size");
		one.setOnAction(a -> clipCanvas.setZoom(1));
		items.add(one);
		MenuItem settings = new MenuItem("Display options ...");
		settings.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog", PamGuiManagerFX.iconSize));
		settings.setOnAction(a -> showSettings("Display"));
		items.add(settings);

		contextMenu.show(clipCanvas, e.getScreenX(), e.getScreenY());
	}

	/**
	 * Show the settings pane on a tab.
	 * @param tabName - the tab to show.
	 */
	public void showSettings(String tabName) {
		setControls();
		for (Tab tab : settingsTabs.getTabs()) {
			if (tab.getText().equals(tabName)) {
				settingsTabs.getSelectionModel().select(tab);
			}
		}
		hidingPane.showHidePane(true);
	}

	public String getUniqueName() {
		return displayControl.getUnitName() + " FX Clip Display";
	}

	@Override
	public void symbolUpdate() {
		clipCanvas.repaint();
	}

	/*
	 * UserDisplayNodeFX
	 */

	@Override
	public UserDisplayControlFX getUserDisplayControl() {
		return displayControl;
	}

	@Override
	public String getName() {
		return displayControl.getUnitName();
	}

	@Override
	public Region getNode() {
		return this;
	}

	@Override
	public void openNode() {
		clipCanvas.requestFocus();
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
		Platform.runLater(() -> showSettings("Display"));
		return true;
	}

	/**
	 * Observer of the clip data block. 
	 */
	private ClipObserver clipObserver;

	@Override
	public void closeNode() {
		// called when the module is removed, so stop watching for new clips. 
		if (clipObserver != null) {
			for (PamDataBlock<?> block : sourceBlocks) {
				block.deleteObserver(clipObserver);
			}
		}
		clipCanvas.dispose();
		OverlayMarkProviders.singleInstance().removeProvider(marker);
	}

	@Override
	public void notifyModelChanged(int changeType) {
		switch (changeType) {
		case PamControllerInterface.INITIALIZATION_COMPLETE:
		case PamControllerInterface.CHANGED_PROCESS_SETTINGS:
			Platform.runLater(() -> {
				sayValues();
				clipCanvas.settingsChanged();
			});
			break;
		case PamControllerInterface.OFFLINE_DATA_LOADED:
		case PamControllerInterface.DATA_LOAD_COMPLETE:
			if (isViewer()) {
				Platform.runLater(() -> {
					// the scroller can lose the window length when its range is first set.
					if (viewerScroller != null && viewerScroller.getVisibleAmount() < 1000
							&& viewerScroller.getRangeMillis() > viewerScroller.getVisibleAmount()) {
						viewerScroller.setVisibleMillis(Math.min(getViewerVisibleMillis(), viewerScroller.getRangeMillis()));
					}
				});
				scheduleViewerReload();
			}
			break;
		}
	}

	@Override
	public UserDisplayNodeParams getDisplayParams() {
		return params.displayParams;
	}

	@Override
	public void setFrameHolder(PamInternalPane internalFrame) {
	}

	/*
	 * PamSettings
	 */

	@Override
	public String getUnitName() {
		return displayControl.getUnitName();
	}

	@Override
	public String getUnitType() {
		return "Clip Display FX";
	}

	@Override
	public Serializable getSettingsReference() {
		if (clipCanvas != null) {
			params.zoom = clipCanvas.getZoom();
		}
		if (hidingPane != null) {
			params.clipParams.setShowControlPanel(hidingPane.isShowing());
		}
		return params;
	}

	@Override
	public long getSettingsVersion() {
		return ClipDisplayFXParams.serialVersionUID;
	}

	@Override
	public boolean restoreSettings(PamControlledUnitSettings pamControlledUnitSettings) {
		try {
			ClipDisplayFXParams newParams = ((ClipDisplayFXParams) pamControlledUnitSettings.getSettings()).clone();
			if (newParams != null) {
				params = newParams;
				if (params.clipParams == null) {
					params.clipParams = new ClipDisplayParameters();
				}
				if (params.displayParams == null) {
					params.displayParams = new UserDisplayNodeParams();
				}
			}
		}
		catch (Exception e) {
			params = new ClipDisplayFXParams();
		}
		return true;
	}

}
