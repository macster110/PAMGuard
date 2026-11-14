package clipgenerator.layoutFX;

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
import clipgenerator.ClipControl;
import clipgenerator.ClipDataBlock;
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
 * JavaFX clip display. Clips are drawn as spectrogram tiles on one large canvas which
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

	private ClipControl clipControl;

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
	private Slider minAmplitude, ampRange, maxFreq;

	private Label minAmpLabel, ampRangeLabel, maxFreqLabel, fftResLabel;

	private ChoiceBox<Integer> fftLength;

	private ChoiceBox<Double> hScale, vScale;

	private ColorComboBox colourBox;

	private Region colourBar;

	private CheckBox showTrigger, newClipsLast, fullSummary;

	private PamSpinner<Integer> maxClips, maxMinutes;

	private ChoiceBox<String> channelChoice;

	private boolean settingControls;

	private DynamicSettingsPane<Boolean> dataSelectPane;

	private FXSymbolOptionsPane<?> symbolOptionsPane;

	private boolean viewerReloadPending;

	public ClipDisplayFX(ClipControl clipControl) {
		this.clipControl = clipControl;
		projector = new ClipDataProjectorFX();
		PamSettingManager.getInstance().registerSettings(this);
		makeColourArray();
		createPane();

		ClipDataBlock dataBlock = clipControl.getClipDataBlock();
		if (dataBlock != null) {
			dataBlock.addObserver(new ClipObserver());
		}

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

		Node symbolNode = createSymbolPane();
		if (symbolNode != null) {
			PamScrollPane symbolScroll = new PamScrollPane(symbolNode);
			symbolScroll.setFitToWidth(true);
			symbolScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
			settingsTabs.getTabs().add(new Tab("Symbols", symbolScroll));
		}

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

		minAmplitude = new Slider(0, 200, 50);
		minAmplitude.setPrefWidth(150);
		minAmplitude.setMajorTickUnit(50);
		minAmplitude.setMinorTickCount(4);
		minAmplitude.setSnapToTicks(true);
		minAmplitude.setShowTickMarks(true);
		minAmplitude.setShowTickLabels(true);
		minAmplitude.setTooltip(new Tooltip("Amplitude shown at the bottom of the colour scale"));
		specGrid.add(new Label("Min amplitude"), 0, row);
		specGrid.add(minAmplitude, 1, row);
		specGrid.add(minAmpLabel = new Label(), 2, row++);

		ampRange = new Slider(40, 200, 70);
		ampRange.setPrefWidth(150);
		ampRange.setMajorTickUnit(40);
		ampRange.setMinorTickCount(3);
		ampRange.setSnapToTicks(true);
		ampRange.setShowTickMarks(true);
		ampRange.setShowTickLabels(true);
		ampRange.setTooltip(new Tooltip("Range of amplitudes shown on the colour scale"));
		specGrid.add(new Label("Range"), 0, row);
		specGrid.add(ampRange, 1, row);
		specGrid.add(ampRangeLabel = new Label(), 2, row++);

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

		colourBox = new ColorComboBox(ColorComboBox.COLOUR_ARRAY_BOX);
		colourBox.setPrefWidth(150);
		specGrid.add(new Label("Colours"), 0, row);
		specGrid.add(colourBox, 1, row++);
		colourBar = new Region();
		colourBar.setPrefHeight(12);
		colourBar.setMinHeight(12);
		specGrid.add(colourBar, 0, row++, 3, 1);

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
		minAmplitude.valueProperty().addListener((o, a, b) -> controlChanged(true));
		ampRange.valueProperty().addListener((o, a, b) -> controlChanged(true));
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

	private Node createSymbolPane() {
		PamSymbolManager<?> symbolManager = clipControl.getClipDataBlock().getPamSymbolManager();
		if (symbolManager == null) {
			return null;
		}
		try {
			symbolOptionsPane = symbolManager.getFXOptionsPane(getUniqueName(), projector);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		if (symbolOptionsPane == null) {
			return null;
		}
		symbolOptionsPane.addSettingsListener(() -> {
			symbolOptionsPane.getParams(null);
			clipCanvas.repaint();
		});
		PamVBox box = new PamVBox(title("Clip border colours"), symbolOptionsPane.getContentNode());
		box.setSpacing(5);
		box.setPadding(new Insets(5));
		return box;
	}

	private Node createDataPane() {
		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new Insets(5));

		DataSelector ds = getDataSelector();
		if (ds != null) {
			try {
				dataSelectPane = ds.getDialogPaneFX();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (dataSelectPane != null) {
			dataSelectPane.setParams(true);
			dataSelectPane.addSettingsListener(() -> {
				dataSelectPane.getParams(true);
				clipCanvas.updateShownClips();
				updateStatus();
			});
			box.getChildren().addAll(title("Data selection"), dataSelectPane.getContentNode());
		}

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
		return box;
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
		minAmplitude.setValue(p.amlitudeMinVal);
		ampRange.setValue(p.amplitudeRangeVal);
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
		p.amlitudeMinVal = minAmplitude.getValue();
		p.amplitudeRangeVal = ampRange.getValue();
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
		minAmpLabel.setText(String.format("%3.0f dB", p.amlitudeMinVal));
		ampRangeLabel.setText(String.format("%3.0f dB", p.amplitudeRangeVal));
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
		colourBar.setStyle("-fx-background-color: " + getColourGradient() + "; -fx-background-radius: 3;");
	}

	/**
	 * Make a CSS gradient from the colours used for the clip images, low amplitudes on the left.
	 */
	private String getColourGradient() {
		if (colourArray == null) {
			makeColourArray();
		}
		java.awt.Color[] cols = colourArray.getColours();
		StringBuilder sb = new StringBuilder("linear-gradient(to right");
		int nStops = 12;
		for (int i = 0; i <= nStops; i++) {
			int ind = Math.min(cols.length - 1, i * (cols.length - 1) / nStops);
			java.awt.Color c = cols[ind];
			sb.append(String.format(", #%02x%02x%02x %d%%", c.getRed(), c.getGreen(), c.getBlue(), i * 100 / nStops));
		}
		sb.append(")");
		return sb.toString();
	}

	private float getSampleRate() {
		ClipDataBlock dataBlock = clipControl.getClipDataBlock();
		return dataBlock == null ? 0 : dataBlock.getSampleRate();
	}

	private void makeColourArray() {
		colourArray = ColourArray.createStandardColourArray(256, params.clipParams.getColourMap());
	}

	private void updateStatus() {
		int n = clipCanvas.getNumClips();
		int shown = clipCanvas.getNumShownClips();
		int sel = clipCanvas.getSelectedClips().size();
		String str = shown == n ? String.format("%d clips", n) : String.format("%d of %d clips shown", shown, n);
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
		viewerScroller.addDataBlock(clipControl.getClipDataBlock());
		viewerScroller.addObserver(new PamScrollObserver() {
			@Override
			public void scrollValueChanged(AbstractPamScroller abstractPamScroller) {
				loadViewerClips();
			}

			@Override
			public void scrollRangeChanged(AbstractPamScroller absPamScroller) {
				loadViewerClips();
			}
		});
		this.setBottom(viewerScroller.getNode());
	}

	/**
	 * Show the clips within the visible part of the viewer scroll bar.
	 */
	private void loadViewerClips() {
		viewerReloadPending = false;
		ClipDataBlock dataBlock = clipControl.getClipDataBlock();
		if (dataBlock == null) {
			return;
		}
		long t1 = Long.MIN_VALUE, t2 = Long.MAX_VALUE;
		if (viewerScroller != null && viewerScroller.getVisibleAmount() > 0) {
			t1 = viewerScroller.getValueMillis();
			t2 = t1 + viewerScroller.getVisibleAmount();
		}
		ArrayList<ClipDataUnit> clips = new ArrayList<ClipDataUnit>();
		synchronized (dataBlock.getSynchLock()) {
			ArrayList<ClipDataUnit> all = dataBlock.getDataCopy();
			for (ClipDataUnit clip : all) {
				long t = clip.getTimeMilliseconds();
				if (t >= t1 && t <= t2) {
					clips.add(clip);
				}
			}
		}
		for (ClipDataUnit clip : clips) {
			knownChannelSources |= clip.getChannelBitmap();
			if (clip.getTriggerDataUnit() == null) {
				clip.setTriggerDataUnit(clip.findTriggerDataUnit());
			}
		}
		clipCanvas.setClips(clips);
		updateStatus();
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
			ClipDataUnit clip = (ClipDataUnit) dataUnit;
			if (isViewer()) {
				scheduleViewerReload();
				return;
			}
			clip.setTriggerDataUnit(clip.findTriggerDataUnit());
			Platform.runLater(() -> {
				int map = knownChannelSources | clip.getChannelBitmap();
				if (map != knownChannelSources) {
					knownChannelSources = map;
					updateChannelChoice();
				}
				clipCanvas.addClip(clip, !params.clipParams.getNewClipOrder());
				trimClips();
				updateStatus();
			});
		}

		@Override
		public void updateData(PamObservable observable, PamDataUnit pamDataUnit) {
			Platform.runLater(() -> {
				clipCanvas.updateShownClips();
				updateStatus();
			});
		}

		@Override
		public void removeObservable(PamObservable o) {
		}

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
	public boolean shouldShow(ClipDataUnit clip) {
		if (clip == null) {
			return false;
		}
		if (params.displayChannels > 0 && (params.displayChannels & clip.getChannelBitmap()) == 0) {
			return false;
		}
		DataSelector ds = getDataSelector();
		if (ds != null && ds.scoreData(clip) <= 0) {
			return false;
		}
		return true;
	}

	private DataSelector getDataSelector() {
		ClipDataBlock dataBlock = clipControl.getClipDataBlock();
		if (dataBlock == null) {
			return null;
		}
		return dataBlock.getDataSelector(getUniqueName(), false);
	}

	/*
	 * Functions used by the canvas.
	 */

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

	public PamSymbolChooser getSymbolChooser() {
		PamSymbolManager<?> symbolManager = clipControl.getClipDataBlock().getPamSymbolManager();
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
	public PamSymbol getBorderSymbol(ClipDataUnit clip) {
		PamSymbolChooser chooser = getSymbolChooser();
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
	public String getClipSummary(ClipDataUnit clip) {
		if (params.clipParams.isShowFullSummary()) {
			try {
				return PamUtilsFX.htmlToNormal(clip.getSummaryString());
			}
			catch (Exception e) {
				// fall through to the short summary.
			}
		}
		return "Clip UID: " + clip.getUID() + "\nClip UTC: " + PamCalendar.formatTime(clip.getLastUpdateTime(), 3, false);
	}

	/**
	 * Play a clip, as on the Swing display.
	 * @param clip - the clip.
	 */
	public void playClip(ClipDataUnit clip) {
		if (clip == null || clip.getParentDataBlock() == null) {
			return;
		}
		Object process = clip.getParentDataBlock().getParentProcess();
		if (process instanceof ClipProcess) {
			((ClipProcess) process).playClip(clip);
		}
		else {
			soundPlayback.ClipPlayback clipPlayback = soundPlayback.ClipPlayback.getInstance();
			if (clipPlayback.isPlaying()) {
				clipPlayback.stopPlayback();
			}
			else {
				clipPlayback.playClip(clip.getRawData(), clip.getParentDataBlock().getSampleRate(), true);
			}
		}
	}

	/**
	 * Show the right click menu.
	 * @param clip - the clip under the mouse, or null.
	 * @param e - the mouse event.
	 */
	public void showContextMenu(ClipDataUnit clip, MouseEvent e) {
		if (contextMenu != null) {
			contextMenu.hide();
		}
		contextMenu = new ContextMenu();
		List<MenuItem> items = contextMenu.getItems();
		List<ClipDataUnit> selected = clipCanvas.getSelectedClips();

		if (clip != null) {
			// clip menu items from the data block, e.g. play, as in the Swing display.
			int nUnitItems = 0;
			try {
				List<JMenuItem> unitItems = clipControl.getClipDataBlock().getDataUnitMenuItems(null, null, clip);
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
		PamSymbolChooser chooser = getSymbolChooser();
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
		if (dataSelectPane != null) {
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
		return clipControl.getUnitName() + " FX Clip Display";
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
		return null;
	}

	@Override
	public String getName() {
		return clipControl.getUnitName();
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

	@Override
	public void closeNode() {
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
		return clipControl.getUnitName();
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
