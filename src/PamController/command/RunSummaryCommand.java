package PamController.command;

import pamguard.cli.CommandLineOptions;
import pamguard.cli.RunSummary;

/**
 * Get the run summary so far as JSON: files processed, number of data units in
 * each data stream, settings changes, warnings and errors. The same information as
 * written at exit by the -summary command line option. 
 */
public class RunSummaryCommand extends LargeReplyCommand {

	public RunSummaryCommand() {
		super("runsummary");
	}

	@Override
	protected String createOutput(String args) {
		return CommandLineOptions.toJson(RunSummary.getInstance().getSummary());
	}

	@Override
	public String getHint() {
		return "runsummary [file.json]: JSON summary of the run so far (files, detection counts, warnings, errors)";
	}
}
