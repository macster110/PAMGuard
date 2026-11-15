package PamUtils.time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.TimeZone;

import javax.swing.SwingUtilities;

import PamController.PamController;
import PamUtils.PamCalendar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxStyles.PamStylesManagerFX;

/**
 * JavaFX versions of the time zone and global time correction dialogs. These hold the 
 * same options as {@link TimeZoneDisplayDialog} and {@link GlobalTimeDialog}. 
 * 
 * @author PAMGuard
 */
public class TimeDialogsFX {

	private static <T> Dialog<T> makeDialog(String title) {
		Dialog<T> dialog = new Dialog<T>();
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.getDialogPane().getStylesheets().addAll(
				PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		if (PamController.getMainStage() != null && PamController.getMainStage().isShowing()) {
			dialog.initOwner(PamController.getMainStage());
		}
		return dialog;
	}

	/**
	 * Show the display time zone dialog. 
	 * @param current - the current parameters. 
	 * @return new parameters, or null if the dialog was cancelled. 
	 */
	public static TimeDisplayParameters showTimeZoneDialog(TimeDisplayParameters current) {
		Dialog<TimeDisplayParameters> dialog = makeDialog("Time Zone");

		RadioButton useUTC = new RadioButton("Display in UTC");
		TimeZone pcZone = TimeZone.getDefault();
		RadioButton usePC = new RadioButton(String.format("Use PC local time (%s / %s)", pcZone.getID(), pcZone.getDisplayName()));
		RadioButton useOther = new RadioButton("Other");
		ToggleGroup group = new ToggleGroup();
		useUTC.setToggleGroup(group);
		usePC.setToggleGroup(group);
		useOther.setToggleGroup(group);

		ComboBox<String> zones = new ComboBox<String>();
		String[] ids = TimeZone.getAvailableIDs();
		Arrays.sort(ids);
		zones.getItems().addAll(ids);
		zones.disableProperty().bind(useOther.selectedProperty().not());

		useUTC.setSelected(current.zoneType == TimeDisplayParameters.TIME_ZONE_UTC);
		usePC.setSelected(current.zoneType == TimeDisplayParameters.TIME_ZONE_PC);
		useOther.setSelected(current.zoneType == TimeDisplayParameters.TIME_ZONE_OTHER);
		if (current.zoneType == TimeDisplayParameters.TIME_ZONE_OTHER && current.timeZone != null) {
			zones.getSelectionModel().select(current.timeZone.getID());
		}
		else if (current.zoneType == TimeDisplayParameters.TIME_ZONE_PC) {
			zones.getSelectionModel().select(pcZone.getID());
		}
		else {
			zones.getSelectionModel().select("UTC");
		}

		Label note = new Label("All processing and data storage will continue to use UTC. "
				+ "The selection you make here only affects what is displayed on the screen.");
		note.setWrapText(true);
		note.setMaxWidth(420);

		HBox otherRow = new HBox(8, useOther, zones);
		otherRow.setAlignment(Pos.CENTER_LEFT);
		VBox content = new VBox(10, useUTC, usePC, otherRow, note);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			if (useOther.isSelected() && zones.getSelectionModel().getSelectedItem() == null) {
				event.consume();
				PamDialogFX.showWarning(null, "Time Zone", "You must select a time zone from the drop down list.");
			}
		});

		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) {
				return null;
			}
			TimeDisplayParameters params = current.clone();
			if (useUTC.isSelected()) {
				params.zoneType = TimeDisplayParameters.TIME_ZONE_UTC;
				params.timeZone = PamCalendar.defaultTimeZone;
			}
			else if (usePC.isSelected()) {
				params.zoneType = TimeDisplayParameters.TIME_ZONE_PC;
				params.timeZone = TimeZone.getDefault();
			}
			else {
				params.zoneType = TimeDisplayParameters.TIME_ZONE_OTHER;
				params.timeZone = TimeZone.getTimeZone(zones.getSelectionModel().getSelectedItem());
			}
			return params;
		});

		Optional<TimeDisplayParameters> result = dialog.showAndWait();
		return result.orElse(null);
	}

	/**
	 * Show the global time corrections dialog. 
	 * @param globalTimeManager - the global time manager. 
	 * @return new parameters, or null if the dialog was cancelled. 
	 */
	public static GlobalTimeParameters showGlobalTimeDialog(GlobalTimeManager globalTimeManager) {
		Dialog<GlobalTimeParameters> dialog = makeDialog("Global time corrections");

		GlobalTimeParameters params = globalTimeManager.getGlobalTimeParameters().clone();
		ArrayList<PCTimeCorrector> correctors = globalTimeManager.getPcTimeCorrectors();

		ComboBox<String> correctorChoice = new ComboBox<String>();
		int selInd = 0;
		for (int i = 0; i < correctors.size(); i++) {
			correctorChoice.getItems().add(correctors.get(i).getName());
			if (correctors.get(i).getClass().getName().equals(params.getSelectedTimeSource())) {
				selInd = i;
			}
		}
		if (correctors.size() > 0) {
			correctorChoice.getSelectionModel().select(selInd);
		}
		Button correctorSettings = new Button("Settings...");
		correctorSettings.setOnAction(e -> {
			int ind = correctorChoice.getSelectionModel().getSelectedIndex();
			if (ind >= 0) {
				// the settings for each time source are still Swing dialogs. 
				PCTimeCorrector corrector = correctors.get(ind);
				SwingUtilities.invokeLater(() -> corrector.showDialog(PamController.getMainFrame()));
			}
		});

		RadioButton logOnly = new RadioButton("Log changes only");
		RadioButton useAndLog = new RadioButton("Log changes and correct UTC in all modules");
		ToggleGroup group = new ToggleGroup();
		logOnly.setToggleGroup(group);
		useAndLog.setToggleGroup(group);
		logOnly.setSelected(!params.isUpdateUTC());
		useAndLog.setSelected(params.isUpdateUTC());

		TextField smoothing = new TextField(String.format("%d", params.getSmoothingTimeSeconds()));
		smoothing.setPrefColumnCount(5);
		TextField startupDelay = new TextField(String.format("%d", params.getStartupDelay()));
		startupDelay.setPrefColumnCount(5);

		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(10);
		grid.setPadding(new Insets(10));
		grid.add(new Label("Time source"), 0, 0);
		grid.add(correctorChoice, 1, 0);
		grid.add(correctorSettings, 2, 0);
		grid.add(logOnly, 0, 1, 3, 1);
		grid.add(useAndLog, 0, 2, 3, 1);
		grid.add(new Label("Logging and smoothing interval"), 0, 3);
		grid.add(smoothing, 1, 3);
		grid.add(new Label("s"), 2, 3);
		grid.add(new Label("Startup delay"), 0, 4);
		grid.add(startupDelay, 1, 4);
		grid.add(new Label("ms"), 2, 4);
		dialog.getDialogPane().setContent(grid);

		dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			try {
				int smooth = Integer.valueOf(smoothing.getText().trim());
				int delay = Integer.valueOf(startupDelay.getText().trim());
				if (smooth <= 0 || delay < 0) {
					throw new NumberFormatException();
				}
			}
			catch (NumberFormatException ex) {
				event.consume();
				PamDialogFX.showWarning(null, "Global time corrections", 
						"The logging and smoothing interval must be a whole number of seconds greater than zero, "
						+ "and the startup delay a whole number of milliseconds.");
			}
		});

		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) {
				return null;
			}
			params.setUpdateUTC(useAndLog.isSelected());
			int ind = correctorChoice.getSelectionModel().getSelectedIndex();
			if (ind >= 0) {
				params.setSelectedTimeSource(correctors.get(ind).getClass().getName());
			}
			params.setSmoothingTimeSeconds(Integer.valueOf(smoothing.getText().trim()));
			params.setStartupDelay(Integer.valueOf(startupDelay.getText().trim()));
			return params;
		});

		return dialog.showAndWait().orElse(null);
	}

}
