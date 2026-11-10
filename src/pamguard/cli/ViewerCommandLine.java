package pamguard.cli;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import PamController.PamController;
import PamController.PamControlledUnit;
import PamguardMVC.PamDataBlock;
import export.PamDataUnitExporter;
import export.PamExporterManager;
import export.swing.ExportTask;
import offlineProcessing.OfflineTask;
import offlineProcessing.OfflineTaskGroup;
import offlineProcessing.OfflineTaskManager;
import offlineProcessing.TaskGroupParams;
import offlineProcessing.TaskStatus;
import pamScrollSystem.AbstractPamScroller;
import pamScrollSystem.AbstractScrollManager;
import pamguard.GlobalArguments;

import static pamguard.cli.CommandLineOption.Category.VIEWER;

/**
 * Command line control of viewer mode: open the displays at a given time so a
 * user can check particular detections (-viewtime), and run offline tasks and data
 * exports without a user (-offlinetask, -export) for scripted or AI driven analysis.
 * <p>
 * Everything here happens once the viewer has loaded its first data.
 */
public class ViewerCommandLine {

	public static final String VIEWTIME = "-viewtime";

	public static final String VIEWSPAN = "-viewspan";

	public static final String EXPORT = "-export";

	public static final String EXPORTFORMAT = "-exportformat";

	public static final String EXPORTDATA = "-exportdata";

	private static Long viewTime;

	private static Double viewSpanSeconds;

	private static String exportFolder;

	private static String exportFormat;

	private static List<String> exportData = new ArrayList<>();

	private static boolean done = false;

	static void registerOptions() {
		CommandLineOptions.register(new CommandLineOption(VIEWTIME, VIEWER,
				"Open the viewer displays at this time, e.g. to check particular detections. Times are UTC, as "
				+ "2026-01-31T14:32:05Z, 2026-01-31 14:32:05, 2026-01-31, or milliseconds since 1970.")
				.values("time")
				.example("-v -databasefile survey.sqlite3 -viewtime \"2026-01-31 14:32:05\" -viewspan 120")
				.handler((opt, values, state) -> viewTime = parseTime(values[0])));
		CommandLineOptions.register(new CommandLineOption(VIEWSPAN, VIEWER,
				"Length of data to load around -viewtime, in seconds.")
				.values("seconds")
				.handler((opt, values, state) -> {
					try {
						viewSpanSeconds = Double.parseDouble(values[0]);
					}
					catch (NumberFormatException e) {
						throw new CommandLineException(VIEWSPAN + " needs a number of seconds, not " + values[0]);
					}
				}));
		CommandLineOptions.register(new CommandLineOption(EXPORT, VIEWER,
				"Export detections to this folder once the viewer has loaded, then exit if -autoexit is set. "
				+ "Exports every data stream that can be exported unless -exportdata is given. Runs after any -offlinetask tasks.")
				.values("folder")
				.example("-v -nogui -databasefile survey.sqlite3 -export /data/export -exportformat MATLAB -autoexit")
				.handler((opt, values, state) -> exportFolder = values[0]));
		CommandLineOptions.register(new CommandLineOption(EXPORTFORMAT, VIEWER,
				"Export format for -export: MATLAB (the default; .mat files), R, or wav (\"raw sound\" clips). Matched "
				+ "against the exporter name or file extension, ignoring case.")
				.values("format")
				.handler((opt, values, state) -> exportFormat = values[0]));
		CommandLineOptions.register(new CommandLineOption(EXPORTDATA, VIEWER,
				"A data stream to export with -export, by its long name (e.g. \"Click Detector, Clicks\") or its name. "
				+ "-describe lists the data streams.")
				.values("dataStream").repeatable()
				.handler((opt, values, state) -> exportData.add(values[0])));
	}

	/**
	 * @return true if anything is to be done once the viewer has loaded.
	 */
	public static boolean hasActions() {
		return viewTime != null || exportFolder != null || OfflineTaskManager.getManager().getCommandLineTasks().size() > 0;
	}

	/**
	 * Called once, when the viewer has finished loading its first data.
	 */
	public static synchronized void onViewerDataLoaded() {
		if (done) {
			return;
		}
		done = true;
		if (viewTime != null) {
			showTime(viewTime, viewSpanSeconds);
		}
		boolean runTasks = OfflineTaskManager.getManager().getCommandLineTasks().size() > 0
				&& (GlobalArguments.isBatch() || GlobalArguments.getParam(PamController.AUTOSTART) != null);
		boolean runExport = exportFolder != null;
		if (!runTasks && !runExport) {
			return;
		}
		Thread t = new Thread(() -> {
			if (runTasks) {
				OfflineTaskManager.getManager().runCommandLineTasks();
			}
			if (runExport) {
				runExport();
			}
			if (GlobalArguments.getParam(PamController.AUTOEXIT) != null) {
				System.out.println("Viewer command line tasks complete, PAMGuard will exit (-autoexit)");
				PamController.getInstance().batchProcessingComplete();
			}
		}, "Viewer command line tasks");
		t.start();
	}

	/**
	 * Move all of the viewer scrollers to a time.
	 * @param timeMillis time
	 * @param spanSeconds data length to load, or null to keep the current length.
	 */
	public static void showTime(long timeMillis, Double spanSeconds) {
		AbstractScrollManager scrollManager = AbstractScrollManager.getScrollManager();
		if (scrollManager == null) {
			return;
		}
		if (spanSeconds != null && spanSeconds > 0) {
			long span = (long) (spanSeconds * 1000);
			for (AbstractPamScroller scroller : scrollManager.getPamScrollers()) {
				scroller.setRangeMillis(timeMillis - span/2, timeMillis + span/2, false);
			}
		}
		scrollManager.scrollToTime(null, timeMillis);
		scrollManager.reLoad();
		System.out.println("Viewer moved to " + Instant.ofEpochMilli(timeMillis));
	}

	/**
	 * Export data headlessly to the -export folder.
	 * @return true if all exports completed
	 */
	public static boolean runExport() {
		PamExporterManager exportManager = new PamExporterManager();
		int choice = findExporter(exportManager, exportFormat);
		if (choice < 0) {
			List<String> names = new ArrayList<>();
			for (int i = 0; i < exportManager.getNumExporters(); i++) {
				names.add(exportManager.getExporter(i).getName());
			}
			String msg = String.format("Unknown export format \"%s\". Formats are: %s", exportFormat, String.join(", ", names));
			fail(msg);
			return false;
		}
		PamDataUnitExporter exporter = exportManager.getExporter(choice);
		File folder = new File(exportFolder);
		folder.mkdirs();
		exportManager.getExportParams().exportChoice = choice;
		exportManager.getExportParams().folder = folder.getAbsolutePath();

		List<PamDataBlock> blocks = new ArrayList<>();
		for (PamDataBlock block : PamController.getInstance().getDataBlocks()) {
			if (block.getUnitClass() == null || !exporter.hasCompatibleUnits(block.getUnitClass())) {
				continue;
			}
			if (exportData.isEmpty() || matchesStream(block)) {
				blocks.add(block);
			}
		}
		if (blocks.isEmpty()) {
			fail("No data streams to export as " + exporter.getName() + (exportData.isEmpty() ? "" : " matching " + exportData));
			return false;
		}
		boolean allOK = true;
		for (PamDataBlock block : blocks) {
			System.out.printf("Exporting %s as %s to %s\n", block.getLongDataName(), exporter.getName(), folder.getAbsolutePath());
			String[] before = folder.list();
			ExportTask task;
			try {
				task = new ExportTask(block, exportManager);
			}
			catch (Exception e) {
				fail("Unable to create export for " + block.getLongDataName() + ": " + e.getMessage());
				allOK = false;
				continue;
			}
			HeadlessExportGroup group = new HeadlessExportGroup("Command line export " + block.getLongDataName());
			group.addTask(task);
			group.getTaskGroupParams().dataChoice = TaskGroupParams.PROCESS_ALL;
			group.getTaskGroupParams().setTaskSelection(0, true);
			task.setDoRun(true);
			TaskStatus status = OfflineTaskManager.runTaskGroupAndWait(group, null);
			List<String> newFiles = new ArrayList<>();
			String[] after = folder.list();
			if (after != null) {
				List<String> old = before == null ? new ArrayList<>() : java.util.Arrays.asList(before);
				for (String f : after) {
					if (!old.contains(f)) {
						newFiles.add(new File(folder, f).getAbsolutePath());
					}
				}
			}
			Map<String, Object> exp = new LinkedHashMap<>();
			exp.put("dataStream", block.getLongDataName());
			exp.put("format", exporter.getName());
			exp.put("folder", folder.getAbsolutePath());
			exp.put("status", String.valueOf(status));
			exp.put("files", newFiles);
			RunSummary.getInstance().addExport(exp);
			if (status != TaskStatus.COMPLETE) {
				fail("Export of " + block.getLongDataName() + " finished with status " + status);
				allOK = false;
			}
		}
		return allOK;
	}

	private static boolean matchesStream(PamDataBlock block) {
		for (String s : exportData) {
			if (s.equalsIgnoreCase(block.getLongDataName()) || s.equalsIgnoreCase(block.getDataName())
					|| s.equalsIgnoreCase(ExportTask.getExportName(block, block.getDataName()))) {
				return true;
			}
		}
		return false;
	}

	private static int findExporter(PamExporterManager exportManager, String format) {
		if (format == null) {
			format = "MATLAB";
		}
		for (int i = 0; i < exportManager.getNumExporters(); i++) {
			PamDataUnitExporter ex = exportManager.getExporter(i);
			String ext = ex.getFileExtension();
			if (format.equalsIgnoreCase(ex.getName()) || (ext != null && format.equalsIgnoreCase(ext.replace(".", "")))) {
				return i;
			}
		}
		if (format.equalsIgnoreCase("wav")) {
			for (int i = 0; i < exportManager.getNumExporters(); i++) {
				if (exportManager.getExporter(i).getName().toLowerCase().contains("sound")) {
					return i;
				}
			}
		}
		return -1;
	}

	private static void fail(String msg) {
		System.out.println("Error: " + msg);
		RunSummary.getInstance().addError(msg);
		RunSummary.getInstance().setExitCode(ExitCode.TASK_FAILED);
	}

	/**
	 * Parse a time from the command line. Times without a zone are UTC.
	 * @param text time as ISO 8601, "yyyy-MM-dd HH:mm:ss[.SSS]", a date, or milliseconds.
	 * @return milliseconds since 1970
	 * @throws CommandLineException if the time can't be read.
	 */
	public static long parseTime(String text) throws CommandLineException {
		String t = text.trim();
		if (t.matches("\\d{9,}")) {
			return Long.parseLong(t);
		}
		try {
			return Instant.parse(t).toEpochMilli();
		}
		catch (DateTimeParseException e) {
		}
		String iso = t.replace(' ', 'T');
		try {
			return LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		catch (DateTimeParseException e) {
		}
		try {
			return LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		catch (DateTimeParseException e) {
		}
		throw new CommandLineException("Can't read the time \"" + text + "\". Use e.g. 2026-01-31T14:32:05Z or \"2026-01-31 14:32:05\"");
	}

	/**
	 * Offline task group for an export, which doesn't belong to a module.
	 */
	private static class HeadlessExportGroup extends OfflineTaskGroup {

		public HeadlessExportGroup(String settingsName) {
			super(null, settingsName);
		}

		@Override
		public String getUnitType() {
			return "Export Data";
		}

		@Override
		public boolean addTask(OfflineTask task) {
			setPrimaryDataBlock(null);
			return super.addTask(task);
		}
	}

	/**
	 * List the offline tasks available in this configuration, by the name to use
	 * with -offlinetask.
	 * @return task names and descriptions
	 */
	public static List<Map<String, Object>> listOfflineTasks() {
		List<Map<String, Object>> list = new ArrayList<>();
		try {
			for (OfflineTask task : OfflineTaskManager.getManager().getAllOfflineTasks()) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("name", task.getLongName());
				m.put("task", task.getName());
				PamControlledUnit unit = task.getTaskControlledUnit();
				if (unit != null) {
					m.put("module", unit.getUnitName());
				}
				if (task.getDataBlock() != null) {
					m.put("dataStream", task.getDataBlock().getLongDataName());
				}
				list.add(m);
			}
		}
		catch (Exception e) {
		}
		return list;
	}
}
