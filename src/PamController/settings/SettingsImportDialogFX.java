package PamController.settings;

import java.io.File;
import java.util.ArrayList;

import PamController.PamController;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.fxStyles.PamStylesManagerFX;

/**
 * JavaFX dialog to import modules from another configuration file. The same job as 
 * {@link SettingsImportDialog}: pick a psfx file, then choose for each module whether 
 * to skip it, add it as a new module or use it to replace an existing module. 
 */
public class SettingsImportDialogFX {

	/**
	 * Ask for a psfx file, show the import choices and import the chosen modules. 
	 * @param pamController the PAMGuard controller. 
	 * @return true if modules were imported. 
	 */
	public static boolean showDialog(PamController pamController) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Import modules from a configuration");
		fileChooser.getExtensionFilters().add(new ExtensionFilter("PAMGuard Settings Files", "*.psfx"));
		File file = fileChooser.showOpenDialog(PamController.getMainStage());
		if (file == null) {
			return false;
		}
		return showDialog(pamController, file);
	}

	/**
	 * Show the import choices for a psfx file and import the chosen modules. 
	 * @param pamController the PAMGuard controller. 
	 * @param file the psfx file to import from. 
	 * @return true if modules were imported. 
	 */
	public static boolean showDialog(PamController pamController, File file) {
		SettingsImport settingsImport = new SettingsImport(pamController);
		ArrayList<SettingsImportGroup> groups = settingsImport.readSettingsGroups(file.getAbsolutePath());
		if (groups == null) {
			return false;
		}

		Dialog<Boolean> dialog = new Dialog<Boolean>();
		dialog.setTitle("Import from " + file.getName());
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.getDialogPane().getStylesheets().addAll(
				PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		if (PamController.getMainStage() != null && PamController.getMainStage().isShowing()) {
			dialog.initOwner(PamController.getMainStage());
		}
		dialog.setResizable(true);

		TableView<SettingsImportGroup> table = new TableView<SettingsImportGroup>();
		table.setEditable(true);
		table.setItems(FXCollections.observableArrayList(groups));

		TableColumn<SettingsImportGroup, String> typeCol = new TableColumn<>("Module Type");
		typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsedModuleInfo().getUnitType()));
		typeCol.setPrefWidth(170);

		TableColumn<SettingsImportGroup, String> nameCol = new TableColumn<>("Module Name");
		nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsedModuleInfo().unitName));
		nameCol.setPrefWidth(170);

		TableColumn<SettingsImportGroup, ImportChoice> choiceCol = new TableColumn<>("Import Options");
		choiceCol.setCellValueFactory(c -> new SimpleObjectProperty<ImportChoice>(c.getValue().getImportChoice()));
		choiceCol.setPrefWidth(250);
		choiceCol.setCellFactory(col -> new TableCell<SettingsImportGroup, ImportChoice>() {
			private ComboBox<ImportChoice> box = new ComboBox<ImportChoice>();
			{
				box.setMaxWidth(Double.MAX_VALUE);
				box.setOnAction(e -> {
					SettingsImportGroup group = getTableRow() == null ? null : getTableRow().getItem();
					if (group != null && box.getValue() != null) {
						group.setImportChoice(box.getValue());
					}
				});
			}
			@Override
			protected void updateItem(ImportChoice item, boolean empty) {
				super.updateItem(item, empty);
				SettingsImportGroup group = getTableRow() == null ? null : getTableRow().getItem();
				if (empty || group == null) {
					setGraphic(null);
					return;
				}
				box.getItems().setAll(group.getImportChoices());
				box.setValue(group.getImportChoice());
				setGraphic(box);
			}
		});

		table.getColumns().add(typeCol);
		table.getColumns().add(nameCol);
		table.getColumns().add(choiceCol);
		table.setPrefSize(620, 300);

		Label info = new Label();
		info.setWrapText(true);
		info.setMaxWidth(600);
		info.setPadding(new Insets(0, 0, 10, 0));
		if (groups.size() == 0) {
			info.setText("The format of older psf files is not supported by the import functions. If no modules are listed, "
					+ "open and then close the configuration you want to import from with the latest version of PAMGuard and try again.");
			info.setStyle("-fx-text-fill: red;");
			dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
		}
		else {
			info.setText("Select the modules you want to import and whether to add them as new modules "
					+ "or replace existing modules of the same type.");
		}

		BorderPane content = new BorderPane();
		content.setTop(info);
		content.setCenter(table);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		dialog.setResultConverter(button -> button == ButtonType.OK);
		Boolean ok = dialog.showAndWait().orElse(false);
		if (ok == null || !ok) {
			return false;
		}
		settingsImport.importGroups(groups);
		return true;
	}

}
