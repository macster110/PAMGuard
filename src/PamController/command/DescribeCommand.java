package PamController.command;

import pamguard.cli.CommandLineOptions;
import pamguard.cli.SettingsDescriber;

/**
 * Describe the current configuration as JSON: modules, data streams, every 
 * settings object and parameter, offline tasks and the module types which could
 * be added. The same as the -describe command line option.
 */
public class DescribeCommand extends LargeReplyCommand {

	public DescribeCommand() {
		super("describe");
	}

	@Override
	protected String createOutput(String args) {
		return CommandLineOptions.toJson(SettingsDescriber.describe(true));
	}

	@Override
	public String getHint() {
		return "describe [file.json]: JSON description of every module, data stream and settings parameter (written to the file if given)";
	}
}
