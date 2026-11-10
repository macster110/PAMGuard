package PamController.command;

import pamguard.cli.SettingsOverrides;
import pamguard.cli.SettingsPathException;

/**
 * Change one setting in a running PAMGuard, e.g. 
 * setparam Click Detector.dbThreshold=12. The module is given a changed copy of 
 * its settings through restoreSettings(), as when settings are loaded. Some modules 
 * only act on a change when processing next starts. 
 */
public class SetParamCommand extends LargeReplyCommand {

	public SetParamCommand() {
		super("setparam");
	}

	@Override
	protected boolean allowFileOutput() {
		return false;
	}

	@Override
	protected String createOutput(String args) {
		if (args == null || args.trim().length() == 0) {
			return "error: use setparam <module>.<parameter>=<value>";
		}
		try {
			return "ok: " + SettingsOverrides.applySetToModules(args.trim());
		} catch (SettingsPathException e) {
			return "error: " + e.getMessage();
		}
	}

	@Override
	public String getHint() {
		return "setparam <module>.<parameter>=<value>: change one setting (see describe for names). Takes effect when processing next starts";
	}
}
