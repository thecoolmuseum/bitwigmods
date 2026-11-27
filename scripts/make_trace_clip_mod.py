"""Generate mymods/clip-follow-trace/mod.json: a trace-only mod (no code) that logs how the Detail Editor is
given the clip to show, while Patchbay's Debug Mode is on. Names are Bitwig 6.1.7's.

  mise exec -- python scripts/make_trace_clip_mod.py
"""
import json, re, subprocess, shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = r"C:\Program Files\Bitwig Studio\bin\bitwig.jar"

def methods(cls):
    out = subprocess.run(["javap", "-p", "-s", "-cp", JAR, cls], capture_output=True, text=True, check=True).stdout.splitlines()
    for i, line in enumerate(out):
        m = re.match(r"^  ((?:\w+ )*)([\w.$\[\]<>,]+) (\w+)\(.*\);$", line)
        if m and i + 1 < len(out) and "descriptor:" in out[i + 1]:
            yield m.group(1), m.group(3), out[i + 1].split("descriptor:")[1].strip()

# (class, predicate on (modifiers, name, descriptor))
def void_only(mods, name, desc): return desc.endswith(")V") and "abstract" not in mods and "static" not in mods
SPEC = [
    ("baN", lambda m, n, d: "abstract" not in m and "static" not in m and "private" not in m),   # the Detail Editor panel
    ("aHd", lambda m, n, d: "abstract" not in m and "static" not in m and "private" not in m),   # the piano roll
    ("aNi", lambda m, n, d: n in ("sM3",) or d.startswith("(Ljava/util/List;")),
]
targets, trace = {}, []
for cls, pred in SPEC:
    for mods, name, desc in methods(cls):
        if not pred(mods, name, desc): continue
        tid = f"{cls}_{name}_{len(targets)}"
        targets[tid] = {"kind": "method", "owner": cls, "name": name, "descriptor": desc}
        trace.append({"id": tid, "target": tid})
manifest = {
    "manifest_version": 2,
    "mod": {"id": "clip-follow-trace", "name": "Piano Roll: Clip Follow Trace (diagnostic)", "version": "0.1.0-dev.1",
            "author": "ada", "description": "Logs how the Detail Editor is given the clip to show, for investigation. No behaviour change.",
            "area": "Piano Roll"},
    "built_against": {"bitwig_version": "6.1.7", "platform": "windows", "architecture": "x64"},
    "targets": targets, "hooks": [], "trace_targets": trace,
}
d = ROOT / "mymods" / "clip-follow-trace"
d.mkdir(parents=True, exist_ok=True)
(d / "mod.json").write_text(json.dumps(manifest, indent=2) + "\n", "utf8")
print(len(trace), "trace targets ->", d / "mod.json")
