package pamguard.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import PamController.PamControlledUnitSettings;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamController.settings.output.xml.ModuleNode;
import PamController.settings.output.xml.PamguardXMLReader;

/**
 * Change module settings from the command line (-settings file.xml and
 * -set module.parameter=value) or from network commands (setxmlsettings and
 * setparam).
 * <p>
 * Command line changes are applied to the settings as soon as they have been read
 * from the psfx file or database, before any modules are created, so modules start
 * with the changed settings exactly as though they had been in the file.
 * <p>
 * Settings are identified by the name of the module (or other settings owner)
 * which owns them, e.g. "Click Detector". Where two owners share a name, use
 * "unit type:unit name", e.g. "Click Detector:Click Detector". -describe lists the
 * key to use for every owner.
 */
public class SettingsOverrides {

	private static List<String> xmlFiles = new ArrayList<>();

	private static List<String> sets = new ArrayList<>();

	/**
	 * A settings object and the unit type and name it belongs to.
	 */
	public static class SettingsTarget {
		public final String unitType;
		public final String unitName;
		public final Object settings;
		public SettingsTarget(String unitType, String unitName, Object settings) {
			this.unitType = unitType;
			this.unitName = unitName;
			this.settings = settings;
		}
	}

	/**
	 * Add an XML file of settings to apply at start up.
	 * @param file file name
	 */
	public static void addXMLFile(String file) {
		xmlFiles.add(file);
	}

	/**
	 * Add a single setting change to apply at start up.
	 * @param keyValue module.parameter=value
	 */
	public static void addSet(String keyValue) {
		sets.add(keyValue);
	}

	/**
	 * @return true if there are any changes to apply at start up.
	 */
	public static boolean hasOverrides() {
		return xmlFiles.size() > 0 || sets.size() > 0;
	}

	/**
	 * Apply the command line changes to a list of settings, normally the list just
	 * loaded from a psfx file or database. Errors are printed and recorded in the
	 * run summary.
	 * @param settingsList settings list (changed in place)
	 * @return true if every change was applied
	 */
	public static boolean applyToSettingsList(List<PamControlledUnitSettings> settingsList) {
		if (!hasOverrides()) {
			return true;
		}
		if (settingsList == null) {
			error("No settings were loaded, so command line settings changes (-settings, -set) can't be applied");
			return false;
		}
		boolean ok = true;
		for (String xmlFile : xmlFiles) {
			try {
				Document doc = parseXML(new File(xmlFile));
				List<String> done = applyXML(doc, settingsList);
				for (String d : done) {
					System.out.println("Settings from " + xmlFile + ": " + d);
				}
			} catch (SettingsPathException e) {
				error("Settings file " + xmlFile + ": " + e.getMessage());
				ok = false;
			}
		}
		List<SettingsTarget> targets = new ArrayList<>();
		for (PamControlledUnitSettings pcus : settingsList) {
			Object settings;
			try {
				settings = pcus.getSettings();
			}
			catch (Exception e) {
				continue;
			}
			if (settings != null) {
				targets.add(new SettingsTarget(pcus.getUnitType(), pcus.getUnitName(), settings));
			}
		}
		for (String set : sets) {
			try {
				String result = applySet(targets, set, "commandline");
				System.out.println("Setting " + result);
			} catch (SettingsPathException e) {
				error("-set " + set + ": " + e.getMessage());
				ok = false;
			}
		}
		return ok;
	}

	/**
	 * Apply one key=value change to the first matching target.
	 * @param targets settings objects which can be changed (changed in place)
	 * @param keyValue owner.parameter=value
	 * @param source where the change came from, for the run summary
	 * @return description of the change
	 * @throws SettingsPathException if the key can't be found or the value is invalid
	 */
	public static String applySet(List<SettingsTarget> targets, String keyValue, String source) throws SettingsPathException {
		int eq = keyValue.indexOf('=');
		if (eq <= 0) {
			throw new SettingsPathException("Expected module.parameter=value");
		}
		String key = keyValue.substring(0, eq).trim();
		String value = keyValue.substring(eq+1);
		Match match = findTarget(targets, key);
		Object old = SettingsPath.set(match.target.settings, match.path, value);
		Object now = SettingsPath.get(match.target.settings, match.path);
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("source", source);
		change.put("unitType", match.target.unitType);
		change.put("unitName", match.target.unitName);
		change.put("parameter", match.path);
		change.put("oldValue", SettingsPath.jsonValue(old));
		change.put("newValue", SettingsPath.jsonValue(now));
		RunSummary.getInstance().addSettingsChange(change);
		return String.format("%s.%s = %s (was %s)", match.target.unitName, match.path,
				CommandLineOptions.toJson(SettingsPath.jsonValue(now)).trim(), CommandLineOptions.toJson(SettingsPath.jsonValue(old)).trim());
	}

	private static class Match {
		SettingsTarget target;
		String path;
		Match(SettingsTarget target, String path) {
			this.target = target;
			this.path = path;
		}
	}

	/**
	 * Find the settings object a key refers to. The key is "owner.parameter.path",
	 * where owner is a unit name or "unit type:unit name". Since unit names can
	 * contain dots, every possible split is tried, and the one whose parameter path
	 * exists is used.
	 */
	private static Match findTarget(List<SettingsTarget> targets, String key) throws SettingsPathException {
		List<Match> found = new ArrayList<>();
		List<Match> nameMatches = new ArrayList<>();
		for (SettingsTarget t : targets) {
			String[] prefixes = {t.unitType + ":" + t.unitName, t.unitName};
			for (String prefix : prefixes) {
				if (prefix == null || !key.startsWith(prefix + ".")) {
					continue;
				}
				Match m = new Match(t, key.substring(prefix.length() + 1));
				nameMatches.add(m);
				try {
					SettingsPath.get(t.settings, m.path);
					found.add(m);
				}
				catch (SettingsPathException e) {
				}
				break;
			}
		}
		if (found.size() == 1) {
			return found.get(0);
		}
		if (found.size() > 1) {
			List<String> opts = new ArrayList<>();
			for (Match m : found) {
				opts.add(m.target.unitType + ":" + m.target.unitName + "." + m.path);
			}
			throw new SettingsPathException("More than one settings object matches. Use one of: " + String.join(", ", opts));
		}
		if (nameMatches.size() > 0) {
			// owner found but not the parameter: report why.
			Match m = nameMatches.get(0);
			SettingsPath.get(m.target.settings, m.path);
		}
		List<String> names = new ArrayList<>();
		for (SettingsTarget t : targets) {
			if (!names.contains(t.unitName)) {
				names.add(t.unitName);
			}
		}
		throw new SettingsPathException(String.format("No module or settings called \"%s\". Available names are: %s",
				key.contains(".") ? key.substring(0, key.indexOf('.')) : key, String.join("; ", names)));
	}

	/**
	 * Apply settings in an XML document to a list of settings. Only the fields in
	 * the XML are changed.
	 * @param doc XML document in the format written by PamguardXMLWriter
	 * @param settingsList settings list (changed in place)
	 * @return description of each settings object changed
	 * @throws SettingsPathException if the XML refers to settings which don't exist
	 */
	public static List<String> applyXML(Document doc, List<PamControlledUnitSettings> settingsList) throws SettingsPathException {
		PamguardXMLReader xmlReader = new PamguardXMLReader(doc);
		ArrayList<ModuleNode> nodes = xmlReader.getModuleNodes();
		if (nodes == null || nodes.isEmpty()) {
			throw new SettingsPathException("No module settings found in the XML");
		}
		List<String> done = new ArrayList<>();
		for (ModuleNode aNode : nodes) {
			for (Node settingsNode : xmlReader.getSettingsNodes(aNode)) {
				String className = xmlReader.getSettingsClassName(settingsNode);
				PamControlledUnitSettings target = null;
				for (PamControlledUnitSettings pcus : settingsList) {
					if (!pcus.isSettingsOf(aNode.getUnitType(), aNode.getUnitName())) {
						continue;
					}
					Object s = pcus.getSettings();
					if (s != null && (className == null || s.getClass().getName().equals(className))) {
						target = pcus;
						break;
					}
				}
				if (target == null) {
					throw new SettingsPathException(String.format("No settings of type %s for %s \"%s\" in the configuration",
							className, aNode.getUnitType(), aNode.getUnitName()));
				}
				xmlReader.unpackSettingsNodeInto(settingsNode, target.getSettings());
				Map<String, Object> change = new LinkedHashMap<>();
				change.put("source", "xml");
				change.put("unitType", aNode.getUnitType());
				change.put("unitName", aNode.getUnitName());
				change.put("settingsClass", className);
				RunSummary.getInstance().addSettingsChange(change);
				done.add(String.format("%s \"%s\" (%s)", aNode.getUnitType(), aNode.getUnitName(), className));
			}
		}
		return done;
	}

	/**
	 * Apply XML settings to running modules. Each module is given a changed copy of
	 * its settings through restoreSettings(), the same as when settings are loaded.
	 * @param doc XML document
	 * @return description of what was changed
	 * @throws SettingsPathException if any module can't be found
	 */
	public static List<String> applyXMLToModules(Document doc) throws SettingsPathException {
		List<PamControlledUnitSettings> copies = getLiveSettingsCopies();
		List<String> done = applyXML(doc, copies);
		restoreChanged(copies);
		return done;
	}

	/**
	 * Apply a key=value change to running modules. The owner is given a changed
	 * copy of its settings through restoreSettings().
	 * @param keyValue owner.parameter=value
	 * @return description of the change
	 * @throws SettingsPathException if the key can't be found or the value is invalid
	 */
	public static String applySetToModules(String keyValue) throws SettingsPathException {
		List<PamControlledUnitSettings> copies = getLiveSettingsCopies();
		List<SettingsTarget> targets = new ArrayList<>();
		for (PamControlledUnitSettings c : copies) {
			targets.add(new SettingsTarget(c.getUnitType(), c.getUnitName(), c.getSettings()));
		}
		String result = applySet(targets, keyValue, "network");
		restoreChanged(copies);
		return result;
	}

	/**
	 * Get serialised copies of the settings of every owner, so changes can be made
	 * without touching the live objects until they are restored.
	 */
	private static List<PamControlledUnitSettings> getLiveSettingsCopies() {
		List<PamControlledUnitSettings> copies = new ArrayList<>();
		ArrayList<PamSettings> owners = PamSettingManager.getInstance().getOwners();
		if (owners == null) {
			return copies;
		}
		for (PamSettings owner : owners) {
			Object ref = owner.getSettingsReference();
			if (ref == null) {
				continue;
			}
			Object copy = deepCopy(ref);
			if (copy == null) {
				continue;
			}
			copies.add(new PamControlledUnitSettings(owner.getUnitType(), owner.getUnitName(),
					owner.getClass().getName(), owner.getSettingsVersion(), copy));
		}
		return copies;
	}

	/**
	 * Restore copies which differ from the live settings.
	 */
	private static void restoreChanged(List<PamControlledUnitSettings> copies) {
		ArrayList<PamSettings> owners = PamSettingManager.getInstance().getOwners();
		for (PamControlledUnitSettings c : copies) {
			for (PamSettings owner : owners) {
				if (!c.isSettingsOf(owner.getUnitType(), owner.getUnitName())) {
					continue;
				}
				Object ref = owner.getSettingsReference();
				if (ref == null || ref.getClass() != c.getSettings().getClass()) {
					continue;
				}
				if (!sameSerialised(ref, c.getSettings())) {
					owner.restoreSettings(c);
				}
				break;
			}
		}
	}

	/**
	 * Copy an object by serialising it.
	 * @param obj object
	 * @return copy, or null if it can't be serialised
	 */
	public static Object deepCopy(Object obj) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(obj);
			oos.close();
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
			return ois.readObject();
		}
		catch (Exception e) {
			return null;
		}
	}

	private static boolean sameSerialised(Object a, Object b) {
		try {
			ByteArrayOutputStream ba = new ByteArrayOutputStream();
			new ObjectOutputStream(ba).writeObject(a);
			ByteArrayOutputStream bb = new ByteArrayOutputStream();
			new ObjectOutputStream(bb).writeObject(b);
			return java.util.Arrays.equals(ba.toByteArray(), bb.toByteArray());
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Parse an XML file.
	 * @param file file
	 * @return document
	 * @throws SettingsPathException if the file can't be read
	 */
	public static Document parseXML(File file) throws SettingsPathException {
		if (!file.exists()) {
			throw new SettingsPathException("File not found: " + file.getAbsolutePath());
		}
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			return builder.parse(file);
		}
		catch (Exception e) {
			throw new SettingsPathException("Invalid XML: " + e.getMessage());
		}
	}

	/**
	 * Parse an XML string.
	 * @param xml xml
	 * @return document
	 * @throws SettingsPathException if the XML is invalid
	 */
	public static Document parseXML(String xml) throws SettingsPathException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(xml)));
		}
		catch (Exception e) {
			throw new SettingsPathException("Invalid XML: " + e.getMessage());
		}
	}

	private static void error(String message) {
		System.out.println("Error: " + message);
		RunSummary.getInstance().addError(message);
	}
}
