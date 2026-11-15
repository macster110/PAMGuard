package ltsa.layoutFX;

import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import fftManager.FFTDataUnit;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import ltsa.LtsaControl;
import ltsa.LtsaParameters;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;

/**
 * JavaFX settings for the Long Term Spectral Average. Holds the same options as the Swing 
 * {@link ltsa.LtsaDialog}: the FFT data source, channels and the measurement interval. 
 */
public class LtsaSettingsPaneFX extends SettingsPane<LtsaParameters> {

	private LtsaControl ltsaControl;

	private PamBorderPane mainPane;

	private SourcePaneFX sourcePane;

	private Spinner<Integer> interval;

	private Spinner<Integer> longerFactor;

	public LtsaSettingsPaneFX(LtsaControl ltsaControl) {
		super(null);
		this.ltsaControl = ltsaControl;

		PamVBox holder = new PamVBox();
		holder.setSpacing(5);

		Label sourceLabel = new Label("FFT data source");
		PamGuiManagerFX.titleFont2style(sourceLabel);
		sourcePane = new SourcePaneFX(FFTDataUnit.class, true, true);
		sourcePane.excludeDataBlock(ltsaControl.getLtsaProcess().getLtsaDataBlock(), true);

		Label measureLabel = new Label("Measurement");
		PamGuiManagerFX.titleFont2style(measureLabel);
		measureLabel.setPadding(new Insets(10, 0, 0, 0));

		interval = new Spinner<Integer>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 86400, 60, 10));
		interval.setEditable(true);
		interval.setPrefWidth(110);
		longerFactor = new Spinner<Integer>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10, 1));
		longerFactor.setEditable(true);
		longerFactor.setPrefWidth(110);

		GridPane grid = new GridPane();
		grid.setHgap(5);
		grid.setVgap(5);
		grid.setAlignment(Pos.CENTER_LEFT);
		grid.add(new Label("Measurement interval"), 0, 0);
		grid.add(interval, 1, 0);
		grid.add(new Label("seconds"), 2, 0);
		grid.add(new Label("Longer average"), 0, 1);
		grid.add(longerFactor, 1, 1);
		grid.add(new Label("x measurement interval"), 2, 1);

		Label info = new Label("The LTSA averages the FFT data over each measurement interval. A second, "
				+ "longer average is made over several intervals.");
		info.setWrapText(true);
		info.setMaxWidth(380);

		holder.getChildren().addAll(sourceLabel, sourcePane, measureLabel, grid, info);
		mainPane = new PamBorderPane(holder);
		mainPane.setPadding(new Insets(5));
	}

	@Override
	public LtsaParameters getParams(LtsaParameters currParams) {
		LtsaParameters params = (currParams == null ? ltsaControl.getLtsaParameters() : currParams).clone();
		PamDataBlock<?> dataBlock = sourcePane.getSource();
		if (dataBlock == null) {
			PamDialogFX.showWarning(null, getName(), "Select an FFT data source for the LTSA.");
			return null;
		}
		params.dataSource = dataBlock.getLongDataName();
		params.channelMap = sourcePane.getChannelList();
		if (params.channelMap == 0) {
			PamDialogFX.showWarning(null, getName(), "Select at least one channel for the LTSA.");
			return null;
		}
		try {
			interval.increment(0); // commits any typed text
			longerFactor.increment(0);
			params.intervalSeconds = interval.getValue();
			params.longerFactor = longerFactor.getValue();
		}
		catch (Exception e) {
			PamDialogFX.showWarning(null, getName(), "The measurement interval must be a whole number of seconds.");
			return null;
		}
		return params;
	}

	@Override
	public void setParams(LtsaParameters input) {
		sourcePane.setSourceList();
		sourcePane.setSource(input.dataSource);
		sourcePane.setChannelList(input.channelMap);
		interval.getValueFactory().setValue(Math.max(1, input.intervalSeconds));
		longerFactor.getValueFactory().setValue(Math.max(1, input.longerFactor));
	}

	@Override
	public String getName() {
		return "LTSA Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
