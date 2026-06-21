package deepWhistle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;

/**
 * Settings pane for the {@link DeepWhistleMask}. The DeepWhistle mask is
 * relatively simple and has a single confidence threshold parameter which is
 * set using a spinner between 0 and 1.
 * <p>
 * It also shows the FFT-source configuration the model expects so the user can
 * set up the FFT (spectrogram) module that feeds the mask correctly.
 *
 * @author Jamie Macaulay
 */
public class DeepWhistleMaskPane extends FFTMaskSettingsPane {

	/**
	 * Recommended FFT settings for the DeepWhistle model. Shown to the user so
	 * the FFT source feeding the mask can be configured to match the data the
	 * model was trained on.
	 */
	private static final String FFT_GUIDANCE =
			"The DeepWhistle model expects the FFT data source to use a ~2 ms FFT "
			+ "length and a ~8 ms hop (the FFT magnitude is automatically rescaled "
			+ "for the window function and FFT length you choose). Configure these "
			+ "in the FFT (spectrogram) module that feeds this mask.";

	/**
	 * The main content node holding the controls.
	 */
	private PamVBox mainPane;

	/**
	 * Spinner used to set the confidence threshold (between 0 and 1).
	 */
	private PamSpinner<Double> confidenceSpinner;

	public DeepWhistleMaskPane() {
		createPane();
	}

	private void createPane() {

		confidenceSpinner = new PamSpinner<Double>(0.0, 1.0, DeepWhistleParamters.DEFAULT_CONFIDENCE, 0.05);
		confidenceSpinner.setEditable(true);
		confidenceSpinner.setPrefWidth(80);
		confidenceSpinner.setTooltip(new Tooltip(
				"The confidence threshold for the mask. FFT bins with a model confidence below this value are removed."));

		Label label = new Label("Confidence threshold");

		PamHBox confidenceBox = new PamHBox();
		confidenceBox.setSpacing(5);
		confidenceBox.setAlignment(Pos.CENTER_LEFT);
		confidenceBox.getChildren().addAll(label, confidenceSpinner);

		//FFT configuration guidance for the model.
		Label infoLabel = new Label(FFT_GUIDANCE);
		infoLabel.setWrapText(true);
		infoLabel.setMaxWidth(380);
		infoLabel.setGraphic(PamGlyphDude.createPamIcon("mdi2i-information-outline", Color.GRAY, PamGuiManagerFX.iconSize));
		infoLabel.setTooltip(new Tooltip(FFT_GUIDANCE));

		mainPane = new PamVBox();
		mainPane.setSpacing(5);
		mainPane.setPadding(new Insets(5, 0, 5, 0));
		mainPane.getChildren().addAll(confidenceBox, infoLabel);
	}

	@Override
	public Pane getContentNode() {
		return mainPane;
	}

	@Override
	public void setParams(MaskedFFTParamters params) {
		if (params instanceof DeepWhistleParamters) {
			confidenceSpinner.getValueFactory().setValue(((DeepWhistleParamters) params).confidenceThreshold);
		}
	}

	@Override
	public void getParams(MaskedFFTParamters params) {
		if (params instanceof DeepWhistleParamters) {
			((DeepWhistleParamters) params).confidenceThreshold = confidenceSpinner.getValue();
		}
	}

}
