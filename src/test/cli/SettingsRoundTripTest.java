package test.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import PamController.PSFXReadWriter;
import PamController.PamControlledUnitSettings;
import PamController.PamSettingsGroup;
import PamController.settings.output.xml.ModuleNode;
import PamController.settings.output.xml.PamguardXMLReader;
import PamController.settings.output.xml.PamguardXMLWriter;
import pamguard.cli.SettingsOverrides;
import pamguard.cli.SettingsPath;
import pamguard.cli.SettingsPathException;
import test.helper.PamControllerTestHelper;

/**
 * Tests that module settings survive being written to XML and read back, which is
 * what the -settings command line option and the setxmlsettings network command
 * rely on. Every settings object in every psfx shipped with PAMGuard is tested.
 * <p>
 * Runs headless: reading a psfx doesn't need a running PAMGuard.
 */
public class SettingsRoundTripTest {

	@BeforeAll
	public static void setup() throws Exception {
		// the XML writer registers its own settings, so needs a PamController.
		PamControllerTestHelper.InitializePamControllerForTesting();
	}

	/**
	 * @return every psfx shipped with PAMGuard (configurations folder and the root folder).
	 */
	private static List<File> shippedPsfxFiles() {
		List<File> files = new ArrayList<>();
		File root = new File(System.getProperty("user.dir"));
		for (File folder : new File[] {new File(root, "configurations"), root}) {
			File[] found = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".psfx"));
			if (found != null) {
				Arrays.sort(found);
				files.addAll(Arrays.asList(found));
			}
		}
		return files;
	}

	private static List<PamControlledUnitSettings> loadSettings(File psfx) {
		PamSettingsGroup group = PSFXReadWriter.getInstance().loadFileSettings(psfx);
		List<PamControlledUnitSettings> list = new ArrayList<>();
		if (group == null) {
			return list;
		}
		for (PamControlledUnitSettings pcus : group.getUnitSettings()) {
			Object settings;
			try {
				settings = pcus.getSettings();
			}
			catch (Exception e) {
				continue;
			}
			if (settings != null && SettingsPath.getParameterSet(settings) != null) {
				list.add(pcus);
			}
		}
		return list;
	}

	private static byte[] serialise(Object obj) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		oos.close();
		return bos.toByteArray();
	}

	/**
	 * Applying a module's own settings, as XML, to a copy of those settings must not
	 * change anything. If this fails, -settings would corrupt settings it was not
	 * asked to change.
	 */
	@Test
	public void applyingOwnXMLChangesNothing() throws Exception {
		List<File> psfxFiles = shippedPsfxFiles();
		assertFalse(psfxFiles.isEmpty(), "No psfx files found to test in " + System.getProperty("user.dir"));
		PamguardXMLWriter writer = PamguardXMLWriter.getXMLWriter();
		List<String> failures = new ArrayList<>();
		int nTested = 0;
		for (File psfx : psfxFiles) {
			for (PamControlledUnitSettings pcus : loadSettings(psfx)) {
				Object original = pcus.getSettings();
				Object copy = SettingsOverrides.deepCopy(original);
				if (copy == null) {
					continue; // not serialisable, so can't be in a psfx anyway
				}
				Object before = SettingsOverrides.deepCopy(copy);
				Document doc = writer.writeSettings(pcus);
				applyXML(doc, copy);
				nTested++;
				List<String> diffs = new ArrayList<>();
				deepDiff("", before, copy, new IdentityHashMap<>(), 0, diffs);
				if (!diffs.isEmpty()) {
					failures.add(String.format("%s: %s \"%s\" (%s) changed %s", psfx.getName(), pcus.getUnitType(), 
							pcus.getUnitName(), original.getClass().getName(), diffs));
					continue;
				}
				/*
				 * Applying the XML may tidy up values which aren't visible as parameters 
				 * (e.g. null replaced by a default), but doing it a second time must change 
				 * nothing at all. 
				 */
				byte[] once = serialise(copy);
				applyXML(doc, copy);
				if (!Arrays.equals(once, serialise(copy))) {
					failures.add(String.format("%s: %s \"%s\" (%s) changed when applied twice", psfx.getName(), pcus.getUnitType(), 
							pcus.getUnitName(), original.getClass().getName()));
				}
			}
		}
		System.out.printf("Applied own XML to %d settings objects, %d changed\n", nTested, failures.size());
		assertTrue(nTested > 0);
		assertEquals(new ArrayList<String>(), failures, "Settings changed by applying their own XML");
	}

	/**
	 * Compare two objects field by field, recording differences. A field which was 
	 * null before may change, since reading XML can replace a null with a default. 
	 */
	private static void deepDiff(String path, Object a, Object b, IdentityHashMap<Object, Boolean> seen, int depth, List<String> diffs) {
		if (depth > 10 || a == null) {
			return;
		}
		if (b == null) {
			diffs.add(path + ": " + a + " -> null");
			return;
		}
		Class<?> c = a.getClass();
		if (c != b.getClass()) {
			diffs.add(path + ": " + c.getName() + " -> " + b.getClass().getName());
			return;
		}
		if (a instanceof Number || a instanceof String || a instanceof Boolean || a instanceof Character 
				|| c.isEnum() || a instanceof File) {
			if (!a.equals(b)) {
				diffs.add(path + ": " + a + " -> " + b);
			}
			return;
		}
		if (seen.containsKey(a)) {
			return;
		}
		seen.put(a, true);
		if (c.isArray()) {
			int n = Array.getLength(a);
			if (n != Array.getLength(b)) {
				diffs.add(path + ": length " + n + " -> " + Array.getLength(b));
				return;
			}
			for (int i = 0; i < n; i++) {
				deepDiff(path + "[" + i + "]", Array.get(a, i), Array.get(b, i), seen, depth+1, diffs);
			}
			return;
		}
		if (a instanceof Collection) {
			deepDiff(path, ((Collection<?>) a).toArray(), ((Collection<?>) b).toArray(), seen, depth, diffs);
			return;
		}
		if (a instanceof Map) {
			deepDiff(path + ".keys", ((Map<?,?>) a).keySet().toArray(), ((Map<?,?>) b).keySet().toArray(), seen, depth, diffs);
			deepDiff(path + ".values", ((Map<?,?>) a).values().toArray(), ((Map<?,?>) b).values().toArray(), seen, depth, diffs);
			return;
		}
		for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
					continue;
				}
				try {
					f.setAccessible(true);
					deepDiff(path + "." + f.getName(), f.get(a), f.get(b), seen, depth+1, diffs);
				}
				catch (Exception e) {
				}
			}
		}
	}

	private static void applyXML(Document doc, Object target) {
		PamguardXMLReader reader = new PamguardXMLReader(doc);
		for (ModuleNode mn : reader.getModuleNodes()) {
			for (Node sn : reader.getSettingsNodes(mn)) {
				reader.unpackSettingsNodeInto(sn, target);
			}
		}
	}

	/**
	 * Settings written to XML and read into a newly created object should give the
	 * same XML again. Classes which can't do this yet (mostly because they hold lists
	 * or maps, which the XML reader can't read yet) are listed rather than failing the
	 * test, but at least half must work, so a general break in the reader is caught.
	 */
	@Test
	public void xmlRoundTripsToNewObject() throws Exception {
		PamguardXMLWriter writer = PamguardXMLWriter.getXMLWriter();
		List<String> notRoundTripping = new ArrayList<>();
		List<String> tested = new ArrayList<>();
		for (File psfx : shippedPsfxFiles()) {
			for (PamControlledUnitSettings pcus : loadSettings(psfx)) {
				Object original = pcus.getSettings();
				String className = original.getClass().getName();
				if (tested.contains(className)) {
					continue;
				}
				Constructor<?> constructor;
				try {
					constructor = original.getClass().getConstructor();
				}
				catch (NoSuchMethodException e) {
					continue;
				}
				tested.add(className);
				Object fresh = constructor.newInstance();
				Document doc = writer.writeSettings(pcus);
				PamguardXMLReader reader = new PamguardXMLReader(doc);
				for (ModuleNode mn : reader.getModuleNodes()) {
					for (Node sn : reader.getSettingsNodes(mn)) {
						reader.unpackSettingsNodeInto(sn, fresh);
					}
				}
				String xml1 = settingsXML(writer, pcus, original);
				String xml2 = settingsXML(writer, pcus, fresh);
				if (!xml1.equals(xml2)) {
					notRoundTripping.add(className);
				}
			}
		}
		System.out.printf("XML round trip: %d of %d settings classes work\n", tested.size() - notRoundTripping.size(), tested.size());
		for (String c : notRoundTripping) {
			System.out.println("  doesn't round trip yet: " + c);
		}
		assertTrue(tested.size() > 0);
		assertTrue(notRoundTripping.size() * 2 <= tested.size(), "Most settings classes no longer round trip through XML");
	}

	private static String settingsXML(PamguardXMLWriter writer, PamControlledUnitSettings pcus, Object settings) {
		PamControlledUnitSettings p = new PamControlledUnitSettings(pcus.getUnitType(), pcus.getUnitName(),
				pcus.getOwnerClassName(), pcus.getVersionNo(), settings);
		String xml = writer.getAsString(writer.writeSettings(p));
		// remove the INFO element, which holds a time stamp
		return xml.replaceAll("<INFO[^>]*/>", "");
	}

	/**
	 * Settings paths, as used by -set: read, set and convert values, and report
	 * unknown parameters helpfully.
	 */
	@Test
	public void settingsPathsSetValues() throws Exception {
		TestParams p = new TestParams();
		assertEquals(5, SettingsPath.set(p, "count", "7"));
		assertEquals(7, p.count);
		SettingsPath.set(p, "threshold", "12.5");
		assertEquals(12.5, p.threshold);
		SettingsPath.set(p, "enabled", "false");
		assertFalse(p.enabled);
		SettingsPath.set(p, "name", "hello world");
		assertEquals("hello world", p.name);
		SettingsPath.set(p, "gains", "1,2,3.5");
		assertEquals(3, p.gains.length);
		assertEquals(3.5, p.gains[2]);
		SettingsPath.set(p, "gains[0]", "9");
		assertEquals(9., p.gains[0]);
		SettingsPath.set(p, "inner.value", "42");
		assertEquals(42, p.inner.value);
		SettingsPath.set(p, "hiddenValue", "3");
		assertEquals(3, p.getHiddenValue());
		SettingsPath.set(p, "shape", "square");
		assertEquals(TestParams.Shape.SQUARE, p.shape);
		try {
			SettingsPath.set(p, "cout", "1");
			throw new AssertionError("Expected an unknown parameter error");
		}
		catch (SettingsPathException e) {
			assertTrue(e.getMessage().contains("count"), "Error should list the parameters: " + e.getMessage());
		}
		try {
			SettingsPath.set(p, "count", "seven");
			throw new AssertionError("Expected a bad value error");
		}
		catch (SettingsPathException e) {
		}
		assertFalse(SettingsPath.describe(p).isEmpty());
	}

	public static class TestParams implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		public enum Shape {ROUND, SQUARE};
		public int count = 5;
		public double threshold = 3;
		public boolean enabled = true;
		public String name = "x";
		public double[] gains = {1, 1};
		public Shape shape = Shape.ROUND;
		public Inner inner = new Inner();
		private int hiddenValue = 0;
		public int getHiddenValue() {
			return hiddenValue;
		}
	}

	public static class Inner implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		public int value = 1;
	}
}
