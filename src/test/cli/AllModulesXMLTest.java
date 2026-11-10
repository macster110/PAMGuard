package test.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import PamController.PamControlledUnit;
import PamController.PamControlledUnitSettings;
import PamController.PamController;
import PamController.PamGUIManager;
import PamController.PamSettingManager;
import PamController.PamSettings;
import PamController.settings.output.xml.ModuleNode;
import PamController.settings.output.xml.PamguardXMLReader;
import PamController.settings.output.xml.PamguardXMLWriter;
import PamController.settings.output.xml.XMLValueTypes;
import PamModel.PamModuleInfo;
import pamguard.cli.SettingsOverrides;
import test.helper.PamControllerTestHelper;

/**
 * Creates one of every type of module and checks that every module's settings
 * (and every other settings object, such as display settings) can be written to XML
 * and read back, both into a new settings object and into a copy of the existing one,
 * without anything changing. This is what -settings, setxmlsettings and the XML
 * settings export rely on.
 *
 * @author Jamie Macaulay
 */
public class AllModulesXMLTest {

	private static List<String> notCreated = new ArrayList<>();

	@BeforeAll
	public static void createEveryModule() throws Exception {
		// as with -nogui, so that modules don't create windows (there's no display here) 
		PamGUIManager.setType(PamGUIManager.NOGUI);
		PamControllerTestHelper.InitializePamControllerForTesting();
		PamController pc = PamController.getInstance();
		for (PamModuleInfo info : PamModuleInfo.getModuleList()) {
			if (info.getMaxNumber() > 0 && countOf(pc, info) >= info.getMaxNumber()) {
				continue;
			}
			try {
				PamControlledUnit unit = pc.addModule(info, info.getDefaultName());
				if (unit == null) {
					notCreated.add(info.getClassName());
				}
			}
			catch (Throwable e) {
				notCreated.add(info.getClassName() + " (" + e + ")");
			}
		}
		System.out.printf("Created %d modules; %d module types couldn't be created here:\n", pc.getNumControlledUnits(), notCreated.size());
		for (String s : notCreated) {
			System.out.println("  " + s);
		}
	}

	private static int countOf(PamController pc, PamModuleInfo info) {
		int n = 0;
		for (int i = 0; i < pc.getNumControlledUnits(); i++) {
			if (pc.getControlledUnit(i).getClass().getName().equals(info.getClassName())) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Every settings object, read from XML into a new object, gives the same XML.
	 */
	@Test
	public void everySettingsObjectRoundTrips() throws Exception {
		PamguardXMLWriter writer = PamguardXMLWriter.getXMLWriter();
		List<String> failures = new ArrayList<>();
		int nTested = 0;
		for (PamSettings owner : new ArrayList<>(PamSettingManager.getInstance().getOwners())) {
			Serializable settings;
			try {
				settings = owner.getSettingsReference();
			}
			catch (Throwable e) {
				continue;
			}
			if (settings == null) {
				continue;
			}
			PamControlledUnitSettings pcus = new PamControlledUnitSettings(owner.getUnitType(), owner.getUnitName(),
					owner.getClass().getName(), owner.getSettingsVersion(), settings);
			String what = String.format("%s \"%s\" (%s)", owner.getUnitType(), owner.getUnitName(), settings.getClass().getName());
			nTested++;
			try {
				Document doc = writer.writeSettings(pcus);
				// into a new object
				Object fresh = XMLValueTypes.newInstance(settings.getClass());
				if (fresh == null) {
					failures.add(what + ": can't create a new object of this class");
					continue;
				}
				PamguardXMLReader reader = new PamguardXMLReader(doc);
				for (ModuleNode mn : reader.getModuleNodes()) {
					for (Node sn : reader.getSettingsNodes(mn)) {
						reader.unpackSettingsNodeInto(sn, fresh);
					}
				}
				SettingsRoundTripTest.copyNulls(settings, fresh, 0);
				String xml1 = SettingsRoundTripTest.settingsXML(writer, pcus, settings);
				String xml2 = SettingsRoundTripTest.settingsXML(writer, pcus, fresh);
				if (!xml1.equals(xml2)) {
					failures.add(what + ": new object differs at " + SettingsRoundTripTest.firstDifference(xml1, xml2));
					continue;
				}
				// into a copy of the existing object
				Object copy = SettingsOverrides.deepCopy(settings);
				if (copy != null) {
					Object before = SettingsOverrides.deepCopy(copy);
					SettingsRoundTripTest.applyXML(doc, copy);
					List<String> diffs = new ArrayList<>();
					SettingsRoundTripTest.deepDiff("", before, copy, new IdentityHashMap<>(), 0, diffs);
					if (!diffs.isEmpty()) {
						failures.add(what + ": applying its own XML changed " + diffs);
					}
				}
			}
			catch (Throwable e) {
				failures.add(what + ": " + e);
			}
		}
		System.out.printf("XML round trip of %d settings objects from every module type: %d failed\n", nTested, failures.size());
		for (String f : failures) {
			System.out.println("  " + f);
		}
		assertTrue(nTested > 50, "Too few settings objects to test: " + nTested);
		assertEquals(new ArrayList<String>(), failures, "Settings which don't round trip through XML");
	}

}
