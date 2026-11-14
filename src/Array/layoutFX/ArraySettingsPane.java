package Array.layoutFX;

import java.io.File;

import org.controlsfx.control.SegmentedButton;

import Array.ArrayManager;
import Array.Hydrophone;
import Array.PamArray;
import PamController.PamController;
import PamController.SettingsPane;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert.AlertType;
import pamViewFX.fxNodes.PamGridPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxGlyphs.PamGlyphDude;
import pamViewFX.fxNodes.PamVBox;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxNodes.PamBorderPane;
import pamViewFX.fxNodes.PamButton;
import pamViewFX.fxNodes.PamHBox;

/**
 * The main settings pane for settings up a hydrophone array. 
 * 
 * @author Jamie Macaulay
 * 
 * @param <FlipPane>
 *
 */
public class ArraySettingsPane extends SettingsPane<PamArray >{

	/**
	 * Minimum size of the 3D pane.
	 */
	private static final double MIN_3D_WIDTH = 450;

	/**
	 * Minimum height of the 3D pane.
	 */
	private static final double MIN_3D_HEIGHT = 600;

	/**
	 * Pane for adding or removing streamers. 
	 */
	private StreamersPane streamerPane; 

	private PamBorderPane mainPane;

	/**
	 * Pane for adding or removing hydrophones. 
	 */
	private HydrophonesPane hydrophonePane;

	//	private Pane holder;

	private Label hydrophoneLabel;

	/**
	 * Pane which shows a 3D representation of the hydrophone array. 
	 */
	private Array3DPane array3DPane;

	private PropogationPane propogationPane;

	private Label recivierDiagramLabel;

	/**
	 * List of arrays held by the array manager.
	 */
	private ComboBox<PamArray> arrayList;

	/**
	 * The array (from the array manager list) being edited.
	 */
	private PamArray selectedArray;

	/**
	 * Instrument information required by Tethys.
	 */
	private TextField instrumentType, instrumentId;

	private boolean settingArrayList;

	/**
	 * Pane with controls to change speed of sound. 
	 */
	private SettingsPane<PamArray> environmentalPane;

	private FileChooser fileChooser;

	//a copy of the current array
	private PamArray currentArray; 

	public ArraySettingsPane() {
		super(null);

		mainPane=new PamBorderPane(); 

		mainPane.setCenter(createArrayPane());
		//		mainPane.setStyle("-fx-background-color: red;");
		mainPane.setMaxWidth(Double.MAX_VALUE);
		mainPane.setMinWidth(1100);
		mainPane.setStyle("-fx-padding: 0,0,0,0");


		recivierDiagramLabel = new Label("Hydrophone Diagram"); 
		PamGuiManagerFX.titleFont1style(recivierDiagramLabel);
		recivierDiagramLabel.setPadding(new Insets(5,5,5,5));

		Label environmentLabel = new Label("Environment"); 
		PamGuiManagerFX.titleFont1style(environmentLabel);
		environmentLabel.setPadding(new Insets(0,0,5,0)); //little more space under this label

		environmentalPane = createEnvironmentPane();

		PamVBox rightPane = new PamVBox();
		rightPane.setSpacing(5);

		Pane hydrophone3DPane = create3DPane();
		// instrument information required by Tethys, as in the Swing dialog.
		PamGridPane instrumentGrid = new PamGridPane();
		instrumentGrid.setHgap(5);
		instrumentGrid.setVgap(5);
		instrumentGrid.add(new Label("Instrument Type"), 0, 0);
		instrumentGrid.add(instrumentType, 1, 0);
		instrumentGrid.add(new Label("Instrument Id"), 2, 0);
		instrumentGrid.add(instrumentId, 3, 0);
		instrumentType.setPrefColumnCount(10);
		instrumentId.setPrefColumnCount(8);

		rightPane.getChildren().addAll(recivierDiagramLabel, hydrophone3DPane, environmentLabel, new PamBorderPane(environmentalPane.getContentNode()), 
				instrumentGrid);
		VBox.setVgrow(hydrophone3DPane, Priority.ALWAYS);

		mainPane.setRight(rightPane);

		//		streamerPane.getStreamerTable().getItems().addListener((ListChangeListener<? super StreamerProperty>) c->{
		//			//the streamer table has changed and so the streamer needs changed
		//			System.out.println("Streamer Changed!!!");
		//		});

		streamerPane.addStreamerListener((x,y)->{
			PamArray  array = getParams(new PamArray("temp_array: ", null)) ;
			System.out.println("Streamer changed!"); 
			array3DPane.drawArray(array);
		});

		hydrophonePane.addStreamerListener((x,y)->{
			PamArray  array = getParams(new PamArray("temp_array: ", null)) ;
			System.out.println("Hydrophone changed!" + array.getHydrophoneCount()); 
			array3DPane.drawArray(array);
		});

		//		mainPane.setMinWidth(800);

		//		mainPane.setCenter(createArrayPane());
		//		
		//		mainPane.getAdvPane().setCenter(new Label("Advanced Settings"));


		//		//mainPane.getFront().setStyle("-fx-background-color: grey;");
		//		mainPane.setStyle("-fx-background-color: red;");
		//		
		//		FlipPane aflipPane = new FlipPane(); 
		//		aflipPane.setStyle("-fx-background-color: red;");
		//		
		//		PamHBox stackPane = new PamHBox(); 
		//		stackPane.setStyle("-fx-background-color: red;");
		//		
		//		Button button = new Button(); 
		//		button.setOnAction((action)->{
		//			System.out.println(" 1 " + stackPane.getPadding());
		//			System.out.println(" 2 " +PamBorderPane.getMargin(stackPane));
		//			System.out.println(" 3 " + holder.getPadding());
		//		});
		//		
		//		stackPane.getChildren().add(button);
		//
		//		
		//		mainPane.setPadding(new Insets(0,0,0,0));


		//		holder = new StackPane(); 
		//		holder.getChildren().add(mainPane);
		//		holder.setStyle("-fx-padding: 0,0,0,0");

	}

	private Pane create3DPane() {

		StackPane stackPane = new StackPane(); 
		this.array3DPane = new HydrophoneArray3DPane();

		//important because the 3D pane has not default size
		stackPane.setMinWidth(MIN_3D_WIDTH);
		stackPane.setMinHeight(MIN_3D_HEIGHT);

		//		stackPane.prefHeightProperty().bind(mainPane.heightProperty().subtract(100));

		stackPane.getChildren().add(array3DPane);


		//add buttons
		ToggleButton b1 = new ToggleButton("2D");
		ToggleButton b2 = new ToggleButton("3D");

		b1.setOnAction((action)->{
			array3DPane.set3D(false);
		});

		b2.setOnAction((action)->{
			array3DPane.set3D(true);
		});
		

		SegmentedButton segmentedButton = new SegmentedButton();    
		segmentedButton.getButtons().addAll(b1, b2);
		segmentedButton.setPadding(new Insets(5,5,5,5));
		segmentedButton.setMinWidth(100);

		StackPane.setAlignment(segmentedButton, Pos.TOP_RIGHT);
		stackPane.getChildren().add(segmentedButton);
		
		
		final ContextMenu contextMenu = new ContextMenu();
		final MenuItem item1 = new MenuItem("Reset");
		item1.setOnAction((action)->{
			array3DPane.resetView();
		});
		contextMenu.getItems().add(item1);
		segmentedButton.setContextMenu(contextMenu);

//		stackPane.setOnContextMenuRequested(e -> 
//		contextMenu.show(stackPane, e.getScreenX(), e.getScreenY()));


		b2.setSelected(true);

		return stackPane;
	}

	/**
	 * Create the environment pane. 
	 * @return the environment pane. 
	 */
	private SettingsPane<PamArray> createEnvironmentPane() {
		this.propogationPane = new PropogationPane();
		return propogationPane;
	}

	/**
	 * Create the main pane. 
	 * @return the main array pane. 
	 */
	private Pane createArrayPane() {

		Label arrayLabel = new Label("Array"); 
		arrayLabel.setPadding(new Insets(5,5,5,5));
		PamGuiManagerFX.titleFont1style(arrayLabel);

		//holds the array label and also some button for import and export. 
		PamHBox arrayImportExportBox = new PamHBox();
		arrayImportExportBox.setSpacing(5);
		arrayImportExportBox.setAlignment(Pos.CENTER_LEFT);
		arrayImportExportBox.setPadding(new Insets(5,0,0,0));

		fileChooser = new FileChooser();
		fileChooser.setTitle("Open Resource File");
		fileChooser.getExtensionFilters().addAll(
				new ExtensionFilter("PAMGuard Array Files", "*.paf"));

		PamButton importButton = new PamButton("Import..."); 
		importButton.setOnAction((action)->{
			importArray();
		});
		importButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-file-import", PamGuiManagerFX.iconSize)); 
		importButton.setTooltip(new Tooltip("Import array settings from a .pgaf file"));

		PamButton exportButton = new PamButton("Export..."); 
		exportButton.setOnAction((action)->{
			exportArray();
		});
		exportButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-file-export", PamGuiManagerFX.iconSize)); 
		exportButton.setTooltip(new Tooltip("Export array settings to a .pgaf file"));

		//balnk region to make it look nicer
		Region blank = new Region();
		blank.setPrefWidth(70);

		// array management, as in the Swing array dialog.
		MenuButton manageButton = new MenuButton("Manage");
		manageButton.setGraphic(PamGlyphDude.createPamIcon("mdi2f-format-list-bulleted", PamGuiManagerFX.iconSize));
		manageButton.setTooltip(new Tooltip("Create, copy, rename or delete hydrophone arrays"));
		MenuItem newItem = new MenuItem("New Array...");
		newItem.setOnAction(a -> newArray());
		MenuItem copyItem = new MenuItem("Copy...");
		copyItem.setOnAction(a -> copyArray());
		MenuItem renameItem = new MenuItem("Rename...");
		renameItem.setOnAction(a -> renameArray());
		MenuItem deleteItem = new MenuItem("Delete");
		deleteItem.setOnAction(a -> deleteArray());
		manageButton.getItems().addAll(newItem, copyItem, renameItem, deleteItem);

		arrayImportExportBox.getChildren().addAll(manageButton, importButton, exportButton); 

		PamBorderPane titleHolder = new PamBorderPane();
		titleHolder.setLeft(arrayLabel);
		titleHolder.setRight(arrayImportExportBox);

		// the list of arrays and instrument information.
		arrayList = new ComboBox<PamArray>();
		arrayList.setMaxWidth(Double.MAX_VALUE);
		arrayList.setMinWidth(150);
		arrayList.setTooltip(new Tooltip("Select which of the stored hydrophone arrays to use"));
		arrayList.valueProperty().addListener((obs, oldArray, newArray) -> {
			if (settingArrayList || newArray == null || newArray == oldArray) {
				return;
			}
			// keep any changes made to the previous array.
			if (oldArray != null) {
				PamArray updated = getParams(oldArray);
				if (updated == null) {
					return;
				}
			}
			selectArray(newArray);
		});
		instrumentType = new TextField();
		instrumentType.setTooltip(new Tooltip("Instrument type, e.g. Towed array, HARP, EAR, Popup, DMON, Rock Hopper, etc."));
		instrumentId = new TextField();
		instrumentId.setTooltip(new Tooltip("Instrument identifier, e.g. serial number"));
		titleHolder.setCenter(arrayList);
		PamBorderPane.setAlignment(arrayList, Pos.CENTER_LEFT);
		PamBorderPane.setMargin(arrayList, new Insets(5, 10, 0, 5));

		//the streamer pane for changing streamer settings. 
		streamerPane = new StreamersPane(); 
		streamerPane.setMaxWidth(Double.MAX_VALUE);

		hydrophoneLabel = new Label("Hydrophones"); 
		PamGuiManagerFX.titleFont1style(hydrophoneLabel);
		hydrophoneLabel.setPadding(new Insets(5,5,5,5));

		hydrophonePane = new HydrophonesPane(); 
		hydrophonePane.setMaxWidth(Double.MAX_VALUE);

		//		PamButton advancedButton = new PamButton(); 
		//		advancedButton.setOnAction((action)->{
		//			mainPane.flipToBack();
		//		});
		//		advancedButton.setGraphic(PamGlyphDude.createPamIcon("mdi2c-cog")); 

		//		PamHBox advancedPane = new PamHBox(); 
		//		advancedPane.setSpacing(5);
		//		advancedPane.setAlignment(Pos.CENTER_RIGHT);
		//		advancedPane.getChildren().addAll(new Label("Advanced"), advancedButton);

		PamVBox vBox = new PamVBox(); 
		vBox.setSpacing(5);
		vBox.getChildren().addAll(titleHolder, streamerPane, hydrophoneLabel,
				hydrophonePane); 

		return vBox; 
	}

	/**
	 * Select a file to export array settings to. 
	 */
	private void exportArray() {
		File file = fileChooser.showSaveDialog(getFXWindow());
		if (file==null) return;
		// export the array as currently shown, including any unsaved changes.
		PamArray base = selectedArray != null ? selectedArray.clone() : new PamArray("saved_array", null);
		PamArray pamArray = getParams(base); 
		if (pamArray == null) return;
		String path = file.getAbsolutePath();
		if (!path.toLowerCase().endsWith("." + ArrayManager.getArrayFileType())) {
			path += "." + ArrayManager.getArrayFileType();
		}
		pamArray.setArrayFile(path);
		boolean isSaved = ArrayManager.saveArrayToFile(pamArray); 
		if (isSaved==false) {
			PamDialogFX.showError("Unable to save the array file to \n" + file.toString());
		}
	}

	/**
	 * Select a file to import array settings
	 */
	private void importArray() {
		File file = fileChooser.showOpenDialog(getFXWindow());
		if (file==null) return;
		PamArray pamArray = ArrayManager.loadArrayFromFile(file.getPath()); 
		if (pamArray == null) {
			PamDialogFX.showError("Unable to load the array file \n" + file.toString());
			return;
		}
		// as in the Swing dialog, an imported array is added to the list of arrays.
		pamArray.setArrayFile(file.getAbsolutePath());
		pamArray.sortHydrophoneLocator();
		addArray(pamArray);
	}

	/**
	 * Add an array to the array manager and show it.
	 * @param newArray - the new array.
	 */
	private void addArray(PamArray newArray) {
		ArrayManager.getArrayManager().addArray(newArray);
		selectArray(newArray);
	}

	/**
	 * Show an array from the array manager list.
	 */
	private void selectArray(PamArray array) {
		selectedArray = array;
		fillArrayList();
		setParams(array);
	}

	private void fillArrayList() {
		settingArrayList = true;
		arrayList.getItems().setAll(ArrayManager.getArrayManager().getRecentArrays());
		if (selectedArray != null && !arrayList.getItems().contains(selectedArray)) {
			arrayList.getItems().add(selectedArray);
		}
		arrayList.setValue(selectedArray);
		settingArrayList = false;
	}

	private String askName(String title, String text, String defaultName) {
		TextInputDialog dialog = new TextInputDialog(defaultName == null ? "" : defaultName);
		dialog.initOwner(getFXWindow());
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(text);
		return dialog.showAndWait().map(String::trim).filter(n -> n.length() > 0).orElse(null);
	}

	private void newArray() {
		String name = askName("New Array", "Enter a name for the new hydrophone array", null);
		if (name != null) {
			addArray(new PamArray(name, null));
		}
	}

	private void copyArray() {
		if (selectedArray == null) return;
		String name = askName("Copy Array", "Enter a name for the array copy", null);
		if (name == null) return;
		PamArray copy = getParams(selectedArray.clone());
		if (copy == null) return;
		copy.setArrayName(name);
		copy.setArrayFile(null);
		addArray(copy);
	}

	private void renameArray() {
		if (selectedArray == null) return;
		String name = askName("Rename Array", "Enter a new name for the array", selectedArray.getArrayName());
		if (name == null) return;
		selectedArray.setArrayName(name);
		currentArray.setArrayName(name);
		fillArrayList();
	}

	private void deleteArray() {
		if (selectedArray == null) return;
		ArrayManager arrayManager = ArrayManager.getArrayManager();
		if (arrayManager.getRecentArrays().size() < 2) {
			PamDialogFX.showWarning("You cannot delete the last array in the list");
			return;
		}
		boolean ok = PamDialogFX.showMessageDialog(getFXWindow(), "Confirm array delete",
				"Are you sure you want to delete the array \"" + selectedArray.toString() + "\"", 
				ButtonType.YES, ButtonType.NO, AlertType.CONFIRMATION);
		if (!ok) return;
		arrayManager.removeArray(selectedArray);
		selectedArray = null;
		selectArray(arrayManager.getRecentArrays().get(0));
	}

	/**
	 * Get the array, from the array manager's list, which is being edited.
	 * @return the selected array.
	 */
	public PamArray getSelectedArray() {
		return selectedArray;
	}

	/**
	 * Set the array to edit. Use this rather than setParams when opening the pane
	 * so that the list of arrays is updated.
	 * @param array - an array from the array manager.
	 */
	public void setSelectedArray(PamArray array) {
		selectArray(array);
	}

	/**
	 * Set correct text for the receiver in the current medium (e.g. air or water); 
	 */
	private void setReceieverLabels() {
		hydrophonePane.setRecieverLabels();
		streamerPane.setRecieverLabels();

		hydrophoneLabel.setText(PamController.getInstance().getGlobalMediumManager().getRecieverString(true) + "s");
		recivierDiagramLabel.setText(PamController.getInstance().getGlobalMediumManager().getRecieverString(true) + " diagram"); 
		//		if (singleInstance!=null) {
		//			singleInstance.setTitle("Pamguard "+ PamController.getInstance().getGlobalMediumManager().getRecieverString(false) +" array");
		//		}
	}


	@Override
	public PamArray getParams(PamArray  currParams) {
		
		if (currParams==null) currParams = this.currentArray;
		
		currParams = streamerPane.getParams(currParams); 
		currParams = hydrophonePane.getParams(currParams); 
		currParams.setHydrophoneInterpolation(hydrophonePane.getHydrophoneInterp());
		currParams = environmentalPane.getParams(currParams);
		if (currParams != null && instrumentType != null) {
			currParams.setInstrumentType(PamUtils.PamUtils.trimString(instrumentType.getText()));
			currParams.setInstrumentId(PamUtils.PamUtils.trimString(instrumentId.getText()));
		}
		//		System.out.println("Array settings pane: No. streamers: " + currParams.getStreamerCount());
		return currParams;
	}

	@Override
	public void setParams(PamArray  input) {
		this.currentArray = input.clone();
		//		System.out.println("Hydrophone array is: "+ input); 
		setReceieverLabels();
		hydrophonePane.setParams(input); 
		streamerPane.setParams(input); 
		environmentalPane.setParams(input);
		if (instrumentType != null) {
			instrumentType.setText(input.getInstrumentType());
			instrumentId.setText(input.getInstrumentId());
		}

		//draw the array
		array3DPane.drawArray(input);
	}

	@Override
	public String getName() {
		return "Array Parameters";
	}

	@Override
	public Node getContentNode() {

		return mainPane;
	}

	@Override
	public void paneInitialized() {
		// TODO Auto-generated method stub

	}

	private class HydrophoneArray3DPane extends Array3DPane {

		@Override
		public void hydrophoneSelected(Hydrophone hydrophone) {
			hydrophonePane.selectHydrophone(hydrophone); 
		}

	}

}
