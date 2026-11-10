package pamguard.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import PamController.PamControlledUnit;
import PamController.PamController;
import PamController.PamguardVersionInfo;
import PamguardMVC.PamDataBlock;
import PamguardMVC.PamProcess;

/**
 * Machine readable summary of a PAMGuard run, written as JSON when PAMGuard exits
 * if the -summary option was given. It is meant to let scripts, batch processors
 * and AI assistants check what a run did without reading the log: which settings
 * were used, which modules ran, which files were processed, how many data units
 * (detections, etc.) each data stream produced, any warnings and errors, and the
 * exit code.
 * <p>
 * The summary is written from a shutdown hook, so it is written however PAMGuard
 * exits. Other parts of PAMGuard add information through the static instance.
 */
public class RunSummary {

	private static RunSummary singleInstance;

	/**
	 * Maximum number of files to list individually.
	 */
	private static final int MAXFILES = 10000;

	/**
	 * Maximum number of warnings to keep.
	 */
	private static final int MAXWARNINGS = 500;

	private String outputFile;

	private long startTime = System.currentTimeMillis();

	private String[] args;

	private int runMode = PamController.RUN_NORMAL;

	private List<Map<String, Object>> files = new ArrayList<>();

	private int nFiles = 0;

	private List<Map<String, Object>> warnings = new ArrayList<>();

	private List<String> errors = new ArrayList<>();

	private List<Map<String, Object>> settingsChanges = new ArrayList<>();

	private List<Map<String, Object>> exports = new ArrayList<>();

	private ExitCode exitCode = null;

	private boolean written = false;

	private boolean hookInstalled = false;

	private RunSummary() {
	}

	/**
	 * @return the single run summary
	 */
	public static synchronized RunSummary getInstance() {
		if (singleInstance == null) {
			singleInstance = new RunSummary();
		}
		return singleInstance;
	}

	/**
	 * Set the file to write the summary to, which also turns writing on.
	 * Use "-" to write to the console.
	 * @param outputFile file name
	 */
	public synchronized void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
		if (!hookInstalled) {
			hookInstalled = true;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> write(), "PAMGuard run summary"));
		}
	}

	/**
	 * @return the output file, or null if no summary is being written.
	 */
	public String getOutputFile() {
		return outputFile;
	}

	/**
	 * Record the command line and run mode.
	 * @param args command line arguments
	 * @param runMode run mode
	 */
	public synchronized void setStartInfo(String[] args, int runMode) {
		this.args = args;
		this.runMode = runMode;
	}

	/**
	 * Record a sound file which has been opened for processing.
	 * @param filePath file
	 * @param sampleRate sample rate
	 * @param nChannels number of channels
	 * @param nSamples number of samples
	 */
	public synchronized void addFileProcessed(String filePath, float sampleRate, int nChannels, long nSamples) {
		nFiles++;
		if (files.size() < MAXFILES) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("file", filePath);
			f.put("sampleRate", sampleRate);
			f.put("channels", nChannels);
			if (sampleRate > 0 && nSamples > 0) {
				f.put("durationSeconds", nSamples / (double) sampleRate);
			}
			files.add(f);
		}
	}

	/**
	 * Record a warning. Called for every warning shown with WarnOnce.
	 * @param title warning title
	 * @param message warning message, may contain HTML.
	 */
	public synchronized void addWarning(String title, String message) {
		if (warnings.size() >= MAXWARNINGS) {
			return;
		}
		Map<String, Object> w = new LinkedHashMap<>();
		w.put("time", Instant.now().toString());
		w.put("title", stripHTML(title));
		w.put("message", stripHTML(message));
		warnings.add(w);
	}

	/**
	 * Record an error.
	 * @param message error message
	 */
	public synchronized void addError(String message) {
		errors.add(stripHTML(message));
	}

	/**
	 * Record a settings change made from the command line or a remote command.
	 * @param change description of the change
	 */
	public synchronized void addSettingsChange(Map<String, Object> change) {
		settingsChanges.add(change);
	}

	/**
	 * Record an export made from the command line.
	 * @param export description of the export
	 */
	public synchronized void addExport(Map<String, Object> export) {
		exports.add(export);
	}

	/**
	 * @return the errors so far
	 */
	public synchronized List<String> getErrors() {
		return new ArrayList<>(errors);
	}

	/**
	 * Set the exit code without exiting. The first code other than OK that is set is
	 * kept, so a later "normal" exit still reports the earlier failure.
	 * @param code exit code
	 */
	public synchronized void setExitCode(ExitCode code) {
		if (exitCode == null || exitCode == ExitCode.OK) {
			exitCode = code;
		}
	}

	/**
	 * @return the exit code that will be used: the one set, or ERROR if any errors were recorded, otherwise OK.
	 */
	public synchronized ExitCode getExitCode() {
		if (exitCode != null) {
			return exitCode;
		}
		return errors.isEmpty() ? ExitCode.OK : ExitCode.ERROR;
	}

	/**
	 * Exit PAMGuard with the given code (or an earlier failure code if one was set).
	 * The summary is written by the shutdown hook.
	 * @param code exit code
	 */
	public void exit(ExitCode code) {
		setExitCode(code);
		System.exit(getExitCode().getCode());
	}

	/**
	 * Build the summary.
	 * @return summary as nested maps and lists, ready to write as JSON.
	 */
	public synchronized Map<String, Object> getSummary() {
		Map<String, Object> root = new LinkedHashMap<>();
		long now = System.currentTimeMillis();
		root.put("program", "PAMGuard");
		root.put("version", PamguardVersionInfo.version);
		root.put("startTime", Instant.ofEpochMilli(startTime).toString());
		root.put("endTime", Instant.ofEpochMilli(now).toString());
		root.put("durationSeconds", (now - startTime) / 1000.);
		root.put("runMode", getRunModeName(runMode));
		root.put("commandLine", args == null ? new ArrayList<String>() : Arrays.asList(args));
		ExitCode code = getExitCode();
		root.put("exitCode", code.getCode());
		root.put("exitCodeName", code.name());
		root.put("success", code == ExitCode.OK);
		PamController pc = null;
		try {
			pc = PamController.getInstance();
		}
		catch (Exception e) {
		}
		if (pc != null) {
			try {
				root.put("settingsFile", pc.getPSFNameWithPath());
			}
			catch (Exception e) {
			}
			root.put("modules", getModules(pc));
			root.put("dataStreams", getDataStreams(pc));
		}
		Map<String, Object> fileInfo = new LinkedHashMap<>();
		fileInfo.put("count", nFiles);
		double totalDur = 0;
		for (Map<String, Object> f : files) {
			Object d = f.get("durationSeconds");
			if (d instanceof Double) {
				totalDur += (Double) d;
			}
		}
		fileInfo.put("totalDurationSeconds", totalDur);
		fileInfo.put("files", files);
		root.put("soundFiles", fileInfo);
		root.put("settingsChanges", settingsChanges);
		if (!exports.isEmpty()) {
			root.put("exports", exports);
		}
		root.put("unrecognisedOptions", CommandLineOptions.getUnrecognised());
		root.put("warnings", warnings);
		root.put("errors", errors);
		return root;
	}

	private List<Object> getModules(PamController pc) {
		List<Object> mods = new ArrayList<>();
		try {
			int n = pc.getNumControlledUnits();
			for (int i = 0; i < n; i++) {
				PamControlledUnit unit = pc.getControlledUnit(i);
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("type", unit.getUnitType());
				m.put("name", unit.getUnitName());
				m.put("class", unit.getClass().getName());
				mods.add(m);
			}
		}
		catch (Exception e) {
		}
		return mods;
	}

	private List<Object> getDataStreams(PamController pc) {
		List<Object> streams = new ArrayList<>();
		try {
			int n = pc.getNumControlledUnits();
			for (int i = 0; i < n; i++) {
				PamControlledUnit unit = pc.getControlledUnit(i);
				for (int p = 0; p < unit.getNumPamProcesses(); p++) {
					PamProcess process = unit.getPamProcess(p);
					for (int b = 0; b < process.getNumOutputDataBlocks(); b++) {
						PamDataBlock block = process.getOutputDataBlock(b);
						if (block == null) {
							continue;
						}
						Map<String, Object> s = new LinkedHashMap<>();
						s.put("module", unit.getUnitName());
						s.put("name", block.getDataName());
						s.put("longName", block.getLongDataName());
						if (block.getUnitClass() != null) {
							s.put("dataUnitClass", block.getUnitClass().getName());
						}
						s.put("unitsAdded", block.getUnitsAdded());
						streams.add(s);
					}
				}
			}
		}
		catch (Exception e) {
		}
		return streams;
	}

	/**
	 * Write the summary to the output file (or the console if the file is "-").
	 * Does nothing if no output file was set or it's already been written.
	 */
	public synchronized void write() {
		if (outputFile == null || written) {
			return;
		}
		written = true;
		String json = CommandLineOptions.toJson(getSummary());
		if (outputFile.equals("-")) {
			System.out.println(json);
			return;
		}
		File f = new File(outputFile);
		if (f.getParentFile() != null) {
			f.getParentFile().mkdirs();
		}
		try (Writer w = new FileWriter(f)) {
			w.write(json);
		} catch (IOException e) {
			System.out.println("Unable to write run summary to " + outputFile + ": " + e.getMessage());
		}
	}

	/**
	 * Get a name for a run mode.
	 * @param runMode run mode
	 * @return name, e.g. "normal" or "viewer"
	 */
	public static String getRunModeName(int runMode) {
		switch (runMode) {
		case PamController.RUN_NORMAL:
			return "normal";
		case PamController.RUN_PAMVIEW:
			return "viewer";
		case PamController.RUN_MIXEDMODE:
			return "mixed";
		case PamController.RUN_REMOTE:
			return "remote";
		case PamController.RUN_NETWORKRECEIVER:
			return "networkreceiver";
		case PamController.RUN_NOTHING:
			return "nothing";
		}
		return "unknown";
	}

	/**
	 * Remove HTML tags from a message, turning line breaks into new lines.
	 * @param text text
	 * @return plain text
	 */
	public static String stripHTML(String text) {
		if (text == null) {
			return null;
		}
		String s = text.replaceAll("(?i)<br\\s*/?>|<p>", "\n");
		s = s.replaceAll("<[^>]+>", "");
		s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		return s.trim();
	}
}
