package NMEA.layoutFX;

import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import NMEA.NMEAControl;
import NMEA.NMEADataUnit;
import PamUtils.PamCalendar;
import PamguardMVC.PamDataUnit;
import PamguardMVC.PamObservable;
import PamguardMVC.PamObserverAdapter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.internalNode.PamInternalPane;
import userDisplayFX.UserDisplayControlFX;
import userDisplayFX.UserDisplayNodeFX;
import userDisplayFX.UserDisplayNodeParams;

/**
 * A JavaFX display of the latest NMEA string of each type, e.g. $GPRMC and $GPGGA, with the 
 * number of each received. The same information as the NMEA Strings window in the Swing GUI. 
 */
public class NMEAStringsDisplayFX implements UserDisplayNodeFX {

	/**
	 * One row of the table. 
	 */
	public static class StringRow {
		final SimpleStringProperty id = new SimpleStringProperty();
		final SimpleStringProperty time = new SimpleStringProperty();
		final SimpleStringProperty count = new SimpleStringProperty();
		final SimpleStringProperty string = new SimpleStringProperty();
		int n;
	}

	private NMEAControl nmeaControl;

	private PamBorderPane mainPane;

	private TableView<StringRow> table;

	private Label status;

	private final LinkedHashMap<String, StringRow> rows = new LinkedHashMap<String, StringRow>();

	/**
	 * Strings waiting to be shown, by string id. Only the latest of each type matters. 
	 */
	private final ConcurrentHashMap<String, NMEADataUnit> waiting = new ConcurrentHashMap<String, NMEADataUnit>();

	private final ConcurrentHashMap<String, Integer> waitingCounts = new ConcurrentHashMap<String, Integer>();

	private final AtomicBoolean updatePending = new AtomicBoolean();

	private NMEAObserver observer;

	private UserDisplayNodeParams displayParams = new UserDisplayNodeParams();

	public NMEAStringsDisplayFX(NMEAControl nmeaControl) {
		this.nmeaControl = nmeaControl;

		table = new TableView<StringRow>();
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.setPlaceholder(new Label("No NMEA data received yet"));
		table.getColumns().add(column("String", 90, r -> r.id));
		table.getColumns().add(column("Time", 110, r -> r.time));
		table.getColumns().add(column("Count", 70, r -> r.count));
		TableColumn<StringRow, String> data = column("Data", 500, r -> r.string);
		table.getColumns().add(data);

		status = new Label();
		// the status goes on the right since the GUI's process controls float over the top left of a tab. 
		status.setPadding(new Insets(8, 60, 8, 5));
		status.setMaxWidth(Double.MAX_VALUE);
		status.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

		mainPane = new PamBorderPane(table);
		mainPane.setTop(status);
		mainPane.setPrefSize(700, 300);
		sayStatus();
	}

	private static TableColumn<StringRow, String> column(String name, double width, 
			java.util.function.Function<StringRow, SimpleStringProperty> prop) {
		TableColumn<StringRow, String> col = new TableColumn<StringRow, String>(name);
		col.setPrefWidth(width);
		col.setCellValueFactory(cell -> prop.apply(cell.getValue()));
		return col;
	}

	private void sayStatus() {
		status.setText(nmeaControl.getUnitName() + ": " + rows.size() + " string types");
	}

	private class NMEAObserver extends PamObserverAdapter {

		@Override
		public String getObserverName() {
			return nmeaControl.getUnitName() + " strings display";
		}

		@Override
		public void addData(PamObservable o, PamDataUnit dataUnit) {
			NMEADataUnit nmeaUnit = (NMEADataUnit) dataUnit;
			String id = nmeaUnit.getStringId();
			if (id == null) {
				return;
			}
			waiting.put(id, nmeaUnit);
			waitingCounts.merge(id, 1, Integer::sum);
			// update the table at most once per FX pulse, however fast the strings arrive. 
			if (updatePending.compareAndSet(false, true)) {
				Platform.runLater(() -> showWaiting());
			}
		}
	}

	private void showWaiting() {
		updatePending.set(false);
		for (String id : waiting.keySet()) {
			NMEADataUnit unit = waiting.remove(id);
			Integer n = waitingCounts.remove(id);
			if (unit == null) continue;
			StringRow row = rows.get(id);
			if (row == null) {
				row = new StringRow();
				row.id.set(id);
				rows.put(id, row);
				table.getItems().add(row);
			}
			row.n += n == null ? 1 : n;
			row.time.set(PamCalendar.formatTime(unit.getTimeMilliseconds()));
			row.count.set(String.format("%d", row.n));
			row.string.set(unit.getCharData() == null ? "" : unit.getCharData().toString().trim());
		}
		sayStatus();
	}

	@Override
	public UserDisplayControlFX getUserDisplayControl() {
		return null;
	}

	@Override
	public String getName() {
		return nmeaControl.getUnitName() + " strings";
	}

	@Override
	public Region getNode() {
		return mainPane;
	}

	@Override
	public void openNode() {
		if (observer == null && nmeaControl.getNMEADataBLock() != null) {
			observer = new NMEAObserver();
			nmeaControl.getNMEADataBLock().addObserver(observer);
		}
	}

	@Override
	public boolean isStaticDisplay() {
		// the strings table gets a tab of its own. 
		return true;
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
		return false;
	}

	@Override
	public void closeNode() {
		if (observer != null && nmeaControl.getNMEADataBLock() != null) {
			nmeaControl.getNMEADataBLock().deleteObserver(observer);
		}
		observer = null;
	}

	@Override
	public void notifyModelChanged(int changeType) {
	}

	@Override
	public UserDisplayNodeParams getDisplayParams() {
		return displayParams;
	}

	@Override
	public void setFrameHolder(PamInternalPane internalFrame) {
	}

}
