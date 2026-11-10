package pamguard;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import PamController.PamController;

/**
 * macOS desktop integration.
 * <p>
 * On macOS a double clicked document (or a document chosen from the Finder's
 * "Open With" menu) is not passed to the program on the command line. Instead it
 * arrives as an "open file" event from the Desktop. This class
 * <ul>
 * <li>picks up a file which was used to launch PAMGuard, so that a .psfx file
 * starts PAMGuard in normal mode and a .sqlite3 database starts it in viewer
 * mode, without the start up dialog,</li>
 * <li>launches a new instance of PAMGuard for any file opened once PAMGuard is
 * already running, so that the running configuration is never disturbed,</li>
 * <li>adds "Open additional instance of PAMGuard" to the Dock icon's menu.
 * macOS only ever activates the running copy of an application when it's double
 * clicked in the Finder, so this is how a user starts a second instance.</li>
 * <li>stops App Nap from slowing PAMGuard down when its windows are hidden or
 * the computer is idle, see {@link #disableAppNap(int)}.</li>
 * </ul>
 * The file type associations and LSAppNapIsDisabled are declared in the app's
 * Info.plist, see build/macos/configure_info_plist.sh.
 *
 * @author Jamie Macaulay
 */
public class MacOSIntegration {

	/**
	 * Command line option to open a file at start up. The run mode is chosen from
	 * the file type: normal mode for a settings file, viewer mode for a database.
	 */
	public static final String OPENFILE = "-openfile";

	/**
	 * Text of the Dock menu item which launches another instance.
	 */
	public static final String NEW_INSTANCE_TEXT = "Open additional instance of PAMGuard";

	/**
	 * Files received before start up has decided what to open.
	 */
	private static final LinkedBlockingQueue<File> launchFiles = new LinkedBlockingQueue<>();

	/**
	 * Set once the run mode has been chosen. Files opened after this go to a new
	 * instance.
	 */
	private static volatile boolean startupComplete = false;

	private static boolean installed = false;

	/**
	 * @return true if running on macOS.
	 */
	public static boolean isMac() {
		String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().startsWith("mac");
	}

	/**
	 * Find the .app bundle PAMGuard is running from. The jar files live in
	 * PAMGuard.app/Contents/Java/classpath.
	 * @return the .app folder or null if not running from an app bundle.
	 */
	public static File getAppBundle() {
		try {
			URL codeSource = Pamguard.class.getProtectionDomain().getCodeSource().getLocation();
			File f = new File(codeSource.toURI());
			while (f != null) {
				if (f.getName().toLowerCase().endsWith(".app") && f.isDirectory()) {
					return f;
				}
				f = f.getParentFile();
			}
		}
		catch (Exception e) {
			System.out.println("Unable to find the PAMGuard app bundle: " + e.getMessage());
		}
		return null;
	}

	/**
	 * Install the open file handler and the Dock menu. Does nothing if not on a
	 * Mac or if there is no display.
	 */
	public static synchronized void install() {
		if (installed || !isMac() || GraphicsEnvironment.isHeadless()) {
			return;
		}
		installed = true;
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
					desktop.setOpenFileHandler(e -> filesOpened(e.getFiles()));
				}
			}
		}
		catch (Throwable e) {
			System.out.println("Unable to install the macOS open file handler: " + e.getMessage());
		}
		SwingUtilities.invokeLater(() -> installDockMenu());
	}

	private static void installDockMenu() {
		try {
			if (!Taskbar.isTaskbarSupported()) {
				return;
			}
			Taskbar taskbar = Taskbar.getTaskbar();
			if (!taskbar.isSupported(Taskbar.Feature.MENU)) {
				return;
			}
			PopupMenu dockMenu = new PopupMenu();
			MenuItem newInstance = new MenuItem(NEW_INSTANCE_TEXT);
			newInstance.addActionListener(e -> openNewInstance(null));
			dockMenu.add(newInstance);
			taskbar.setMenu(dockMenu);
		}
		catch (Throwable e) {
			System.out.println("Unable to add the PAMGuard Dock menu: " + e.getMessage());
		}
	}

	/**
	 * Called by the Desktop when the user opens files with PAMGuard.
	 */
	private static void filesOpened(List<File> files) {
		if (files == null) {
			return;
		}
		for (File file : files) {
			System.out.println("macOS open file request: " + file.getAbsolutePath());
			if (getFileRunMode(file) < 0) {
				System.out.println("PAMGuard can't open files of this type: " + file.getName());
				continue;
			}
			synchronized (launchFiles) {
				if (!startupComplete) {
					launchFiles.add(file);
					continue;
				}
			}
			// PAMGuard is already running, so open the file in a new instance.
			openNewInstance(file);
		}
	}

	/**
	 * Wait a short time for an open file event. When PAMGuard is launched by
	 * opening a file the event arrives just after start up.
	 * @param maxWaitMillis maximum time to wait.
	 * @return the file used to launch PAMGuard or null.
	 */
	public static File waitForLaunchFile(long maxWaitMillis) {
		if (!installed) {
			return null;
		}
		try {
			return launchFiles.poll(maxWaitMillis, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			return null;
		}
	}

	/**
	 * Called once the run mode has been chosen. Any file opened from now on (or
	 * already received but not used) is opened in a new instance of PAMGuard.
	 */
	public static void setStartupComplete() {
		List<File> extra = new ArrayList<>();
		synchronized (launchFiles) {
			startupComplete = true;
			launchFiles.drainTo(extra);
		}
		for (File f : extra) {
			openNewInstance(f);
		}
	}

	/**
	 * Work out which run mode to use for a file.
	 * @param file settings file or database.
	 * @return PamController.RUN_NORMAL for a psfx file, PamController.RUN_PAMVIEW
	 * for a Sqlite database or -1 for anything else.
	 */
	public static int getFileRunMode(File file) {
		if (file == null) {
			return -1;
		}
		String name = file.getName().toLowerCase();
		if (name.endsWith(".psfx") || name.endsWith(".psf")) {
			return PamController.RUN_NORMAL;
		}
		if (name.endsWith(".sqlite3") || name.endsWith(".sqlite")) {
			return PamController.RUN_PAMVIEW;
		}
		return -1;
	}

	/**
	 * Launch a new instance of PAMGuard.
	 * @param file file to open in the new instance, or null to start normally
	 * (with the start up dialog).
	 * @return true if the new process was started.
	 */
	public static boolean openNewInstance(File file) {
		List<String> command = new ArrayList<>();
		File bundle = getAppBundle();
		if (bundle != null) {
			// -n makes macOS start a new copy of the app rather than activate this one.
			command.add("open");
			command.add("-n");
			command.add(bundle.getAbsolutePath());
			if (file != null) {
				command.add("--args");
				command.add(OPENFILE);
				command.add(file.getAbsolutePath());
			}
		}
		else {
			// not running from an app bundle (e.g. a development build), so re-run this command.
			ProcessHandle.Info info = ProcessHandle.current().info();
			Optional<String> exe = info.command();
			Optional<String[]> args = info.arguments();
			if (exe.isEmpty() || args.isEmpty()) {
				System.out.println("Unable to launch a new instance of PAMGuard: command line unknown");
				return false;
			}
			command.add(exe.get());
			for (String a : args.get()) {
				command.add(a);
			}
			if (file != null) {
				command.add(OPENFILE);
				command.add(file.getAbsolutePath());
			}
		}
		System.out.println("Launching new PAMGuard instance: " + String.join(" ", command));
		try {
			new ProcessBuilder(command).inheritIO().start();
			return true;
		}
		catch (IOException e) {
			System.out.println("Unable to launch a new instance of PAMGuard: " + e.getMessage());
			return false;
		}
	}

	/*
	 * ---------------- App Nap ----------------
	 */

	/**
	 * Bundle identifier of the PAMGuard app (see the macos-profile in pom.xml).
	 */
	public static final String BUNDLE_ID = "org.pamguard.PAMGuard";

	/*
	 * NSActivityOptions values from Foundation/NSProcessInfo.h
	 */
	private static final long NSActivityIdleSystemSleepDisabled = 1L << 20;
	private static final long NSActivityUserInitiated = 0x00FFFFFFL | NSActivityIdleSystemSleepDisabled;
	private static final long NSActivityUserInitiatedAllowingIdleSystemSleep = NSActivityUserInitiated & ~NSActivityIdleSystemSleepDisabled;
	private static final long NSActivityLatencyCritical = 0xFF00000000L;

	/**
	 * The activity token. Keeping it (and never ending the activity) keeps App
	 * Nap off until PAMGuard exits.
	 */
	private static Pointer appNapActivity;

	/**
	 * The Objective C runtime. objc_msgSend is mapped once for each set of
	 * argument types needed. On Apple silicon it must never be called as a
	 * variadic function, which these fixed signatures guarantee.
	 */
	private interface ObjC extends Library {
		Pointer objc_getClass(String className);
		Pointer sel_registerName(String selectorName);
		Pointer objc_msgSend(Pointer receiver, Pointer selector);
		Pointer objc_msgSend(Pointer receiver, Pointer selector, String utf8String);
		Pointer objc_msgSend(Pointer receiver, Pointer selector, long options, Pointer reason);
	}

	/**
	 * Stop macOS throttling PAMGuard. Does nothing on other operating systems.
	 * <p>
	 * macOS "App Nap" slows an application right down when its windows are hidden,
	 * minimised or covered, or when the computer has not been used for a while. The
	 * process is given a low CPU and IO priority and its timers are delayed, so
	 * real time processing falls behind. A process can opt out by telling macOS that
	 * it is doing user initiated, latency critical work with
	 * {@code [[NSProcessInfo processInfo] beginActivityWithOptions:reason:]}. The
	 * activity is held for the lifetime of the program.
	 * <p>
	 * For real time operation the activity also stops the computer going to sleep
	 * when it is idle (the display can still sleep). In viewer mode the computer is
	 * allowed to sleep as normal.
	 * <p>
	 * As a fallback the NSAppSleepDisabled user default is also set for the PAMGuard
	 * app bundle, which disables App Nap from the next launch. This is the same as
	 * running {@code defaults write org.pamguard.PAMGuard NSAppSleepDisabled -bool YES}.
	 * @param runMode PAMGuard run mode. Real time modes also stop the computer
	 * sleeping when idle.
	 */
	public static synchronized void disableAppNap(int runMode) {
		if (!isMac() || appNapActivity != null) {
			return;
		}
		boolean realTime = runMode == PamController.RUN_NORMAL || runMode == PamController.RUN_NETWORKRECEIVER;
		try {
			ObjC objc = Native.load("objc", ObjC.class);
			Pointer processInfo = objc.objc_msgSend(objc.objc_getClass("NSProcessInfo"), objc.sel_registerName("processInfo"));
			Pointer reason = objc.objc_msgSend(objc.objc_getClass("NSString"), objc.sel_registerName("stringWithUTF8String:"),
					realTime ? "PAMGuard real time acoustic processing" : "PAMGuard data processing");
			long options = (realTime ? NSActivityUserInitiated : NSActivityUserInitiatedAllowingIdleSystemSleep) | NSActivityLatencyCritical;
			Pointer token = objc.objc_msgSend(processInfo, objc.sel_registerName("beginActivityWithOptions:reason:"), options, reason);
			if (token == null) {
				System.out.println("macOS App Nap: unable to start activity");
			}
			else {
				// the token is autoreleased, so retain it to keep the activity running.
				objc.objc_msgSend(token, objc.sel_registerName("retain"));
				appNapActivity = token;
				System.out.println("macOS App Nap disabled" + (realTime ? " and idle sleep prevented" : ""));
			}
		}
		catch (Throwable e) {
			System.out.println("macOS App Nap: unable to disable App Nap: " + e.getMessage());
			try {
				// a stale copy of JNA earlier on the class path is the usual cause
				System.out.println("macOS App Nap: JNA loaded from "
						+ com.sun.jna.Native.class.getProtectionDomain().getCodeSource().getLocation());
			}
			catch (Throwable e2) {
			}
		}
		// fallback for the next launch in case the activity didn't work.
		Thread t = new Thread(() -> setAppSleepDisabledDefault(), "macOS App Nap default");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Set NSAppSleepDisabled in the PAMGuard app's user defaults if it isn't set
	 * already. Only done when running from the app bundle since a development
	 * build runs as a plain java process.
	 */
	private static void setAppSleepDisabledDefault() {
		if (getAppBundle() == null || !new File("/usr/bin/defaults").exists()) {
			return;
		}
		String current = runDefaults("read", BUNDLE_ID, "NSAppSleepDisabled");
		if (current != null && current.trim().equals("1")) {
			return;
		}
		if (runDefaults("write", BUNDLE_ID, "NSAppSleepDisabled", "-bool", "YES") != null) {
			System.out.println("macOS App Nap: set NSAppSleepDisabled for " + BUNDLE_ID);
		}
	}

	/**
	 * Run the defaults command.
	 * @return the output, or null if the command failed.
	 */
	private static String runDefaults(String... args) {
		String[] command = new String[args.length + 1];
		command[0] = "/usr/bin/defaults";
		System.arraycopy(args, 0, command, 1, args.length);
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			String out;
			try (InputStream is = p.getInputStream()) {
				out = new String(is.readAllBytes());
			}
			if (!p.waitFor(10, TimeUnit.SECONDS)) {
				p.destroy();
				return null;
			}
			return p.exitValue() == 0 ? out : null;
		}
		catch (IOException | InterruptedException e) {
			return null;
		}
	}
}
