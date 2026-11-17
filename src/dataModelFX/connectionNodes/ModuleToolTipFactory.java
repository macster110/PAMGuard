package dataModelFX.connectionNodes;

import java.util.ArrayList;

import PamController.PamControlledUnit;
import PamController.PamGUIManager;
import PamController.status.ModuleStatus;
import PamModel.ModulesMenuGroup;
import PamModel.PamDependency;
import PamModel.PamModuleInfo;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamProcess;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Makes the tool tips shown on module icons in the JavaFX data model, both on the
 * modules already in the data model and on the icons in the list of modules which
 * can be added.
 * <p>
 * The tool tip shows the module's name and type, the menu group it belongs to, its
 * description, what data it needs and makes, and whether it has JavaFX settings. The
 * tool tips have the style class "module-tooltip", which primer-pamguard.css makes
 * opaque (the Primer theme draws tool tips partly transparent, which made them hard
 * to read over the module icons).
 *
 * @author Jamie Macaulay
 */
public class ModuleToolTipFactory {

	/**
	 * Style class of the module tool tips.
	 */
	public static final String MODULE_TOOLTIP_STYLE = "module-tooltip";

	/**
	 * Number of characters at which the text wraps. The text is wrapped here rather
	 * than with Label.setWrapText(), which made the tool tip window far too tall.
	 */
	private static final int WRAP_CHARS = 52;

	/**
	 * Get a tool tip for a module which is in the data model. The content is
	 * rebuilt each time the tool tip shows, so that the inputs, outputs and status
	 * are up to date.
	 * @param pamControlledUnit - the module.
	 * @return the tool tip, or null if the module has no module info.
	 */
	public static Tooltip getToolTip(PamControlledUnit pamControlledUnit) {
		if (pamControlledUnit.getPamModuleInfo()==null) return null;

		Tooltip tooltip = createTooltip();
		tooltip.setGraphic(createUnitContent(pamControlledUnit));
		tooltip.setOnShowing((e)->{
			tooltip.setGraphic(createUnitContent(pamControlledUnit));
		});
		return tooltip;
	}

	/**
	 * Get a tool tip for a type of module, e.g. in the list of modules which can be
	 * added to the data model. The content is rebuilt each time the tool tip shows so
	 * that the number of modules in use is up to date.
	 * @param pamModuleInfo - the module type.
	 * @return the tool tip.
	 */
	public static Tooltip getToolTip(PamModuleInfo pamModuleInfo) {
		Tooltip tooltip = createTooltip();
		tooltip.setGraphic(createModuleInfoContent(pamModuleInfo));
		tooltip.setOnShowing((e)->{
			tooltip.setGraphic(createModuleInfoContent(pamModuleInfo));
		});
		return tooltip;
	}

	private static Tooltip createTooltip() {
		Tooltip tooltip = new Tooltip();
		tooltip.getStyleClass().add(MODULE_TOOLTIP_STYLE);
		tooltip.setShowDelay(Duration.millis(500));
		tooltip.setShowDuration(Duration.seconds(30));
		tooltip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		return tooltip;
	}

	/**
	 * Content for a module which is in the data model.
	 */
	private static VBox createUnitContent(PamControlledUnit pcu) {
		PamModuleInfo info = pcu.getPamModuleInfo();

		//the module type, if the user has given the module a different name, and its group.
		String subtitle = null;
		if (!info.getDescription().equals(pcu.getUnitName())) {
			subtitle = info.getDescription();
		}
		String group = getGroupName(info);
		if (group != null && subtitle != null) {
			subtitle = subtitle + "  \u00B7  " + group;
		}
		else if (group != null) {
			subtitle = group;
		}

		VBox vBox = createHeader(pcu.getUnitName(), subtitle, info.getToolTipText());

		GridPane grid = createGrid();
		int row = 0;

		//inputs
		ArrayList<String> inputs = getInputs(pcu);
		if (inputs.size() > 0) {
			row = addRow(grid, row, "Input", String.join("\n", inputs));
		}
		else if (info.getDependency() != null) {
			row = addRow(grid, row, "Input", "Not connected - needs " + getDependencyText(info.getDependency()));
		}

		//outputs
		ArrayList<String> outputs = getOutputs(pcu);
		if (outputs.size() > 0) {
			row = addRow(grid, row, "Output", String.join("\n", outputs));
		}

		row = addRow(grid, row, "Settings", getSettingsText(info));

		String status = getStatusText(pcu);
		if (status != null) {
			row = addRow(grid, row, "Status", status);
		}

		vBox.getChildren().addAll(new Separator(), grid);
		return vBox;
	}

	/**
	 * Content for a type of module which can be added to the data model.
	 */
	private static VBox createModuleInfoContent(PamModuleInfo info) {

		VBox vBox = createHeader(info.getDescription(), getGroupName(info), info.getToolTipText());

		GridPane grid = createGrid();
		int row = 0;

		if (info.getDependency() != null) {
			row = addRow(grid, row, "Input", "Needs " + getDependencyText(info.getDependency()));
		}
		else {
			row = addRow(grid, row, "Input", "None needed");
		}

		row = addRow(grid, row, "Settings", getSettingsText(info));

		String inUse = info.getNInstances() + " in this configuration";
		if (info.getMaxNumber() > 0) {
			inUse = inUse + " (maximum " + info.getMaxNumber() + ")";
		}
		row = addRow(grid, row, "In use", inUse);

		vBox.getChildren().addAll(new Separator(), grid);

		Label hint = new Label(wrap(getAddHint(info), WRAP_CHARS));
		hint.getStyleClass().add("module-tooltip-hint");
		vBox.getChildren().add(hint);

		return vBox;
	}

	private static String getAddHint(PamModuleInfo info) {
		if (!info.canCreate()) {
			return "The maximum number of these modules are already in use.";
		}
		return "Drag onto the data model to add.";
	}

	/**
	 * Title, subtitle and description at the top of the tool tip.
	 */
	private static VBox createHeader(String title, String subtitle, String description) {
		VBox vBox = new VBox(4);

		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("module-tooltip-title");
		vBox.getChildren().add(titleLabel);

		if (subtitle != null && subtitle.length() > 0) {
			Label subLabel = new Label(subtitle);
			subLabel.getStyleClass().add("module-tooltip-subtitle");
			vBox.getChildren().add(subLabel);
		}

		if (description != null && description.trim().length() > 0) {
			Label descLabel = new Label(wrap(description.trim(), WRAP_CHARS));
			descLabel.getStyleClass().add("module-tooltip-text");
			VBox.setMargin(descLabel, new Insets(4, 0, 0, 0));
			vBox.getChildren().add(descLabel);
		}
		return vBox;
	}

	/**
	 * Break text into lines of at most about nChars characters, at spaces. Existing
	 * line breaks are kept.
	 */
	static String wrap(String text, int nChars) {
		StringBuilder sb = new StringBuilder();
		String[] lines = text.split("\n");
		for (int l = 0; l < lines.length; l++) {
			if (l > 0) sb.append('\n');
			int lineLen = 0;
			String[] words = lines[l].trim().split("\\s+");
			for (int i = 0; i < words.length; i++) {
				if (lineLen > 0 && lineLen + 1 + words[i].length() > nChars) {
					sb.append('\n');
					lineLen = 0;
				}
				else if (lineLen > 0) {
					sb.append(' ');
					lineLen++;
				}
				sb.append(words[i]);
				lineLen += words[i].length();
			}
		}
		return sb.toString();
	}

	private static GridPane createGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(3);
		return grid;
	}

	private static int addRow(GridPane grid, int row, String key, String value) {
		Label keyLabel = new Label(key);
		keyLabel.getStyleClass().add("module-tooltip-key");
		keyLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
		Label valueLabel = new Label(wrap(value, WRAP_CHARS - 10));
		valueLabel.getStyleClass().add("module-tooltip-text");
		GridPane.setValignment(keyLabel, VPos.TOP);
		grid.add(keyLabel, 0, row);
		grid.add(valueLabel, 1, row);
		return row + 1;
	}

	private static String getGroupName(PamModuleInfo info) {
		ModulesMenuGroup group = info.getModulesMenuGroup();
		if (group == null) return null;
		return group.getMenuName();
	}

	/**
	 * @return whether the module has JavaFX settings or only Swing ones.
	 */
	private static String getSettingsText(PamModuleInfo info) {
		for (int i = 0; i < info.getNGUIFlags(); i++) {
			if (info.getGUICompatabilityFlag(i) == PamGUIManager.FX) {
				return "JavaFX settings";
			}
		}
		return "Swing settings dialog";
	}

	/**
	 * Describe what a module needs, e.g. "Raw data, e.g. from Sound Acquisition".
	 */
	private static String getDependencyText(PamDependency dependency) {
		String text = "data";
		if (dependency.getRequiredDataType() != null) {
			text = getDataTypeName(dependency.getRequiredDataType()) + " data";
		}
		String provider = dependency.getDefaultProvider();
		if (provider != null) {
			PamModuleInfo providerInfo = PamModuleInfo.findModuleInfo(provider);
			if (providerInfo != null) {
				text = text + ", e.g. from " + providerInfo.getDescription();
			}
		}
		return text;
	}

	/**
	 * Turn a data unit class name into words, e.g. RawDataUnit to Raw, FFTDataUnit
	 * to FFT and ClickDetection to Click Detection.
	 */
	static String getDataTypeName(Class<?> dataType) {
		String name = dataType.getSimpleName();
		if (name.endsWith("DataUnit") && name.length() > "DataUnit".length()) {
			name = name.substring(0, name.length() - "DataUnit".length());
		}
		//split camel case but keep acronyms such as FFT together.
		name = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
		name = name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
		return name;
	}

	/**
	 * @return the names of the data the module's processes take, with the module
	 * each comes from.
	 */
	private static ArrayList<String> getInputs(PamControlledUnit pcu) {
		ArrayList<String> inputs = new ArrayList<String>();
		for (int i = 0; i < pcu.getNumPamProcesses(); i++) {
			PamProcess process = pcu.getPamProcess(i);
			if (process == null) continue;
			PamDataBlock parent = process.getParentDataBlock();
			if (parent == null) continue;
			String name = getBlockName(parent);
			if (!inputs.contains(name)) inputs.add(name);
		}
		return inputs;
	}

	/**
	 * @return the names of the data the module makes.
	 */
	private static ArrayList<String> getOutputs(PamControlledUnit pcu) {
		ArrayList<String> outputs = new ArrayList<String>();
		for (int i = 0; i < pcu.getNumPamProcesses(); i++) {
			PamProcess process = pcu.getPamProcess(i);
			if (process == null) continue;
			for (int j = 0; j < process.getNumOutputDataBlocks(); j++) {
				PamDataBlock block = process.getOutputDataBlock(j);
				if (block == null) continue;
				String name = block.getDataName();
				if (name != null && !outputs.contains(name)) outputs.add(name);
			}
		}
		return outputs;
	}

	/**
	 * Name of a data block with the module it comes from, if that is different.
	 */
	private static String getBlockName(PamDataBlock block) {
		String name = block.getDataName();
		PamProcess process = block.getParentProcess();
		if (process == null) return name;
		PamControlledUnit unit = process.getPamControlledUnit();
		if (unit == null || unit.getUnitName() == null) return name;
		if (name == null || name.equals(unit.getUnitName())) return unit.getUnitName();
		return name + " (" + unit.getUnitName() + ")";
	}

	/**
	 * @return the module's status, or null if it has none.
	 */
	private static String getStatusText(PamControlledUnit pcu) {
		ModuleStatus status;
		try {
			status = pcu.getModuleStatus();
		}
		catch (Exception e) {
			return null;
		}
		if (status == null) return null;
		String text = status.getStatusString();
		String message = status.getMessage();
		if (message != null && message.length() > 0) {
			text = text + ": " + message;
		}
		return text;
	}

}
