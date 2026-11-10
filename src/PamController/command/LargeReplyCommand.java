package PamController.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.swing.SwingUtilities;

import PamController.PamGUIManager;

/**
 * Base for commands which may return a lot of text (e.g. JSON). UDP replies are
 * limited to about 64kB, so these commands can write their output to a file instead:
 * if the first argument after the command is a file name, the output is written there
 * and the reply is "written to &lt;file&gt;". 
 * <p>
 * The work is done on the AWT thread when there is a GUI, since it reads or changes
 * module settings, and the result is waited for so it can be returned.
 */
public abstract class LargeReplyCommand extends ExtCommand {

	/**
	 * Largest reply which is sent directly rather than needing a file.
	 */
	public static final int MAXREPLY = 60000;

	public LargeReplyCommand(String name) {
		super(name, true);
	}

	/**
	 * Create the output.
	 * @param args the command arguments after the command name (may be empty)
	 * @return output text
	 */
	protected abstract String createOutput(String args);

	/**
	 * @return true if the first argument can be an output file name.
	 */
	protected boolean allowFileOutput() {
		return true;
	}

	@Override
	public String execute(String command) {
		// everything after the first word (the command name, which may have a pg prefix) 
		String trimmed = command.trim();
		int sp = trimmed.indexOf(' ');
		String args = sp < 0 ? "" : trimmed.substring(sp+1).trim();
		String[] words = CommandManager.splitCommandLine(args);
		String outFile = null;
		String rest = args;
		if (allowFileOutput() && words != null && words.length > 0 && looksLikeFile(words[0])) {
			outFile = words[0];
			rest = CommandManager.getCommandFromIndex(args, 1);
			if (rest == null) {
				rest = "";
			}
		}
		String output = runOnGUIThread(rest);
		if (outFile != null) {
			File f = new File(outFile);
			if (f.getParentFile() != null) {
				f.getParentFile().mkdirs();
			}
			try (Writer w = new FileWriter(f)) {
				w.write(output);
			} catch (IOException e) {
				return "error: unable to write " + outFile + ": " + e.getMessage();
			}
			return "written to " + f.getAbsolutePath();
		}
		if (output != null && output.getBytes().length > MAXREPLY) {
			return String.format("error: reply is %d bytes, too long for a network reply. Give a file name: %s <file>", 
					output.getBytes().length, getName());
		}
		return output;
	}

	private String runOnGUIThread(String args) {
		if (PamGUIManager.getGUIType() == PamGUIManager.NOGUI || SwingUtilities.isEventDispatchThread()) {
			return createOutput(args);
		}
		String[] result = new String[1];
		try {
			SwingUtilities.invokeAndWait(() -> result[0] = createOutput(args));
		} catch (Exception e) {
			return "error: " + e.getMessage();
		}
		return result[0];
	}

	/**
	 * @param word first argument
	 * @return true if it looks like a file name for the output (ends .json, .xml or .txt)
	 */
	protected boolean looksLikeFile(String word) {
		String w = word.toLowerCase();
		return w.endsWith(".json") || w.endsWith(".xml") || w.endsWith(".txt");
	}
}
