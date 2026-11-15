package NMEA.layoutFX;

import java.util.ArrayList;

import javax.swing.SwingUtilities;

import NMEA.NMEAControl;
import NMEA.NMEADialog2;
import NMEA.NMEAParameters;
import NMEA.NMEAParameters.NmeaSources;
import NMEA.NMEAProvider;
import NMEA.serial.SerialNMEAProvider;
import NMEA.simulated.SimulatedNMEAProvider;
import NMEA.udp.UdpNMEAProvider;
import PamController.PamController;
import PamController.SettingsPane;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.utilityPanes.LatLongStrip;
import serialComms.jserialcomm.PJSerialComm;

/**
 * JavaFX settings for NMEA data collection. Holds the same options as the Swing NMEA dialog: 
 * the source of the data (serial port, UDP or simulated) and the settings for that source. 
 * Sources added by plugins keep their own Swing settings, which can be opened from here. 
 */
public class NMEASettingsPaneFX extends SettingsPane<NMEAParameters> {

	private static final int[] BAUD_RATES = { 110, 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 
			230400, 460800, 921600 };

	private static final String[] HEADING_OPTIONS = { "No heading data", "Magnetic headings", 
			"True headings (e.g. from a gyro)" };

	private NMEAControl nmeaControl;

	private PamBorderPane mainPane;

	private ComboBox<String> sourceBox;

	private ArrayList<NMEAProvider> providers;

	private PamBorderPane sourceHolder;

	// serial
	private CheckBox autoPort;
	private ComboBox<String> serialPort;
	private ComboBox<Integer> baudRate;
	private Pane serialPane;

	// UDP
	private TextField udpPort;
	private CheckBox multicast;
	private TextField multicastGroup;
	private Pane udpPane;

	// simulated
	private LatLongStrip simLat, simLong;
	private TextField simSpeed, simCourse, simDrunkenness, simInterval, simDecimalPlaces;
	private CheckBox simContinuous, simAIS;
	private RadioButton[] simHeading;
	private Pane simPane;

	// plugin sources
	private Pane pluginPane;

	public NMEASettingsPaneFX(NMEAControl nmeaControl) {
		super(null);
		this.nmeaControl = nmeaControl;
		providers = nmeaControl.getNmeaProviders();

		Label sourceLabel = new Label("NMEA source");
		PamGuiManagerFX.titleFont2style(sourceLabel);
		sourceBox = new ComboBox<String>();
		for (NMEAProvider provider : providers) {
			sourceBox.getItems().add(provider.getName());
		}
		sourceBox.setMaxWidth(Double.MAX_VALUE);
		sourceBox.setOnAction(e -> showSourcePane());

		serialPane = createSerialPane();
		udpPane = createUdpPane();
		simPane = createSimPane();
		pluginPane = createPluginPane();

		sourceHolder = new PamBorderPane();
		sourceHolder.setPadding(new Insets(10, 0, 0, 0));

		PamVBox holder = new PamVBox(5, sourceLabel, sourceBox, sourceHolder);
		holder.setPrefWidth(420);
		mainPane = new PamBorderPane(holder);
		mainPane.setPadding(new Insets(5));
	}

	private static Label title(String text) {
		Label label = new Label(text);
		PamGuiManagerFX.titleFont2style(label);
		return label;
	}

	private static TextField field(int columns) {
		TextField field = new TextField();
		field.setPrefColumnCount(columns);
		return field;
	}

	private static GridPane grid() {
		GridPane grid = new GridPane();
		grid.setHgap(5);
		grid.setVgap(5);
		return grid;
	}

	private Pane createSerialPane() {
		autoPort = new CheckBox("Find the serial port automatically");
		autoPort.setTooltip(new Tooltip("Search the available serial ports for NMEA data"));
		serialPort = new ComboBox<String>();
		serialPort.disableProperty().bind(autoPort.selectedProperty());
		baudRate = new ComboBox<Integer>();
		for (int baud : BAUD_RATES) {
			baudRate.getItems().add(baud);
		}
		GridPane grid = grid();
		grid.add(new Label("Port"), 0, 0);
		grid.add(serialPort, 1, 0);
		grid.add(new Label("Baud rate"), 0, 1);
		grid.add(baudRate, 1, 1);
		return new VBox(5, title("Serial port"), autoPort, grid);
	}

	private Pane createUdpPane() {
		udpPort = field(6);
		multicast = new CheckBox("Multicast");
		multicastGroup = field(12);
		multicastGroup.disableProperty().bind(multicast.selectedProperty().not());
		GridPane grid = grid();
		grid.add(new Label("UDP port"), 0, 0);
		grid.add(udpPort, 1, 0);
		grid.add(multicast, 0, 1, 2, 1);
		grid.add(new Label("Group IP address"), 0, 2);
		grid.add(multicastGroup, 1, 2);
		return new VBox(5, title("External NMEA server (UDP)"), grid);
	}

	private Pane createSimPane() {
		simLat = new LatLongStrip(true);
		simLong = new LatLongStrip(false);
		simSpeed = field(6);
		simCourse = field(6);
		simDrunkenness = field(6);
		simDrunkenness.setTooltip(new Tooltip("Random changes to the course, in degrees"));
		simContinuous = new CheckBox("Change the course continuously");
		simInterval = field(6);
		simDecimalPlaces = field(6);
		simDecimalPlaces.setTooltip(new Tooltip("Decimal places for the minutes of latitude and longitude"));
		simAIS = new CheckBox("Generate simulated AIS data");

		GridPane grid = grid();
		int row = 0;
		grid.add(new Label("Speed"), 0, row);
		grid.add(simSpeed, 1, row);
		grid.add(new Label("knots"), 2, row++);
		grid.add(new Label("Course"), 0, row);
		grid.add(simCourse, 1, row);
		grid.add(new Label("degrees"), 2, row++);
		grid.add(new Label("Random course change"), 0, row);
		grid.add(simDrunkenness, 1, row);
		grid.add(new Label("degrees"), 2, row++);
		grid.add(new Label("Time between strings"), 0, row);
		grid.add(simInterval, 1, row);
		grid.add(new Label("s"), 2, row++);
		grid.add(new Label("Decimal places"), 0, row);
		grid.add(simDecimalPlaces, 1, row++);

		ToggleGroup headingGroup = new ToggleGroup();
		simHeading = new RadioButton[HEADING_OPTIONS.length];
		VBox headingBox = new VBox(5);
		for (int i = 0; i < HEADING_OPTIONS.length; i++) {
			simHeading[i] = new RadioButton(HEADING_OPTIONS[i]);
			simHeading[i].setToggleGroup(headingGroup);
			headingBox.getChildren().add(simHeading[i]);
		}

		return new VBox(5, title("Simulated start position"), simLat, simLong, title("Simulated movement"), grid, 
				simContinuous, title("Simulated heading"), headingBox, simAIS);
	}

	private Pane createPluginPane() {
		Label info = new Label("This NMEA source is added by a plugin and has its own settings dialog.");
		info.setWrapText(true);
		Button settings = new Button("Source settings...");
		settings.setOnAction(e -> SwingUtilities.invokeLater(() -> NMEADialog2.showDialog(PamController.getMainFrame(), nmeaControl)));
		return new VBox(5, info, settings);
	}

	private NMEAProvider getSelectedProvider() {
		int ind = sourceBox.getSelectionModel().getSelectedIndex();
		return (ind < 0 || ind >= providers.size()) ? null : providers.get(ind);
	}

	private void showSourcePane() {
		NMEAProvider provider = getSelectedProvider();
		Pane pane = null;
		if (provider instanceof SerialNMEAProvider) pane = serialPane;
		else if (provider instanceof UdpNMEAProvider) pane = udpPane;
		else if (provider instanceof SimulatedNMEAProvider) pane = simPane;
		else if (provider != null) pane = pluginPane;
		sourceHolder.setCenter(pane);
		if (mainPane.getScene() != null && mainPane.getScene().getWindow() != null) {
			mainPane.getScene().getWindow().sizeToScene();
		}
	}

	@Override
	public void setParams(NMEAParameters params) {
		String providerClass = params.getProviderClass();
		for (int i = 0; i < providers.size(); i++) {
			if (providers.get(i).getClass().getName().equals(providerClass)) {
				sourceBox.getSelectionModel().select(i);
			}
		}

		serialPort.getItems().clear();
		try {
			serialPort.getItems().addAll(PJSerialComm.getSerialPortNames());
		}
		catch (Throwable e) {
			// no serial port library on this computer
		}
		if (params.serialPortName != null && !serialPort.getItems().contains(params.serialPortName)) {
			serialPort.getItems().add(params.serialPortName);
		}
		serialPort.getSelectionModel().select(params.serialPortName);
		autoPort.setSelected(params.autoSerialPort);
		baudRate.getSelectionModel().select(Integer.valueOf(params.serialPortBitsPerSecond));

		udpPort.setText(String.format("%d", params.port));
		multicast.setSelected(params.multicast);
		multicastGroup.setText(params.multicastGroup);

		simLat.setValue(params.simStartLatitude);
		simLong.setValue(params.simStartLongitude);
		simSpeed.setText(String.format("%.1f", params.simStartSpeed));
		simCourse.setText(String.format("%.1f", params.simStartHeading));
		simDrunkenness.setText(String.format("%.1f", params.drunkenness));
		simInterval.setText(String.format("%.1f", params.simTimeInterval));
		simDecimalPlaces.setText(String.format("%d", params.getLatLongDecimalPlaces()));
		simContinuous.setSelected(params.continousChange);
		simAIS.setSelected(params.generateAIS);
		for (int i = 0; i < simHeading.length; i++) {
			simHeading[i].setSelected(params.simHeadingData == i);
		}
		showSourcePane();
	}

	private NMEAParameters warn(String message) {
		PamDialogFX.showWarning(null, getName(), message);
		return null;
	}

	@Override
	public NMEAParameters getParams(NMEAParameters currParams) {
		NMEAParameters params = (currParams == null ? nmeaControl.getNmeaParameters() : currParams).clone();
		NMEAProvider provider = getSelectedProvider();
		if (provider == null) {
			return warn("Select a source for the NMEA data.");
		}
		params.setProviderClass(provider.getClass());
		try {
			if (provider instanceof SerialNMEAProvider) {
				params.sourceType = NmeaSources.SERIAL;
				params.autoSerialPort = autoPort.isSelected();
				if (!params.autoSerialPort) {
					if (serialPort.getSelectionModel().getSelectedItem() == null) {
						return warn("Select a serial port, or tick the box to find the port automatically.");
					}
					params.serialPortName = serialPort.getSelectionModel().getSelectedItem();
				}
				Integer baud = baudRate.getSelectionModel().getSelectedItem();
				if (baud == null) {
					return warn("Select a baud rate for the serial port.");
				}
				params.serialPortBitsPerSecond = baud;
			}
			else if (provider instanceof UdpNMEAProvider) {
				params.sourceType = NmeaSources.UDP;
				params.port = Integer.valueOf(udpPort.getText().trim());
				params.multicast = multicast.isSelected();
				if (params.multicast) {
					params.multicastGroup = multicastGroup.getText().trim();
				}
			}
			else if (provider instanceof SimulatedNMEAProvider) {
				params.sourceType = NmeaSources.SIMULATED;
				params.simStartLatitude = simLat.getValue();
				params.simStartLongitude = simLong.getValue();
				params.simStartSpeed = Double.valueOf(simSpeed.getText().trim());
				params.simStartHeading = Double.valueOf(simCourse.getText().trim());
				params.drunkenness = Double.valueOf(simDrunkenness.getText().trim());
				params.simTimeInterval = Double.valueOf(simInterval.getText().trim());
				params.setLatLongDecimalPlaces(Integer.valueOf(simDecimalPlaces.getText().trim()));
				params.continousChange = simContinuous.isSelected();
				params.generateAIS = simAIS.isSelected();
				for (int i = 0; i < simHeading.length; i++) {
					if (simHeading[i].isSelected()) {
						params.simHeadingData = i;
					}
				}
				if (params.simTimeInterval <= 0) {
					return warn("The time between simulated strings must be greater than 0 s.");
				}
			}
		}
		catch (NumberFormatException e) {
			return warn("One of the numbers is not valid. Check the values and try again.");
		}
		params.simThread = params.sourceType == NmeaSources.SIMULATED;
		return params;
	}

	@Override
	public String getName() {
		return "NMEA Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
