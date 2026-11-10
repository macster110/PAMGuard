package pamguard.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Description of a single PAMGuard command line option. Every option PAMGuard
 * understands should be described by one of these and registered with
 * {@link CommandLineOptions}, so that the help text (plain text or JSON) is always
 * complete and up to date. The JSON help in particular is meant to let scripts and
 * AI assistants discover what PAMGuard can do without reading the source code.
 * <p>
 * An option has a name (e.g. -wavfilefolder), optional aliases, zero or more
 * values (e.g. &lt;folder&gt;), a description and a {@link Handler} which is called
 * when the option is found on the command line.
 *
 * @author PAMGuard
 *
 */
public class CommandLineOption {

	/**
	 * Categories used to group options in the help.
	 */
	public enum Category {
		INFO("Information"),
		RUNMODE("Run mode"),
		GUI("User interface"),
		SETTINGS("Settings"),
		INPUT_OUTPUT("Input and output"),
		PROCESSING("Batch processing and automation"),
		VIEWER("Viewer mode"),
		NETWORK("Network control and data sending"),
		LOGGING("Logging and debugging"),
		DEVELOPER("Developer options");

		private String title;

		private Category(String title) {
			this.title = title;
		}

		public String getTitle() {
			return title;
		}
	}

	/**
	 * Called when an option is found on the command line.
	 */
	public interface Handler {
		/**
		 * Act on an option.
		 * @param option the option (useful when one handler serves several options).
		 * @param values the values that followed the option on the command line. There
		 * will be exactly {@link CommandLineOption#getNumValues()} of them, or for an
		 * option with an optional value, zero or one.
		 * @param state start up state which handlers in the main PAMGuard class can modify.
		 * @throws CommandLineException if a value is invalid.
		 */
		public void apply(CommandLineOption option, String[] values, StartupState state) throws CommandLineException;
	}

	private String name;

	private List<String> aliases = new ArrayList<>();

	private String[] valueNames = new String[0];

	private boolean optionalValue = false;

	private String description;

	private Category category;

	private boolean hidden = false;

	private boolean repeatable = false;

	private String example;

	private Handler handler;

	/**
	 * Create an option that takes no values.
	 * @param name option name including leading -, e.g. "-nogui"
	 * @param category help category
	 * @param description one or two sentences shown in the help
	 */
	public CommandLineOption(String name, Category category, String description) {
		this.name = name;
		this.category = category;
		this.description = description;
	}

	/**
	 * Set the names of the values which follow the option, e.g. "folder". The number
	 * of value names is the number of values the option consumes.
	 * @param valueNames value names
	 * @return this, for chaining
	 */
	public CommandLineOption values(String... valueNames) {
		this.valueNames = valueNames == null ? new String[0] : valueNames;
		return this;
	}

	/**
	 * Single value which may be left out. The value is only taken if the next
	 * argument doesn't start with a '-'.
	 * @param valueName name of the value
	 * @return this, for chaining
	 */
	public CommandLineOption optionalValue(String valueName) {
		this.valueNames = new String[] {valueName};
		this.optionalValue = true;
		return this;
	}

	/**
	 * Other names for the same option.
	 * @param aliases alternative names, including leading -.
	 * @return this, for chaining
	 */
	public CommandLineOption aliases(String... aliases) {
		this.aliases.addAll(Arrays.asList(aliases));
		return this;
	}

	/**
	 * Hide the option from the normal help. Hidden options are still listed in the
	 * JSON help (marked hidden) and with -help all.
	 * @return this, for chaining
	 */
	public CommandLineOption hidden() {
		this.hidden = true;
		return this;
	}

	/**
	 * The option can be given more than once, e.g. -offlinetask.
	 * @return this, for chaining
	 */
	public CommandLineOption repeatable() {
		this.repeatable = true;
		return this;
	}

	/**
	 * An example of the option in use, shown in the help.
	 * @param example example, e.g. "-set \"Click Detector.threshold=12\""
	 * @return this, for chaining
	 */
	public CommandLineOption example(String example) {
		this.example = example;
		return this;
	}

	/**
	 * Set what happens when the option is found.
	 * @param handler handler
	 * @return this, for chaining
	 */
	public CommandLineOption handler(Handler handler) {
		this.handler = handler;
		return this;
	}

	/**
	 * Does this option match a command line argument. Matching ignores case, as it
	 * always has in PAMGuard.
	 * @param arg command line argument
	 * @return true if it matches the name or an alias.
	 */
	public boolean matches(String arg) {
		if (arg == null) {
			return false;
		}
		if (name.equalsIgnoreCase(arg)) {
			return true;
		}
		for (String alias : aliases) {
			if (alias.equalsIgnoreCase(arg)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the option name, including leading -
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return other names for the option
	 */
	public List<String> getAliases() {
		return aliases;
	}

	/**
	 * @return names of the values which follow the option
	 */
	public String[] getValueNames() {
		return valueNames;
	}

	/**
	 * @return number of values which follow the option (maximum number if optional)
	 */
	public int getNumValues() {
		return valueNames.length;
	}

	/**
	 * @return true if the value can be left out
	 */
	public boolean isOptionalValue() {
		return optionalValue;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isHidden() {
		return hidden;
	}

	public boolean isRepeatable() {
		return repeatable;
	}

	public String getExample() {
		return example;
	}

	public Handler getHandler() {
		return handler;
	}

	/**
	 * @return the option as it would appear in a usage line, e.g. "-psf &lt;file&gt;"
	 */
	public String getUsage() {
		StringBuilder sb = new StringBuilder(name);
		for (String v : valueNames) {
			if (optionalValue) {
				sb.append(" [").append(v).append("]");
			}
			else {
				sb.append(" <").append(v).append(">");
			}
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return getUsage();
	}
}
