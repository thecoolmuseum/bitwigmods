"""Look up what Bitwig 6.1.4 names became in 6.1.7 (BML's map pack).

  mise exec -- python scripts/bwmap.py <class>            a class: its new name
  mise exec -- python scripts/bwmap.py <class> <member>   members of that class called <member> (all descriptors)
Names are the obfuscated ones as written in mods built against 6.1.4.
"""
import gzip, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = next(ROOT.glob("BML-*/maps/windows-6.1.7-map1/remap/windows-6.1.4__windows-6.1.7.tsv.gz"))
rows = [r.split("\t") for r in gzip.open(PACK, "rt", encoding="utf8").read().splitlines()]
cls = {r[1]: r[2] for r in rows if r[0] == "C"}
a = sys.argv[1:]
if len(a) == 1:
    print(a[0], "->", cls.get(a[0], "(not mapped: same name or none)"))
elif len(a) == 2:
    for r in rows:
        if r[0] in "MF" and r[1] == a[0] and r[2] == a[1]:
            print(r[0], f"{r[1]}.{r[2]}{r[3]}  ->  {r[4]}.{r[5]}{r[6]}")
