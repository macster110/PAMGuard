package PamController.settings.output.xml;

import java.io.File;
import java.io.IOException;

import org.w3c.dom.Document;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamUtils.PamCalendar;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import pamViewFX.PamGuiManagerFX;
import pamViewFX.fxNodes.pamDialogFX.PamDialogFX;
import pamViewFX.fxStyles.PamStylesManagerFX;

/**
 * JavaFX dialog to export the configuration as XML. The same options as 
 * {@link XMLWriterDialog}: which modules to write, what to include, a preview and the 
 * output file. 
 */
public class XMLWriterDialogFX {

	private PamguardXMLWriter xmlWriter;

	private long currentTime;

	private Dialog<ButtonType> dialog;

	private RadioButton allModules, oneModule, oneModulePlusChain;
	private ComboBox<String> modules;
	private CheckBox nonModuleData, includeFinal, writeNames, writeTips, writeOnStart;
	private TextField fileName;

	private static final ButtonType WRITE = new ButtonType("Write", ButtonData.OK_DONE);

	/**
	 * Show the dialog. 
	 */
	public static void showDialog() {
		new XMLWriterDialogFX().show();
	}

	private XMLWriterDialogFX() {
		xmlWriter = PamguardXMLWriter.getXMLWriter();
		currentTime = PamCalendar.getTimeInMillis();

		dialog = new Dialog<ButtonType>();
		dialog.setTitle("XML Configuration Output");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getButtonTypes().addAll(WRITE, ButtonType.CLOSE);
		dialog.getDialogPane().getStylesheets().addAll(
				PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		if (PamController.getMainStage() != null && PamController.getMainStage().isShowing()) {
			dialog.initOwner(PamController.getMainStage());
		}
		dialog.setResizable(true);

		ToggleGroup group = new ToggleGroup();
		allModules = new RadioButton("All modules");
		oneModule = new RadioButton("One module");
		oneModulePlusChain = new RadioButton("Up to selected module");
		allModules.setToggleGroup(group);
		oneModule.setToggleGroup(group);
		oneModulePlusChain.setToggleGroup(group);
		allModules.setOnAction(e -> enableControls());
		oneModule.setOnAction(e -> enableControls());
		oneModulePlusChain.setOnAction(e -> enableControls());
		modules = new ComboBox<String>();
		modules.setMaxWidth(Double.MAX_VALUE);
		nonModuleData = new CheckBox("Output non module data");
		includeFinal = new CheckBox("Include class constants");
		writeNames = new CheckBox("Write parameter names");
		writeTips = new CheckBox("Write parameter tips");

		oneModule.setTooltip(new Tooltip("Write settings for a single module"));
		allModules.setTooltip(new Tooltip("Write settings for all modules"));
		oneModulePlusChain.setTooltip(new Tooltip("Write settings for a module and all modules providing input to that module"));
		modules.setTooltip(new Tooltip("Select PAMGuard module"));
		nonModuleData.setTooltip(new Tooltip("Write data not associated with PAMGuard modules (mostly display information)"));
		includeFinal.setTooltip(new Tooltip("Write class constants"));
		writeNames.setTooltip(new Tooltip("Write user friendly names (parameter values are primarily labelled with their Java variable names)"));
		writeTips.setTooltip(new Tooltip("Write longer descriptions of parameter values"));

		GridPane moduleGrid = new GridPane();
		moduleGrid.setHgap(15);
		moduleGrid.setVgap(5);
		moduleGrid.add(allModules, 0, 0);
		moduleGrid.add(oneModule, 0, 1);
		moduleGrid.add(oneModulePlusChain, 0, 2);
		moduleGrid.add(modules, 0, 3);
		moduleGrid.add(nonModuleData, 1, 0);
		moduleGrid.add(includeFinal, 1, 1);
		moduleGrid.add(writeNames, 1, 2);
		moduleGrid.add(writeTips, 1, 3);

		fileName = new TextField();
		fileName.setPrefColumnCount(40);
		HBox.setHgrow(fileName, Priority.ALWAYS);
		Button defaultFile = new Button("Default");
		defaultFile.setOnAction(e -> makeDefaultFileName());
		Button browse = new Button("Browse...");
		browse.setOnAction(e -> browseFileName());
		Button preview = new Button("Preview");
		preview.setOnAction(e -> showPreview());
		browse.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		defaultFile.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		HBox fileBox = new HBox(5, fileName, browse, defaultFile);
		writeOnStart = new CheckBox("Write settings file whenever PAMGuard starts");
		writeOnStart.setTooltip(new Tooltip("Write an XML configuration file every time PAMGuard starts"));

		Label moduleTitle = new Label("Module selection");
		PamGuiManagerFX.titleFont2style(moduleTitle);
		Label outputTitle = new Label("Output options");
		PamGuiManagerFX.titleFont2style(outputTitle);

		VBox content = new VBox(8, moduleTitle, moduleGrid, outputTitle, new Label("File name"), fileBox, 
				writeOnStart, preview);
		content.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(content);

		// keep the dialog open if writing fails. 
		dialog.getDialogPane().lookupButton(WRITE).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
			if (!writeFile(new File(fileName.getText()))) {
				e.consume();
			}
		});
		dialog.setOnHidden(e -> getParams());

		setParams();
	}

	private void show() {
		dialog.showAndWait();
	}

	private void setParams() {
		PamController pamController = PamController.getInstance();
		XMLWriterSettings params = xmlWriter.getWriterSettings();
		int nCU = pamController.getNumControlledUnits();
		modules.getItems().clear();
		int iSel = -1;
		for (int i = 0; i < nCU; i++) {
			PamControlledUnit pcu = pamController.getControlledUnit(i);
			modules.getItems().add(pcu.getUnitName());
			if (pcu.getUnitType().equals(params.selectedModuleType) && pcu.getUnitName().equals(params.selectedModuleName)) {
				iSel = i;
			}
		}
		allModules.setSelected(params.moduleSelection == XMLWriterSettings.MODULES_ALL);
		oneModule.setSelected(params.moduleSelection == XMLWriterSettings.MODULES_ONE);
		oneModulePlusChain.setSelected(params.moduleSelection == XMLWriterSettings.MODULES_CHAIN);
		if (iSel >= 0) {
			modules.getSelectionModel().select(iSel);
		}
		else if (nCU > 0) {
			modules.getSelectionModel().select(0);
		}
		nonModuleData.setSelected(params.nonModuleData);
		includeFinal.setSelected(params.includeConstants);
		writeNames.setSelected(params.writeShortNames);
		writeTips.setSelected(params.writeToolTips);
		writeOnStart.setSelected(params.writeAtStart);
		makeDefaultFileName();
		enableControls();
	}

	private void getParams() {
		XMLWriterSettings params = xmlWriter.getWriterSettings().clone();
		if (allModules.isSelected()) {
			params.moduleSelection = XMLWriterSettings.MODULES_ALL;
		}
		else if (oneModule.isSelected()) {
			params.moduleSelection = XMLWriterSettings.MODULES_ONE;
		}
		else {
			params.moduleSelection = XMLWriterSettings.MODULES_CHAIN;
		}
		int iSel = modules.getSelectionModel().getSelectedIndex();
		PamController pamController = PamController.getInstance();
		if (iSel >= 0 && iSel < pamController.getNumControlledUnits()) {
			PamControlledUnit pcu = pamController.getControlledUnit(iSel);
			params.selectedModuleType = pcu.getUnitType();
			params.selectedModuleName = pcu.getUnitName();
		}
		params.nonModuleData = nonModuleData.isSelected();
		params.includeConstants = includeFinal.isSelected();
		params.writeShortNames = writeNames.isSelected();
		params.writeToolTips = writeTips.isSelected();
		params.writeAtStart = writeOnStart.isSelected();
		xmlWriter.setWriterSettings(params);
	}

	private void enableControls() {
		modules.setDisable(allModules.isSelected());
		nonModuleData.setDisable(!allModules.isSelected());
	}

	private void makeDefaultFileName() {
		fileName.setText(xmlWriter.makeFileName(currentTime));
	}

	private void browseFileName() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("XML output file");
		fileChooser.getExtensionFilters().add(new ExtensionFilter("XML file", "*.xml"));
		File currFile = new File(fileName.getText());
		if (currFile.getParentFile() != null && currFile.getParentFile().isDirectory()) {
			fileChooser.setInitialDirectory(currFile.getParentFile());
		}
		fileChooser.setInitialFileName(currFile.getName());
		File f = fileChooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
		if (f != null) {
			fileName.setText(f.getAbsolutePath());
		}
	}

	private boolean writeFile(File file) {
		getParams();
		Document doc = xmlWriter.writeSelection(currentTime);
		if (doc == null) {
			PamDialogFX.showWarning(null, "XML output", "Nothing to write for the current selection");
			return false;
		}
		try {
			xmlWriter.writeToFile(doc, file);
		} catch (IOException e) {
			PamDialogFX.showWarning(null, "Unable to write XML document", e.getMessage());
			return false;
		}
		return true;
	}

	private void showPreview() {
		getParams();
		Document doc = xmlWriter.writeSelection(currentTime);
		if (doc == null) {
			return;
		}
		Dialog<ButtonType> previewDialog = new Dialog<ButtonType>();
		previewDialog.setTitle("XML Preview");
		previewDialog.setHeaderText(null);
		previewDialog.initOwner(dialog.getDialogPane().getScene().getWindow());
		previewDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		previewDialog.getDialogPane().getStylesheets().addAll(
				PamStylesManagerFX.getPamStylesManagerFX().getCurStyle().getDialogCSS());
		previewDialog.setResizable(true);
		TextArea text = new TextArea(xmlWriter.getAsString(doc));
		text.setEditable(false);
		text.setStyle("-fx-font-family: monospace;");
		text.setPrefSize(700, 500);
		previewDialog.getDialogPane().setContent(text);
		previewDialog.show();
	}

}
