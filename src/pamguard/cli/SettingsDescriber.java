package pamguard.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamController.PamguardVersionInfo;
import PamModel.PamDependency;
import PamModel.PamModuleInfo;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamProcess;

/**
 * Writes a JSON description of a PAMGuard configuration: every module, the data
 * each produces, every settings object with each of its parameters (name, type,
 * current value and description) and the key to use to change it with -set, plus
 * the list of module types which can be added.
 * <p>
 * This is the "schema" for a configuration: it lets a script or AI assistant find
 * out what a configuration contains and what can be changed, without opening any
 * dialogs. Used by the -describe command line option and the describe network command.
 */
public class SettingsDescriber {

	private static String describeFile;

	/**
	 * Ask for a description to be written once the configuration has loaded.
	 * @param file output file, or "-" for the console.
	 */
	public static void setDescribe(String file) {
		describeFile = file;
	}

	/**
	 * @return the file the description is to be written to, or null if none was asked for.
	 */
	public static String getDescribeFile() {
		return describeFile;
	}

	/**
	 * Build the description of the current configuration.
	 * @param includeAvailable also list the module types which could be added.
	 * @return description as maps and lists, ready to write as JSON.
	 */
	public static Map<String, Object> describe(boolean includeAvailable) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("program", "PAMGuard");
		root.put("version", PamguardVersionInfo.version);
		PamController pc = PamController.getInstance();
		if (pc != null) {
			root.put("runMode", RunSummary.getRunModeName(pc.getRunMode()));
			try {
				root.put("settingsFile", pc.getPSFNameWithPath());
			}
			catch (Exception e) {
			}
			root.put("modules", describeModules(pc));
		}
		root.put("settings", describeSettings());
		root.put("offlineTasks", ViewerCommandLine.listOfflineTasks());
		if (includeAvailable) {
			root.put("availableModules", describeAvailable());
		}
		root.put("howToChange", "Use -set \"<setKey>.<parameter name>=<value>\" on the command line, the setparam network command, "
				+ "or an XML file with -settings. Parameters marked settable:false can only be changed with XML.");
		return root;
	}

	private static List<Object> describeModules(PamController pc) {
		List<Object> mods = new ArrayList<>();
		for (int i = 0; i < pc.getNumControlledUnits(); i++) {
			PamControlledUnit unit = pc.getControlledUnit(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("type", unit.getUnitType());
			m.put("name", unit.getUnitName());
			m.put("class", unit.getClass().getName());
			List<Object> blocks = new ArrayList<>();
			for (int p = 0; p < unit.getNumPamProcesses(); p++) {
				PamProcess process = unit.getPamProcess(p);
				PamDataBlock parent = process.getParentDataBlock();
				for (int b = 0; b < process.getNumOutputDataBlocks(); b++) {
					PamDataBlock block = process.getOutputDataBlock(b);
					if (block == null) {
						continue;
					}
					Map<String, Object> bl = new LinkedHashMap<>();
					bl.put("name", block.getDataName());
					bl.put("longName", block.getLongDataName());
					if (block.getUnitClass() != null) {
						bl.put("dataUnitClass", block.getUnitClass().getName());
					}
					if (parent != null) {
						bl.put("input", parent.getLongDataName());
					}
					bl.put("channelMap", block.getChannelMap());
					bl.put("sampleRate", block.getSampleRate());
					blocks.add(bl);
				}
			}
			m.put("dataBlocks", blocks);
			mods.add(m);
		}
		return mods;
	}

	private static List<Object> describeSettings() {
		List<Object> list = new ArrayList<>();
		ArrayList<PamSettings> owners = PamSettingManager.getInstance().getOwners();
		if (owners == null) {
			return list;
		}
		Map<String, Integer> nameCount = new HashMap<>();
		for (PamSettings owner : owners) {
			nameCount.merge(String.valueOf(owner.getUnitName()), 1, Integer::sum);
		}
		for (PamSettings owner : owners) {
			Object ref;
			try {
				ref = owner.getSettingsReference();
			}
			catch (Exception e) {
				continue;
			}
			if (ref == null) {
				continue;
			}
			Map<String, Object> s = new LinkedHashMap<>();
			String name = String.valueOf(owner.getUnitName());
			String key = nameCount.get(name) > 1 ? owner.getUnitType() + ":" + name : name;
			s.put("setKey", key);
			s.put("unitType", owner.getUnitType());
			s.put("unitName", owner.getUnitName());
			s.put("ownerClass", owner.getClass().getName());
			s.put("settingsClass", ref.getClass().getName());
			s.put("parameters", SettingsPath.describe(ref));
			list.add(s);
		}
		return list;
	}

	private static List<Object> describeAvailable() {
		List<Object> list = new ArrayList<>();
		ArrayList<PamModuleInfo> mods = PamModuleInfo.getModuleList();
		if (mods == null) {
			return list;
		}
		for (PamModuleInfo mi : mods) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("description", mi.getDescription());
			m.put("class", mi.getClassName());
			if (mi.getModulesMenuGroup() != null) {
				m.put("group", mi.getModulesMenuGroup().getMenuName());
			}
			if (mi.getToolTipText() != null) {
				m.put("help", mi.getToolTipText());
			}
			PamDependency dep = mi.getDependency();
			if (dep != null) {
				Map<String, Object> d = new LinkedHashMap<>();
				if (dep.getRequiredDataType() != null) {
					d.put("dataType", dep.getRequiredDataType().getName());
				}
				d.put("defaultProvider", dep.getDefaultProvider());
				m.put("requires", d);
			}
			m.put("instances", mi.getNInstances());
			if (mi.getMaxNumber() > 0) {
				m.put("maxInstances", mi.getMaxNumber());
			}
			list.add(m);
		}
		return list;
	}

	/**
	 * Write the description to a file, or the console if the file is "-".
	 * @param file file name
	 * @return true if written OK
	 */
	public static boolean write(String file) {
		String json = CommandLineOptions.toJson(describe(true));
		if (file == null || file.equals("-")) {
			System.out.println(json);
			return true;
		}
		File f = new File(file);
		if (f.getParentFile() != null) {
			f.getParentFile().mkdirs();
		}
		try (Writer w = new FileWriter(f)) {
			w.write(json);
			System.out.println("Configuration description written to " + f.getAbsolutePath());
			return true;
		} catch (IOException e) {
			RunSummary.getInstance().addError("Unable to write configuration description to " + file + ": " + e.getMessage());
			return false;
		}
	}
}
