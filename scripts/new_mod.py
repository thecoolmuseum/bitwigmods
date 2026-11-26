"""Create a new mod folder in mymods/ in BML's source layout.

  mise exec -- python scripts/new_mod.py <mod-id> "Area: What it does" [--author NAME]
"""
import argparse, json, re, subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("id"); p.add_argument("name"); p.add_argument("--author", default="Your Name")
a = p.parse_args()
if not re.fullmatch(r"[a-z][a-z0-9-]*", a.id):
    raise SystemExit("mod id: lowercase words joined by '-'")
d = Path(__file__).resolve().parent.parent / "mymods" / a.id
if d.exists():
    raise SystemExit(f"{d} already exists")
(d / "src").mkdir(parents=True)
area = a.name.split(": ", 1)[0] if ": " in a.name else None
mod = {"id": a.id, "name": a.name, "version": "0.1.0-dev.1", "author": a.author,
       "description": "TODO", "documentation": "readme"}
if area: mod["area"] = area
manifest = {"manifest_version": 2, "mod": mod,
            "built_against": {"bitwig_version": "6.1.4", "platform": "windows", "architecture": "x64"},
            "resources": {"readme": {"kind": "file", "path": "README.md", "media_type": "text/markdown"}}}
(d / "mod.json").write_text(json.dumps(manifest, indent=2) + "\n", "utf8")
(d / "build.json").write_text(json.dumps({"release": 21, "kit": True, "classpath_mods": []}, indent=2) + "\n", "utf8")
(d / "README.md").write_text(f"# {a.name}\n\nTODO\n", "utf8")
(d / "src" / ".gitkeep").write_text("", "utf8")
print(f"created {d}\nnext: add @Hook code under src/ (Patchbay/docs/sdk/writing.md), then\n  python scripts/build_mod.py {a.id}")
