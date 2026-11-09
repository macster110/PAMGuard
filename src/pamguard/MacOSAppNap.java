package pamguard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import PamController.PamController;

/**
 * Stops macOS from throttling PAMGuard.
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
 * LSAppNapIsDisabled is set in the app's Info.plist, see
 * build/macos/configure_info_plist.sh.
 *
 * @author Jamie Macaulay
 */
public class MacOSAppNap {

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
	private static Pointer activity;

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
	 * @param runMode PAMGuard run mode. Real time modes also stop the computer
	 * sleeping when idle.
	 */
	public static synchronized void disableAppNap(int runMode) {
		if (!MacOSIntegration.isMac() || activity != null) {
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
				activity = token;
				System.out.println("macOS App Nap disabled" + (realTime ? " and idle sleep prevented" : ""));
			}
		}
		catch (Throwable e) {
			System.out.println("macOS App Nap: unable to disable App Nap: " + e.getMessage());
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
		if (MacOSIntegration.getAppBundle() == null || !new File("/usr/bin/defaults").exists()) {
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
