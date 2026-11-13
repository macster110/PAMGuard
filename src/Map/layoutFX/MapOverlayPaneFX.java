package Map.layoutFX;

import java.util.ArrayList;

import Map.MapDetectionData;
import Map.MapPanel;
import Map.SimpleMap;
import PamController.PamController;
import PamView.symbol.PamSymbolChooser;
import PamView.symbol.PamSymbolManager;
import PamguardMVC.PamDataBlock;
import PamguardMVC.dataSelector.DataSelector;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamGridPane;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxSettingsPanes.DynamicSettingsPane;
import pamViewFX.symbol.FXSymbolOptionsPane;

/**
 * Pane which shows the data overlays that can be drawn on the map and allows
 * users to select which are plotted, for how long, whether they fade and to
 * open data selection and symbol options. Same options as the Swing map's
 * "Plot overlay options" dialog. Changes are applied immediately.
 *
 * @author Jamie Macaulay
 *
 */
public class MapOverlayPaneFX extends PamVBox {

	private SimpleMap simpleMap;

	private MapCanvasFX mapCanvas;

	private PamGridPane gridPane;

	public MapOverlayPaneFX(SimpleMap simpleMap, MapCanvasFX mapCanvas) {
		this.simpleMap = simpleMap;
		this.mapCanvas = mapCanvas;
		this.setSpacing(5);

		Label title = new Label("Data Overlays");
		PamGuiManagerFX.titleFont2style(title);

		gridPane = new PamGridPane();
		gridPane.setHgap(5);
		gridPane.setVgap(4);
		ColumnConstraints nameCol = new ColumnConstraints();
		nameCol.setHgrow(Priority.ALWAYS);
		nameCol.setMinWidth(100);
		gridPane.getColumnConstraints().add(nameCol);
		ColumnConstraints timeCol = new ColumnConstraints(105);
		gridPane.getColumnConstraints().add(timeCol);
		for (int i = 0; i < (isViewer() ? 3 : 2); i++) {
			ColumnConstraints checkCol = new ColumnConstraints(38);
			checkCol.setHalignment(HPos.CENTER);
			gridPane.getColumnConstraints().add(checkCol);
		}
		gridPane.getColumnConstraints().add(new ColumnConstraints(36));

		ScrollPane scrollPane = new ScrollPane(gridPane);
		scrollPane.setFitToWidth(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.getStyleClass().add("edge-to-edge");
		PamVBox.setVgrow(scrollPane, Priority.ALWAYS);

		this.getChildren().addAll(title, scrollPane);
		refresh();
	}

	private boolean isViewer() {
		return PamController.getInstance().getRunMode() == PamController.RUN_PAMVIEW;
	}

	/**
	 * Recreate the list of overlays.
	 */
	public void refresh() {
		gridPane.getChildren().clear();
		simpleMap.getMapDetectionsManager().createBlockList();
		ArrayList<MapDetectionData> mddList = simpleMap.getMapDetectionsManager().getMapDetectionDatas();

		int row = 0;
		int col = 1;
		gridPane.add(headerLabel("Time (s)", "The maximum time these data are shown for"), col++, row);
		gridPane.add(headerLabel("Fade", "Fade the data over time"), col++, row);
		gridPane.add(headerLabel("All", "Show all available data"), col++, row);
		if (isViewer()) {
			gridPane.add(headerLabel("Ahead", "Show data ahead of scroll bar (future data) instead of behind"), col++, row);
		}
		row++;

		int nShown = 0;
		synchronized (mddList) {
			for (MapDetectionData md : new ArrayList<MapDetectionData>(mddList)) {
				if (md.dataBlock == null) {
					continue;
				}
				addOverlayRow(md, row++);
				nShown++;
			}
		}
		if (nShown == 0) {
			Label noData = new Label("There are no data which can be shown on the map");
			noData.setWrapText(true);
			gridPane.add(noData, 0, row, 5, 1);
		}
	}

	private Label headerLabel(String text, String tip) {
		Label label = new Label(text);
		label.setTooltip(new Tooltip(tip));
		PamGridPane.setHalignment(label, HPos.CENTER);
		return label;
	}

	private void addOverlayRow(MapDetectionData md, int row) {
		int col = 0;
		CheckBox plot = new CheckBox(md.dataName);
		plot.setSelected(md.select);
		plot.setTooltip(new Tooltip("Plot " + md.dataName));
		plot.setMaxWidth(Double.MAX_VALUE);
		plot.setMinWidth(50);
		gridPane.add(plot, col++, row);

		PamSpinner<Double> time = new PamSpinner<Double>(0., 1e9, md.getDisplayMilliseconds() / 1000., 10.);
		time.setEditable(true);
		time.setPrefWidth(105);
		time.setMinWidth(105);
		time.getStyleClass().add(javafx.scene.control.Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
		time.setTooltip(new Tooltip("The maximum time these data should display for"));
		gridPane.add(time, col++, row);

		CheckBox fade = new CheckBox();
		fade.setSelected(md.fade);
		fade.setTooltip(new Tooltip("Fade lines over time"));
		PamGridPane.setHalignment(fade, HPos.CENTER);
		gridPane.add(fade, col++, row);

		CheckBox all = new CheckBox();
		all.setSelected(md.allAvailable);
		all.setTooltip(new Tooltip("Show all available data"));
		PamGridPane.setHalignment(all, HPos.CENTER);
		gridPane.add(all, col++, row);

		CheckBox lookAhead = new CheckBox();
		lookAhead.setSelected(md.lookAhead);
		lookAhead.setTooltip(new Tooltip("Show data ahead of scrollbar (future data) instead of behind"));
		PamGridPane.setHalignment(lookAhead, HPos.CENTER);
		if (isViewer()) {
			gridPane.add(lookAhead, col++, row);
		}

		PamButton options = new PamButton();
		options.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog", PamGuiManagerFX.iconSize));
		options.setTooltip(new Tooltip("Data selection and symbol options"));
		boolean hasOptions = md.dataBlock.getDataSelector(simpleMap.getSelectorName(), false, MapPanel.DATASELECTNAME) != null
				|| md.dataBlock.getPamSymbolManager() != null;
		options.setDisable(!hasOptions);
		options.setOnAction(e -> showOptions(md));
		gridPane.add(options, col++, row);

		Runnable enable = () -> {
			time.setDisable(!plot.isSelected() || all.isSelected());
			fade.setDisable(!plot.isSelected());
			all.setDisable(!plot.isSelected());
			lookAhead.setDisable(!plot.isSelected() || all.isSelected());
		};
		enable.run();

		plot.selectedProperty().addListener((obs, o, n) -> {
			md.select = n;
			enable.run();
			dataChanged();
		});
		time.valueProperty().addListener((obs, o, n) -> {
			if (n != null) {
				md.setDisplayMilliseconds((long) (n * 1000));
				mapCanvas.repaint(false);
			}
		});
		fade.selectedProperty().addListener((obs, o, n) -> {
			md.fade = n;
			mapCanvas.repaint(false);
		});
		all.selectedProperty().addListener((obs, o, n) -> {
			md.allAvailable = n;
			enable.run();
			mapCanvas.repaint(false);
		});
		lookAhead.selectedProperty().addListener((obs, o, n) -> {
			md.lookAhead = n;
			mapCanvas.repaint(false);
		});
	}

	/**
	 * Called when the data which are plotted have changed.
	 */
	private void dataChanged() {
		simpleMap.getMapPanel().updateObservers();
		for (MapOverlayListener l : listeners) {
			l.overlaysChanged();
		}
		mapCanvas.repaint(false);
	}

	/**
	 * Show the data selector and symbol options for a data block.
	 * @param md - the map detection data.
	 */
	@SuppressWarnings("rawtypes")
	private void showOptions(MapDetectionData md) {
		PamDataBlock dataBlock = md.dataBlock;
		DataSelector ds = dataBlock.getDataSelector(simpleMap.getSelectorName(), false, MapPanel.DATASELECTNAME);
		PamSymbolManager symbolManager = dataBlock.getPamSymbolManager();

		DynamicSettingsPane<Boolean> dataSelPane = null;
		FXSymbolOptionsPane<?> symbolPane = null;

		PamVBox content = new PamVBox();
		content.setSpacing(10);
		content.setPadding(new Insets(10));
		if (ds != null) {
			try {
				dataSelPane = ds.getDialogPaneFX();
			}
			catch (Exception e) {
				dataSelPane = null;
			}
			if (dataSelPane != null) {
				Label l = new Label("Data Selection");
				PamGuiManagerFX.titleFont2style(l);
				content.getChildren().addAll(l, dataSelPane.getContentNode());
				dataSelPane.setParams(true);
			}
		}
		if (symbolManager != null) {
			try {
				symbolPane = symbolManager.getFXOptionsPane(simpleMap.getMapDetectionsManager().getUnitName(),
						mapCanvas.getProjector());
			}
			catch (Exception e) {
				symbolPane = null;
			}
			if (symbolPane != null) {
				Label l = new Label("Symbol Options");
				PamGuiManagerFX.titleFont2style(l);
				content.getChildren().addAll(l, symbolPane.getContentNode());
			}
		}
		if (content.getChildren().isEmpty()) {
			content.getChildren().add(new Label("There are no options for " + md.dataName));
		}

		Dialog<ButtonType> dialog = new Dialog<ButtonType>();
		dialog.setTitle(md.dataName + " map options");
		dialog.setResizable(true);
		if (getScene() != null) {
			dialog.initOwner(getScene().getWindow());
		}
		ScrollPane scrollPane = new ScrollPane(content);
		scrollPane.setFitToWidth(true);
		scrollPane.setPrefViewportHeight(Math.min(600, content.prefHeight(-1) + 20));
		dialog.getDialogPane().setContent(scrollPane);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		if (getScene() != null) {
			dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		}
		ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
		if (result == ButtonType.OK) {
			if (dataSelPane != null) {
				dataSelPane.getParams(true);
			}
			if (symbolPane != null) {
				symbolPane.getParams(null);
			}
			mapCanvas.repaint(true);
		}
	}

	private ArrayList<MapOverlayListener> listeners = new ArrayList<MapOverlayListener>();

	/**
	 * Listener for changes to which overlays are plotted.
	 */
	public interface MapOverlayListener {
		public void overlaysChanged();
	}

	public void addOverlayListener(MapOverlayListener listener) {
		listeners.add(listener);
	}

	/**
	 * Align the content to the top.
	 */
	{
		this.setAlignment(Pos.TOP_LEFT);
	}

}
