package clickDetector.layoutFX;

import PamController.SettingsPane;
import clickDetector.ClickControl;
import clickDetector.ClickParameters;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.Pane;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;

/**
 * Pane containing controls to change echo detection parameters in the click detector. 
 * @author Jamie Macaulay 
 *
 */
public class ClickEchoPane extends SettingsPane<ClickParameters> {
	
	/**
	 * Check box on whether to run an echo detector. 
	 */
	private CheckBox runEchoCheckBox;

	/**
	 * Check box to set whether to discard echoes. 
	 */
	private CheckBox discardEchoCheckBox;

	/**
	 * The intervel in seconds for echo detector. 
	 */
	private PamSpinner<Double> echoIntervalSpinner;

	/**
	 * The main pane. 
	 */
	private PamBorderPane mainPane; 
	
	private ClickControl clickControl;

	public ClickEchoPane(ClickControl clickControl){
		super(null);
		this.clickControl = clickControl;
		mainPane = new PamBorderPane();
		mainPane.setCenter(createEchoPane());
	}
	/**
	 * Create pane for echo detector. 
	 * @return pane to change settings on echo detector. 
	 */
	private Pane createEchoPane(){
		
		PamVBox vBox=new PamVBox();
		vBox.setSpacing(5);
		
		Label echoLable1 = new Label("Echo Detection Policy");
		PamGuiManagerFX.titleFont2style(echoLable1);
		vBox.getChildren().add(echoLable1);
		
		vBox.getChildren().add(runEchoCheckBox=new CheckBox("Run Echo Detector online"));
		runEchoCheckBox.setOnAction((action)->{
			discardEchoCheckBox.setDisable(!runEchoCheckBox.isSelected());
			if (!runEchoCheckBox.isSelected()) discardEchoCheckBox.setSelected(false);
		});
		vBox.getChildren().add(discardEchoCheckBox=new CheckBox("Discard Echoes"));

		
		Label echoLabe2 = new Label("Echo Detection Settings");
		PamGuiManagerFX.titleFont2style(echoLabe2);
		vBox.getChildren().add(echoLabe2);
		
		
		PamHBox intervalBox=new PamHBox(); 
		intervalBox.setSpacing(5);
		
		echoIntervalSpinner=new PamSpinner<Double>(0.00000, 100, 0.1, 0.0001);
		echoIntervalSpinner.setEditable(true);
		echoIntervalSpinner.getValueFactory().setConverter(PamSpinner.createStringConverter(5));
		echoIntervalSpinner.setPrefWidth(130);
		echoIntervalSpinner.setTooltip(new javafx.scene.control.Tooltip("The maximum interval between a click and its echo"));
		echoIntervalSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		
		intervalBox.getChildren().addAll(new Label("Max Interval"),echoIntervalSpinner,new Label("seconds") ); 
		
		vBox.getChildren().add(intervalBox);
	
		return vBox; 
	} 
	
	/**
	 * Get the parameters of the simple echo detector, if it is being used. 
	 * @return the echo parameters or null. 
	 */
	private clickDetector.echoDetection.SimpleEchoParams getEchoParams() {
		if (clickControl == null || !(clickControl.getEchoDetectionSystem() instanceof PamController.PamSettings)) return null; 
		java.io.Serializable ref = ((PamController.PamSettings) clickControl.getEchoDetectionSystem()).getSettingsReference(); 
		if (ref instanceof clickDetector.echoDetection.SimpleEchoParams) {
			return (clickDetector.echoDetection.SimpleEchoParams) ref; 
		}
		return null; 
	}
	
	@Override
	public ClickParameters getParams(ClickParameters clickParameters) {
		clickParameters.runEchoOnline = runEchoCheckBox.isSelected(); 
		clickParameters.discardEchoes = discardEchoCheckBox.isSelected(); 
		clickDetector.echoDetection.SimpleEchoParams echoParams = getEchoParams(); 
		if (echoParams != null) {
			Double val = echoIntervalSpinner.getValue(); 
			if (val == null || val < 0) {
				pamViewFX.fxNodes.pamDialogFX.PamDialogFX.showWarning("Invalid echo delay value");
				return null; 
			}
			echoParams.maxIntervalSeconds = val; 
		}
		return clickParameters;
	}
	
	@Override
	public void setParams(ClickParameters clickParameters) {
		runEchoCheckBox.setSelected(clickParameters.runEchoOnline);
		discardEchoCheckBox.setSelected(clickParameters.discardEchoes);
		discardEchoCheckBox.setDisable(!runEchoCheckBox.isSelected());
		clickDetector.echoDetection.SimpleEchoParams echoParams = getEchoParams(); 
		echoIntervalSpinner.setDisable(echoParams == null);
		if (echoParams != null) {
			echoIntervalSpinner.getValueFactory().setValue(echoParams.maxIntervalSeconds);
		}
	}

	@Override
	public String getName() {
		return "Click Echo Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
		// TODO Auto-generated method stub
	}
	

}
