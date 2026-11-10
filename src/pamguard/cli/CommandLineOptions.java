package pamguard.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import PamController.PamguardVersionInfo;

/**
 * Registry of every command line option PAMGuard understands. The main PAMGuard
 * class uses it to read the command line, and the help (-help, or -help json for a
 * machine readable version) is generated from it, so the two can't get out of step.
 * <p>
 * Built in options are registered by {@link BuiltInOptions}. Modules and plugins
 * can add their own with {@link #register(CommandLineOption)}; options registered
 * after start up won't be acted on by the main class, but they will appear in the
 * help, and modules can read their values with pamguard.CommandLine.
 */
public class CommandLineOptions {

	private static List<CommandLineOption> options = new ArrayList<>();

	private static List<String> unrecognised = new ArrayList<>();

	static {
		BuiltInOptions.registerAll();
	}

	/**
	 * Register a command line option.
	 * @param option option
	 * @return the option
	 */
	public static synchronized CommandLineOption register(CommandLineOption option) {
		CommandLineOption existing = find(option.getName());
		if (existing != null) {
			options.remove(existing);
		}
		options.add(option);
		return option;
	}

	/**
	 * Find an option by name or alias, ignoring case.
	 * @param arg command line argument
	 * @return option or null
	 */
	public static synchronized CommandLineOption find(String arg) {
		for (CommandLineOption opt : options) {
			if (opt.matches(arg)) {
				return opt;
			}
		}
		return null;
	}

	/**
	 * @return a copy of the list of all registered options
	 */
	public static synchronized List<CommandLineOption> getOptions() {
		return new ArrayList<>(options);
	}

	/**
	 * @return arguments starting with - which weren't recognised by the last call to parse.
	 */
	public static List<String> getUnrecognised() {
		return unrecognised;
	}

	/**
	 * Read the command line and call the handler of every option found.
	 * Arguments starting with - which are not registered are reported but otherwise
	 * ignored, since plugins may read their own options through pamguard.CommandLine.
	 * @param args command line arguments
	 * @param state start up state for the handlers to fill in
	 * @throws CommandLineException if an option is missing a value or has a bad one.
	 */
	public static void parse(String[] args, StartupState state) throws CommandLineException {
		unrecognised.clear();
		if (args == null) {
			return;
		}
		int nArgs = args.length;
		int iArg = 0;
		while (iArg < nArgs) {
			String anArg = args[iArg++];
			CommandLineOption opt = find(anArg);
			if (opt == null) {
				if (anArg.startsWith("-")) {
					unrecognised.add(anArg);
					System.out.printf("Unrecognised command line option \"%s\" (ignored unless a plugin uses it). Run with -help for a list of options\n", anArg);
				}
				continue;
			}
			String[] values;
			if (opt.isOptionalValue()) {
				if (iArg < nArgs && !args[iArg].startsWith("-")) {
					values = new String[] {args[iArg++]};
				}
				else {
					values = new String[0];
				}
			}
			else {
				int nVal = opt.getNumValues();
				if (iArg + nVal > nArgs) {
					throw new CommandLineException(String.format("Command line option %s needs %d value(s): %s",
							anArg, nVal, opt.getUsage()));
				}
				values = new String[nVal];
				for (int i = 0; i < nVal; i++) {
					values[i] = args[iArg++];
				}
			}
			if (opt.getHandler() != null) {
				opt.getHandler().apply(opt, values, state);
			}
			if (state.exitNow) {
				return;
			}
		}
	}

	/**
	 * Look for the information options (-help and -version) and, if one is found,
	 * act on it. Called before anything else so that the output isn't mixed up with
	 * PAMGuard's normal start up messages.
	 * @param args command line arguments
	 * @return true if an information option was found, in which case PAMGuard should exit.
	 */
	public static boolean handleInfoOptions(String[] args) {
		if (args == null) {
			return false;
		}
		CommandLineOption help = find("-help");
		CommandLineOption version = find("-version");
		for (int i = 0; i < args.length; i++) {
			CommandLineOption opt = null;
			if (help != null && help.matches(args[i])) {
				opt = help;
			}
			else if (version != null && version.matches(args[i])) {
				opt = version;
			}
			if (opt == null) {
				continue;
			}
			String[] values = new String[0];
			if (i + 1 < args.length && !args[i+1].startsWith("-")) {
				values = new String[] {args[i+1]};
			}
			try {
				opt.getHandler().apply(opt, values, new StartupState());
			} catch (CommandLineException e) {
				System.out.println(e.getMessage());
			}
			return true;
		}
		return false;
	}

	/**
	 * Print the help as plain text.
	 * @param out where to print
	 * @param showHidden also show hidden (developer) options
	 */
	public static void printHelp(PrintStream out, boolean showHidden) {
		out.println("PAMGuard " + PamguardVersionInfo.version);
		out.println();
		out.println("Usage: java -jar Pamguard.jar [options]");
		out.println("Run with no options for the normal GUI. Options are not case sensitive.");
		out.println("Use -help json for a machine readable version of this help.");
		for (CommandLineOption.Category cat : CommandLineOption.Category.values()) {
			List<CommandLineOption> catOpts = new ArrayList<>();
			for (CommandLineOption opt : getOptions()) {
				if (opt.getCategory() == cat && (showHidden || !opt.isHidden())) {
					catOpts.add(opt);
				}
			}
			if (catOpts.isEmpty()) {
				continue;
			}
			out.println();
			out.println(cat.getTitle() + ":");
			for (CommandLineOption opt : catOpts) {
				String usage = opt.getUsage();
				if (!opt.getAliases().isEmpty()) {
					usage += "  (also " + String.join(", ", opt.getAliases()) + ")";
				}
				out.println("  " + usage);
				String desc = opt.getDescription();
				if (opt.isRepeatable()) {
					desc += " Can be used more than once.";
				}
				for (String line : wrap(desc, 72)) {
					out.println("      " + line);
				}
				if (opt.getExample() != null) {
					out.println("      e.g. " + opt.getExample());
				}
			}
		}
		out.println();
		out.println("Exit codes:");
		for (ExitCode code : ExitCode.values()) {
			out.printf("  %d  %s\n", code.getCode(), code.getDescription());
		}
		out.println();
		out.println("Examples:");
		out.println("  Process a folder of sound files without a GUI, then exit, writing a summary:");
		out.println("    java -jar Pamguard.jar -nogui -psf config.psfx -wavfilefolder /data/wav -binaryfolder /data/bin \\");
		out.println("         -databasefile /data/db.sqlite3 -summary /data/run.json -autostart -autoexit");
		out.println("  Change a setting for this run only:");
		out.println("    java -jar Pamguard.jar -nogui -psf config.psfx -set \"Click Detector.dbThreshold=12\" ...");
		out.println("  List every module's settings as JSON:");
		out.println("    java -jar Pamguard.jar -nogui -psf config.psfx -describe settings.json");
		out.println();
	}

	/**
	 * @return the help as a JSON string.
	 */
	public static String getHelpJson() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("program", "PAMGuard");
		root.put("version", PamguardVersionInfo.version);
		root.put("usage", "java -jar Pamguard.jar [options]");
		root.put("caseSensitive", false);
		List<Object> optList = new ArrayList<>();
		for (CommandLineOption opt : getOptions()) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("name", opt.getName());
			if (!opt.getAliases().isEmpty()) {
				o.put("aliases", opt.getAliases());
			}
			o.put("category", opt.getCategory().name().toLowerCase());
			List<String> vals = new ArrayList<>();
			for (String v : opt.getValueNames()) {
				vals.add(v);
			}
			o.put("values", vals);
			o.put("optionalValue", opt.isOptionalValue());
			o.put("repeatable", opt.isRepeatable());
			o.put("description", opt.getDescription());
			if (opt.getExample() != null) {
				o.put("example", opt.getExample());
			}
			if (opt.isHidden()) {
				o.put("hidden", true);
			}
			optList.add(o);
		}
		root.put("options", optList);
		List<Object> codes = new ArrayList<>();
		for (ExitCode code : ExitCode.values()) {
			Map<String, Object> c = new LinkedHashMap<>();
			c.put("code", code.getCode());
			c.put("name", code.name());
			c.put("description", code.getDescription());
			codes.add(c);
		}
		root.put("exitCodes", codes);
		return toJson(root);
	}

	/**
	 * Write an object (normally made of Maps, Lists and simple values) as indented JSON.
	 * @param object object
	 * @return JSON string
	 */
	public static String toJson(Object object) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			return mapper.writeValueAsString(object);
		}
		catch (Exception e) {
			return "{\"error\": \"" + e.getMessage() + "\"}";
		}
	}

	private static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		if (text == null) {
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			if (line.length() > 0 && line.length() + word.length() + 1 > width) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}
}
