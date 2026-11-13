package IshmaelLocator.layoutFX;

import IshmaelLocator.IshLocControl;
import IshmaelLocator.IshLocHyperbParams;
import IshmaelLocator.IshLocParams;
import PamController.SettingsPane;
import PamDetection.PamDetection;
import PamguardMVC.PamDataBlock;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.PamToggleSwitch;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;

/**
 * JavaFX settings pane for the Ishmael hyperbolic locator. Has the same
 * controls as the Swing IshLocHyperbParamsDialog.
 *
 * @author Jamie Macaulay
 *
 */
public class IshLocPaneFX extends SettingsPane<IshLocParams> {

	private static final double LABEL_WIDTH = 170;

	/**
	 * The main pane.
	 */
	private PamVBox mainPane;

	/**
	 * Switch to take input from a detector.
	 */
	private PamToggleSwitch detectorSwitch;

	/**
	 * The detection source.
	 */
	private SourcePaneFX sourcePane;

	/**
	 * Time before the detection.
	 */
	private PamSpinner<Double> tBeforeSpinner;

	/**
	 * Time after the detection.
	 */
	private PamSpinner<Double> tAfterSpinner;

	/**
	 * Number of dimensions for the hyperbolic locator.
	 */
	private ChoiceBox<Integer> nDimensionsBox;

	private IshLocControl ishLocControl;

	/**
	 * The last parameters set in the pane.
	 */
	private IshLocParams lastParams;

	public IshLocPaneFX(IshLocControl ishLocControl) {
		super(null);
		this.ishLocControl = ishLocControl;
		mainPane = createPane();
	}

	private PamVBox createPane() {
		PamVBox mainPane = new PamVBox();
		mainPane.setSpacing(5);
		mainPane.setPadding(new Insets(5));

		detectorSwitch = new PamToggleSwitch("Get input from a detector");
		detectorSwitch.setTooltip(new Tooltip("Localise detections from a detector. Anything which outputs detections can be used."));
		detectorSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> enableControls());

		sourcePane = new SourcePaneFX("Detection Source", PamDetection.class, true, true);

		Label autoLabel = new Label("Auto detection");
		PamGuiManagerFX.titleFont2style(autoLabel);

		PamGridPane timeGrid = new PamGridPane();
		timeGrid.setHgap(5);
		timeGrid.setVgap(5);

		Label beforeLabel = new Label("Time before detection");
		beforeLabel.setPrefWidth(LABEL_WIDTH);
		timeGrid.add(beforeLabel, 0, 0);
		tBeforeSpinner = new PamSpinner<Double>(0., 1000., 0.2, 0.05);
		tBeforeSpinner.setEditable(true);
		tBeforeSpinner.getValueFactory().setConverter(PamSpinner.createStringConverter(3));
		tBeforeSpinner.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		timeGrid.add(tBeforeSpinner, 1, 0);
		timeGrid.add(new Label("s"), 2, 0);

		Label afterLabel = new Label("Time after detection");
		afterLabel.setPrefWidth(LABEL_WIDTH);
		timeGrid.add(afterLabel, 0, 1);
		tAfterSpinner = new PamSpinner<Double>(0., 1000., 0.2, 0.05);
		tAfterSpinner.setEditable(true);
		tAfterSpinner.getValueFactory().setConverter(PamSpinner.createStringConverter(3));
		tAfterSpinner.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		timeGrid.add(tAfterSpinner, 1, 1);
		timeGrid.add(new Label("s"), 2, 1);

		Label hyperbLabel = new Label("Hyperbolic Localisation");
		PamGuiManagerFX.titleFont2style(hyperbLabel);

		PamGridPane dimGrid = new PamGridPane();
		dimGrid.setHgap(5);
		Label dimLabel = new Label("Number of dimensions");
		dimLabel.setPrefWidth(LABEL_WIDTH);
		dimGrid.add(dimLabel, 0, 0);
		nDimensionsBox = new ChoiceBox<Integer>();
		nDimensionsBox.getItems().addAll(2, 3);
		nDimensionsBox.setTooltip(new Tooltip("Localise in 2D (x,y) or 3D (x,y,z)"));
		dimGrid.add(nDimensionsBox, 1, 0);

		mainPane.getChildren().addAll(detectorSwitch, sourcePane, autoLabel, timeGrid, hyperbLabel, dimGrid);
		return mainPane;
	}

	private void enableControls() {
		boolean b = detectorSwitch.isSelected();
		sourcePane.setEnabledWithChannels(b);
		tBeforeSpinner.setDisable(!b);
		tAfterSpinner.setDisable(!b);
	}

	@Override
	public IshLocParams getParams(IshLocParams currParams) {
		//the dialog calls getParams(null) to validate the pane. 
		if (currParams == null) currParams = lastParams;
		if (currParams == null) return null;
		PamDataBlock<?> source = sourcePane.getSource();
		if (detectorSwitch.isSelected()) {
			if (source == null) {
				PamDialogFX.showWarning("You must select a detection source for the Ishmael locator");
				return null;
			}
			if (source.getSequenceMapObject() != null) {
				PamDialogFX.showWarning("The selected source detector uses Beamformer output as a data source, and Beamformer output does not contain "
						+ "the link back to a single channel of raw audio data that is required for analysis. Please either change the source "
						+ "detector's data source, or select a different detector.");
				return null;
			}
		}
		int channels = sourcePane.getChannelList();
		if (channels == 0) {
			PamDialogFX.showWarning("You must select at least one channel for the Ishmael locator");
			return null;
		}
		currParams.setUseDetector(detectorSwitch.isSelected());
		if (source != null) {
			currParams.inputDataSource = source.toString();
		}
		currParams.channelList = channels;
		currParams.setTBefore(tBeforeSpinner.getValue());
		currParams.setTAfter(tAfterSpinner.getValue());
		if (currParams instanceof IshLocHyperbParams) {
			((IshLocHyperbParams) currParams).setNDimensions(nDimensionsBox.getValue() == null ? 2 : nDimensionsBox.getValue());
		}
		return currParams;
	}

	@Override
	public void setParams(IshLocParams input) {
		lastParams = input;
		// don't let the locator subscribe to its own output.
		if (ishLocControl.getLocatorOutputDataBlock() != null) {
			sourcePane.excludeDataBlock(ishLocControl.getLocatorOutputDataBlock(), true);
		}
		sourcePane.setSourceList();
		sourcePane.setSource(input.inputDataSource);
		sourcePane.setChannelList(input.channelList);
		detectorSwitch.setSelected(input.isUseDetector());
		tBeforeSpinner.getValueFactory().setValue(input.getTBefore());
		tAfterSpinner.getValueFactory().setValue(input.getTAfter());
		int nDim = 2;
		if (input instanceof IshLocHyperbParams) {
			nDim = ((IshLocHyperbParams) input).getNDimensions();
		}
		nDimensionsBox.setValue(nDim == 3 ? 3 : 2);
		enableControls();
	}

	@Override
	public String getName() {
		return "Ishmael Locator";
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
		return "detectors.ishmael.docs.ishmael_overview";
	}

}
