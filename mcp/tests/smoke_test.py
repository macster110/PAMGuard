"""
Smoke test for the PAMGuard MCP server. Starts the server over stdio, lists its tools
and, if a psfx and a sound folder are given, describes the configuration, processes the
files and checks the job finishes with detections.

    PAMGUARD_CMD="java -jar Pamguard.jar" python smoke_test.py [config.psfx sound_folder]
"""

import asyncio
import json
import os
import sys
import tempfile
from pathlib import Path

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

SERVER = Path(__file__).resolve().parents[1] / "pamguard_mcp" / "server.py"


def result_json(result):
    if result.is_error if hasattr(result, "is_error") else getattr(result, "isError", False):
        raise AssertionError(result.content[0].text if result.content else "tool error")
    text = result.content[0].text if result.content else "{}"
    return json.loads(text)


async def main(psfx=None, sound_folder=None):
    params = StdioServerParameters(command=sys.executable, args=[str(SERVER)], env=dict(os.environ))
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = [t.name for t in (await session.list_tools()).tools]
            print("tools:", tools)
            assert "start_processing" in tools and "describe_configuration" in tools

            options = result_json(await session.call_tool("pamguard_options", {}))
            print("PAMGuard", options["version"], "with", len(options["options"]), "options")

            if not psfx:
                return
            desc = result_json(await session.call_tool("describe_configuration", {"settings_file": psfx}))
            print("modules:", [m["name"] for m in desc["modules"]])
            module = desc["settingsKeys"][0]["setKey"]
            one = result_json(await session.call_tool("describe_configuration",
                                                      {"settings_file": psfx, "module": module}))
            assert one["settings"], one

            out = tempfile.mkdtemp(prefix="pgmcp")
            job = result_json(await session.call_tool("start_processing", {
                "settings_file": psfx, "sound_folder": sound_folder, "output_folder": out}))
            print("job", job["jobId"])
            while True:
                status = result_json(await session.call_tool("job_status", {"job_id": job["jobId"]}))
                if status["status"] != "running":
                    break
                await asyncio.sleep(2)
            print(json.dumps(status.get("summary"), indent=1)[:2000])
            assert status["exitCode"] == 0, status
            assert status["summary"]["soundFiles"]["count"] > 0
            print("OK")


if __name__ == "__main__":
    asyncio.run(main(*sys.argv[1:3]))
