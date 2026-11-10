# Running PAMGuard from the command line, scripts and AI assistants

PAMGuard can be run without its GUI, so that a script, a batch job or an AI
assistant can process data, change settings, reprocess and export results, and then
hand over to a person to check the results in the viewer. This page explains how the
pieces fit together. Every option is listed in
[command_line_options.md](command_line_options.md), and PAMGuard prints the same list
with `-help` (or `-help json` for a machine readable version).

In the examples, `pamguard` stands for however you start PAMGuard, e.g.
`java -Xmx8g -jar Pamguard.jar` or the full path to `Pamguard.exe`.

## The workflow

1. **Find out what a configuration does**: `-describe`
2. **Change settings for a run**: `-set` and `-settings`
3. **Process sound files**: `-nogui -psf ... -wavfilefolder ... -autostart -autoexit`
4. **Check what happened**: `-summary` and the exit code
5. **Reprocess and export in viewer mode**: `-v -nogui -offlinetask ... -export ...`
6. **Let a person check the results**: `-v -viewtime ...`

The [MCP server](../mcp/README.md) wraps all of this as tools for AI assistants.

## 1. Describe a configuration

```
pamguard -nogui -psf survey.psfx -describe survey.json
```

writes a JSON description of the configuration and exits. It contains:

- `modules`: every module, its type, and the data streams it produces (with their
  inputs, channels and sample rates).
- `settings`: every settings object, with every parameter's name, type, current
  value, units, description and (for enums) the allowed choices. `setKey` is the name
  to use with `-set`.
- `offlineTasks`: the offline tasks available, by the name to use with `-offlinetask`.
- `availableModules`: every module type that could be added.

Add `-v -databasefile db.sqlite3` instead of `-psf` to describe a viewer database.

## 2. Change settings

`-set` changes one parameter for this run:

```
pamguard -nogui -psf survey.psfx -set "Click Detector.dbThreshold=12" \
    -set "Click Detector.preFilter.highPassFreq=2000" -set "FFT (Spectrogram) Engine.fftLength=1024" ...
```

The key is the `setKey` from `-describe` (normally the module name), a dot, and the
parameter name. Dots step into nested settings and `[n]` picks an array element
(`Array Manager.hydrophonesList[0]...`). Values are converted to the parameter's type:
numbers, `true`/`false`, enum names (any case), and comma separated lists for arrays.
If a name or value is wrong, PAMGuard says what the choices are and exits with code 3.

`-settings file.xml` applies settings from XML, in the format written by the XML
settings export or the `getxmlsettings` network command. Only the fields in the file
are changed, so a file can hold just the settings you want to change. This is the way
to change settings that `-describe` marks `"settable": false`.

Both are applied as soon as the settings are loaded and before any module is created,
so modules start with the new settings exactly as though they were in the psfx. The
psfx itself isn't changed.

## 3. Process sound files

```
pamguard -nogui -psf survey.psfx -wavfilefolder /data/wav -binaryfolder /data/out/binary \
    -databasefile /data/out/survey.sqlite3 -summary /data/out/run.json -autostart -autoexit
```

`-nogui` runs without any windows, so it also works on a server. `-autostart` starts
processing once everything is loaded and `-autoexit` exits when the last file is done.
Warnings that would normally pop up as dialogs are printed and recorded in the summary.

## 4. Check what happened

`-summary file.json` writes a summary when PAMGuard exits, however it exits:

- `exitCode`, `exitCodeName`, `success`
- `settingsFile`, `commandLine`, `settingsChanges` (each `-set`, with old and new values)
- `modules` and `dataStreams`, with `unitsAdded`: how many data units (detections,
  FFT frames, ...) each data stream produced
- `soundFiles`: how many files were processed, their total duration, and each file
- `warnings` and `errors`, `unrecognisedOptions`

The exit code says whether it worked without reading anything:

| Code | Meaning |
|---|---|
| 0 | OK |
| 1 | Error (see the summary) |
| 2 | Invalid command line arguments |
| 3 | Settings could not be loaded or a `-set`/`-settings` change could not be applied |
| 4 | Processing could not start: a module failed to prepare |
| 5 | An offline task or export failed |

(Before 2.03, a successful `-autoexit` run exited with 6. Scripts which tested for 6
should test for 0.)

## 5. Reprocess and export in viewer mode

```
pamguard -v -nogui -databasefile survey.sqlite3 -binaryfolder /data/out/binary \
    -set "Click Detector.<classifier setting>=..." \
    -offlinetask "Click Detector:Click Detector:Reclassify Clicks" -autostart \
    -export /data/out/export -exportformat MATLAB -exportdata "Click Detector, Clicks" \
    -summary viewer.json -autoexit
```

- `-offlinetask` runs an offline task over all the data (use the names from
  `-describe`). With a GUI the normal progress dialog is shown; without one, progress
  is printed.
- `-export` exports detections to a folder as MATLAB `.mat` files (the default), R, or
  wav clips (`-exportformat wav`). Without `-exportdata`, every data stream that can be
  exported is. The files written are listed in the summary.

## 6. Let a person check the results

```
pamguard -v -databasefile survey.sqlite3 -viewtime "2026-01-31 14:32:05" -viewspan 120
```

opens the viewer with its displays at that time (UTC), so a script or assistant can
point a person straight at the detections it wants checked.

## Controlling a running PAMGuard

Start PAMGuard with `-port 8000` and it accepts commands as UDP packets on that port
(the `-r` and `-nogui` modes can be driven entirely this way). Send `help` for the
list. Useful ones:

| Command | What it does |
|---|---|
| `status` | 0 idle, 1 running, ... |
| `start`, `stop`, `exit` | |
| `describe [file.json]` | The same as `-describe`. Replies over about 60 kB must go to a file. |
| `setparam <key>.<parameter>=<value>` | Change one setting, as with `-set`. Takes effect when processing next starts. |
| `getxmlsettings <unit type> <unit name>` | Settings as XML |
| `setxmlsettings <xml>` | Apply XML settings; only the fields given are changed |
| `listtasks [file.json]` | Offline tasks |
| `runsummary [file.json]` | The run summary so far |

Names with spaces go in double quotes, e.g. `getxmlsettings "Click Detector" "Click Detector"`.

## Reading the results

- Database: SQLite, so any SQLite library, or pandas, reads it.
- Binary files (`.pgdf`): [pypamguard](https://github.com/PAMGuard/pypamguard)
  (`pip install pypamguard`) in Python, [PamBinaries](https://github.com/TaikiSan21/PamBinaries)
  and [PAMpal](https://github.com/TaikiSan21/PAMpal) in R, and the
  [PAMGuard MATLAB library](https://github.com/PAMGuard/PAMGuardMatlab).
- `-export` writes `.mat` files which `scipy.io.loadmat` reads.

## For plugin developers

Command line options are registered in `pamguard.cli.CommandLineOptions`, and the help
is generated from them. A plugin can register its own options with
`CommandLineOptions.register(...)` so they appear in `-help`, and read their values
with `pamguard.CommandLine.getCommandLine().getCommandParameter(...)`.

Settings classes which implement `ManagedParameters` can give each parameter a short
name, description and units (`PamParameterData.setInfo`), which `-describe` passes on.
Those descriptions are what an assistant reads to understand a setting, so they are
worth filling in.
