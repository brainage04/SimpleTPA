"""Copy the verified UI Void scene to this directory; never modify its source."""
from pathlib import Path
import json
import shutil

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "captures-ui"
RUNTIME = HERE / "runtime"
RUNTIME.mkdir(exist_ok=True)
for directory in ("saves", "config", "mods", "resourcepacks", ".fabric"):
    if (SOURCE / "runtime" / directory).exists():
        shutil.copytree(SOURCE / "runtime" / directory, RUNTIME / directory, dirs_exist_ok=True)
options = (SOURCE / "runtime/options.txt").read_text().splitlines()
changes = {"soundDevice": '"Round3Crops2"', "guiScale": "4", "maxFps": "20", "renderDistance": "2", "simulationDistance": "2", "renderClouds": '"false"', "soundCategory_master": "0.01"}
(RUNTIME / "options.txt").write_text("\n".join(line.split(":", 1)[0] + ":" + changes.get(line.split(":", 1)[0], line.split(":", 1)[1]) for line in options) + "\n")
(RUNTIME / "alsoft.conf").write_text("[general]\ndrivers=pulse\n[pulse]\ndefault-sink=round3_crops2\n")
args = (SOURCE / "client-ui.args").read_text().replace(str(SOURCE / "runtime"), str(RUNTIME))
args = args.replace("--width\n3840", "--width\n5120")
args = "-XX:ActiveProcessorCount=8\n" + args
(HERE / "client-crops2.args").write_text(args)
launcher = (SOURCE / "launch-ui.sh").read_text()
launcher = launcher.replace(str(SOURCE), str(HERE)).replace("client-ui.args", "client-crops2.args").replace("DISPLAY=:182", "DISPLAY=:197")
launcher = launcher.replace("captures-ui", "captures-crops2").replace(":182", ":197").replace("round3_ui_mcscreens", "round3_crops2")
launcher = launcher.replace("exec /nix/store", 'export PULSE_SINK=round3_crops2\nexport LIBGL_ALWAYS_SOFTWARE=1\nexport LP_NUM_THREADS=8\nexec /nix/store')
(HERE / "launch-crops2.sh").write_text(launcher)
(HERE / "frames").mkdir(exist_ok=True)
(HERE / "evidence").mkdir(exist_ok=True)
print(json.dumps({"source": str(SOURCE), "destination": str(HERE), "width": 5120, "height": 2160, "guiScale": 4, "display": ":197", "sink": "round3_crops2"}, indent=2))
