package PamController.startup;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Display (UI) scaling for PAMGuard on Linux.
 * <p>
 * Java only scales Swing and JavaFX on Linux if it's told to, through the
 * sun.java2d.uiScale (Swing / AWT) and glass.gtk.uiScale (JavaFX) system properties,
 * or if it can work the scale out from GDK_SCALE or the GNOME settings. Some
 * systems get this right and some don't, so the user can choose the scale in the
 * start up dialog. The choice is saved in a small text file in the PAMGuard
 * settings folder (~/Pamguard/uiScale.txt) which holds either "auto" or a whole
 * number scale factor. The Linux launcher script reads the same file.
 * <p>
 * The properties are only read when AWT and JavaFX start, so {@link #applySavedScale()}
 * must be called at the very start of main, before anything touches AWT, Swing or
 * JavaFX. Changing the scale once PAMGuard is running means a restart, see
 * {@link #restartPamguard()}.
 * <p>
 * Java on Linux only supports whole number scale factors, so 1.5 behaves as 1.
 * <p>
 * Nothing here does anything on Windows or macOS, which scale the display themselves.
 *
 * @author Jamie Macaulay
 */
public class LinuxUIScale {

	/**
	 * Swing / AWT scale property.
	 */
	public static final String AWT_SCALE_PROPERTY = "sun.java2d.uiScale";

	/**
	 * JavaFX scale property on Linux (GTK).
	 */
	public static final String FX_SCALE_PROPERTY = "glass.gtk.uiScale";

	/**
	 * Value saved for automatic scaling.
	 */
	public static final String AUTO = "auto";

	/**
	 * Saved scale = 0 means automatic.
	 */
	public static final int SCALE_AUTO = 0;

	/**
	 * Largest scale offered.
	 */
	public static final int MAX_SCALE = 4;

	/**
	 * Environment variable set by the Linux launcher script to the exit code which
	 * asks it to start PAMGuard again. If it's not set, PAMGuard restarts itself.
	 */
	public static final String RESTART_CODE_ENV = "PAMGUARD_RESTART_CODE";

	/**
	 * Environment variable which forces a scale in the launcher script, in which
	 * case the saved scale isn't used.
	 */
	public static final String SCALE_ENV = "PAMGUARD_UI_SCALE";

	private static final String SCALE_FILE = "uiScale.txt";

	/**
	 * The scale the AWT property had when PAMGuard started, before this class
	 * changed anything, i.e. what came from the command line or launcher.
	 */
	private static String launchScale;

	private LinuxUIScale() {
	}

	/**
	 * @return true if running on Linux.
	 */
	public static boolean isLinux() {
		String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().startsWith("linux");
	}

	/**
	 * @return the file the scale choice is saved in.
	 */
	public static File getScaleFile() {
		/*
		 * Same folder as pamguard.Pamguard.getSettingsFolder(), without calling it
		 * so that this can safely run before anything else in main.
		 */
		return new File(System.getProperty("user.home") + File.separator + "Pamguard" + File.separator + SCALE_FILE);
	}

	/**
	 * Get the saved scale.
	 * @return the saved whole number scale, or {@link #SCALE_AUTO} for automatic or
	 * if nothing (or nothing sensible) is saved.
	 */
	public static int getSavedScale() {
		File f = getScaleFile();
		if (!f.exists()) {
			return SCALE_AUTO;
		}
		try {
			List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
			for (String line : lines) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				return parseScale(line);
			}
		}
		catch (Exception e) {
			System.out.println("LinuxUIScale: unable to read " + f + ": " + e.getMessage());
		}
		return SCALE_AUTO;
	}

	/**
	 * Save the scale.
	 * @param scale whole number scale, or {@link #SCALE_AUTO} for automatic.
	 * @return true if saved OK.
	 */
	public static boolean saveScale(int scale) {
		File f = getScaleFile();
		try {
			if (f.getParentFile() != null) {
				f.getParentFile().mkdirs();
			}
			String value = scale <= SCALE_AUTO ? AUTO : Integer.toString(scale);
			String text = "# PAMGuard display scale on Linux: auto, or a whole number scale factor (1, 2, 3...)\n"
					+ "# Set from the PAMGuard start up dialog and read by the PAMGuard launcher.\n"
					+ value + "\n";
			Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
			return true;
		}
		catch (IOException e) {
			System.out.println("LinuxUIScale: unable to save " + f + ": " + e.getMessage());
			return false;
		}
	}

	/**
	 * Parse a scale value.
	 * @param value e.g. "auto", "2", "2x", "200%".
	 * @return whole number scale (rounded), or {@link #SCALE_AUTO}.
	 */
	public static int parseScale(String value) {
		if (value == null) {
			return SCALE_AUTO;
		}
		value = value.trim().toLowerCase();
		if (value.isEmpty() || value.equals(AUTO)) {
			return SCALE_AUTO;
		}
		try {
			double d;
			if (value.endsWith("%")) {
				d = Double.parseDouble(value.substring(0, value.length() - 1).trim()) / 100.;
			}
			else if (value.endsWith("x")) {
				d = Double.parseDouble(value.substring(0, value.length() - 1).trim());
			}
			else {
				d = Double.parseDouble(value);
			}
			int scale = (int) Math.round(d);
			if (scale < 1) {
				return SCALE_AUTO;
			}
			return Math.min(scale, MAX_SCALE);
		}
		catch (NumberFormatException e) {
			return SCALE_AUTO;
		}
	}

	/**
	 * Apply the saved scale. Call at the very start of main, before AWT, Swing or
	 * JavaFX are used. A saved whole number scale overrides anything passed on the
	 * command line. With automatic scaling, the command line (or launcher script) value is kept,
	 * and if there isn't one, Java works the scale out for itself.
	 * <p>
	 * The JavaFX scale is set to match the Swing one unless it was set on the command line.
	 */
	public static void applySavedScale() {
		launchScale = System.getProperty(AWT_SCALE_PROPERTY);
		if (!isLinux()) {
			return;
		}
		int saved = getSavedScale();
		if (saved != SCALE_AUTO && System.getenv(SCALE_ENV) == null) {
			System.setProperty(AWT_SCALE_PROPERTY, Integer.toString(saved));
			System.setProperty(FX_SCALE_PROPERTY, Integer.toString(saved));
		}
		else {
			String awt = System.getProperty(AWT_SCALE_PROPERTY);
			if (awt != null && System.getProperty(FX_SCALE_PROPERTY) == null) {
				int s = parseScale(awt);
				if (s != SCALE_AUTO) {
					System.setProperty(FX_SCALE_PROPERTY, Integer.toString(s));
				}
			}
		}
	}

	/**
	 * @return a line for the log saying which scale is in use and why.
	 */
	public static String getScaleDescription() {
		int saved = getSavedScale();
		String inUse = System.getProperty(AWT_SCALE_PROPERTY);
		if (System.getenv(SCALE_ENV) != null) {
			return String.format("Linux display scale %s (from %s)", inUse, SCALE_ENV);
		}
		if (saved != SCALE_AUTO) {
			return String.format("Linux display scale %d (saved in %s)", saved, getScaleFile());
		}
		if (launchScale != null) {
			return String.format("Linux display scale %s (automatic, from the command line or launcher)", launchScale);
		}
		return String.format("Linux display scale automatic (%s)", inUse == null ? "chosen by Java" : inUse);
	}

	/**
	 * Get the scale the display is actually using. This starts AWT if it isn't
	 * already running, so only call it once {@link #applySavedScale()} has been called.
	 * @return the scale, e.g. 1 or 2, or 0 if it can't be found (e.g. headless).
	 */
	public static double getCurrentScale() {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return 0;
			}
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice().getDefaultConfiguration();
			return gc.getDefaultTransform().getScaleX();
		}
		catch (Throwable e) {
			return 0;
		}
	}

	/**
	 * @return true if the scale needs PAMGuard to restart, i.e. a saved scale is
	 * different to the one the display is using now.
	 * @param newScale the new scale, or {@link #SCALE_AUTO}.
	 */
	public static boolean needsRestart(int newScale) {
		if (newScale == SCALE_AUTO) {
			/*
			 * Can't tell what automatic will give without restarting, except when
			 * nothing was forcing the scale this time round.
			 */
			return getSavedScale() != SCALE_AUTO;
		}
		double current = getCurrentScale();
		return current <= 0 || Math.abs(current - newScale) > 0.01;
	}

	/**
	 * Close PAMGuard and start it again with the same command line, so that a new
	 * scale is used. If PAMGuard was started by the Linux launcher script, PAMGuard
	 * exits with the code the script uses to start PAMGuard again (this keeps the
	 * terminal window open). Otherwise a new PAMGuard is started before this one exits.
	 * <p>
	 * Only call this before any settings or data are open, e.g. from the start up dialog.
	 * @return false if PAMGuard couldn't be restarted, otherwise it doesn't return.
	 */
	public static boolean restartPamguard() {
		String restartCode = System.getenv(RESTART_CODE_ENV);
		if (restartCode != null) {
			try {
				int code = Integer.parseInt(restartCode.trim());
				System.out.println("Restarting PAMGuard to change the display scale");
				System.exit(code);
			}
			catch (NumberFormatException e) {
				// fall through and restart directly.
			}
		}
		List<String> command = getCommandLine();
		if (command == null || command.isEmpty()) {
			return false;
		}
		try {
			System.out.println("Restarting PAMGuard to change the display scale: " + String.join(" ", command));
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(new File(System.getProperty("user.dir")));
			pb.inheritIO();
			pb.start();
		}
		catch (IOException e) {
			System.out.println("Unable to restart PAMGuard: " + e.getMessage());
			return false;
		}
		System.exit(0);
		return true;
	}

	/**
	 * Get the full command line this PAMGuard was started with.
	 * @return the command and its arguments, or null.
	 */
	private static List<String> getCommandLine() {
		// on Linux this is the exact command line, including all the JVM options.
		File cmdLine = new File("/proc/self/cmdline");
		if (cmdLine.exists()) {
			try {
				byte[] bytes = Files.readAllBytes(cmdLine.toPath());
				ArrayList<String> parts = new ArrayList<>();
				int start = 0;
				for (int i = 0; i < bytes.length; i++) {
					if (bytes[i] == 0) {
						parts.add(new String(bytes, start, i - start, StandardCharsets.UTF_8));
						start = i + 1;
					}
				}
				if (start < bytes.length) {
					parts.add(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8));
				}
				if (!parts.isEmpty()) {
					return parts;
				}
			}
			catch (IOException e) {
				// try the process handle instead.
			}
		}
		ProcessHandle.Info info = ProcessHandle.current().info();
		if (info.command().isPresent() && info.arguments().isPresent()) {
			ArrayList<String> parts = new ArrayList<>();
			parts.add(info.command().get());
			for (String a : info.arguments().get()) {
				parts.add(a);
			}
			return parts;
		}
		return null;
	}

	/**
	 * Work out a scale from the desktop settings, the same way the launcher script
	 * does: GDK_SCALE, then Xft.dpi (96 dpi = 1), then the GNOME scaling factor.
	 * For information only - Java does its own detection when no scale is set.
	 * @return the detected scale, or {@link #SCALE_AUTO} if nothing was found.
	 */
	public static int detectDesktopScale() {
		int s = parseScale(System.getenv("GDK_SCALE"));
		if (s != SCALE_AUTO) {
			return s;
		}
		String dpi = runAndFind(new String[] {"xrdb", "-query"}, "Xft.dpi:");
		if (dpi != null) {
			try {
				int d = (int) Math.round(Double.parseDouble(dpi) / 96.);
				if (d >= 1) {
					return Math.min(d, MAX_SCALE);
				}
			}
			catch (NumberFormatException e) {
			}
		}
		String gnome = runAndFind(new String[] {"gsettings", "get", "org.gnome.desktop.interface", "scaling-factor"}, "");
		if (gnome != null) {
			gnome = gnome.replace("uint32", "").trim();
			s = parseScale(gnome);
			if (s != SCALE_AUTO) {
				return s;
			}
		}
		return SCALE_AUTO;
	}

	/**
	 * Run a command and return what follows the first line starting with a prefix.
	 */
	private static String runAndFind(String[] command, String prefix) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.startsWith(prefix)) {
						return line.substring(prefix.length()).trim();
					}
				}
			}
		}
		catch (Exception e) {
			// command not there - nothing found.
		}
		return null;
	}
}
