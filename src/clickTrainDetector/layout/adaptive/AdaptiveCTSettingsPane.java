package clickTrainDetector.layout.adaptive;

import PamController.SettingsPane;
import PamDetection.LocContents;
import PamguardMVC.PamDataBlock;
import PamguardMVC.RawDataHolder;
import clickTrainDetector.ClickTrainControl;
import clickTrainDetector.clickTrainAlgorithms.adaptive.AdaptiveCTParams;
import clickTrainDetector.clickTrainAlgorithms.adaptive.AdaptiveClickTrainAlgorithm;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.utilityPanes.PamToggleSwitch;

/**
 * Settings pane for the adaptive click train algorithm. Exposes a handful of
 * intuitive parameters (maximum ICI, sensitivity, detection probability) plus a
 * set of toggles to select which features the detector uses to group clicks
 * (ICI, amplitude, bearing, waveform correlation). Features that the source data
 * cannot provide (bearing, waveform correlation) are greyed out.
 *
 * @author Jamie Macaulay
 */
public class AdaptiveCTSettingsPane extends SettingsPane<AdaptiveCTParams> {

	private final PamBorderPane mainPane;

	private final AdaptiveClickTrainAlgorithm adaptiveClickTrainAlgorithm;

	private PamSpinner<Double> maxICISpinner;

	private Slider sensitivitySlider;

	private Slider detectionProbSlider;

	private PamToggleSwitch useICI;

	private PamToggleSwitch useAmplitude;

	private PamToggleSwitch useBearing;

	private PamToggleSwitch useCorrelation;

	public AdaptiveCTSettingsPane(AdaptiveClickTrainAlgorithm adaptiveClickTrainAlgorithm) {
		super(null);
		this.adaptiveClickTrainAlgorithm = adaptiveClickTrainAlgorithm;
		mainPane = new PamBorderPane();
		mainPane.setCenter(createPane());
	}

	/** Tooltip text shared between each control and its descriptive label. */
	private static final String TIP_MAXICI =
			"The absolute maximum inter-click interval (ICI) allowed within a click train.\n"
					+ "Trains whose median ICI exceeds this are rejected.";

	private static final String TIP_SENSITIVITY =
			"How strictly clicks must conform to a train. Loose (left) detects more but may merge trains;\n"
					+ "tight (right) is stricter and produces cleaner but possibly fragmented trains.";

	private static final String TIP_DETPROB =
			"The expected fraction of clicks that are detected. Lower values let the detector bridge\n"
					+ "more missed clicks (coasts); higher values keep trains tighter.";

	private static final String TIP_ICI =
			"Group clicks by the consistency of their inter-click interval. ICI is the single most\n"
					+ "reliable cue and is normally left on.";

	private static final String TIP_AMPLITUDE =
			"Group clicks by the consistency of their amplitude.";

	private static final String TIP_BEARING =
			"Group clicks by the consistency of their bearing. Available only for multi-channel data\n"
					+ "that provides a bearing.";

	private static final String TIP_CORRELATION =
			"Group clicks by waveform similarity (cross correlation), and refine the ICI measurement.\n"
					+ "More accurate but more processor intensive. Available only when waveform data is present.";

	private Pane createPane() {

		Label title = new Label("Adaptive Detector Settings");
		title.setFont(Font.font(null, FontWeight.BOLD, 11));

		PamGridPane grid = new PamGridPane();
		grid.setHgap(5);
		grid.setVgap(8);
		int row = 0;

		// max ICI
		maxICISpinner = new PamSpinner<>(0.0, Double.MAX_VALUE, 0.4, 0.01);
		maxICISpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		maxICISpinner.setEditable(true);
		maxICISpinner.setPrefWidth(90);
		maxICISpinner.setTooltip(new Tooltip(TIP_MAXICI));
		PamHBox iciBox = new PamHBox();
		iciBox.setAlignment(Pos.CENTER_LEFT);
		iciBox.setSpacing(5);
		iciBox.getChildren().addAll(maxICISpinner, new Label("s"));
		grid.add(tipLabel("Max. ICI", TIP_MAXICI), 0, row);
		grid.add(iciBox, 1, row);
		row++;

		// sensitivity
		sensitivitySlider = new Slider(0, 1, 0.5);
		sensitivitySlider.setShowTickMarks(true);
		sensitivitySlider.setMajorTickUnit(0.5);
		sensitivitySlider.setPrefWidth(160);
		sensitivitySlider.setTooltip(new Tooltip(TIP_SENSITIVITY));
		grid.add(tipLabel("Sensitivity", TIP_SENSITIVITY), 0, row);
		grid.add(labelledSlider(sensitivitySlider, "loose", "tight"), 1, row);
		row++;

		// detection probability
		detectionProbSlider = new Slider(0, 1, 0.9);
		detectionProbSlider.setShowTickMarks(true);
		detectionProbSlider.setMajorTickUnit(0.5);
		detectionProbSlider.setPrefWidth(160);
		detectionProbSlider.setTooltip(new Tooltip(TIP_DETPROB));
		grid.add(tipLabel("Detection prob.", TIP_DETPROB), 0, row);
		grid.add(detectionProbSlider, 1, row);
		row++;

		PamVBox holder = new PamVBox();
		holder.setSpacing(8);
		holder.setPadding(new Insets(10, 0, 0, 0));
		holder.getChildren().addAll(title, grid, createFeaturePane());

		return holder;
	}

	/**
	 * Create the pane that lets the user pick which features the detector uses.
	 */
	private Pane createFeaturePane() {
		Label label = new Label("Features used");
		label.setFont(Font.font(null, FontWeight.BOLD, 11));
		label.setTooltip(new Tooltip("Select which click features are used to group clicks into trains."));

		useICI = new PamToggleSwitch("Inter-click interval (ICI)");
		useICI.setTooltip(new Tooltip(TIP_ICI));

		useAmplitude = new PamToggleSwitch("Amplitude");
		useAmplitude.setTooltip(new Tooltip(TIP_AMPLITUDE));

		useBearing = new PamToggleSwitch("Bearing");
		useBearing.setTooltip(new Tooltip(TIP_BEARING));

		useCorrelation = new PamToggleSwitch("Waveform correlation");
		useCorrelation.setTooltip(new Tooltip(TIP_CORRELATION));

		PamVBox box = new PamVBox();
		box.setSpacing(5);
		box.setPadding(new Insets(5, 0, 0, 0));
		box.getChildren().addAll(label, useICI, useAmplitude, useBearing, useCorrelation);
		return box;
	}

	/**
	 * Create a label with a tooltip installed, so hovering the field name shows the
	 * same help as its control.
	 */
	private Label tipLabel(String text, String tip) {
		Label label = new Label(text);
		label.setTooltip(new Tooltip(tip));
		return label;
	}

	/**
	 * Wrap a slider with min/max text labels.
	 */
	private Pane labelledSlider(Slider slider, String minLabel, String maxLabel) {
		PamHBox box = new PamHBox();
		box.setAlignment(Pos.CENTER_LEFT);
		box.setSpacing(5);
		box.getChildren().addAll(new Label(minLabel), slider, new Label(maxLabel));
		return box;
	}

	/**
	 * Grey out features the source data cannot provide. The toggle's selected state
	 * (the user's preference) is preserved; the algorithm ignores unavailable
	 * features regardless.
	 *
	 * @param dataBlock - the current source data block, or null to query the
	 *                  algorithm's current parent.
	 */
	private void updateFeatureAvailability(PamDataBlock<?> dataBlock) {
		PamDataBlock<?> block = dataBlock;
		if (block == null) {
			block = adaptiveClickTrainAlgorithm.getClickTrainControl().getParentDataBlock();
		}

		boolean bearingAvailable = block != null && block.getLocalisationContents() != null
				&& block.getLocalisationContents().hasLocContent(LocContents.HAS_BEARING);
		boolean waveformAvailable = block != null && RawDataHolder.class.isAssignableFrom(block.getUnitClass());

		useBearing.setDisable(!bearingAvailable);
		useCorrelation.setDisable(!waveformAvailable);
	}

	@Override
	public AdaptiveCTParams getParams(AdaptiveCTParams currParams) {
		AdaptiveCTParams params = currParams.clone();
		params.maxICI = maxICISpinner.getValue();
		params.sensitivity = sensitivitySlider.getValue();
		params.detectionProb = detectionProbSlider.getValue();
		params.useICI = useICI.isSelected();
		params.useAmplitude = useAmplitude.isSelected();
		params.useBearing = useBearing.isSelected();
		params.useCorrelation = useCorrelation.isSelected();
		return params;
	}

	@Override
	public void setParams(AdaptiveCTParams input) {
		maxICISpinner.getValueFactory().setValue(input.maxICI);
		sensitivitySlider.setValue(input.sensitivity);
		detectionProbSlider.setValue(input.detectionProb);
		useICI.setSelected(input.useICI);
		useAmplitude.setSelected(input.useAmplitude);
		useBearing.setSelected(input.useBearing);
		useCorrelation.setSelected(input.useCorrelation);
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
		return "Adaptive Click Train Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
