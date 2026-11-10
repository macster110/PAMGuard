package PamController.command;

import java.util.List;

import org.w3c.dom.Document;

import pamguard.cli.SettingsOverrides;
import pamguard.cli.SettingsPathException;

/**
 * Set module settings from XML, in the format returned by getxmlsettings. Every 
 * module in the XML is updated, and only the fields in the XML are changed. 
 */
public class SetXMLSettings extends ExtCommand {

	public SetXMLSettings() {
		super("setxmlsettings", true);
	}

	@Override
	public String execute(String command) {
		/*
		 * Remove the name of the command (first word, which may have a pg prefix) and
		 * what's left is the xml. 
		 */
		String xmlString = command.trim();
		int sp = xmlString.indexOf(' ');
		if (sp < 0) {
			return "error: no xml settings";
		}
		xmlString = xmlString.substring(sp+1).trim();
		try {
			Document doc = SettingsOverrides.parseXML(xmlString);
			List<String> done = SettingsOverrides.applyXMLToModules(doc);
			return "xml settings pushed to modules: " + String.join("; ", done);
		} catch (SettingsPathException e) {
			return "error: invalid xml settings can't be pushed to modules: " + e.getMessage();
		}
	}

	@Override
	public String getHint() {
		return "Set XML settings for one or more modules, (see getxmlsettings to retreive module settings to modify). Only the fields in the XML are changed";
	}
}
