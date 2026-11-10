"""
Regenerate the reference pages in this folder from PAMGuard itself, so they always
match the code:

    command_line_options.md   from  -help json
    modules.md                from  -describe (the list of module types)

Usage (from the PAMGuard folder):
    PAMGUARD_CMD="java -jar Pamguard.jar" python docs/generate_docs.py [config.psfx]

A psfx is needed to list the module types; any configuration will do.
"""

import json
import os
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path

DOCS = Path(__file__).resolve().parent


def pamguard(args):
    cmd = shlex.split(os.environ["PAMGUARD_CMD"]) + args
    return subprocess.run(cmd, capture_output=True, text=True, errors="replace").stdout


def first_json(text):
    return json.JSONDecoder().raw_decode(text[text.find("{"):])[0]


def options_page():
    h = first_json(pamguard(["-help", "json"]))
    lines = [
        "# PAMGuard command line options",
        "",
        f"Generated from `-help json` (PAMGuard {h['version']}) by `docs/generate_docs.py`; don't edit by hand.",
        "Options are not case sensitive. See [command_line.md](command_line.md) for how to use them together.",
        "",
    ]
    categories = []
    for o in h["options"]:
        if o["category"] not in categories:
            categories.append(o["category"])
    for cat in categories:
        opts = [o for o in h["options"] if o["category"] == cat and not o.get("hidden")]
        if not opts:
            continue
        title = opts[0].get("categoryTitle") or cat.replace("_", " ").capitalize()
        lines += [f"## {title}", "", "| Option | Description |", "|---|---|"]
        for o in opts:
            usage = o["name"] + "".join(
                f" [{v}]" if o["optionalValue"] else f" <{v}>" for v in o["values"])
            if o.get("aliases"):
                usage += " (also " + ", ".join(o["aliases"]) + ")"
            desc = o["description"] + (" Can be used more than once." if o["repeatable"] else "")
            if o.get("example"):
                desc += f" Example: `{o['example']}`"
            lines.append(f"| `{usage.replace('|', '/')}` | {desc.replace('|', '/')} |")
        lines.append("")
    lines += ["## Exit codes", "", "| Code | Name | Meaning |", "|---|---|---|"]
    for c in h["exitCodes"]:
        lines.append(f"| {c['code']} | {c['name']} | {c['description']} |")
    (DOCS / "command_line_options.md").write_text("\n".join(lines) + "\n")


def modules_page(psfx):
    out = Path(tempfile.mkdtemp()) / "describe.json"
    pamguard(["-nogui", "-psf", psfx, "-describe", str(out)])
    d = json.loads(out.read_text())
    groups = {}
    for m in d["availableModules"]:
        groups.setdefault(m.get("group") or "Other", []).append(m)
    lines = [
        "# PAMGuard modules",
        "",
        f"Module types in PAMGuard {d['version']}, generated from `-describe` by `docs/generate_docs.py`.",
        "Plugins add more. `Needs` is the type of data a module takes as input.",
        "",
    ]
    for g, mods in groups.items():
        lines += [f"## {g}", "", "| Module | Needs | Description | Java class |", "|---|---|---|---|"]
        for m in mods:
            needs = m.get("requires", {}).get("dataType", "")
            needs = needs.split(".")[-1] if needs else ""
            help_text = (m.get("help") or "").replace("|", "/").replace("\n", " ")
            lines.append(f"| {m['description']} | {needs} | {help_text} | `{m['class']}` |")
        lines.append("")
    (DOCS / "modules.md").write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    options_page()
    if len(sys.argv) > 1:
        modules_page(sys.argv[1])
    print("Written", DOCS / "command_line_options.md", "and", DOCS / "modules.md" if len(sys.argv) > 1 else "")
