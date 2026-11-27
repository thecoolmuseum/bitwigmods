"""Build a mod from mymods/<mod-id> with BML's build_mod.py (same arguments).

BML's tools only look for mods in Patchbay/frameworks and Mods/; this adds mymods/ to that search, and
uses the JDK on PATH (mise: Java 25.0.4.1) instead of the fixed "Program Files" Java path.
  mise exec -- python scripts/build_mod.py <mod-id> [--check | --replace | --out DIR]
"""
import shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = next(p for p in (ROOT / "Patchbay" / "tools", ROOT / "BML" / "tools") if p.is_dir())
sys.path.insert(0, str(TOOLS))
import workspace as ws  # noqa: E402

ws.MOD_FOLDERS = (*ws.MOD_FOLDERS, ROOT / "mymods")
import build_mod  # noqa: E402

javac = shutil.which("javac")
if not javac:
    raise SystemExit("javac not found: run through `mise exec --`")
build_mod.JDK = Path(javac).resolve().parent
build_mod.modkit.JDK = build_mod.JDK  # the mod kit's own build checks the JDK too
sys.exit(build_mod.main())

