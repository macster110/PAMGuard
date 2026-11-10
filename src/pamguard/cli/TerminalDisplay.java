package pamguard.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Acquisition.AcquisitionControl;
import Acquisition.DaqSystem;
import Acquisition.FileInputSystem;
import Acquisition.FolderInputSystem;
import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamGUIManager;
import PamController.PamguardVersionInfo;
import PamController.command.TerminalController;
import PamController.PamSettingManager;
import PamUtils.PamCalendar;
import PamguardMVC.PamDataBlock;

/**
 * A text display for PAMGuard running in a terminal without a GUI (-nogui), a bit like
 * the status bar at the bottom of the Swing GUI. It shows progress through the sound
 * files and folder when processing files, or the sound level on each channel when
 * acquiring in real time, with the number of detections so far. Press l to see the log
 * (everything PAMGuard prints) and s to go back to the progress display.
 * <p>
 * It only starts when PAMGuard's output is an interactive terminal, so scripts, pipes
 * and the MCP server, which read the output, get the plain log. Use -progress on or
 * -progress off to override that.
 * <p>
 * The progress display is drawn on the terminal's alternate screen, so the log stays on
 * the normal screen, and output that arrives while the progress display is showing is
 * printed when you switch back to the log.
 *
 * @author Jamie Macaulay
 */
public class TerminalDisplay {

	public static final String PROGRESS = "-progress";

	private static final String ESC = "\033[";

	/**
	 * Number of log lines kept to print when switching back to the log.
	 */
	private static final int MAXHIDDENLINES = 2000;

	private static String progressMode = "auto";

	private static TerminalDisplay instance;

	private final PrintStream realOut, realErr;

	private final Object lock = new Object();

	private volatile boolean showingLog = false;

	/**
	 * Lines printed while the progress display was showing.
	 */
	private final ArrayDeque<String> hiddenLines = new ArrayDeque<>();

	private int droppedLines = 0;

	private String lastLine = "";

	private ScheduledExecutorService timer;

	private String savedStty;

	private final long startTime = System.currentTimeMillis();

	private int columns = 100, rows = 30;

	private long lastSizeCheck = 0;

	private long lastDataTime, lastWallTime;

	private double speed = 0;

	private double folderFraction = 0;

	private int lastFileCount = 0;

	/**
	 * Width of the text to the right of the progress bars, so they line up. 
	 */
	private static final int RIGHTWIDTH = 24;

	private TerminalDisplay(PrintStream realOut, PrintStream realErr) {
		this.realOut = realOut;
		this.realErr = realErr;
	}

	static void registerOptions() {
		CommandLineOptions.register(new CommandLineOption(PROGRESS, CommandLineOption.Category.GUI,
				"With -nogui in a terminal, show a progress display (file and folder progress, or sound levels "
				+ "in real time, and detection counts) instead of the log. Press l for the log and s for progress. "
				+ "auto (the default) shows it only when the output is an interactive terminal, so scripts get the plain log.")
				.values("auto|on|off")
				.example("-progress off")
				.handler((opt, values, state) -> {
					String v = values[0].toLowerCase();
					if (!v.equals("auto") && !v.equals("on") && !v.equals("off")) {
						throw new CommandLineException("-progress must be auto, on or off, not " + values[0]);
					}
					progressMode = v;
				}));
	}

	/**
	 * Start the progress display if PAMGuard is running without a GUI in an interactive
	 * terminal (or -progress on was given).
	 */
	public static void startIfWanted() {
		if (instance != null || progressMode.equals("off") || PamGUIManager.getGUIType() != PamGUIManager.NOGUI) {
			return;
		}
		if (progressMode.equals("auto") && !isInteractiveTerminal()) {
			return;
		}
		TerminalDisplay td = new TerminalDisplay(System.out, System.err);
		if (td.start()) {
			instance = td;
		}
	}

	/**
	 * @return true if standard input and output are an interactive terminal.
	 */
	private static boolean isInteractiveTerminal() {
		java.io.Console console = System.console();
		if (console == null) {
			return false;
		}
		try {
			// Java 22 and later return a console even when output is redirected, and say if it's a terminal.
			Method isTerminal = console.getClass().getMethod("isTerminal");
			return (Boolean) isTerminal.invoke(console);
		}
		catch (Exception e) {
			return true;
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private boolean start() {
		if (!setupTerminal()) {
			return false;
		}
		System.setOut(new PrintStream(new CaptureStream(realOut), true));
		System.setErr(new PrintStream(new CaptureStream(realErr), true));
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Terminal display restore"));
		synchronized (lock) {
			// alternate screen, hide cursor
			realOut.print(ESC + "?1049h" + ESC + "?25l");
			realOut.flush();
		}
		Thread keys = new Thread(this::readKeys, "Terminal display keys");
		keys.setDaemon(true);
		keys.start();
		timer = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "Terminal display");
			t.setDaemon(true);
			return t;
		});
		timer.scheduleAtFixedRate(() -> {
			try {
				draw();
			}
			catch (Throwable e) {
				// never let the display stop PAMGuard
			}
		}, 0, 500, TimeUnit.MILLISECONDS);
		return true;
	}

	/**
	 * Put the terminal into a mode where single key presses can be read without Enter,
	 * and (on Windows) where it understands ANSI escape sequences.
	 * @return false if that isn't possible, in which case the plain log is used.
	 */
	private boolean setupTerminal() {
		if (isWindows()) {
			try {
				com.sun.jna.platform.win32.Kernel32 k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE;
				com.sun.jna.platform.win32.WinNT.HANDLE in = k32.GetStdHandle(com.sun.jna.platform.win32.Wincon.STD_INPUT_HANDLE);
				com.sun.jna.platform.win32.WinNT.HANDLE out = k32.GetStdHandle(com.sun.jna.platform.win32.Wincon.STD_OUTPUT_HANDLE);
				com.sun.jna.ptr.IntByReference mode = new com.sun.jna.ptr.IntByReference();
				if (!k32.GetConsoleMode(out, mode) || !k32.SetConsoleMode(out, mode.getValue() | 0x0004)) {
					// no ENABLE_VIRTUAL_TERMINAL_PROCESSING, so can't draw the display
					return false;
				}
				if (k32.GetConsoleMode(in, mode)) {
					savedStty = Integer.toString(mode.getValue());
					// turn off ENABLE_LINE_INPUT and ENABLE_ECHO_INPUT
					k32.SetConsoleMode(in, mode.getValue() & ~0x0002 & ~0x0004);
				}
				return true;
			}
			catch (Throwable e) {
				return false;
			}
		}
		savedStty = stty("-g");
		if (savedStty == null) {
			return false;
		}
		stty("-icanon -echo min 1");
		return true;
	}

	private void restoreTerminal() {
		if (savedStty == null) {
			return;
		}
		if (isWindows()) {
			try {
				com.sun.jna.platform.win32.Kernel32 k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE;
				k32.SetConsoleMode(k32.GetStdHandle(com.sun.jna.platform.win32.Wincon.STD_INPUT_HANDLE), Integer.valueOf(savedStty));
			}
			catch (Throwable e) {
			}
		}
		else {
			stty(savedStty);
		}
		savedStty = null;
	}

	/**
	 * Run stty on the terminal.
	 * @return its output, or null if it failed.
	 */
	private static String stty(String args) {
		try {
			Process p = new ProcessBuilder("sh", "-c", "stty " + args + " < /dev/tty").redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes()).trim();
			return p.waitFor() == 0 ? out : null;
		}
		catch (Exception e) {
			return null;
		}
	}

	private void stop() {
		if (timer != null) {
			timer.shutdownNow();
		}
		synchronized (lock) {
			if (!showingLog) {
				realOut.print(ESC + "?25h" + ESC + "?1049l");
				printHiddenLines();
				showingLog = true;
			}
			realOut.flush();
		}
		restoreTerminal();
	}

	/**
	 * @return true if the terminal display is running (and so reading the keyboard). 
	 */
	public static boolean isActive() {
		return instance != null;
	}

	/**
	 * A command being typed after ':', or null. 
	 */
	private volatile StringBuilder command;

	private void readKeys() {
		InputStream in = new java.io.FileInputStream(java.io.FileDescriptor.in);
		try {
			int c;
			while ((c = in.read()) >= 0) {
				if (command != null) {
					typeCommand(c);
				}
				else if (c == 'l' || c == 'L') {
					showLog();
				}
				else if (c == 's' || c == 'S') {
					showProgress();
				}
				else if (c == ':') {
					// type a command, as with the network commands (start, stop, status, ...)
					command = new StringBuilder();
					echo(":");
				}
			}
		}
		catch (IOException e) {
		}
	}

	private void typeCommand(int c) {
		if (c == '\r' || c == '\n') {
			String cmd = command.toString().trim();
			command = null;
			echo("\n");
			if (cmd.length() > 0) {
				TerminalController tc = TerminalController.getInstance();
				if (tc != null) {
					System.out.println("Command: " + cmd);
					tc.interpretCommand(cmd);
				}
			}
		}
		else if (c == 27) {
			// escape cancels
			command = null;
			echo("\n");
		}
		else if (c == 127 || c == 8) {
			if (command.length() > 0) {
				command.setLength(command.length()-1);
				echo("\b \b");
			}
		}
		else if (c >= 32) {
			command.append((char) c);
			echo(String.valueOf((char) c));
		}
		if (!showingLog) {
			draw();
		}
	}

	/**
	 * Echo typed characters in the log view (the progress view shows the command itself).  
	 */
	private void echo(String text) {
		synchronized (lock) {
			if (showingLog) {
				realOut.print(text);
				realOut.flush();
			}
		}
	}

	private void showLog() {
		synchronized (lock) {
			if (showingLog) {
				return;
			}
			showingLog = true;
			// back to the normal screen, which has the log on it, then the lines printed since.
			realOut.print(ESC + "?25h" + ESC + "?1049l");
			printHiddenLines();
			realOut.println("--- log: press s for the progress display, or : to type a command ---");
			realOut.flush();
		}
	}

	private void showProgress() {
		synchronized (lock) {
			if (!showingLog) {
				return;
			}
			showingLog = false;
			realOut.print(ESC + "?1049h" + ESC + "?25l");
			realOut.flush();
		}
		draw();
	}

	private void printHiddenLines() {
		if (droppedLines > 0) {
			realOut.printf("... %d earlier lines not shown ...\n", droppedLines);
		}
		for (String line : hiddenLines) {
			realOut.println(line);
		}
		hiddenLines.clear();
		droppedLines = 0;
	}

	/**
	 * Called with each complete line written to System.out or System.err.
	 */
	private void addLine(String line) {
		if (line.trim().length() > 0) {
			lastLine = line;
		}
		if (!showingLog) {
			hiddenLines.add(line);
			while (hiddenLines.size() > MAXHIDDENLINES) {
				hiddenLines.removeFirst();
				droppedLines++;
			}
		}
	}

	/**
	 * Stream that replaces System.out and System.err. In the log view it passes everything
	 * on to the terminal; in the progress view it keeps the lines to print later.
	 */
	private class CaptureStream extends OutputStream {

		private final PrintStream real;

		private final ByteArrayOutputStream line = new ByteArrayOutputStream();

		CaptureStream(PrintStream real) {
			this.real = real;
		}

		@Override
		public void write(int b) throws IOException {
			synchronized (lock) {
				if (showingLog) {
					real.write(b);
				}
				if (b == '\n') {
					addLine(line.toString(Charset.defaultCharset().name()).replace("\r", ""));
					line.reset();
				}
				else {
					line.write(b);
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			synchronized (lock) {
				for (int i = off; i < off+len; i++) {
					write(b[i]);
				}
			}
		}

		@Override
		public void flush() throws IOException {
			synchronized (lock) {
				if (showingLog) {
					real.flush();
				}
			}
		}
	}

	private void checkSize() {
		long now = System.currentTimeMillis();
		if (now - lastSizeCheck < 2000) {
			return;
		}
		lastSizeCheck = now;
		try {
			if (isWindows()) {
				com.sun.jna.platform.win32.Kernel32 k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE;
				com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO info = new com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO();
				if (k32.GetConsoleScreenBufferInfo(k32.GetStdHandle(com.sun.jna.platform.win32.Wincon.STD_OUTPUT_HANDLE), info)) {
					columns = info.srWindow.Right - info.srWindow.Left + 1;
					rows = info.srWindow.Bottom - info.srWindow.Top + 1;
				}
			}
			else {
				String size = stty("size");
				if (size != null) {
					String[] bits = size.split("\\s+");
					rows = Integer.valueOf(bits[0]);
					columns = Integer.valueOf(bits[1]);
				}
			}
		}
		catch (Throwable e) {
		}
		columns = Math.max(40, Math.min(columns, 300));
		rows = Math.max(10, rows);
	}

	private void draw() {
		if (showingLog) {
			return;
		}
		checkSize();
		List<String> lines = makeLines();
		StringBuilder sb = new StringBuilder();
		sb.append(ESC).append("H");
		int n = Math.min(lines.size(), rows-1);
		for (int i = 0; i < n; i++) {
			String l = lines.get(i);
			if (l.length() > columns) {
				l = l.substring(0, columns);
			}
			sb.append(l).append(ESC).append("K\n");
		}
		// last line: the command being typed, or the most recent log message
		StringBuilder cmd = command;
		String msg = cmd != null ? ":" + cmd : "Last message: " + lastLine;
		if (msg.length() > columns) {
			msg = msg.substring(0, columns);
		}
		for (int i = n; i < rows-1; i++) {
			sb.append(ESC).append("K\n");
		}
		if (cmd != null) {
			sb.append(msg).append(ESC).append("K");
		}
		else {
			sb.append(ESC).append("2m").append(msg).append(ESC).append("0m").append(ESC).append("K");
		}
		synchronized (lock) {
			if (!showingLog) {
				realOut.print(sb.toString());
				realOut.flush();
			}
		}
	}

	private List<String> makeLines() {
		List<String> lines = new ArrayList<>();
		PamController pc = PamController.getInstance();
		String keys = "[l] log  [s] progress  [:] command";
		String title = String.format("PAMGuard %s   %s   elapsed %s", PamguardVersionInfo.version, statusName(pc, folderFraction),
				duration(System.currentTimeMillis() - startTime));
		lines.add(pad(title, columns - keys.length()) + keys);
		String psf = PamSettingManager.getInstance() == null ? null : PamSettingManager.getInstance().getSettingsFileName();
		if (psf != null) {
			lines.add("Settings: " + new File(psf).getName());
		}
		lines.add("");
		if (pc == null) {
			lines.add("Starting ...");
			return lines;
		}
		boolean anyAcquisition = false;
		for (int i = 0; i < pc.getNumControlledUnits(); i++) {
			PamControlledUnit unit = pc.getControlledUnit(i);
			if (unit instanceof AcquisitionControl) {
				anyAcquisition = true;
				addAcquisitionLines((AcquisitionControl) unit, lines);
				lines.add("");
			}
		}
		if (!anyAcquisition && pc.getNumControlledUnits() == 0) {
			lines.add("Loading the configuration ...");
		}
		addDetectionLines(pc, lines);
		return lines;
	}

	private void addAcquisitionLines(AcquisitionControl daq, List<String> lines) {
		DaqSystem system = daq.getAcquisitionProcess().getRunningSystem();
		if (system == null) {
			system = daq.findDaqSystem(null);
		}
		lines.add(daq.getUnitName() + ": " + daq.toString());
		if (system instanceof FileInputSystem) {
			FileInputSystem fis = (FileInputSystem) system;
			int labelWidth = 9;
			if (system instanceof FolderInputSystem) {
				FolderInputSystem folder = (FolderInputSystem) system;
				int nFiles = folder.getTotalFiles();
				int iFile = Math.min(folder.getCurrentFileIndex(), nFiles);
				double fileFrac = fileFraction(fis);
				double frac = nFiles > 0 ? (iFile + fileFrac) / nFiles : 0;
				/*
				 * Between files, the file index moves on before the new file is opened, so
				 * don't let the folder progress go backwards. 
				 */
				if (nFiles == lastFileCount && frac < folderFraction && frac > folderFraction - 1.5 / nFiles) {
					frac = folderFraction;
				}
				folderFraction = frac;
				lastFileCount = nFiles;
				String right = pad(String.format(" %d/%d files", Math.min(iFile+1, nFiles), nFiles), RIGHTWIDTH-7) 
						+ String.format("%6.1f%%", frac * 100);
				lines.add(pad("  Folder", labelWidth) + bar(frac, columns - labelWidth - right.length()) + right);
			}
			double fileFrac = fileFraction(fis);
			String right = pad("", RIGHTWIDTH-7) + String.format("%6.1f%%", fileFrac * 100);
			lines.add(pad("  File", labelWidth) + bar(fileFrac, columns - labelWidth - right.length()) + right);
			File f = fis.getCurrentFile();
			if (f != null) {
				lines.add("  " + f.getName());
			}
			long dataTime = PamCalendar.getTimeInMillis();
			updateSpeed(dataTime);
			String info = "  Data time " + PamCalendar.formatDBDateTime(dataTime) + " UTC";
			if (speed > 0) {
				info += String.format("   %.1f x real time", speed);
			}
			lines.add(info);
		}
		else {
			double[] peaks = daq.getRecentPeakLevels();
			if (peaks.length == 0) {
				lines.add("  No sound data yet");
			}
			int labelWidth = 8;
			for (int i = 0; i < peaks.length; i++) {
				// on a scale of -60 to 0 dB, as on the Swing status bar
				double db = peaks[i] > 0 ? 20 * Math.log10(peaks[i]) : -100;
				String right = pad("", RIGHTWIDTH-12) + (db > -100 ? String.format("%6.1f dB FS", db) : "     - dB FS");
				lines.add(pad(String.format("  Ch %d", i), labelWidth) + bar((db + 60) / 60., columns - labelWidth - right.length()) + right);
			}
		}
	}

	private double fileFraction(FileInputSystem fis) {
		long n = fis.getFileSamples();
		if (n <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(1, (double) fis.getReadFileSamples() / n));
	}

	private void updateSpeed(long dataTime) {
		long now = System.currentTimeMillis();
		if (lastWallTime > 0 && now - lastWallTime >= 1000) {
			double s = (double) (dataTime - lastDataTime) / (now - lastWallTime);
			if (s >= 0 && s < 1e5) {
				speed = speed == 0 ? s : speed * 0.7 + s * 0.3;
			}
			lastWallTime = now;
			lastDataTime = dataTime;
		}
		else if (lastWallTime == 0) {
			lastWallTime = now;
			lastDataTime = dataTime;
		}
	}

	private void addDetectionLines(PamController pc, List<String> lines) {
		ArrayList<PamDataBlock> blocks;
		try {
			blocks = pc.getDetectorDataBlocks();
		}
		catch (Exception e) {
			return;
		}
		if (blocks == null || blocks.isEmpty()) {
			return;
		}
		lines.add("Detections");
		int nameWidth = 0;
		for (PamDataBlock b : blocks) {
			nameWidth = Math.max(nameWidth, b.getLongDataName().length());
		}
		nameWidth = Math.min(nameWidth + 2, columns - 14);
		for (PamDataBlock b : blocks) {
			lines.add(pad("  " + b.getLongDataName(), nameWidth + 2) + String.format("%,10d", b.getUnitsAdded()));
		}
	}

	private static String statusName(PamController pc, double folderFraction) {
		if (pc == null) {
			return "Starting";
		}
		switch (pc.getPamStatus()) {
		case PamController.PAM_IDLE:
			// PAMGuard stops and starts again for each file in a folder
			return folderFraction > 0 && folderFraction < 1 ? "Next file" : "Idle";
		case PamController.PAM_RUNNING:
			return "Running";
		case PamController.PAM_STALLED:
			return "Stalled";
		case PamController.PAM_INITIALISING:
			return "Initialising";
		case PamController.PAM_STOPPING:
			return "Stopping";
		case PamController.PAM_COMPLETE:
			return "Complete";
		case PamController.PAM_OFFLINETASK:
			return "Running offline task";
		default:
			return "";
		}
	}

	/**
	 * A text progress bar, e.g. [#########---------]
	 * @param fraction 0 to 1
	 * @param width total width including the brackets
	 */
	static String bar(double fraction, int width) {
		int inner = Math.max(5, width - 2);
		fraction = Math.max(0, Math.min(1, Double.isNaN(fraction) ? 0 : fraction));
		int n = (int) Math.round(fraction * inner);
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < inner; i++) {
			sb.append(i < n ? '#' : '-');
		}
		return sb.append(']').toString();
	}

	private static String pad(String s, int width) {
		if (s.length() >= width) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s);
		while (sb.length() < width) {
			sb.append(' ');
		}
		return sb.toString();
	}

	private static String duration(long millis) {
		long s = millis / 1000;
		return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

}
