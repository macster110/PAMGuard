package PamController.pamWizard;

import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * JavaFX version of {@link PamAutoConfigDialog}: lets the user pick an automatic
 * configuration from a list, showing each option's description. Used by the FX
 * GUI when sound files are dropped onto a blank configuration.
 *
 * @author Jamie Macaulay
 */
public class PamAutoConfigDialogFX {

	/**
	 * Show the modal options dialog and return the chosen configuration, or null if
	 * cancelled. Must be called on the JavaFX application thread.
	 *
	 * @param configs the configurations to offer.
	 * @return the selected configuration, or null.
	 */
	public static PamAutoConfig showDialog(List<PamAutoConfig> configs) {
		if (configs == null || configs.isEmpty()) {
			return null;
		}

		Dialog<PamAutoConfig> dialog = new Dialog<>();
		dialog.setTitle("Import sound files");
		dialog.setHeaderText("Choose how you would like to view the dropped files");

		ListView<PamAutoConfig> listView = new ListView<>(FXCollections.observableArrayList(configs));
		listView.setPrefWidth(240);
		listView.setCellFactory(lv -> new ListCell<PamAutoConfig>() {
			@Override
			protected void updateItem(PamAutoConfig item, boolean empty) {
				super.updateItem(item, empty);
				setText((empty || item == null) ? null : item.getConfigName());
			}
		});

		TextArea description = new TextArea();
		description.setEditable(false);
		description.setWrapText(true);
		description.setPrefColumnCount(34);
		description.setPrefRowCount(8);

		listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
			description.setText(newVal == null || newVal.getConfigDescription() == null ? "" : newVal.getConfigDescription());
		});

		VBox right = new VBox(5, new Label("Description"), description);
		right.setPadding(new Insets(0, 0, 0, 10));

		BorderPane content = new BorderPane();
		content.setPadding(new Insets(10));
		content.setLeft(listView);
		content.setCenter(right);
		dialog.getDialogPane().setContent(content);

		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK
				? listView.getSelectionModel().getSelectedItem() : null);

		listView.getSelectionModel().selectFirst();

		Optional<PamAutoConfig> result = dialog.showAndWait();
		return result.orElse(null);
	}
}
