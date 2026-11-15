package GPS.layoutFX;

import GPS.GPSControl;
import GPS.GPSParameters;
import NMEA.NMEADataUnit;
import PamController.PamController;
import PamController.SettingsPane;
import PamguardMVC.PamDataBlock;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.SourcePaneFX;

/**
 * JavaFX settings for GPS processing. Holds the same options as the Swing GPS dialog: the NMEA 
 * source, which navigation string to read, read options, smoothing, heading data and the vessel 
 * dimensions. 
 */
public class GPSSettingsPaneFX extends SettingsPane<GPSParameters> {

	private static final String[] READ_TYPES = { "Read all GPS data", "Read at fixed time intervals", 
			"Read on timer or course or speed change" };

	private static final String[] READ_INFO = { "Read and display all available GPS data", 
			"Read GPS data at fixed time intervals", 
			"Read GPS data at fixed time intervals OR when the course or speed change" };

	private TabPane mainPane;

	private SourcePaneFX sourcePane;

	private CheckBox allowWildcard;
	private RadioButton rmcString, ggaString;
	private TextField rmcInitials, ggaInitials;

	private ComboBox<String> readType;
	private Label readInfo;
	private TextField timeInterval, speedInterval, courseInterval;

	private CheckBox smoothData;
	private TextField smoothingTime;

	private CheckBox readHeading;
	private SourcePaneFX headingSource;

	private TextField[] dims = new TextField[4];
	private CheckBox showArrow;
	private TextField arrowLength;

	private GPSControl gpsControl;

	public GPSSettingsPaneFX(GPSControl gpsControl) {
		super(null);
		this.gpsControl = gpsControl;
		mainPane = new TabPane();
		mainPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		mainPane.getTabs().add(new Tab("NMEA Source", createSourcePane()));
		mainPane.getTabs().add(new Tab("Heading", createHeadingPane()));
		mainPane.getTabs().add(new Tab("Vessel", createVesselPane()));
	}

	private Label title(String text) {
		Label label = new Label(text);
		PamGuiManagerFX.titleFont2style(label);
		return label;
	}

	private TextField numField(int cols) {
		TextField field = new TextField();
		field.setPrefColumnCount(cols);
		return field;
	}

	private Node createSourcePane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(5));

		sourcePane = new SourcePaneFX(NMEADataUnit.class, false, true);

		// main string
		allowWildcard = new CheckBox("Allow any string initials");
		allowWildcard.setTooltip(new Tooltip("Use RMC or GGA data from any source independent of the string initials (e.g. GP, GN, etc.)"));
		ToggleGroup group = new ToggleGroup();
		rmcString = new RadioButton("RMC String");
		ggaString = new RadioButton("GGA String");
		rmcString.setToggleGroup(group);
		ggaString.setToggleGroup(group);
		rmcInitials = numField(3);
		ggaInitials = numField(3);
		GridPane stringGrid = new GridPane();
		stringGrid.setHgap(5);
		stringGrid.setVgap(5);
		stringGrid.add(allowWildcard, 0, 0, 3, 1);
		stringGrid.add(rmcString, 0, 1);
		stringGrid.add(rmcInitials, 1, 1);
		stringGrid.add(new Label("RMC"), 2, 1);
		stringGrid.add(ggaString, 0, 2);
		stringGrid.add(ggaInitials, 1, 2);
		stringGrid.add(new Label("GGA"), 2, 2);
		allowWildcard.setOnAction(e -> enableControls());
		rmcString.setOnAction(e -> enableControls());
		ggaString.setOnAction(e -> enableControls());

		// read options
		readType = new ComboBox<String>();
		readType.getItems().addAll(READ_TYPES);
		readType.setOnAction(e -> enableControls());
		readInfo = new Label();
		readInfo.setWrapText(true);
		timeInterval = numField(5);
		speedInterval = numField(5);
		courseInterval = numField(5);
		GridPane readGrid = new GridPane();
		readGrid.setHgap(5);
		readGrid.setVgap(5);
		readGrid.add(readType, 0, 0, 3, 1);
		readGrid.add(readInfo, 0, 1, 3, 1);
		readGrid.add(new Label("Time interval"), 0, 2);
		readGrid.add(timeInterval, 1, 2);
		readGrid.add(new Label("s"), 2, 2);
		readGrid.add(new Label("Speed interval"), 0, 3);
		readGrid.add(speedInterval, 1, 3);
		readGrid.add(new Label("knots"), 2, 3);
		readGrid.add(new Label("Angle interval"), 0, 4);
		readGrid.add(courseInterval, 1, 4);
		readGrid.add(new Label("degrees"), 2, 4);

		// smoothing
		smoothData = new CheckBox("Smooth GPS data");
		smoothData.setOnAction(e -> enableControls());
		smoothingTime = numField(5);
		GridPane smoothGrid = new GridPane();
		smoothGrid.setHgap(5);
		smoothGrid.setVgap(5);
		smoothGrid.add(smoothData, 0, 0, 3, 1);
		smoothGrid.add(new Label("Smoothing time"), 0, 1);
		smoothGrid.add(smoothingTime, 1, 1);
		smoothGrid.add(new Label("s"), 2, 1);

		vBox.getChildren().addAll(title("NMEA Source"), sourcePane, title("Main navigation data string"), 
				stringGrid, title("Read options"), readGrid, title("Smoothing options"), smoothGrid);
		return vBox;
	}

	private Node createHeadingPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(5));
		readHeading = new CheckBox("Read heading data");
		readHeading.setOnAction(e -> enableControls());
		headingSource = new SourcePaneFX(NMEADataUnit.class, false, true);
		vBox.getChildren().addAll(title("Heading data"), readHeading, headingSource);
		return vBox;
	}

	private Node createVesselPane() {
		PamVBox vBox = new PamVBox();
		vBox.setSpacing(5);
		vBox.setPadding(new Insets(5));

		String[] names = { "A: GPS to bow", "B: GPS to stern", "C: GPS to port side", "D: GPS to starboard side" };
		GridPane dimGrid = new GridPane();
		dimGrid.setHgap(5);
		dimGrid.setVgap(5);
		for (int i = 0; i < 4; i++) {
			dims[i] = numField(5);
			dimGrid.add(new Label(names[i]), 0, i);
			dimGrid.add(dims[i], 1, i);
			dimGrid.add(new Label("m"), 2, i);
		}

		showArrow = new CheckBox("Show ship heading arrow");
		showArrow.setOnAction(e -> enableControls());
		arrowLength = numField(5);
		GridPane arrowGrid = new GridPane();
		arrowGrid.setHgap(5);
		arrowGrid.setVgap(5);
		arrowGrid.add(showArrow, 0, 0, 3, 1);
		arrowGrid.add(new Label("Arrow length"), 0, 1);
		arrowGrid.add(arrowLength, 1, 1);
		arrowGrid.add(new Label("s"), 2, 1);

		vBox.getChildren().addAll(title("Ship dimensions"), dimGrid, title("Predicted position"), arrowGrid);
		return vBox;
	}

	private void enableControls() {
		boolean wild = allowWildcard.isSelected();
		rmcInitials.setDisable(!rmcString.isSelected() || wild);
		ggaInitials.setDisable(!ggaString.isSelected() || wild);
		int t = Math.max(0, readType.getSelectionModel().getSelectedIndex());
		readInfo.setText(READ_INFO[t]);
		timeInterval.setDisable(t == 0);
		speedInterval.setDisable(t != 2);
		courseInterval.setDisable(t != 2);
		smoothingTime.setDisable(!smoothData.isSelected());
		headingSource.setDisable(!readHeading.isSelected());
		arrowLength.setDisable(!showArrow.isSelected());
	}

	@Override
	public void setParams(GPSParameters params) {
		sourcePane.setSourceList();
		sourcePane.setSource(params.getNmeaSource());
		allowWildcard.setSelected(params.allowWildcard);
		rmcString.setSelected(params.mainString == GPSParameters.READ_RMC);
		ggaString.setSelected(params.mainString == GPSParameters.READ_GGA);
		rmcInitials.setText(params.rmcInitials);
		ggaInitials.setText(params.ggaInitials);

		readType.getSelectionModel().select(Math.min(Math.max(params.readType, 0), READ_TYPES.length-1));
		timeInterval.setText(String.format("%d", params.readInterval));
		speedInterval.setText(String.format("%.1f", params.speedInterval));
		courseInterval.setText(String.format("%.1f", params.courseInterval));

		smoothData.setSelected(params.weShouldSmooth());
		smoothingTime.setText(String.format("%.1f", params.getSmoothingTime()));

		readHeading.setSelected(params.readHeading);
		headingSource.setSourceList();
		headingSource.setSource(params.headingNMEASource);

		double[] d = { params.dimA, params.dimB, params.dimC, params.dimD };
		for (int i = 0; i < 4; i++) {
			dims[i].setText(String.format("%.1f", d[i]));
		}
		showArrow.setSelected(params.plotPredictedPosition);
		arrowLength.setText(String.format("%d", params.predictionTime));

		enableControls();
	}

	private GPSParameters warn(String msg) {
		PamDialogFX.showWarning(null, "GPS settings", msg);
		return null;
	}

	@Override
	public GPSParameters getParams(GPSParameters currParams) {
		GPSParameters params = (currParams == null ? gpsControl.getGpsParameters() : currParams).clone();
		boolean isNormal = PamController.getInstance().getRunMode() == PamController.RUN_NORMAL;

		// main string
		params.allowWildcard = allowWildcard.isSelected();
		params.mainString = ggaString.isSelected() ? GPSParameters.READ_GGA : GPSParameters.READ_RMC;
		params.rmcInitials = rmcInitials.getText();
		params.ggaInitials = ggaInitials.getText();
		if (isNormal && !params.allowWildcard) {
			if (params.rmcInitials.length() != 2) {
				return warn("Expecting a two character identifier for RMC strings");
			}
			if (params.ggaInitials.length() != 2) {
				return warn("Expecting a two character identifier for GGA strings");
			}
		}

		PamDataBlock<?> source = sourcePane.getSource();
		if (source != null) {
			params.setNmeaSource(source.getLongDataName());
		}
		else if (!isNormal) {
			params.setNmeaSource(null);
		}

		double[] d = new double[4];
		try {
			for (int i = 0; i < 4; i++) {
				d[i] = Double.valueOf(dims[i].getText());
			}
		}
		catch (NumberFormatException e) {
			return warn("Invalid ship dimension");
		}
		params.dimA = d[0];
		params.dimB = d[1];
		params.dimC = d[2];
		params.dimD = d[3];

		params.plotPredictedPosition = showArrow.isSelected();
		try {
			params.predictionTime = Integer.valueOf(arrowLength.getText());
		}
		catch (NumberFormatException e) {
			return warn("Invalid heading arrow length. It must be a whole number of seconds");
		}

		params.readType = Math.max(0, readType.getSelectionModel().getSelectedIndex());
		try {
			params.readInterval = Integer.valueOf(timeInterval.getText());
			params.speedInterval = Double.valueOf(speedInterval.getText());
			params.courseInterval = Double.valueOf(courseInterval.getText());
		}
		catch (NumberFormatException e) {
			return warn("Invalid read interval. The time interval must be a whole number of seconds");
		}

		params.setSmoothingFlag(smoothData.isSelected());
		try {
			params.setSmoothingTime(Float.valueOf(smoothingTime.getText()));
		}
		catch (NumberFormatException e) {
			return warn("Invalid smoothing time");
		}

		params.readHeading = readHeading.isSelected();
		PamDataBlock<?> hdb = headingSource.getSource();
		if (hdb != null) {
			params.headingNMEASource = hdb.getDataName();
		}
		else if (isNormal) {
			params.headingNMEASource = null;
			if (params.readHeading) {
				return warn("Heading data NMEA source must be specified");
			}
		}
		return params;
	}

	@Override
	public String getName() {
		return "GPS Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
