package clickTrainDetector.layout.ukf;

import PamController.SettingsPane;
import PamDetection.LocContents;
import PamguardMVC.PamDataBlock;
import clickTrainDetector.ClickTrainControl;
import clickTrainDetector.clickTrainAlgorithms.ukf.AffinityNN;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFClickTrainAlgorithm;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFParams;
import clickTrainDetector.clickTrainAlgorithms.ukf.UKFTracker;
import clickTrainDetector.clickTrainAlgorithms.ukf.DefaultUKFParams;
import clickTrainDetector.clickTrainAlgorithms.ukf.DefaultUKFParams.DefaultUKFSpecies;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.io.File;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.flipPane.PamFlipPane;
import pamViewFX.fxNodes.utilityPanes.PamToggleSwitch;

/**
 * Settings pane for the UKF click train algorithm.
 *
 * @author Jamie Macaulay
 */
public class UKFCTSettingsPane extends SettingsPane<UKFParams> {

	private final PamBorderPane mainPane;

	private final UKFClickTrainAlgorithm ukfClickTrainAlgorithm;

	private PamToggleSwitch useAmplitude;
	private PamToggleSwitch useBearing;
	private PamSpinner<Double> bearingFloorSpinner;
	private PamSpinner<Double> maxICISpinner;
	private PamSpinner<Double> frameDurationSpinner;
	private PamSpinner<Integer> maxCoastSpinner;
	private PamSpinner<Integer> minLengthSpinner;
	private PamSpinner<Integer> confirmHitsSpinner;
	private PamToggleSwitch twoStage;
	private PamSpinner<Double> confidenceAmplitudeSpinner;
	private PamSpinner<Integer> nScanDepthSpinner;
	private PamSpinner<Integer> beamWidthSpinner;
	private PamSpinner<Integer> nScanKBestSpinner;
	private PamSpinner<Double> nScanCoastCostSpinner;
	private PamSpinner<Double> nScanNewTrackCostSpinner;
	private PamToggleSwitch useCustomAffinity;
	private TextField affinityFileField;
	private Label affinityInfoLabel;
	private PamButton affinityBrowseButton;
	private PamButton trainNetworkButton;
	private PamFlipPane flipPane;
	private UKFAffinityTrainPane trainPane;

	private static final String TIP_AMPLITUDE = "Track amplitude consistency (in addition to ICI).";
	private static final String TIP_BEARING =
			"Track bearing consistency. Available only for multi-channel data that provides a bearing.";
	private static final String TIP_BEARING_FLOOR =
			"The minimum bearing jump (degrees) tolerated between clicks - the bearing measurement-noise\n"
					+ "floor. Bearings are normally smooth, so keep this small. Raise it for species with noisy\n"
					+ "bearings (e.g. harbour porpoise, whose narrowband clicks give poor bearing measurements).";
	private static final String TIP_MAXICI =
			"The absolute maximum inter-click interval allowed within a click train (seconds).";
	private static final String TIP_FRAME =
			"Duration of the detection batching window. Clicks within a window are jointly assigned to\n"
					+ "tracks by the Hungarian algorithm. Use a value below the shortest expected ICI.";
	private static final String TIP_MAXCOAST =
			"Maximum number of consecutive missed clicks (coasts) before a track is closed.";
	private static final String TIP_MINLENGTH = "Minimum number of clicks for a track to be saved.";
	private static final String TIP_CONFIRM =
			"Number of clicks a track must gather before it is 'confirmed'. Until then it is tentative\n"
					+ "and is only offered clicks after every confirmed track has claimed one, so a new (possibly\n"
					+ "spurious) track cannot steal a click from an established train. 2 to the minimum train length.";
	private static final String TIP_NSCAN_DEPTH =
			"Multi-hypothesis association deferral, in frames. 0 uses greedy single-frame association\n"
					+ "(each frame's assignment is committed immediately). A positive value keeps the best few\n"
					+ "global assignments for this many frames before committing, so an ambiguous association\n"
					+ "(e.g. two trains crossing) can be corrected by later evidence. 4-8 is a reasonable range.";
	private static final String TIP_BEAMWIDTH =
			"Number of competing global hypotheses kept in the N-scan search. Larger explores more\n"
					+ "association possibilities at proportionally more processing cost.";
	private static final String TIP_NSCAN_KBEST =
			"Number of best assignments (Murty k-best) generated per hypothesis per frame in the N-scan\n"
					+ "search. Larger considers more alternatives at more processing cost.";
	private static final String TIP_NSCAN_COAST =
			"Cost (nats) charged in the N-scan search when an active, currently-due track is left without\n"
					+ "a click in a frame. Should be cheaper than accepting a clearly wrong match.";
	private static final String TIP_NSCAN_NEWTRACK =
			"Cost (nats) charged in the N-scan search when a loud click is not associated to any existing\n"
					+ "track and instead starts a new one.";
	private static final String TIP_TWOSTAGE =
			"Use ByteTrack-style two-stage association: associate loud (high-confidence) clicks first,\n"
					+ "then recover quiet (low-confidence) clicks against the remaining tracks.";
	private static final String TIP_CONFAMP =
			"Amplitude (dB) threshold separating high- from low-confidence clicks for two-stage\n"
					+ "association. Clicks at or above it are high-confidence. Set to 0 to treat all clicks as high.";
	private static final String TIP_CUSTOM_AFFINITY =
			"Use a custom (trained) affinity network instead of the built-in default. The network\n"
					+ "scores how well a detection matches a track during association. Provide a JSON file\n"
					+ "listing the network's 'weights' and 'biases'. If unset, the default Gaussian-gating\n"
					+ "network is used.";

	public UKFCTSettingsPane(UKFClickTrainAlgorithm ukfClickTrainAlgorithm) {
		super(null);
		this.ukfClickTrainAlgorithm = ukfClickTrainAlgorithm;
		mainPane = new PamBorderPane();

		// front = the settings (which include the "Train affinity network…" button);
		// back = the training pane. Flipping is triggered by that button.
		Pane front = createPane();
		trainPane = new UKFAffinityTrainPane(ukfClickTrainAlgorithm, this::onNetworkTrained);

		flipPane = new PamFlipPane();
		flipPane.getAdvLabel().setText("Train Affinity Network");
		flipPane.setFrontContent(front);
		flipPane.setAdvPaneContent(trainPane.getNode());

		trainNetworkButton.setOnAction(e -> {
			trainPane.refresh();
			flipPane.flipToBack();
		});

		mainPane.setCenter(flipPane);
	}

	/**
	 * Called when training has produced and saved a network file: point the custom
	 * affinity controls at it and flip back to the settings.
	 */
	private void onNetworkTrained(java.io.File file) {
		useCustomAffinity.setSelected(true);
		affinityFileField.setText(file.getAbsolutePath());
		enableAffinityControls(true);
		flipPane.flipToFront();
	}

	private Pane createPane() {
		Label title = new Label("UKF Detector Settings");
		title.setFont(Font.font(null, FontWeight.BOLD, 11));

		PamGridPane grid = new PamGridPane();
		grid.setHgap(5);
		grid.setVgap(8);
		int row = 0;

		maxICISpinner = doubleSpinner(0.0, Double.MAX_VALUE, 0.4, 0.01);
		maxICISpinner.setTooltip(new Tooltip(TIP_MAXICI));
		grid.add(tipLabel("Max. ICI", TIP_MAXICI), 0, row);
		grid.add(withUnit(maxICISpinner, "s"), 1, row++);

		frameDurationSpinner = doubleSpinner(0.001, Double.MAX_VALUE, 0.02, 0.01);
		frameDurationSpinner.setTooltip(new Tooltip(TIP_FRAME));
		grid.add(tipLabel("Frame window", TIP_FRAME), 0, row);
		grid.add(withUnit(frameDurationSpinner, "s"), 1, row++);

		maxCoastSpinner = intSpinner(1, 100, 4);
		maxCoastSpinner.setTooltip(new Tooltip(TIP_MAXCOAST));
		grid.add(tipLabel("Max. coasts", TIP_MAXCOAST), 0, row);
		grid.add(maxCoastSpinner, 1, row++);

		minLengthSpinner = intSpinner(3, 100, 3);
		minLengthSpinner.setTooltip(new Tooltip(TIP_MINLENGTH));
		grid.add(tipLabel("Min. train length", TIP_MINLENGTH), 0, row);
		grid.add(minLengthSpinner, 1, row++);

		confirmHitsSpinner = intSpinner(2, 100, 3);
		confirmHitsSpinner.setTooltip(new Tooltip(TIP_CONFIRM));
		grid.add(tipLabel("Confirm after", TIP_CONFIRM), 0, row);
		grid.add(withUnit(confirmHitsSpinner, "clicks"), 1, row++);

		PamVBox holder = new PamVBox();
		holder.setSpacing(8);
		holder.setPadding(new Insets(10, 0, 0, 0));
		holder.getChildren().addAll(title, grid, new javafx.scene.control.Separator(), createFeaturePane(),
				new javafx.scene.control.Separator(), createTwoStagePane(), createNScanPane(),
				new javafx.scene.control.Separator(), createAffinityPane(), new javafx.scene.control.Separator(),
				createDefaultSpeciesPane());
		return holder;
	}

	/**
	 * Create the collapsible advanced pane for multi-hypothesis (N-scan) association.
	 * Collapsed by default; the sub-controls are disabled when the deferral depth is
	 * 0 (greedy association).
	 */
	private Node createNScanPane() {
		PamGridPane grid = new PamGridPane();
		grid.setHgap(5);
		grid.setVgap(8);
		int row = 0;

		nScanDepthSpinner = intSpinner(0, 100, 7);
		nScanDepthSpinner.setTooltip(new Tooltip(TIP_NSCAN_DEPTH));
		grid.add(tipLabel("N-scan depth", TIP_NSCAN_DEPTH), 0, row);
		grid.add(withUnit(nScanDepthSpinner, "frames"), 1, row++);

		beamWidthSpinner = intSpinner(1, 50, 3);
		beamWidthSpinner.setTooltip(new Tooltip(TIP_BEAMWIDTH));
		grid.add(tipLabel("Beam width", TIP_BEAMWIDTH), 0, row);
		grid.add(beamWidthSpinner, 1, row++);

		nScanKBestSpinner = intSpinner(1, 20, 3);
		nScanKBestSpinner.setTooltip(new Tooltip(TIP_NSCAN_KBEST));
		grid.add(tipLabel("Assignments (k-best)", TIP_NSCAN_KBEST), 0, row);
		grid.add(nScanKBestSpinner, 1, row++);

		nScanCoastCostSpinner = doubleSpinner(0.0, Double.MAX_VALUE, 1.5, 0.5);
		nScanCoastCostSpinner.setTooltip(new Tooltip(TIP_NSCAN_COAST));
		grid.add(tipLabel("Coast cost", TIP_NSCAN_COAST), 0, row);
		grid.add(withUnit(nScanCoastCostSpinner, "nats"), 1, row++);

		nScanNewTrackCostSpinner = doubleSpinner(0.0, Double.MAX_VALUE, 2.5, 0.5);
		nScanNewTrackCostSpinner.setTooltip(new Tooltip(TIP_NSCAN_NEWTRACK));
		grid.add(tipLabel("New-track cost", TIP_NSCAN_NEWTRACK), 0, row);
		grid.add(withUnit(nScanNewTrackCostSpinner, "nats"), 1, row++);

		// the beam sub-controls only matter when deferral is on.
		nScanDepthSpinner.valueProperty().addListener((o, ov, nv) -> setNScanControlsDisabled(nv == null || nv == 0));

		TitledPane titled = new TitledPane("Multi-hypothesis association (N-scan)", grid);
		titled.setExpanded(false);
		return titled;
	}

	private void setNScanControlsDisabled(boolean disabled) {
		beamWidthSpinner.setDisable(disabled);
		nScanKBestSpinner.setDisable(disabled);
		nScanCoastCostSpinner.setDisable(disabled);
		nScanNewTrackCostSpinner.setDisable(disabled);
	}

	/**
	 * Create a pane allowing the user to load default settings, optionally tuned for
	 * a particular species.
	 * @return pane for selecting default species settings.
	 */
	private Pane createDefaultSpeciesPane() {
		MenuButton speciesChoiceBox = new MenuButton("Select Species");
		for (DefaultUKFSpecies species : DefaultUKFSpecies.values()) {
			MenuItem menuItem = new MenuItem(DefaultUKFParams.getDefaultSpeciesName(species));
			menuItem.setOnAction(action -> setParams(DefaultUKFParams.getDefaultUKFParams(species,
					ukfClickTrainAlgorithm.getClickTrainControl().getParentDataBlock())));
			speciesChoiceBox.getItems().add(menuItem);
		}

		PamHBox box = new PamHBox();
		box.setSpacing(5);
		box.setAlignment(Pos.CENTER_RIGHT);
		box.setPadding(new Insets(5, 0, 0, 0));
		box.getChildren().addAll(new Label("Optimise for Species"), speciesChoiceBox);
		return box;
	}

	private Pane createAffinityPane() {
		Label label = new Label("Affinity network");
		label.setFont(Font.font(null, FontWeight.BOLD, 11));

		useCustomAffinity = new PamToggleSwitch("Use custom affinity network");
		useCustomAffinity.setTooltip(new Tooltip(TIP_CUSTOM_AFFINITY));
		useCustomAffinity.selectedProperty().addListener((o, ov, nv) -> enableAffinityControls(nv));

		affinityFileField = new TextField();
		affinityFileField.setPromptText("Path to affinity network JSON file");
		affinityFileField.setTooltip(new Tooltip(TIP_CUSTOM_AFFINITY));
		HBox.setHgrow(affinityFileField, Priority.ALWAYS);

		affinityBrowseButton = new PamButton("Browse…");
		affinityBrowseButton.setOnAction(e -> browseForNetwork());

		PamHBox fileBox = new PamHBox();
		fileBox.setSpacing(5);
		fileBox.getChildren().addAll(affinityFileField, affinityBrowseButton);

		// summary feedback on the network that is (or would be) used.
		affinityInfoLabel = new Label();
		affinityInfoLabel.setWrapText(true);
		affinityInfoLabel.setFont(Font.font(null, 10));
		affinityInfoLabel.setPadding(new Insets(0, 0, 0, 5));
		// refresh the summary whenever the file path changes.
		affinityFileField.textProperty().addListener((o, ov, nv) -> updateAffinityInfo());

		// the action is wired in the constructor, once the flip pane exists.
		trainNetworkButton = new PamButton("Train affinity network…");
		trainNetworkButton.setTooltip(new Tooltip(
				"Train the affinity network from manually-annotated click trains, then save and use it."));

		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new javafx.geometry.Insets(5, 0, 0, 0));
		box.getChildren().addAll(label, useCustomAffinity, fileBox, affinityInfoLabel, trainNetworkButton);
		return box;
	}

	/**
	 * Update the summary label below the file field with information about the
	 * network that will be used: the built-in default, or the custom network loaded
	 * from the selected file (or an error if it cannot be loaded).
	 */
	private void updateAffinityInfo() {
		if (affinityInfoLabel == null) {
			return;
		}
		String path = affinityFileField.getText() == null ? "" : affinityFileField.getText().trim();
		if (!useCustomAffinity.isSelected() || path.isEmpty()) {
			affinityInfoLabel.setStyle("");
			affinityInfoLabel.setText("Using the built-in default (Gaussian-gating) network.");
			return;
		}
		try {
			AffinityNN network = AffinityNN.fromFile(new File(path), UKFTracker.FEATURE_DIM);
			int[] sizes = network.layerOutputSizes();
			StringBuilder arch = new StringBuilder().append(network.inputDim());
			for (int size : sizes) {
				arch.append(" → ").append(size);
			}
			affinityInfoLabel.setStyle("");
			affinityInfoLabel.setText(String.format("Loaded network: %s  (%d layer%s, %d parameters)", arch.toString(),
					network.numLayers(), network.numLayers() == 1 ? "" : "s", network.numParameters()));
		} catch (Exception e) {
			affinityInfoLabel.setStyle("-fx-text-fill: red;");
			affinityInfoLabel.setText("Could not load network: " + e.getMessage());
		}
	}

	private void browseForNetwork() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Select affinity network file");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Affinity network (*.json)", "*.json"));
		if (affinityFileField.getText() != null && !affinityFileField.getText().isEmpty()) {
			File current = new File(affinityFileField.getText());
			if (current.getParentFile() != null && current.getParentFile().isDirectory()) {
				chooser.setInitialDirectory(current.getParentFile());
			}
		}
		File file = chooser.showOpenDialog(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
		if (file != null) {
			affinityFileField.setText(file.getAbsolutePath());
		}
	}

	private void enableAffinityControls(boolean useCustom) {
		affinityFileField.setDisable(!useCustom);
		affinityBrowseButton.setDisable(!useCustom);
		updateAffinityInfo();
	}

	private Pane createFeaturePane() {
		Label label = new Label("Features used");
		label.setFont(Font.font(null, FontWeight.BOLD, 11));

		Label iciLabel = new Label("Inter-click interval (always used)");
		iciLabel.setTooltip(new Tooltip("ICI is always tracked by the UKF."));

		useAmplitude = new PamToggleSwitch("Amplitude");
		useAmplitude.setTooltip(new Tooltip(TIP_AMPLITUDE));
		useBearing = new PamToggleSwitch("Bearing");
		useBearing.setTooltip(new Tooltip(TIP_BEARING));

		bearingFloorSpinner = doubleSpinner(0.0, 180.0, 3.0, 0.5);
		bearingFloorSpinner.setTooltip(new Tooltip(TIP_BEARING_FLOOR));
		PamHBox bearingFloorBox = new PamHBox();
		bearingFloorBox.setAlignment(Pos.CENTER_LEFT);
		bearingFloorBox.setSpacing(5);
		bearingFloorBox.setPadding(new Insets(0, 0, 0, 20));
		bearingFloorBox.getChildren().addAll(tipLabel("Bearing jump floor", TIP_BEARING_FLOOR), bearingFloorSpinner,
				new Label("°"));
		// the floor only applies when bearing tracking is on.
		useBearing.selectedProperty().addListener((o, ov, nv) -> bearingFloorSpinner.setDisable(!nv));

		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new Insets(5, 0, 0, 0));
		box.getChildren().addAll(label, iciLabel, useAmplitude, useBearing, bearingFloorBox);
		return box;
	}

	private Pane createTwoStagePane() {
		Label label = new Label("Two-stage association");
		label.setFont(Font.font(null, FontWeight.BOLD, 11));

		twoStage = new PamToggleSwitch("Two-stage (ByteTrack)");
		twoStage.setTooltip(new Tooltip(TIP_TWOSTAGE));
		twoStage.selectedProperty().addListener((o, ov, nv) -> confidenceAmplitudeSpinner.setDisable(!nv));

		confidenceAmplitudeSpinner = doubleSpinner(0.0, Double.MAX_VALUE, 0.0, 1.0);
		confidenceAmplitudeSpinner.setTooltip(new Tooltip(TIP_CONFAMP));

		PamHBox confBox = new PamHBox();
		confBox.setAlignment(Pos.CENTER_LEFT);
		confBox.setSpacing(5);
		confBox.getChildren().addAll(tipLabel("Confidence amplitude", TIP_CONFAMP), confidenceAmplitudeSpinner,
				new Label("dB"));

		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new Insets(5, 0, 0, 0));
		box.getChildren().addAll(label, twoStage, confBox);
		return box;
	}

	private PamSpinner<Double> doubleSpinner(double min, double max, double init, double step) {
		PamSpinner<Double> spinner = new PamSpinner<>(min, max, init, step);
		spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		spinner.setEditable(true);
		spinner.setPrefWidth(90);
		return spinner;
	}

	private PamSpinner<Integer> intSpinner(int min, int max, int init) {
		PamSpinner<Integer> spinner = new PamSpinner<>(min, max, init, 1);
		spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		spinner.setEditable(true);
		spinner.setPrefWidth(90);
		return spinner;
	}

	private Pane withUnit(Node control, String unit) {
		PamHBox box = new PamHBox();
		box.setAlignment(Pos.CENTER_LEFT);
		box.setSpacing(5);
		box.getChildren().addAll(control, new Label(unit));
		return box;
	}

	private Label tipLabel(String text, String tip) {
		Label label = new Label(text);
		label.setTooltip(new Tooltip(tip));
		return label;
	}

	private void updateFeatureAvailability(PamDataBlock<?> dataBlock) {
		PamDataBlock<?> block = dataBlock;
		if (block == null) {
			block = ukfClickTrainAlgorithm.getClickTrainControl().getParentDataBlock();
		}
		boolean bearingAvailable = block != null && block.getLocalisationContents() != null
				&& block.getLocalisationContents().hasLocContent(LocContents.HAS_BEARING);
		useBearing.setDisable(!bearingAvailable);
		bearingFloorSpinner.setDisable(!bearingAvailable || !useBearing.isSelected());
	}

	@Override
	public UKFParams getParams(UKFParams currParams) {
		UKFParams params = currParams.clone();
		params.useAmplitude = useAmplitude.isSelected();
		params.useBearing = useBearing.isSelected();
		params.bearingFloorDeg = bearingFloorSpinner.getValue();
		params.maxICI = maxICISpinner.getValue();
		params.frameDuration = frameDurationSpinner.getValue();
		params.maxCoast = maxCoastSpinner.getValue();
		params.minTrackLength = minLengthSpinner.getValue();
		params.confirmHits = confirmHitsSpinner.getValue();
		params.twoStage = twoStage.isSelected();
		params.confidenceAmplitude = confidenceAmplitudeSpinner.getValue();
		params.nScanDepth = nScanDepthSpinner.getValue();
		params.beamWidth = beamWidthSpinner.getValue();
		params.nScanKBest = nScanKBestSpinner.getValue();
		params.nScanCoastCost = nScanCoastCostSpinner.getValue();
		params.nScanNewTrackCost = nScanNewTrackCostSpinner.getValue();
		params.useCustomAffinity = useCustomAffinity.isSelected();
		params.affinityNetworkFile = affinityFileField.getText() == null || affinityFileField.getText().trim().isEmpty()
				? null
				: affinityFileField.getText().trim();
		return params;
	}

	@Override
	public void setParams(UKFParams input) {
		useAmplitude.setSelected(input.useAmplitude);
		useBearing.setSelected(input.useBearing);
		bearingFloorSpinner.getValueFactory().setValue(input.bearingFloorDeg);
		maxICISpinner.getValueFactory().setValue(input.maxICI);
		frameDurationSpinner.getValueFactory().setValue(input.frameDuration);
		maxCoastSpinner.getValueFactory().setValue(input.maxCoast);
		minLengthSpinner.getValueFactory().setValue(input.minTrackLength);
		confirmHitsSpinner.getValueFactory().setValue(input.confirmHits);
		twoStage.setSelected(input.twoStage);
		confidenceAmplitudeSpinner.getValueFactory().setValue(input.confidenceAmplitude);
		confidenceAmplitudeSpinner.setDisable(!input.twoStage);
		nScanDepthSpinner.getValueFactory().setValue(input.nScanDepth);
		beamWidthSpinner.getValueFactory().setValue(input.beamWidth);
		nScanKBestSpinner.getValueFactory().setValue(input.nScanKBest);
		nScanCoastCostSpinner.getValueFactory().setValue(input.nScanCoastCost);
		nScanNewTrackCostSpinner.getValueFactory().setValue(input.nScanNewTrackCost);
		setNScanControlsDisabled(input.nScanDepth == 0);
		useCustomAffinity.setSelected(input.useCustomAffinity);
		affinityFileField.setText(input.affinityNetworkFile == null ? "" : input.affinityNetworkFile);
		enableAffinityControls(input.useCustomAffinity);
		updateFeatureAvailability(null);
	}

	@Override
	public void notifyChange(int flag, Object data) {
		if (flag == ClickTrainControl.NEW_PARENT_DATABLOCK) {
			updateFeatureAvailability(data instanceof PamDataBlock ? (PamDataBlock<?>) data : null);
		}
	}

	@Override
	public String getName() {
		return "UKF Click Train Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
