# PAMGuard MCP server

An [MCP](https://modelcontextprotocol.io) server that lets an AI assistant such as
Claude drive PAMGuard. With it, you can ask an assistant things like:

- "What does `survey.psfx` do, and what's the click detector threshold?"
- "Process the files in `D:\2026\deployment3` with `survey.psfx`, but with the click
  detector threshold at 12 dB, and tell me how many clicks and whistles it found."
- "Reclassify the clicks in last week's database, export them to MATLAB, and open the
  viewer at the busiest minute so I can check them."

The server uses only PAMGuard's own command line and network commands (see
[docs/command_line.md](../docs/command_line.md)), so anything it does you can also do
yourself from a terminal or script.

## Install

Needs Python 3.10 or later and a working PAMGuard (2.03 or later).

```
pip install ./mcp          # from the PAMGuard source folder
```

Then tell your MCP client how to start it. For Claude Desktop or Claude Code, add this
to the MCP servers configuration (`claude_desktop_config.json`, or `.mcp.json`):

```json
{
  "mcpServers": {
    "pamguard": {
      "command": "pamguard-mcp",
      "env": {
        "PAMGUARD_CMD": "java -Xmx8g -Djava.library.path=C:/Program Files/Pamguard/lib64 -jar \"C:/Program Files/Pamguard/Pamguard.jar\""
      }
    }
  }
}
```

`PAMGUARD_CMD` is the command that starts PAMGuard, without any options. With Claude
Code you can also run `claude mcp add pamguard -e PAMGUARD_CMD="..." -- pamguard-mcp`.

Optional: `PAMGUARD_WORK` sets the folder for job logs (default `~/Pamguard/mcp`).

## Tools

| Tool | What it does |
|---|---|
| `pamguard_options` | Every command line option and exit code, as JSON (from `-help json`). |
| `describe_configuration` | The modules, data streams, settings and offline tasks in a psfx or viewer database; with `module`, every parameter of one module (name, type, value, units, description). Also checks `set_parameters` are valid. |
| `start_processing` | Process a folder of sound files without a GUI, optionally changing settings for this run. Returns a job id. |
| `start_viewer_tasks` | Viewer mode without a GUI: run offline tasks (e.g. reclassify clicks) and export detections to MATLAB, R or wav. Returns a job id. |
| `run_pamguard` | Run PAMGuard with any options, as a job. |
| `job_status` | Progress of a job; when finished, the exit code, detections per data stream, warnings and errors. |
| `cancel_job` | Stop a job. |
| `open_viewer` | Open the viewer GUI, optionally at a given time, so a person can check results. |
| `live_command`, `live_describe` | Control a PAMGuard started with `-port`: start, stop, status, `setparam`, `getxmlsettings`, `setxmlsettings`, ... |
| `read_run_summary` | Read a run summary JSON written with `-summary`. |

Settings are changed with `Module name.parameter=value`, e.g.
`Click Detector.dbThreshold=12` or `Whistle and Moan Detector.minLength=12`; dots step
into nested settings (`Click Detector.preFilter.highPassFreq=2000`) and `[n]` picks an
array element. `describe_configuration` gives the exact names.

## Analysing results

PAMGuard's binary files and databases can be read directly in Python, R and MATLAB:

- Python: [pypamguard](https://github.com/PAMGuard/pypamguard) reads binary files (`.pgdf`); the database is SQLite, so `sqlite3` or pandas read it directly.
- R: [PamBinaries](https://github.com/TaikiSan21/PamBinaries) and [PAMpal](https://github.com/TaikiSan21/PAMpal).
- MATLAB: the [PAMGuard MATLAB library](https://github.com/PAMGuard/PAMGuardMatlab), or export to `.mat` with `start_viewer_tasks`.

So a typical workflow is: `describe_configuration` → `start_processing` → `job_status`
→ analyse in Python → `start_viewer_tasks` for reprocessing or exports → `open_viewer`
for a person to check the interesting parts.

## Testing

`tests/smoke_test.py` starts the server, lists its tools and, if you give it a psfx and
a folder of sound files, runs a short job end to end:

```
PAMGUARD_CMD="java -jar Pamguard.jar" python mcp/tests/smoke_test.py config.psfx /data/wav
```
