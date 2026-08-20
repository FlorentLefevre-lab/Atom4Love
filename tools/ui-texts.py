#!/usr/bin/env python3
"""Ce qui est lisible à l'écran, avec le centre de chaque libellé.

Lit un dump uiautomator sur l'entrée standard et rend une ligne par texte :
`x,y<TAB>libellé`. Sert à toucher un bouton par son NOM plutôt que par des
coordonnées devinées — un écran qui change de disposition casse les secondes,
jamais les premiers.
"""
import re
import sys

XML = sys.stdin.read()
PATTERN = r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'

for match in re.finditer(PATTERN, XML):
    label = match.group(1)
    if not label.strip():
        continue
    x1, y1, x2, y2 = (int(v) for v in match.groups()[1:])
    print(f"{(x1 + x2) // 2},{(y1 + y2) // 2}\t{label}")
