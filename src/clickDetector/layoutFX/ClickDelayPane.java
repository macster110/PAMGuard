package clickDetector.layoutFX;

import org.controlsfx.control.PopOver;

import Localiser.DelayMeasurementParams;
import PamController.SettingsPane;
import PamView.PamSymbol;
import clickDetector.ClickControl;
import clickDetector.ClickParameters;
import clickDetector.ClickClassifiers.ClickIdentifier;
import fftFilter.FFTFilterParams;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamHBox;
import pamViewFX.fxNodes.PamSpinner;
import pamViewFX.fxNodes.PamSymbolFX;
import pamViewFX.fxNodes.utilityPanes.PamToggleSwitch;
import pamViewFX.fxNodes.utilityPanes.SimpleFilterPaneFX;


/**
 * Pane to change click delay settings. The default settings and each click type are rows of a
 * table, with a column for each delay measurement setting. The filter is set up in a pop over from
 * a settings button in its column. Click types can use the default settings or their own, as in the
 * Swing click delay panel.
 *
 * @author Jamie Macaulay
 *
 */
public class ClickDelayPane extends SettingsPane<ClickParameters> {

	/**
	 * Reference click control.
	 */
	private ClickControl clickControl;

	/**
	 * Delay measurement parameters for each row. Row 0 is the default. Click types which use the
	 * default keep their own settings here too, so they come back if Default is unticked.
	 */
	private DelayMeasurementParams[] delayParams;

	/**
	 * Click type codes. Row 0 is the default.
	 */
	private int[] typesList;

	/**
	 * Whether each click type uses the default settings.
	 */
	private boolean[] useDefault;

	/**
	 * Names of each row.
	 */
	private String[] typeNames;

	/**
	 * Symbols for each row (null for the default).
	 */
	private PamSymbol[] typeSymbols;

	/**
	 * Table of the default and click types.
	 */
	private TableView<Integer> typeTable;

	/**
	 * Incremented whenever settings change, so that every cell shows the current values. Rows
	 * which use the default follow changes to the default row.
	 */
	private SimpleIntegerProperty changeCount = new SimpleIntegerProperty(0);

	/**
	 * Filter settings shown in the pop over.
	 */
	private SimpleFilterPaneFX filterPane;

	private PamBorderPane mainPane;


	public ClickDelayPane(ClickControl clickControl){
		super(null);
		this.clickControl = clickControl;
		mainPane= new PamBorderPane();
		createPane();
	}

	/**
	 * Create the pane.
	 */
	private void createPane(){
		typeTable = new TableView<Integer>();
		typeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		typeTable.setPlaceholder(new Label("No click types"));
		typeTable.setFixedCellSize(34);

		TableColumn<Integer, Integer> symbolCol = column("", 30, null);
		symbolCol.setCellFactory(col -> new TableCell<Integer, Integer>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || typeSymbols == null || item >= typeSymbols.length || typeSymbols[item] == null) {
					setGraphic(null);
				}
				else {
					setGraphic(new PamSymbolFX(typeSymbols[item]).getNode(PamGuiManagerFX.iconSize));
				}
			}
		});

		TableColumn<Integer, Integer> nameCol = column("Click type", 180, null);
		nameCol.setCellFactory(col -> new TableCell<Integer, Integer>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || typeNames == null || item >= typeNames.length) {
					setText(null);
				}
				else {
					setText(typeNames[item]);
				}
			}
		});

		TableColumn<Integer, Integer> defaultCol = column("Default", 90,
				"Use the default (unclassified clicks) settings for this click type");
		defaultCol.setCellFactory(col -> new DefaultCell());

		TableColumn<Integer, Integer> filterCol = column("Filter", 80,
				"Filter data before measurement to improve accuracy. Press the settings button to set up the filter");
		filterCol.setCellFactory(col -> new FilterCell());

		TableColumn<Integer, Integer> upSampleCol = column("Up\nsample", 90,
				"Up sampling data to a higher frequency can improve timing accuracy for narrow band clicks (i.e. harbour porpoise)");
		upSampleCol.setCellFactory(col -> new UpSampleCell());

		TableColumn<Integer, Integer> envelopeCol = column("Envelope", 108,
				"Use the waveform envelope. This can provide more accurate bearings for some narrowband pulses");
		envelopeCol.setCellFactory(col -> new EnvelopeCell(false));

		TableColumn<Integer, Integer> leadingEdgeCol = column("Leading\nedge", 95,
				"Use the leading edge of the envelope only. For long pulses, or where there are echoes");
		leadingEdgeCol.setCellFactory(col -> new EnvelopeCell(true));

		TableColumn<Integer, Integer> restrictCol = column("Restrict length\n(samples)", 165,
				"Only use the first samples of each click. In environments where echoes are an issue this is a simple but effective "
				+ "way to increase the accuracy of time delay calculations. WARNING: Remember that this must cover the potential "
				+ "time delay in grouped detections");
		restrictCol.setCellFactory(col -> new RestrictCell());

		typeTable.getColumns().add(symbolCol);
		typeTable.getColumns().add(nameCol);
		typeTable.getColumns().add(defaultCol);
		typeTable.getColumns().add(upSampleCol);
		typeTable.getColumns().add(envelopeCol);
		typeTable.getColumns().add(leadingEdgeCol);
		typeTable.getColumns().add(restrictCol);
		typeTable.getColumns().add(filterCol);

		filterPane = new SimpleFilterPaneFX();

		mainPane.setCenter(typeTable);
	}

	/**
	 * Make a table column whose value is the row index.
	 * @param title - the column title.
	 * @param width - the column width.
	 * @param tip - tool tip for the column header. Can be null.
	 * @return the column.
	 */
	private TableColumn<Integer, Integer> column(String title, double width, String tip) {
		TableColumn<Integer, Integer> col = new TableColumn<Integer, Integer>();
		Label header = new Label(title);
		header.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
		if (tip != null) {
			header.setTooltip(new Tooltip(tip));
		}
		col.setGraphic(header);
		col.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<Integer>(cell.getValue()));
		col.setPrefWidth(width);
		col.setMinWidth(width);
		col.setSortable(false);
		col.setReorderable(false);
		return col;
	}

	/**
	 * Whether the settings of a row can be changed.
	 */
	private boolean isRowEditable(int row) {
		return row == 0 || !useDefault[row];
	}

	/**
	 * The settings used by a row: the default settings for click types which use the default.
	 */
	private DelayMeasurementParams getUsedParams(int row) {
		if (row > 0 && useDefault[row]) {
			return delayParams[0];
		}
		return delayParams[row];
	}

	/**
	 * Called after settings change so that all cells show the current values.
	 */
	private void settingsChanged() {
		changeCount.set(changeCount.get()+1);
	}

	/**
	 * Base class for cells which show and change a delay setting. Controls are made once and
	 * updated whenever the row or the settings change.
	 */
	private abstract class DelayCell extends TableCell<Integer, Integer> {

		/**
		 * True while the controls are being set from the settings.
		 */
		protected boolean syncing = false;

		DelayCell() {
			setAlignment(Pos.CENTER);
			changeCount.addListener((obs, oldV, newV) -> sync());
		}

		@Override
		protected void updateItem(Integer item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || item == null || delayParams == null || item >= delayParams.length) {
				setGraphic(null);
			}
			else {
				setGraphic(getControl());
				sync();
			}
		}

		/**
		 * The row this cell is on, or -1.
		 */
		protected int getRow() {
			Integer item = getItem();
			if (item == null || isEmpty() || delayParams == null || item >= delayParams.length) {
				return -1;
			}
			return item;
		}

		private void sync() {
			int row = getRow();
			if (row < 0) {
				return;
			}
			syncing = true;
			DelayMeasurementParams dmp = getUsedParams(row);
			if (dmp == null) {
				dmp = new DelayMeasurementParams();
			}
			setValues(row, dmp, isRowEditable(row));
			syncing = false;
		}

		/**
		 * Change the settings of this cell's row, if they can be changed.
		 * @return the settings to change, or null.
		 */
		protected DelayMeasurementParams getEditParams() {
			int row = getRow();
			if (syncing || row < 0 || !isRowEditable(row) || delayParams[row] == null) {
				return null;
			}
			return delayParams[row];
		}

		abstract Node getControl();

		abstract void setValues(int row, DelayMeasurementParams dmp, boolean editable);
	}

	/**
	 * Tick box to use the default settings.
	 */
	private class DefaultCell extends DelayCell {
		private PamToggleSwitch checkBox = new PamToggleSwitch("");

		DefaultCell() {
			checkBox.setAlignment(Pos.CENTER);
			checkBox.getLabel().setVisible(false);
			checkBox.getLabel().setManaged(false);
			checkBox.selectedProperty().addListener((obs, oldV, newV)->{
				int row = getRow();
				if (syncing || row <= 0) return;
				useDefault[row] = checkBox.isSelected();
				if (!useDefault[row] && delayParams[row] == null) {
					// start from the default settings.
					delayParams[row] = delayParams[0].clone();
				}
				settingsChanged();
			});
		}

		@Override
		Node getControl() {
			return checkBox;
		}

		@Override
		void setValues(int row, DelayMeasurementParams dmp, boolean editable) {
			checkBox.setVisible(row > 0);
			checkBox.setSelected(row > 0 && useDefault[row]);
		}
	}

	/**
	 * Tick box to filter the data, and a button which shows the filter settings in a pop over.
	 */
	private class FilterCell extends DelayCell {
		private CheckBox checkBox = new CheckBox();
		private Button settingsButton = new Button();
		private PamHBox holder = new PamHBox(checkBox, settingsButton);

		FilterCell() {
			holder.setSpacing(3);
			holder.setAlignment(Pos.CENTER);
			settingsButton.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog", PamGuiManagerFX.iconSize));
			checkBox.setOnAction((e)->{
				DelayMeasurementParams dmp = getEditParams();
				if (dmp == null) return;
				dmp.filterBearings = checkBox.isSelected();
				if (dmp.filterBearings && dmp.delayFilterParams == null) {
					dmp.delayFilterParams = new FFTFilterParams();
				}
				settingsChanged();
				if (dmp.filterBearings) {
					showFilter(settingsButton, getRow());
				}
			});
			settingsButton.setOnAction((e)->{
				showFilter(settingsButton, getRow());
			});
		}

		@Override
		Node getControl() {
			return holder;
		}

		@Override
		void setValues(int row, DelayMeasurementParams dmp, boolean editable) {
			checkBox.setSelected(dmp.filterBearings);
			checkBox.setDisable(!editable);
			settingsButton.setDisable(!editable || !dmp.filterBearings);
			String filter = "No filter";
			if (dmp.delayFilterParams != null) {
				filter = dmp.delayFilterParams.toString();
			}
			settingsButton.setTooltip(new Tooltip("Filter settings: " + filter));
		}
	}

	/**
	 * Up sampling factor.
	 */
	private class UpSampleCell extends DelayCell {
		private ComboBox<String> comboBox = new ComboBox<String>();

		UpSampleCell() {
			comboBox.getItems().addAll("None", "x2", "x3", "x4");
			comboBox.setOnAction((e)->{
				DelayMeasurementParams dmp = getEditParams();
				if (dmp == null) return;
				dmp.setUpSample(comboBox.getSelectionModel().getSelectedIndex()+1);
				settingsChanged();
			});
		}

		@Override
		Node getControl() {
			return comboBox;
		}

		@Override
		void setValues(int row, DelayMeasurementParams dmp, boolean editable) {
			int ind = Math.min(4, Math.max(1, dmp.getUpSample())) - 1;
			comboBox.getSelectionModel().select(ind);
			comboBox.setDisable(!editable);
		}
	}

	/**
	 * Tick box to use the envelope, or the envelope leading edge.
	 */
	private class EnvelopeCell extends DelayCell {
		private CheckBox checkBox = new CheckBox();
		private boolean leadingEdge;

		EnvelopeCell(boolean leadingEdge) {
			this.leadingEdge = leadingEdge;
			checkBox.setOnAction((e)->{
				DelayMeasurementParams dmp = getEditParams();
				if (dmp == null) return;
				if (leadingEdge) {
					dmp.useLeadingEdge = checkBox.isSelected();
				}
				else {
					dmp.envelopeBearings = checkBox.isSelected();
				}
				settingsChanged();
			});
		}

		@Override
		Node getControl() {
			return checkBox;
		}

		@Override
		void setValues(int row, DelayMeasurementParams dmp, boolean editable) {
			if (leadingEdge) {
				checkBox.setSelected(dmp.useLeadingEdge);
				// the leading edge can only be used with the envelope.
				checkBox.setDisable(!editable || !dmp.envelopeBearings);
			}
			else {
				checkBox.setSelected(dmp.envelopeBearings);
				checkBox.setDisable(!editable);
			}
		}
	}

	/**
	 * Tick box to restrict the number of samples used, and the number of samples.
	 */
	private class RestrictCell extends DelayCell {
		private CheckBox checkBox = new CheckBox();
		private PamSpinner<Integer> spinner = new PamSpinner<Integer>(10, 1000000, 80, 10);
		private PamHBox holder = new PamHBox(checkBox, spinner);

		RestrictCell() {
			holder.setSpacing(5);
			holder.setAlignment(Pos.CENTER);
			spinner.setEditable(true);
			spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
			spinner.setPrefWidth(110);
			checkBox.setOnAction((e)->{
				DelayMeasurementParams dmp = getEditParams();
				if (dmp == null) return;
				dmp.useRestrictedBins = checkBox.isSelected();
				settingsChanged();
			});
			spinner.valueProperty().addListener((obs, oldV, newV)->{
				DelayMeasurementParams dmp = getEditParams();
				if (dmp == null || newV == null) return;
				dmp.restrictedBins = newV;
				// only rows which follow the default need to change.
				if (getRow() == 0) {
					settingsChanged();
				}
			});
		}

		@Override
		Node getControl() {
			return holder;
		}

		@Override
		void setValues(int row, DelayMeasurementParams dmp, boolean editable) {
			checkBox.setSelected(dmp.useRestrictedBins);
			checkBox.setDisable(!editable);
			spinner.getValueFactory().setValue(Math.max(10, dmp.restrictedBins));
			spinner.setDisable(!editable || !dmp.useRestrictedBins);
		}
	}

	/**
	 * Show the filter settings of a row in a pop over.
	 * @param button - the button to show the pop over by.
	 * @param row - the row.
	 */
	private void showFilter(Button button, int row) {
		if (row < 0 || !isRowEditable(row) || delayParams[row] == null) {
			return;
		}
		DelayMeasurementParams dmp = delayParams[row];
		if (dmp.delayFilterParams == null) {
			dmp.delayFilterParams = new FFTFilterParams();
		}
		if (clickControl.getClickDetector() != null && clickControl.getClickDetector().getSampleRate() > 0) {
			filterPane.setSampleRate(clickControl.getClickDetector().getSampleRate());
		}
		filterPane.setParams(dmp.delayFilterParams);

		Label title = new Label("Filter: " + typeNames[row]);
		PamGuiManagerFX.titleFont2style(title);
		PamBorderPane content = new PamBorderPane();
		content.setTop(title);
		content.setCenter(filterPane.getContentNode());
		javafx.scene.layout.BorderPane.setMargin(title, new Insets(0,0,5,0));
		content.setPadding(new Insets(5,10,10,10));

		PopOver popOver = new PopOver();
		popOver.setContentNode(content);
		popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
		// a pop over is a window of its own, so give it the same style as the settings dialog. 
		popOver.getScene().getStylesheets().addAll(pamViewFX.fxStyles.PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		popOver.setOnHidden((e)->{
			FFTFilterParams newParams = filterPane.getParams(dmp.delayFilterParams.clone());
			if (newParams != null) {
				dmp.delayFilterParams = newParams;
			}
			// the filter pane can only be in one pop over at a time.
			content.setCenter(null);
			settingsChanged();
		});
		popOver.show(button);
		// colour the pop over like the window it comes from. The ControlsFX style draws it white and 
		// the Primer colours don't resolve in the pop over window. An inline style takes priority. 
		String fill = getWindowColour(button);
		if (fill != null && popOver.getSkin() != null) {
			Node border = popOver.getSkin().getNode().lookup(".border");
			if (border != null) {
				border.setStyle("-fx-fill: " + fill + ";");
			}
		}
	}

	/**
	 * Get the background colour of the window a node is in. 
	 * @param node - the node. 
	 * @return the colour as CSS, or null. 
	 */
	private static String getWindowColour(Node node) {
		try {
			javafx.scene.layout.Background bg = ((javafx.scene.layout.Region) node.getScene().getRoot()).getBackground();
			if (bg != null && !bg.getFills().isEmpty() && bg.getFills().get(0).getFill() instanceof javafx.scene.paint.Color) {
				javafx.scene.paint.Color c = (javafx.scene.paint.Color) bg.getFills().get(0).getFill();
				return String.format("rgb(%d,%d,%d)", (int) Math.round(c.getRed()*255), 
						(int) Math.round(c.getGreen()*255), (int) Math.round(c.getBlue()*255));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Set up the rows for the current click types.
	 * @param clickParameters - the click parameters
	 */
	private void createRows(ClickParameters clickParameters) {
		ClickIdentifier clickIdentifier = clickControl.getClickIdentifier();
		int nTypes = 1; // always a default.
		int[] codeList = null;
		if (clickIdentifier != null) {
			codeList = clickIdentifier.getCodeList();
			if (codeList != null) {
				nTypes += codeList.length;
			}
		}
		PamSymbol[] symbols = null;
		if (clickIdentifier != null) {
			symbols = clickIdentifier.getSymbols();
		}

		typesList = new int[nTypes];
		typeNames = new String[nTypes];
		typeSymbols = new PamSymbol[nTypes];
		useDefault = new boolean[nTypes];
		delayParams = new DelayMeasurementParams[nTypes];

		delayParams[0] = clickParameters.getDelayMeasurementParams(0, true).clone();
		typeNames[0] = "Unclassified / default";
		for (int i = 1; i < nTypes; i++) {
			typesList[i] = codeList[i-1];
			typeNames[i] = clickIdentifier.getSpeciesName(typesList[i]);
			if (symbols != null && i-1 < symbols.length) {
				typeSymbols[i] = symbols[i-1];
			}
			DelayMeasurementParams dmp = clickParameters.getDelayMeasurementParams(typesList[i], false);
			// types without their own settings are given the default object.
			useDefault[i] = dmp == null || dmp == clickParameters.getDelayMeasurementParams(0, true);
			if (useDefault[i]) {
				delayParams[i] = null;
			}
			else {
				delayParams[i] = dmp.clone();
			}
		}
	}

	public ClickParameters getParams() {
		return getParams(null);
	}

	/**
	 * Copies the settings back into the click settings, removing
	 * old entries as necessary.
	 * @param clickParameters
	 * @return the click parameters, or null if the settings are invalid.
	 */
	public ClickParameters getParams(ClickParameters clickParameters) {
		if (clickParameters==null) return null;
		if (delayParams==null || delayParams[0] == null) {
			return clickParameters;
		}
		for (int i = 0; i < typesList.length; i++) {
			DelayMeasurementParams dmp = getUsedParams(i);
			if (dmp != null && dmp.filterBearings && dmp.delayFilterParams == null) {
				pamViewFX.fxNodes.pamDialogFX.PamDialogFX.showWarning(null, "Delay measurement settings",
						"Filter parameters have not been set for " + typeNames[i]);
				return null;
			}
		}
		clickParameters.setDelayMeasurementParams(0, delayParams[0]);
		for (int i = 1; i < typesList.length; i++) {
			if (useDefault[i]) {
				clickParameters.setDelayMeasurementParams(typesList[i], null);
			}
			else {
				clickParameters.setDelayMeasurementParams(typesList[i], delayParams[i]);
			}
		}
		return clickParameters;
	}

	@Override
	public void setParams(ClickParameters clickParameters) {
		createRows(clickParameters);
		typeTable.getItems().clear();
		for (int i = 0; i < typesList.length; i++) {
			typeTable.getItems().add(i);
		}
		// show all the rows without a scroll bar where possible.
		typeTable.setPrefHeight(typeTable.getFixedCellSize() * (typesList.length + 2.2));
		settingsChanged();
	}

	@Override
	public String getName() {
		return "Click Delay Settings";
	}

	@Override
	public Node getContentNode() {
		return mainPane;
	}

	@Override
	public void paneInitialized() {
	}

}
