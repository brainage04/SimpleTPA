"""Capture only on our Xvfb and verify our audio route before interaction."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
RUNTIME = HERE / "runtime"
ENV = {**os.environ, "DISPLAY": ":197"}
LOG = RUNTIME / "logs/latest.log"

def run(*args):
    return subprocess.run(args, env=ENV, check=True, text=True, capture_output=True).stdout

def key(key_name):
    run("xdotool", "key", "--clearmodifiers", key_name)

def open_chat(text):
    key("t")
    time.sleep(2)
    key("ctrl+a")
    key("BackSpace")
    run("xdotool", "type", "--clearmodifiers", "--delay", "80", text)
    time.sleep(2)

def command(text, pattern):
    mark = LOG.stat().st_size
    open_chat(text)
    key("Return")
    for _ in range(40):
        with LOG.open() as handle:
            handle.seek(mark)
            feedback = handle.read()
        if re.search(pattern, feedback):
            time.sleep(0.5)
            return {"command": text, "feedback": feedback}
        time.sleep(0.25)
    raise RuntimeError(f"Command unverified: {text!r}; log: {feedback}")

def screenshot(name):
    shots = RUNTIME / "screenshots"
    before = set(shots.glob("*.png")) if shots.exists() else set()
    key("F2")
    for _ in range(120):
        fresh = set(shots.glob("*.png")) - before
        if fresh:
            source = max(fresh, key=lambda p: p.stat().st_mtime)
            size = source.stat().st_size
            time.sleep(0.5)
            if size > 1000 and source.stat().st_size == size:
                target = HERE / "frames" / f"{name}.png"
                shutil.copyfile(source, target)
                print(target)
                return {"gameScreenshot": str(source.relative_to(HERE)), "frame": str(target.relative_to(HERE))}
        time.sleep(0.25)
    raise RuntimeError("Minecraft did not write its F2 screenshot")

def main():
    sinks = run("pactl", "list", "short", "sinks")
    inputs = run("pactl", "list", "short", "sink-inputs")
    own = [row.split("\t")[0] for row in sinks.splitlines() if "\tround3_crops2\t" in row]
    assert len(own) == 1 and any(row.split("\t")[1] == own[0] for row in inputs.splitlines()), (sinks, inputs)
    (HERE / "evidence/audio-before-interaction.json").write_text(json.dumps({"sinks": sinks, "sinkInputs": inputs, "ownSinkId": own[0], "soundDevice": 'Round3Crops2'}, indent=2) + "\n")
    windows = run("xdotool", "search", "--name", "Minecraft").splitlines()
    assert windows
    run("xdotool", "windowfocus", "--sync", windows[-1])
    action = sys.argv[1]
    if action == "state":
        screenshot("state")
    elif action == "setup":
        verified = []
        for text, pattern in [("/gamemode creative", "[Cc]reative"), ("/gamemode spectator", "[Ss]pectator"), ("/time set noon", "time marker minecraft:noon"), ("/weather clear", "weather"), ("/tick freeze", "frozen"), ("/tp @s 0.5 -60 0.5 0 -90", "Teleported")]:
            verified.append(command(text, pattern))
        (HERE / "evidence/scene-commands.json").write_text(json.dumps(verified, indent=2) + "\n")
    elif action == "capture":
        tag, typed = sys.argv[2:4] if len(sys.argv) == 4 else ("spawncommands-spawn", "/spawn")
        key("F3+d")
        time.sleep(1)
        open_chat(typed)
        info = screenshot(f"{tag}-wide-full")
        time.sleep(2)
        key("Escape")
        time.sleep(2)
        info.update({"typed": typed, "width": 5120, "height": 2160, "guiScale": 4, "display": ":197", "sky": "UI Void, pitch -90, clouds off; chat cleared with F3+D"})
        (HERE / "evidence" / f"{tag}-wider-capture.json").write_text(json.dumps(info, indent=2) + "\n")
    elif action == "key":
        key(sys.argv[2])
    else:
        raise ValueError(action)

if __name__ == "__main__":
    main()
