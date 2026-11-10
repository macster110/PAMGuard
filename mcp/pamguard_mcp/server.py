"""
MCP server for PAMGuard.

Lets an AI assistant (Claude, or any other MCP client) drive PAMGuard: find out
what a configuration contains, change settings, process sound files without a
GUI, run viewer mode tasks and exports, open the viewer at a given time so a
person can check detections, and control a running PAMGuard over its UDP port.

Everything goes through PAMGuard's own command line and network commands, so the
server needs nothing from PAMGuard except a working installation. See README.md.

Configuration (environment variables):
    PAMGUARD_CMD   command that starts PAMGuard, e.g.
                   "java -Xmx8g -Djava.library.path=/opt/pamguard/lib64 -jar /opt/pamguard/Pamguard.jar"
                   (on Windows, the full path to Pamguard.exe also works).
    PAMGUARD_WORK  folder for job logs and temporary files (default: ~/Pamguard/mcp)
"""

from __future__ import annotations

import json
import os
import shlex
import socket
import subprocess
import tempfile
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

try:
    # mcp 2.x
    from mcp.server.mcpserver import MCPServer as _Server
except ImportError:
    # mcp 1.x
    from mcp.server.fastmcp import FastMCP as _Server

mcp = _Server("pamguard")

EXIT_CODES = {
    0: "OK: completed normally",
    1: "ERROR: see errors in the summary and the log",
    2: "BAD_ARGUMENTS: a command line option was missing a value or had an invalid one",
    3: "SETTINGS_ERROR: settings could not be loaded or a settings change could not be applied",
    4: "PREPARE_FAILED: processing could not start because a module failed to prepare",
    5: "TASK_FAILED: an offline task or export failed",
}


# --------------------------------------------------------------------------- helpers

def pamguard_command() -> list[str]:
    cmd = os.environ.get("PAMGUARD_CMD")
    if not cmd:
        raise RuntimeError(
            "PAMGUARD_CMD is not set. Set it to the command that starts PAMGuard, e.g. "
            "\"java -Xmx8g -jar /path/to/Pamguard.jar\"")
    return shlex.split(cmd, posix=(os.name != "nt"))


def work_folder() -> Path:
    folder = Path(os.environ.get("PAMGUARD_WORK", Path.home() / "Pamguard" / "mcp"))
    folder.mkdir(parents=True, exist_ok=True)
    return folder


def run_sync(args: list[str], timeout: float = 600) -> tuple[int, str]:
    """Run PAMGuard to completion. Returns (exit code, console output)."""
    proc = subprocess.run(pamguard_command() + args, capture_output=True, text=True,
                          timeout=timeout, errors="replace")
    return proc.returncode, proc.stdout + proc.stderr


def read_json(path: Path) -> Any:
    with open(path, encoding="utf-8", errors="replace") as f:
        return json.load(f)


def tail(text: str, n: int = 40) -> str:
    lines = [l for l in text.splitlines() if l.strip()]
    return "\n".join(lines[-n:])


def condense_summary(summary: dict) -> dict:
    """Keep the parts of a run summary an assistant needs, dropping empty data streams."""
    out = {k: summary.get(k) for k in (
        "version", "runMode", "settingsFile", "exitCode", "exitCodeName", "success",
        "startTime", "endTime", "durationSeconds", "settingsChanges", "exports",
        "unrecognisedOptions", "errors")}
    files = summary.get("soundFiles", {})
    out["soundFiles"] = {"count": files.get("count"),
                         "totalDurationSeconds": files.get("totalDurationSeconds")}
    if files.get("files"):
        out["soundFiles"]["first"] = files["files"][0].get("file")
        out["soundFiles"]["last"] = files["files"][-1].get("file")
    out["dataStreams"] = [
        {"name": s.get("longName"), "dataUnitClass": s.get("dataUnitClass"), "count": s.get("unitsAdded")}
        for s in summary.get("dataStreams", []) if s.get("unitsAdded")]
    warnings = summary.get("warnings", [])
    out["warningCount"] = len(warnings)
    out["warnings"] = [f"{w.get('title')}: {w.get('message')}" for w in warnings[:20]]
    return out


# --------------------------------------------------------------------------- jobs

@dataclass
class Job:
    id: str
    kind: str
    args: list[str]
    folder: Path
    summary_file: Path
    process: Optional[subprocess.Popen] = None
    started: float = field(default_factory=time.time)
    ended: Optional[float] = None
    exit_code: Optional[int] = None


JOBS: dict[str, Job] = {}


def start_job(kind: str, args: list[str], folder: Path) -> Job:
    folder.mkdir(parents=True, exist_ok=True)
    job_id = f"{kind}-{time.strftime('%Y%m%d-%H%M%S')}-{uuid.uuid4().hex[:6]}"
    summary_file = folder / f"{job_id}_summary.json"
    full_args = args + ["-summary", str(summary_file)]
    job = Job(job_id, kind, full_args, folder, summary_file)
    log = open(folder / f"{job_id}.log", "w", encoding="utf-8", errors="replace")
    job.process = subprocess.Popen(pamguard_command() + full_args, stdout=log, stderr=subprocess.STDOUT)

    def wait():
        job.exit_code = job.process.wait()
        job.ended = time.time()
        log.close()

    threading.Thread(target=wait, daemon=True).start()
    JOBS[job_id] = job
    return job


def job_info(job: Job) -> dict:
    info: dict[str, Any] = {
        "jobId": job.id,
        "kind": job.kind,
        "status": "running" if job.exit_code is None else ("completed" if job.exit_code == 0 else "failed"),
        "elapsedSeconds": round((job.ended or time.time()) - job.started, 1),
        "outputFolder": str(job.folder),
        "log": str(job.folder / f"{job.id}.log"),
        "commandLine": job.args,
    }
    if job.exit_code is not None:
        info["exitCode"] = job.exit_code
        info["exitMeaning"] = EXIT_CODES.get(job.exit_code, "unknown")
        if job.summary_file.exists():
            info["summary"] = condense_summary(read_json(job.summary_file))
            info["summaryFile"] = str(job.summary_file)
    try:
        log_text = (job.folder / f"{job.id}.log").read_text(errors="replace")
        info["logTail"] = tail(log_text, 15)
    except OSError:
        pass
    return info


def settings_args(settings_file: Optional[str], set_parameters: Optional[list[str]],
                  settings_xml: Optional[str]) -> list[str]:
    args: list[str] = []
    if settings_file:
        args += ["-psf", settings_file]
    for s in set_parameters or []:
        args += ["-set", s]
    if settings_xml:
        args += ["-settings", settings_xml]
    return args


# --------------------------------------------------------------------------- tools: discovery

@mcp.tool()
def pamguard_options() -> dict:
    """List every PAMGuard command line option (name, values, description) and the exit codes.
    Use this to find options the other tools don't cover; run_pamguard accepts any of them."""
    code, out = run_sync(["-help", "json"], timeout=120)
    start = out.find("{")
    if start < 0:
        return {"error": tail(out)}
    # the JSON may be followed by other console output, e.g. from the JVM
    return json.JSONDecoder().raw_decode(out[start:])[0]


@mcp.tool()
def describe_configuration(settings_file: Optional[str] = None, database: Optional[str] = None,
                           binary_folder: Optional[str] = None, module: Optional[str] = None,
                           set_parameters: Optional[list[str]] = None) -> dict:
    """Describe a PAMGuard configuration without running it.

    Give settings_file (a .psfx) for a processing configuration, or database (and
    binary_folder) for viewer mode. Without module, returns the modules, their data
    streams, the settings objects (by the key to use with set_parameters), offline tasks
    and the module types that could be added. With module (a setKey or module name),
    returns every parameter of that module's settings: name, type, current value, units,
    description and allowed choices.

    set_parameters are applied first ("Module name.parameter=value"), so this also checks
    that changes are valid before a long run.
    """
    out_file = Path(tempfile.mkdtemp(prefix="pgdescribe")) / "describe.json"
    args = ["-nogui"]
    if database:
        args += ["-v", "-databasefile", database]
        if binary_folder:
            args += ["-binaryfolder", binary_folder]
    args += settings_args(settings_file, set_parameters, None)
    args += ["-describe", str(out_file)]
    code, log = run_sync(args, timeout=300)
    if code != 0 or not out_file.exists():
        return {"exitCode": code, "exitMeaning": EXIT_CODES.get(code, "unknown"), "log": tail(log)}
    d = read_json(out_file)
    if module:
        matches = [s for s in d.get("settings", [])
                   if module in (s.get("setKey"), s.get("unitName"))
                   or module.lower() in str(s.get("unitName", "")).lower()]
        return {"settings": matches,
                "howToChange": d.get("howToChange")} if matches else {
            "error": f"No settings for '{module}'",
            "setKeys": sorted({s.get("setKey") for s in d.get("settings", [])})}
    return {
        "version": d.get("version"),
        "runMode": d.get("runMode"),
        "settingsFile": d.get("settingsFile"),
        "modules": [{"type": m["type"], "name": m["name"],
                     "dataStreams": [b["longName"] for b in m.get("dataBlocks", [])]}
                    for m in d.get("modules", [])],
        "settingsKeys": [{"setKey": s["setKey"], "class": s["settingsClass"],
                          "parameters": len(s.get("parameters", []))}
                         for s in d.get("settings", []) if s.get("parameters")],
        "offlineTasks": d.get("offlineTasks"),
        "availableModules": [m.get("description") for m in d.get("availableModules", [])],
        "howToChange": d.get("howToChange"),
        "fullDescriptionFile": str(out_file),
    }


# --------------------------------------------------------------------------- tools: processing

@mcp.tool()
def start_processing(settings_file: str, sound_folder: str, output_folder: str,
                     set_parameters: Optional[list[str]] = None, settings_xml: Optional[str] = None,
                     extra_options: Optional[list[str]] = None) -> dict:
    """Process a folder of sound files with a PAMGuard configuration, without a GUI.

    Detections go to output_folder/binary (PAMGuard binary files) and
    output_folder/pamguard.sqlite3 (database). set_parameters change settings for this
    run only, e.g. ["Click Detector.dbThreshold=12"] (use describe_configuration to find
    names). Returns a job id at once; call job_status to follow it. When finished, the
    status includes the number of detections from each data stream, warnings and errors.
    """
    out = Path(output_folder)
    args = ["-nogui"] + settings_args(settings_file, set_parameters, settings_xml)
    args += ["-wavfilefolder", sound_folder,
             "-binaryfolder", str(out / "binary"),
             "-databasefile", str(out / "pamguard.sqlite3"),
             "-autostart", "-autoexit"]
    args += extra_options or []
    return job_info(start_job("process", args, out))


@mcp.tool()
def start_viewer_tasks(database: str, binary_folder: Optional[str] = None,
                       offline_tasks: Optional[list[str]] = None,
                       export_folder: Optional[str] = None, export_format: str = "MATLAB",
                       export_data: Optional[list[str]] = None,
                       set_parameters: Optional[list[str]] = None) -> dict:
    """Run viewer mode jobs on processed data, without a GUI.

    offline_tasks are full task names as listed by describe_configuration (database=...),
    e.g. "Click Detector:Click Detector:Reclassify Clicks"; use set_parameters to change
    the module settings the tasks use. export_folder exports detections (MATLAB .mat, R,
    or wav clips) for analysis in Python, R or MATLAB; export_data limits which data
    streams are exported. Returns a job id; call job_status to follow it.
    """
    args = ["-v", "-nogui", "-databasefile", database]
    if binary_folder:
        args += ["-binaryfolder", binary_folder]
    args += settings_args(None, set_parameters, None)
    for t in offline_tasks or []:
        args += ["-offlinetask", t]
    if offline_tasks:
        args += ["-autostart"]
    if export_folder:
        args += ["-export", export_folder, "-exportformat", export_format]
        for d in export_data or []:
            args += ["-exportdata", d]
    args += ["-autoexit"]
    folder = Path(export_folder) if export_folder else work_folder()
    return job_info(start_job("viewer", args, folder))


@mcp.tool()
def run_pamguard(options: list[str], output_folder: Optional[str] = None) -> dict:
    """Run PAMGuard with any command line options (see pamguard_options), as a job.
    A run summary is always written. Add -nogui unless a person is going to use the GUI."""
    return job_info(start_job("run", list(options), Path(output_folder) if output_folder else work_folder()))


@mcp.tool()
def job_status(job_id: Optional[str] = None) -> dict:
    """Status of a job, or of all jobs if no id is given. A finished job includes its exit
    code and meaning, the number of detections from each data stream, warnings and errors."""
    if job_id is None:
        return {"jobs": [job_info(j) for j in JOBS.values()]}
    job = JOBS.get(job_id)
    return job_info(job) if job else {"error": f"No job {job_id}", "jobs": list(JOBS)}


@mcp.tool()
def cancel_job(job_id: str) -> dict:
    """Stop a running job."""
    job = JOBS.get(job_id)
    if not job:
        return {"error": f"No job {job_id}"}
    if job.exit_code is None and job.process:
        job.process.terminate()
    return job_info(job)


@mcp.tool()
def open_viewer(database: str, binary_folder: Optional[str] = None, time_utc: Optional[str] = None,
                span_seconds: Optional[float] = None) -> dict:
    """Open the PAMGuard viewer (with its GUI) for a person to check results, optionally at a
    given time, e.g. time_utc="2026-01-31 14:32:05", span_seconds=120. Returns at once; the
    viewer stays open until the person closes it."""
    args = ["-v", "-databasefile", database]
    if binary_folder:
        args += ["-binaryfolder", binary_folder]
    if time_utc:
        args += ["-viewtime", time_utc]
    if span_seconds:
        args += ["-viewspan", str(span_seconds)]
    job = start_job("viewer-gui", args, work_folder())
    return {"jobId": job.id, "message": "Viewer opening" + (f" at {time_utc}" if time_utc else "")}


# --------------------------------------------------------------------------- tools: live control

def udp_command(port: int, command: str, host: str = "127.0.0.1", timeout: float = 30) -> str:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.settimeout(timeout)
        s.sendto(command.encode(), (host, port))
        data, _ = s.recvfrom(65535)
        return data.decode(errors="replace")


@mcp.tool()
def live_command(port: int, command: str, host: str = "127.0.0.1") -> str:
    """Send a network command to a PAMGuard started with -port <port>. Commands include
    help, ping, status, start, stop, summary, getxmlsettings <type> <name>,
    setxmlsettings <xml>, setparam <module>.<parameter>=<value>, listtasks, runsummary, exit."""
    return udp_command(port, command, host)


@mcp.tool()
def live_describe(port: int, module: Optional[str] = None, host: str = "127.0.0.1") -> Any:
    """Describe the configuration of a running PAMGuard (started with -port), optionally just
    one module's settings. PAMGuard must be on this computer, since the description is
    passed through a file."""
    out_file = Path(tempfile.mkdtemp(prefix="pglive")) / "describe.json"
    reply = udp_command(port, f"describe {out_file}", host)
    if not out_file.exists():
        return {"error": reply}
    d = read_json(out_file)
    if module:
        return [s for s in d.get("settings", []) if module in (s.get("setKey"), s.get("unitName"))]
    return {"modules": d.get("modules"), "settingsKeys": [s["setKey"] for s in d.get("settings", [])],
            "fullDescriptionFile": str(out_file)}


@mcp.tool()
def read_run_summary(summary_file: str, full: bool = False) -> dict:
    """Read a run summary JSON written by PAMGuard's -summary option."""
    d = read_json(Path(summary_file))
    return d if full else condense_summary(d)


def main():
    mcp.run()


if __name__ == "__main__":
    main()
