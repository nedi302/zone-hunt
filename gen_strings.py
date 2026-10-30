# -*- coding: utf-8 -*-
import os

base = r"E:\project"
entries = []
for name in ("i18n_part1.txt", "i18n_part2.txt", "i18n_part3.txt", "i18n_extra.txt"):
    path = os.path.join(base, name)
    with open(path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.rstrip("\n").rstrip("\r")
            if not line:
                continue
            parts = line.split("|||")
            if len(parts) != 5:
                raise SystemExit(f"{name}:{lineno}: expected 5 fields, got {len(parts)}: {line[:80]}")
            entries.append(parts)

plurals = [
    ("coins", ("%1$d coin", "%1$d coins"), ("%1$d Münze", "%1$d Münzen"), ("%1$d moneda", "%1$d monedas"), ("%1$d pièce", "%1$d pièces")),
    ("hiders_count", ("%1$d hider", "%1$d hiders"), ("%1$d Verstecker", "%1$d Verstecker"), ("%1$d escondido", "%1$d escondidos"), ("%1$d cacheur", "%1$d cacheurs")),
    ("players_count", ("%1$d player", "%1$d players"), ("%1$d Spieler", "%1$d Spieler"), ("%1$d jugador", "%1$d jugadores"), ("%1$d joueur", "%1$d joueurs")),
]

def esc(v):
    v = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace("'", "\\'")
    # Outer quotes are removed by AAPT but stop it from trimming leading or
    # trailing whitespace. Several concatenated UI strings rely on that.
    return '"' + v + '"'

langs = ["en", "de", "es", "fr"]
for idx, lang in enumerate(langs):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key, *vals in entries:
        lines.append(f'    <string name="{key}">{esc(vals[idx])}</string>')
    for key, *vals in plurals:
        lines.append(f'    <plurals name="{key}">')
        lines.append(f'        <item quantity="one">{esc(vals[idx][0])}</item>')
        lines.append(f'        <item quantity="other">{esc(vals[idx][1])}</item>')
        lines.append("    </plurals>")
    lines.append("</resources>")
    folder = "values" if lang == "en" else f"values-{lang}"
    outdir = os.path.join(r"E:\project\app\src\main\res", folder)
    os.makedirs(outdir, exist_ok=True)
    outpath = os.path.join(outdir, "strings.xml")
    with open(outpath, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", outpath, len(entries) + len(plurals), "resources")

keys = [e[0] for e in entries]
dupes = sorted({k for k in keys if keys.count(k) > 1})
print("duplicate keys:", dupes)
