package Localiser.algorithms.timeDelayLocalisers.bearingLoc;


import Localiser.DelayMeasurementParams;
import PamController.SettingsPane;
import fftFilter.FFTFilterParams;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.SimpleFilterPaneFX;

/**
 * JavaFX settings for time delay measurement. Has the same options as the Swing
 * {@link DelayOptionsPanel}: filtering before measurement (the filter is set up in the pane
 * rather than in a separate dialog), up sampling, the waveform envelope and its leading edge, and
 * restricting the number of samples used.
 *
 * @author Jamie Macaulay
 */
public class DelayOptionsPaneFX extends SettingsPane<DelayMeasurementParams> {

	private CheckBox filterBearings, envelopeBearings, useLeadingEdge, upSample;

	/**
	 * The current delay measurement parameters.
	 */
	private DelayMeasurementParams delayMeasurementParams;

	/**
	 * Check box for restricting samples.
	 */
	private CheckBox restrictSamples;

	/**
	 * Restrict the number of samples.
	 */
	private TextField restrictSamplesField;

	/**
	 * Up sampling factor, 2 to 4.
	 */
	private PamSpinner<Integer> upSampleSpinner;

	/**
	 * Filter applied before the delay is measured.
	 */
	private SimpleFilterPaneFX simpleFilterPane;

	private PamVBox mainPane;

	public DelayOptionsPaneFX() {
		super(null);
		mainPane = createDelayOptionsPane();
	}

	private PamVBox createDelayOptionsPane() {

		PamVBox pane = new PamVBox();
		pane.setSpacing(5);

		Label title = new Label("Delay measurement options");
		PamGuiManagerFX.titleFont2style(title);
		pane.getChildren().add(title);

		//filter
		filterBearings = new CheckBox("Filter data before measurement");
		filterBearings.setTooltip(new Tooltip("Filter data prior to bearing measurement to improve accuracy"));
		filterBearings.setOnAction((action)->{
			enableControls();
		});
		simpleFilterPane = new SimpleFilterPaneFX(Orientation.HORIZONTAL);
		Region filterNode = (Region) simpleFilterPane.getContentNode();
		filterNode.setPadding(new Insets(0,0,0,20));
		pane.getChildren().addAll(filterBearings, filterNode);

		//up sampling
		upSample = new CheckBox("Up sample data x");
		upSample.setTooltip(new Tooltip("Up sampling data to a higher frequency can improve timing accuracy "
				+ "for narrow band clicks (i.e. harbour porpoise)"));
		upSample.setOnAction((action)->{
			enableControls();
		});
		upSampleSpinner = new PamSpinner<Integer>(2,4,2,1);
		upSampleSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		upSampleSpinner.setPrefWidth(90);
		PamHBox upSampleBox = new PamHBox(upSample, upSampleSpinner);
		upSampleBox.setSpacing(5);
		upSampleBox.setAlignment(Pos.CENTER_LEFT);
		pane.getChildren().add(upSampleBox);

		//envelope
		envelopeBearings = new CheckBox("Use waveform envelope");
		envelopeBearings.setTooltip(new Tooltip("Using the envelope can provide more accurate bearings for some narrowband pulses"));
		envelopeBearings.setOnAction((action)->{
			enableControls();
		});
		useLeadingEdge = new CheckBox("Use envelope leading edge only");
		useLeadingEdge.setTooltip(new Tooltip("For long pulses, or where there are echoes, restrict the calculation "
				+ "to the leading edge of the envelope"));
		PamHBox leadingEdgeBox = new PamHBox(useLeadingEdge);
		leadingEdgeBox.setPadding(new Insets(0,0,0,20));
		pane.getChildren().addAll(envelopeBearings, leadingEdgeBox);

		//restrict length
		restrictSamples = new CheckBox("Restrict length");
		restrictSamples.setTooltip(new Tooltip("In environments where echoes are an issue restricting initial samples of detections "
				+ "(e.g. click snippets) is a simple but effective way to increase the accuracy of time delay calculations. "
				+ "WARNING: Remember that this must cover the potential time delay in grouped detections "));
		restrictSamples.setOnAction((action)->{
			enableControls();
		});
		restrictSamplesField = new TextField();
		restrictSamplesField.setPrefColumnCount(5);
		PamHBox restrictBox = new PamHBox(restrictSamples, restrictSamplesField, new Label("samples"));
		restrictBox.setSpacing(5);
		restrictBox.setAlignment(Pos.CENTER_LEFT);
		pane.getChildren().add(restrictBox);

		return pane;
	}

	/**
	 * Set the sample rate of the data, which limits the filter frequencies.
	 * @param sampleRate - the sample rate in Hz.
	 */
	public void setSampleRate(double sampleRate) {
		simpleFilterPane.setSampleRate(sampleRate);
	}

	private void enableControls() {
		simpleFilterPane.getContentNode().setDisable(!filterBearings.isSelected());
		upSampleSpinner.setDisable(!upSample.isSelected());
		useLeadingEdge.setDisable(!envelopeBearings.isSelected());
		restrictSamplesField.setDisable(!restrictSamples.isSelected());
	}

	@Override
	public void setParams(DelayMeasurementParams delayMeasurementParams) {
		this.delayMeasurementParams = delayMeasurementParams.clone();
		filterBearings.setSelected(delayMeasurementParams.filterBearings);
		FFTFilterParams filterParams = delayMeasurementParams.delayFilterParams;
		if (filterParams == null) {
			filterParams = new FFTFilterParams();
		}
		simpleFilterPane.setParams(filterParams);
		upSample.setSelected(delayMeasurementParams.getUpSample() > 1);
		upSampleSpinner.getValueFactory().setValue(Math.min(4, Math.max(2, delayMeasurementParams.getUpSample())));
		envelopeBearings.setSelected(delayMeasurementParams.envelopeBearings);
		useLeadingEdge.setSelected(delayMeasurementParams.useLeadingEdge);
		restrictSamples.setSelected(delayMeasurementParams.useRestrictedBins);
		restrictSamplesField.setText(String.format("%d", delayMeasurementParams.restrictedBins));
		enableControls();
	}

	@Override
	public DelayMeasurementParams getParams(DelayMeasurementParams delayMeasurementParams) {
		if (delayMeasurementParams == null) {
			delayMeasurementParams = new DelayMeasurementParams();
		}
		// only change the filter if it's used or had already been set, as the Swing dialog does.
		FFTFilterParams filterParams = null;
		if (this.delayMeasurementParams != null) {
			filterParams = this.delayMeasurementParams.delayFilterParams;
		}
		if (filterBearings.isSelected() || filterParams != null) {
			FFTFilterParams newFilter = simpleFilterPane.getParams(new FFTFilterParams());
			if (newFilter == null) {
				return null;
			}
			filterParams = newFilter;
		}
		delayMeasurementParams.delayFilterParams = filterParams;
		delayMeasurementParams.filterBearings = filterBearings.isSelected();
		delayMeasurementParams.setUpSample(upSample.isSelected() ? upSampleSpinner.getValue().intValue() : 1);
		delayMeasurementParams.envelopeBearings = envelopeBearings.isSelected();
		delayMeasurementParams.useLeadingEdge = useLeadingEdge.isSelected() && delayMeasurementParams.envelopeBearings;
		delayMeasurementParams.useRestrictedBins = restrictSamples.isSelected();
		try {
			delayMeasurementParams.restrictedBins = Integer.valueOf(restrictSamplesField.getText().trim());
		}
		catch(Exception e) {
			PamDialogFX.showWarning(null, "Delay measurement settings", "The entry in the samples text field is invalid.");
			return null;
		}
		if (delayMeasurementParams.useRestrictedBins && delayMeasurementParams.restrictedBins<10) {
			PamDialogFX.showWarning(null, "Delay measurement settings", "The entry in the samples text field is invalid. It must be >= 10");
			return null;
		}
		if (delayMeasurementParams.filterBearings && delayMeasurementParams.delayFilterParams == null) {
			PamDialogFX.showWarning(null, "Delay measurement settings", "Filter parameters have not been set");
			return null;
		}
		return delayMeasurementParams;
	}

	@Override
	public String getName() {
		return "Delay Measurement";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
