package pamguard.cli;

import java.io.File;

import Acquisition.FolderInputSystem;
import Acquisition.SoundCardSystem;
import Array.ArrayManager;
import NMEA.NMEAControl;
import PamController.PamController;
import PamController.PamGUIManager;
import PamController.PamSettingManager;
import PamController.PamguardVersionInfo;
import PamController.pamBuoyGlobals;
import PamController.fileprocessing.ReprocessStoreChoice;
import PamModel.SMRUEnable;
import PamView.FullScreen;
import PamguardMVC.debug.Debug;
import SoundRecorder.RecorderControl;
import binaryFileStorage.BinaryStore;
import dataPlotsFX.JamieDev;
import generalDatabase.DBControl;
import networkTransfer.send.NetSendCommandParam;
import offlineProcessing.OfflineTaskManager;
import pamguard.GlobalArguments;
import pamguard.MacOSIntegration;
import pamguard.Pamguard;
import rawDeepLearningClassifier.DLControl;
import rocca.RoccaDev;
import static pamguard.cli.CommandLineOption.Category.*;

/**
 * The command line options built into PAMGuard. These used to be a long list of
 * if / else statements in Pamguard.main(). Each option now has a description so
 * that the help can be generated from this list.
 */
public class BuiltInOptions {

	/**
	 * Option for a machine readable summary of the run.
	 */
	public static final String SUMMARY = "-summary";

	/**
	 * Option to apply settings from an XML file.
	 */
	public static final String SETTINGS_FILE = "-settings";

	/**
	 * Option to change a single setting.
	 */
	public static final String SET = "-set";

	/**
	 * Option to write a description of every module's settings.
	 */
	public static final String DESCRIBE = "-describe";

	static void registerAll() {

		// ---------------------------------------------------------------- info
		CommandLineOptions.register(new CommandLineOption("-help", INFO,
				"Show this help and exit. Use -help json for a machine readable version, or -help all to include developer options.")
				.aliases("--help", "-h", "-?").optionalValue("json|all")
				.handler((opt, values, state) -> {
					String v = values.length > 0 ? values[0] : "";
					if (v.equalsIgnoreCase("json")) {
						System.out.println(CommandLineOptions.getHelpJson());
					}
					else {
						CommandLineOptions.printHelp(System.out, v.equalsIgnoreCase("all"));
					}
					state.exitNow = true;
				}));
		CommandLineOptions.register(new CommandLineOption("-version", INFO,
				"Show the PAMGuard version and exit. Use -version json for a machine readable version.")
				.aliases("--version").optionalValue("json")
				.handler((opt, values, state) -> {
					if (values.length > 0 && values[0].equalsIgnoreCase("json")) {
						System.out.printf("{\"program\": \"PAMGuard\", \"version\": \"%s\", \"date\": \"%s\", \"java\": \"%s\"}\n",
								PamguardVersionInfo.version, PamguardVersionInfo.date, System.getProperty("java.version"));
					}
					else {
						System.out.println("PAMGuard " + PamguardVersionInfo.version + " (" + PamguardVersionInfo.date + ")");
					}
					state.exitNow = true;
				}));

		// ---------------------------------------------------------------- run modes
		CommandLineOptions.register(new CommandLineOption("-v", RUNMODE,
				"Viewer mode: open a database and binary store to view, check and reprocess data.")
				.handler((opt, values, state) -> {
					state.runMode = PamController.RUN_PAMVIEW;
					System.out.println("PAMGUARD Viewer");
				}));
		CommandLineOptions.register(new CommandLineOption("-m", RUNMODE,
				"Mixed mode: analyse sound files while reading other data, such as GPS, back from the database.")
				.handler((opt, values, state) -> {
					state.runMode = PamController.RUN_MIXEDMODE;
					System.out.println("PAMGUARD Offline mixed mode");
				}));
		CommandLineOptions.register(new CommandLineOption("-nr", RUNMODE,
				"Network receiver mode: receive and display data sent from other PAMGuards.")
				.handler((opt, values, state) -> {
					state.runMode = PamController.RUN_NETWORKRECEIVER;
					System.out.println("PAMGUARD Network Reciever Mode");
				}));
		CommandLineOptions.register(new CommandLineOption("-r", RUNMODE,
				"Remote mode: normal mode without a GUI, normally controlled over the network (see -port).")
				.handler((opt, values, state) -> {
					state.runMode = PamController.RUN_REMOTE;
					System.out.println("remote non gui operation.");
				}));
		CommandLineOptions.register(new CommandLineOption("-c", RUNMODE,
				"Show the start up dialog to choose the run mode and the settings file or database.")
				.handler((opt, values, state) -> state.showStartupDialog = true));
		CommandLineOptions.register(new CommandLineOption(MacOSIntegration.OPENFILE, RUNMODE,
				"Open a settings file (normal mode) or a database (viewer mode). The file type sets the run mode.")
				.values("file")
				.handler((opt, values, state) -> state.openFile = new File(values[0])));
		CommandLineOptions.register(new CommandLineOption(GlobalArguments.BATCHFLAG, RUNMODE,
				"Batch processing: avoids dialogs that would otherwise wait for a user. Set by the batch processor.")
				.handler((opt, values, state) -> GlobalArguments.setParam(GlobalArguments.BATCHFLAG, Boolean.TRUE.toString())));
		CommandLineOptions.register(new CommandLineOption(GlobalArguments.BATCHVIEW, RUNMODE,
				"Used by the batch processor: viewer settings are available but are read from and written to psfx files, not the database.")
				.hidden()
				.handler((opt, values, state) -> GlobalArguments.setParam(GlobalArguments.BATCHVIEW, "true")));

		// ---------------------------------------------------------------- GUI
		CommandLineOptions.register(new CommandLineOption("-nogui", GUI,
				"Run without any GUI. Use with -psf and -autostart -autoexit for unattended processing.")
				.handler((opt, values, state) -> {
					PamGUIManager.setType(PamGUIManager.NOGUI);
					System.out.println("no gui operation.");
				}));
		CommandLineOptions.register(new CommandLineOption("-swing", GUI, "Use the Swing GUI (the default).")
				.handler((opt, values, state) -> PamGUIManager.setType(PamGUIManager.SWING)));
		CommandLineOptions.register(new CommandLineOption("-fx", GUI, "Use the JavaFX GUI.")
				.handler((opt, values, state) -> PamGUIManager.setType(PamGUIManager.FX)));
		CommandLineOptions.register(new CommandLineOption("-nosplash", GUI, "Don't show the splash screen.")
				.handler((opt, values, state) -> state.showSplash = false));
		CommandLineOptions.register(new CommandLineOption("-fullscreen", GUI, "Start with the main window full screen.")
				.handler((opt, values, state) -> {
					FullScreen.setGoFullScreen(true);
					System.out.println("Setting full screen view");
				}));

		// ---------------------------------------------------------------- settings
		CommandLineOptions.register(new CommandLineOption("-psf", SETTINGS,
				"Settings file (.psfx) to use in normal mode, without asking.")
				.aliases("-psfx").values("file")
				.handler((opt, values, state) -> {
					PamSettingManager.remote_psf = values[0];
					System.out.println("Running using settings from " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(SETTINGS_FILE, SETTINGS,
				"Apply module settings from an XML file once the configuration has loaded. The file format is the one "
				+ "written by File > Export XML settings or the getxmlsettings network command; only the modules in the file are changed.")
				.values("file.xml").repeatable()
				.handler((opt, values, state) -> SettingsOverrides.addXMLFile(values[0])));
		CommandLineOptions.register(new CommandLineOption(SET, SETTINGS,
				"Change one setting once the configuration has loaded. The key is the module name, then a dot, then the "
				+ "parameter name; dots step into nested objects and [n] picks an array element. Use -describe to find names. "
				+ "Changes apply to this run and are saved with the settings at the end of the run.")
				.values("module.parameter=value").repeatable()
				.example("-set \"Click Detector.dbThreshold=12\"")
				.handler((opt, values, state) -> SettingsOverrides.addSet(values[0])));
		CommandLineOptions.register(new CommandLineOption(DESCRIBE, SETTINGS,
				"Once the configuration has loaded, write a JSON description of every module (type, name, class) and every "
				+ "setting (name, type, current value, description) plus the list of module types that can be added, then exit. "
				+ "Writes to the file if given, otherwise to the console. Applied after -settings and -set.")
				.optionalValue("file.json")
				.handler((opt, values, state) -> SettingsDescriber.setDescribe(values.length > 0 ? values[0] : "-")));

		// ---------------------------------------------------------------- input and output
		CommandLineOptions.register(new CommandLineOption(FolderInputSystem.GlobalWavFolderArg, INPUT_OUTPUT,
				"Folder (or single file) of sound files to process, overriding the Sound Acquisition settings.")
				.values("folder")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(FolderInputSystem.GlobalWavFolderArg, values[0]);
					System.out.println("Setting input wav file folder to " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(FolderInputSystem.GlobalWavPrefixArg, INPUT_OUTPUT,
				"File name prefix for sound files written by the Sound Recorder.")
				.values("prefix")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(FolderInputSystem.GlobalWavPrefixArg, values[0]);
					GlobalArguments.setParam(RecorderControl.GlobalWavPrefixArg2, values[0]);
					System.out.println("Setting recording prefix to " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(BinaryStore.GlobalFolderArg, INPUT_OUTPUT,
				"Output folder for binary files, overriding the Binary Storage settings.")
				.values("folder")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(BinaryStore.GlobalFolderArg, values[0]);
					System.out.println("Setting output folder for binary files to " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(DBControl.GlobalDatabaseNameArg, INPUT_OUTPUT,
				"Database file (e.g. .sqlite3), overriding the Database settings. Created if it doesn't exist.")
				.values("file")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(DBControl.GlobalDatabaseNameArg, values[0]);
					System.out.println("Setting output database file to " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(SoundCardSystem.SETDEVNAME, INPUT_OUTPUT,
				"Name of the sound card to record from.")
				.values("name")
				.handler((opt, values, state) -> GlobalArguments.setParam(SoundCardSystem.SETDEVNAME, values[0])));
		CommandLineOptions.register(new CommandLineOption(SoundCardSystem.SETDEVNUMBER, INPUT_OUTPUT,
				"Number of the sound card to record from.")
				.values("number")
				.handler((opt, values, state) -> GlobalArguments.setParam(SoundCardSystem.SETDEVNUMBER, values[0])));
		CommandLineOptions.register(new CommandLineOption(NMEAControl.GlobalPortFlag, INPUT_OUTPUT,
				"Serial port for NMEA (e.g. GPS) data.")
				.values("port")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(NMEAControl.GlobalPortFlag, values[0]);
					System.out.println("Setting nmea serial port to " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption(ArrayManager.FIRST_IDX_SENS, INPUT_OUTPUT,
				"Index of the first hydrophone to use from the array settings.")
				.values("index")
				.handler((opt, values, state) -> GlobalArguments.setParam(ArrayManager.FIRST_IDX_SENS, values[0])));
		CommandLineOptions.register(new CommandLineOption(DLControl.MODELPATH, INPUT_OUTPUT,
				"Model file for the Deep Learning Classifier, overriding its settings.")
				.values("file")
				.handler((opt, values, state) -> GlobalArguments.setParam(DLControl.MODELPATH, values[0])));

		// ---------------------------------------------------------------- processing
		CommandLineOptions.register(new CommandLineOption(PamController.AUTOSTART, PROCESSING,
				"Start processing as soon as PAMGuard has loaded. In viewer mode, runs the -offlinetask tasks.")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(PamController.AUTOSTART, PamController.AUTOSTART);
					System.out.println("Setting autostart ON");
				}));
		CommandLineOptions.register(new CommandLineOption(PamController.AUTOEXIT, PROCESSING,
				"Exit when processing is complete (all sound files processed, or all offline tasks done).")
				.handler((opt, values, state) -> {
					GlobalArguments.setParam(PamController.AUTOEXIT, PamController.AUTOEXIT);
					System.out.println("Setting autoexit ON");
				}));
		CommandLineOptions.register(new CommandLineOption(SUMMARY, PROCESSING,
				"Write a JSON summary of the run when PAMGuard exits: version, settings, modules, files processed, number of "
				+ "detections from each data stream, warnings, errors and the exit code.")
				.values("file.json")
				.handler((opt, values, state) -> RunSummary.getInstance().setOutputFile(values[0])));
		CommandLineOptions.register(new CommandLineOption(OfflineTaskManager.commandFlag, PROCESSING,
				"Viewer mode: an offline task to run, by its full name. Use with -batch -autostart.")
				.values("taskName").repeatable()
				.handler((opt, values, state) -> OfflineTaskManager.getManager().addCommandLineTask(values[0])));
		CommandLineOptions.register(new CommandLineOption(ReprocessStoreChoice.paramName, PROCESSING,
				"What to do if output data already exist when reprocessing: STARTNORMAL, CONTINUECURRENTFILE, "
				+ "CONTINUENEXTFILE, OVERWRITEALL or DONTSSTART.")
				.values("choice")
				.handler((opt, values, state) -> {
					try {
						ReprocessStoreChoice.valueOf(values[0].toUpperCase());
					}
					catch (IllegalArgumentException e) {
						throw new CommandLineException(String.format("%s value \"%s\" is not one of STARTNORMAL, CONTINUECURRENTFILE, CONTINUENEXTFILE, OVERWRITEALL, DONTSSTART",
								ReprocessStoreChoice.paramName, values[0]));
					}
					GlobalArguments.setParam(ReprocessStoreChoice.paramName, values[0].toUpperCase());
				}));

		// ---------------------------------------------------------------- network
		CommandLineOptions.register(new CommandLineOption("-port", NETWORK,
				"UDP port on which to receive control commands (start, stop, status, getxmlsettings, setxmlsettings, ...).")
				.values("port")
				.handler((opt, values, state) -> {
					pamBuoyGlobals.setNetworkControlPort(parseInt(opt, values[0]));
					System.out.println("Setting UDP control port " + values[0]);
				}));
		CommandLineOptions.register(new CommandLineOption("-udpResilient", NETWORK,
				"Make UDP control fault resilient.")
				.handler((opt, values, state) -> {
					pamBuoyGlobals.setNetworkControlResilient();
					System.out.println("Setting UDP control to be fault resilient");
				}));
		CommandLineOptions.register(new CommandLineOption("-multicast", NETWORK,
				"Multicast control address and port, used to control several PAMGuards at once.")
				.aliases("-mport").values("address", "port")
				.handler((opt, values, state) -> {
					int mPort = parseInt(opt, values[1]);
					pamBuoyGlobals.setMultiportConfig(values[0], mPort);
					System.out.printf("Setting multicast control addr %s port %d\n", values[0], mPort);
				}));
		for (NetSendCommandParam p : NetSendCommandParam.values()) {
			CommandLineOptions.register(new CommandLineOption(p.arg, NETWORK,
					"Network sender setting " + p.arg.substring("-netSend.".length()) + ".")
					.values("value")
					.handler((opt, values, state) -> GlobalArguments.setParam(p.arg, values[0])));
		}

		// ---------------------------------------------------------------- logging
		CommandLineOptions.register(new CommandLineOption("-nolog", LOGGING,
				"Don't write console output to the log file.")
				.handler((opt, values, state) -> {
					System.out.println("Disabling log file from command line switch...");
					Pamguard.disableLogFile();
				}));
		CommandLineOptions.register(new CommandLineOption(Debug.flag, LOGGING,
				"Print debug messages to the console.")
				.handler((opt, values, state) -> {
					Debug.setPrintDebug(true);
					Debug.out.println("Enabling debug terminal output.");
				}));

		// ---------------------------------------------------------------- developer
		CommandLineOptions.register(new CommandLineOption("-smru", DEVELOPER, "Enable SMRU modules.").hidden()
				.handler((opt, values, state) -> {
					SMRUEnable.setEnable(true);
					System.out.println("Enabling SMRU modules.");
				}));
		CommandLineOptions.register(new CommandLineOption("-decimus", DEVELOPER, "Enable DECIMUS modules.").hidden()
				.handler((opt, values, state) -> {
					SMRUEnable.setEnableDecimus(true);
					System.out.println("Enabling DECIMUS modules.");
				}));
		CommandLineOptions.register(new CommandLineOption("-meygen17", DEVELOPER, "Enable Meygen Turbine 2017 changes.").hidden()
				.handler((opt, values, state) -> {
					SMRUEnable.setMeygen17(true);
					System.out.println("Enabling Meygen Turbine 2017 fudges.");
				}));
		CommandLineOptions.register(new CommandLineOption("-jamie", DEVELOPER, "Enable Jamie Macaulay's development features.").hidden()
				.handler((opt, values, state) -> {
					JamieDev.setEnabled(true);
					System.out.println("Enabling Jamie Macaulay modifications.");
				}));
		CommandLineOptions.register(new CommandLineOption("-smrudev", DEVELOPER, "Enable SMRU development features.").hidden()
				.handler((opt, values, state) -> {
					SMRUEnable.setDevEnable(true);
					System.out.println("Enabling SMRU development modifications.");
				}));
		CommandLineOptions.register(new CommandLineOption("-rocca", DEVELOPER, "Enable Rocca development mode.").hidden()
				.handler((opt, values, state) -> {
					RoccaDev.setEnabled(true);
					System.out.println("Enabling Rocca development mode");
				}));

		// viewer mode options are in their own class
		ViewerCommandLine.registerOptions();
	}

	private static int parseInt(CommandLineOption opt, String value) throws CommandLineException {
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			throw new CommandLineException(String.format("%s needs a whole number, not \"%s\"", opt.getName(), value));
		}
	}
}
