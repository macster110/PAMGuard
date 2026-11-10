package PamController.command;

import pamguard.cli.CommandLineOptions;
import pamguard.cli.ViewerCommandLine;

/**
 * List the offline tasks in this configuration, by the name to use with the 
 * -offlinetask command line option.
 */
public class ListTasksCommand extends LargeReplyCommand {

	public ListTasksCommand() {
		super("listtasks");
	}

	@Override
	protected String createOutput(String args) {
		return CommandLineOptions.toJson(ViewerCommandLine.listOfflineTasks());
	}

	@Override
	public String getHint() {
		return "listtasks [file.json]: JSON list of offline tasks, by the name to use with -offlinetask";
	}
}
