"""Copies every Phosphor glyph Halo.kt and assets/glyph-map.txt name into app/src/main/assets/glyphs.txt. Run after adding an app to GLYPHS."""
import re
import urllib.request
from pathlib import Path

VERSION = "2.1.1"
root = Path(__file__).resolve().parent.parent
names = set(re.findall(r'to "([a-z0-9-]+)"', (root / "app/src/main/java/app/fluid/launcher/Halo.kt").read_text()))
local = root / "app/src/main/assets/glyph-map.txt"  # gitignored, your own package -> glyph pairs
if local.exists():
    names |= {l.split("\t")[1].strip() for l in local.read_text().splitlines() if "\t" in l and not l.startswith("#")}
names = sorted(names)
lines = [f"# Phosphor Icons {VERSION}, regular weight. MIT License, https://phosphoricons.com"]
for name in names:
    url = f"https://cdn.jsdelivr.net/npm/@phosphor-icons/core@{VERSION}/assets/regular/{name}.svg"
    svg = urllib.request.urlopen(url).read().decode()
    lines.append(name + "\t" + " ".join(re.findall(r'<path[^>]*\sd="([^"]+)"', svg)))
out = root / "app/src/main/assets/glyphs.txt"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(lines) + "\n")
print(len(names), "glyphs ->", out)
