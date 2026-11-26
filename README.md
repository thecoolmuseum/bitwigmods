# Patchbay (BML) workspace

Build workspace for Patchbay Mod Loader. Layout (see Patchbay/tools/workspace.py):

| Folder | What | In git |
|---|---|---|
| `Patchbay/` | loader, modkit, frameworks, tools (shaboogen-dev/Patchbay clone) | no, own repo |
| `Mods/` | the mods (shaboogen-dev/Mods clone) | no, own repo |
| `bitwig/jars/<ver>/` | `bitwig<v>.jar` and `libs<v>.jar` copied from a Bitwig install (proprietary) | no |
| `maps/<build>/` | map pack ZIPs (from `BML-<ver>-workspace-maps.zip`) | no |
| `testing/` | generated test installs and results | no |
| `workspace.json` | overrides: BML folder is named `Patchbay`; 6.1.7 jars are read straight from the installed Bitwig | yes |
| `mise.toml` | Java 25.0.4.1 (Temurin) and Python 3.12 | yes |

Run tools from the workspace root with `mise exec -- python Patchbay/tools/build_mod.py <mod-id>`.
Mods target Bitwig 6.1.4: put its `bitwig.jar`/`libs.jar` in `bitwig/jars/6.1.4/` as `bitwig614.jar` and `libs614.jar`.


## My mods

Each mod you develop is one folder in `mymods/<mod-id>/` (BML's source layout: `mod.json`, `build.json`, `src/`, `README.md`). They are tracked in this repository; build outputs (`*.jar`, `*.zip`) are ignored.

```powershell
mise exec -- python scripts/new_mod.py <mod-id> "Area: What it does" --author NAME   # scaffold mymods/<mod-id>
mise exec -- python scripts/build_mod.py <mod-id>                                      # build -> mymods/<mod-id>/releases/
```

`scripts/build_mod.py` wraps `Patchbay/tools/build_mod.py`: it also searches `mymods/` and uses the mise JDK. A mod needs at least one hook or registration before it builds ([writing.md](Patchbay/docs/sdk/writing.md)).
