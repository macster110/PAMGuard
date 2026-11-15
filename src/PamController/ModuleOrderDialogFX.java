package PamController;

import java.util.ArrayList;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import pamViewFX.fxStyles.PamStylesManagerFX;

/**
 * JavaFX dialog to change the order of the modules in a configuration. The 
 * same job as {@link ModuleOrderDialog}. 
 * 
 * @author PAMGuard
 */
public class ModuleOrderDialogFX {

	/**
	 * Show the dialog. 
	 * @param pamController - the PAMGuard controller. 
	 * @return the new order, as indexes into the current list of modules, or null if cancelled or unchanged. 
	 */
	public static int[] showDialog(PamController pamController) {
		Dialog<int[]> dialog = new Dialog<int[]>();
		dialog.setTitle("Module Ordering");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.getDialogPane().getStylesheets().addAll(
				PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		if (PamController.getMainStage() != null && PamController.getMainStage().isShowing()) {
			dialog.initOwner(PamController.getMainStage());
		}
		dialog.setResizable(true);

		int n = pamController.getNumControlledUnits();
		ArrayList<Integer> order = new ArrayList<Integer>();
		for (int i = 0; i < n; i++) {
			order.add(i);
		}
		ListView<Integer> list = new ListView<Integer>();
		list.getItems().addAll(order);
		list.setCellFactory(view -> new ListCell<Integer>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				}
				else {
					PamControlledUnit unit = pamController.getControlledUnit(item);
					setText(unit == null ? "" : unit.getUnitName() + "  (" + unit.getUnitType() + ")");
				}
			}
		});
		list.setPrefSize(420, Math.min(500, 60 + n * 30));

		Button up = new Button("Move up");
		Button down = new Button("Move down");
		up.setMaxWidth(Double.MAX_VALUE);
		down.setMaxWidth(Double.MAX_VALUE);
		up.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(1));
		down.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(0)
				.or(list.getSelectionModel().selectedIndexProperty().greaterThanOrEqualTo(n - 1)));
		up.setOnAction(e -> move(list, -1));
		down.setOnAction(e -> move(list, 1));

		VBox buttons = new VBox(8, up, down);
		buttons.setAlignment(Pos.TOP_CENTER);
		buttons.setPadding(new Insets(0, 0, 0, 10));

		Label info = new Label("Modules are set up and started in this order. Select a module and move it up or down.");
		info.setWrapText(true);
		info.setMaxWidth(520);
		info.setPadding(new Insets(0, 0, 10, 0));

		BorderPane content = new BorderPane();
		content.setTop(info);
		content.setCenter(list);
		content.setRight(buttons);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) {
				return null;
			}
			int[] newOrder = new int[list.getItems().size()];
			boolean changed = false;
			for (int i = 0; i < newOrder.length; i++) {
				newOrder[i] = list.getItems().get(i);
				changed |= newOrder[i] != i;
			}
			return changed ? newOrder : null;
		});

		return dialog.showAndWait().orElse(null);
	}

	private static void move(ListView<Integer> list, int step) {
		int ind = list.getSelectionModel().getSelectedIndex();
		int newInd = ind + step;
		if (ind < 0 || newInd < 0 || newInd >= list.getItems().size()) {
			return;
		}
		Integer item = list.getItems().remove(ind);
		list.getItems().add(newInd, item);
		list.getSelectionModel().select(newInd);
		list.scrollTo(newInd);
	}

}
