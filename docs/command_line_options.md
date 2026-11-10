# PAMGuard command line options

Generated from `-help json` (PAMGuard 2.02.19f) by `docs/generate_docs.py`; don't edit by hand.
Options are not case sensitive. See [command_line.md](command_line.md) for how to use them together.

## Information

| Option | Description |
|---|---|
| `-help [json/all] (also --help, -h, -?)` | Show this help and exit. Use -help json for a machine readable version, or -help all to include developer options. |
| `-version [json] (also --version)` | Show the PAMGuard version and exit. Use -version json for a machine readable version. |

## Run mode

| Option | Description |
|---|---|
| `-v` | Viewer mode: open a database and binary store to view, check and reprocess data. |
| `-m` | Mixed mode: analyse sound files while reading other data, such as GPS, back from the database. |
| `-nr` | Network receiver mode: receive and display data sent from other PAMGuards. |
| `-r` | Remote mode: normal mode without a GUI, normally controlled over the network (see -port). |
| `-c` | Show the start up dialog to choose the run mode and the settings file or database. |
| `-openfile <file>` | Open a settings file (normal mode) or a database (viewer mode). The file type sets the run mode. |
| `-batch` | Batch processing: avoids dialogs that would otherwise wait for a user. Set by the batch processor. |

## User interface

| Option | Description |
|---|---|
| `-nogui` | Run without any GUI. Use with -psf and -autostart -autoexit for unattended processing. |
| `-swing` | Use the Swing GUI (the default). |
| `-fx` | Use the JavaFX GUI. |
| `-nosplash` | Don't show the splash screen. |
| `-fullscreen` | Start with the main window full screen. |

## Settings

| Option | Description |
|---|---|
| `-psf <file> (also -psfx)` | Settings file (.psfx) to use in normal mode, without asking. |
| `-settings <file.xml>` | Apply module settings from an XML file once the configuration has loaded. The file format is the one written by File > Export XML settings or the getxmlsettings network command; only the modules in the file are changed. Can be used more than once. |
| `-set <module.parameter=value>` | Change one setting once the configuration has loaded. The key is the module name, then a dot, then the parameter name; dots step into nested objects and [n] picks an array element. Use -describe to find names. Changes apply to this run only and are made before any module is created; the settings file is not changed. Can be used more than once. Example: `-set "Click Detector.dbThreshold=12"` |
| `-describe [file.json]` | Once the configuration has loaded, write a JSON description of every module (type, name, class) and every setting (name, type, current value, description) plus the list of module types that can be added, then exit. Writes to the file if given, otherwise to the console. Applied after -settings and -set. |

## Input and output

| Option | Description |
|---|---|
| `-wavfilefolder <folder>` | Folder (or single file) of sound files to process, overriding the Sound Acquisition settings. |
| `-recording.Prefix <prefix>` | File name prefix for sound files written by the Sound Recorder. |
| `-binaryfolder <folder>` | Output folder for binary files, overriding the Binary Storage settings. |
| `-databasefile <file>` | Database file (e.g. .sqlite3), overriding the Database settings. Created if it doesn't exist. |
| `-scname <name>` | Name of the sound card to record from. |
| `-scnumber <number>` | Number of the sound card to record from. |
| `-serialPort <port>` | Serial port for NMEA (e.g. GPS) data. |
| `-array.firstHydrophone <index>` | Index of the first hydrophone to use from the array settings. |
| `-modelPath <file>` | Model file for the Deep Learning Classifier, overriding its settings. |

## Batch processing and automation

| Option | Description |
|---|---|
| `-autostart` | Start processing as soon as PAMGuard has loaded. In viewer mode, runs the -offlinetask tasks. |
| `-autoexit` | Exit when processing is complete (all sound files processed, or all offline tasks done). |
| `-summary <file.json>` | Write a JSON summary of the run when PAMGuard exits: version, settings, modules, files processed, number of detections from each data stream, warnings, errors and the exit code. |
| `-offlinetask <taskName>` | Viewer mode: an offline task to run, by its full name. Use with -batch -autostart. Can be used more than once. |
| `-reprocessoption <choice>` | What to do if output data already exist when reprocessing: STARTNORMAL, CONTINUECURRENTFILE, CONTINUENEXTFILE, OVERWRITEALL or DONTSSTART. |

## Network control and data sending

| Option | Description |
|---|---|
| `-port <port>` | UDP port on which to receive control commands (start, stop, status, getxmlsettings, setxmlsettings, ...). |
| `-udpResilient` | Make UDP control fault resilient. |
| `-multicast <address> <port> (also -mport)` | Multicast control address and port, used to control several PAMGuards at once. |
| `-netSend.active <value>` | Network sender setting active. |
| `-netSend.address <value>` | Network sender setting address. |
| `-netSend.port <value>` | Network sender setting port. |
| `-netSend.id1 <value>` | Network sender setting id1. |
| `-netSend.id2 <value>` | Network sender setting id2. |
| `-netSend.user <value>` | Network sender setting user. |
| `-netSend.password <value>` | Network sender setting password. |
| `-netSend.ssl <value>` | Network sender setting ssl. |
| `-netSend.mqtt <value>` | Network sender setting mqtt. |
| `-netSend.trustPath <value>` | Network sender setting trustPath. |
| `-netSend.trustPass <value>` | Network sender setting trustPass. |
| `-netSend.keyPath <value>` | Network sender setting keyPath. |
| `-netSend.keyPass <value>` | Network sender setting keyPass. |
| `-netSend.json <value>` | Network sender setting json. |
| `-netSend.persistanceDir <value>` | Network sender setting persistanceDir. |

## Logging and debugging

| Option | Description |
|---|---|
| `-nolog` | Don't write console output to the log file. |
| `-debugout` | Print debug messages to the console. |

## Viewer mode

| Option | Description |
|---|---|
| `-viewtime <time>` | Open the viewer displays at this time, e.g. to check particular detections. Times are UTC, as 2026-01-31T14:32:05Z, 2026-01-31 14:32:05, 2026-01-31, or milliseconds since 1970. Example: `-v -databasefile survey.sqlite3 -viewtime "2026-01-31 14:32:05" -viewspan 120` |
| `-viewspan <seconds>` | Length of data to load around -viewtime, in seconds. |
| `-export <folder>` | Export detections to this folder once the viewer has loaded, then exit if -autoexit is set. Exports every data stream that can be exported unless -exportdata is given. Runs after any -offlinetask tasks. Example: `-v -nogui -databasefile survey.sqlite3 -export /data/export -exportformat MATLAB -autoexit` |
| `-exportformat <format>` | Export format for -export: MATLAB (the default; .mat files), R, or wav ("raw sound" clips). Matched against the exporter name or file extension, ignoring case. |
| `-exportdata <dataStream>` | A data stream to export with -export, by its long name (e.g. "Click Detector, Clicks") or its name. -describe lists the data streams. Can be used more than once. |

## Exit codes

| Code | Name | Meaning |
|---|---|---|
| 0 | OK | Completed normally |
| 1 | ERROR | Error |
| 2 | BAD_ARGUMENTS | Invalid command line arguments |
| 3 | SETTINGS_ERROR | Settings could not be loaded or applied |
| 4 | PREPARE_FAILED | Processing could not start: one or more modules failed to prepare |
| 5 | TASK_FAILED | An offline task or export failed |
