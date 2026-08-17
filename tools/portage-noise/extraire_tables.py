#!/usr/bin/env python3
"""Extrait une table de constantes nommée d'un fichier Java, en Kotlin.

Ne s'appuie pas sur la syntaxe de déclaration (qui varie : `int t[/*496*/] = {`,
`long[] t =\\n{`, `char[/*PARAM_N*/] t = {` …) mais sur le NOM : on cherche le
nom, puis la première accolade ouvrante, puis on lit jusqu'à l'accolade
fermante en comptant les niveaux. Le contenu est découpé sur les virgules et
chaque élément est relu comme un nombre — ce qui vérifie au passage qu'aucun
n'est corrompu.

    python3 extraire_tables.py <fichier.java> <nom> <int|long> [nom type ...]
"""
import re
import sys


def extraire(source: str, nom: str):
    m = re.search(r'\b' + re.escape(nom) + r'\b[^={;]*=\s*', source)
    if not m:
        raise SystemExit(f'✗ table {nom} introuvable')
    i = source.index('{', m.end() - 1)
    niveau, j = 0, i
    while j < len(source):
        if source[j] == '{':
            niveau += 1
        elif source[j] == '}':
            niveau -= 1
            if niveau == 0:
                break
        j += 1
    corps = source[i + 1:j]
    corps = re.sub(r'/\*.*?\*/', '', corps, flags=re.S)
    corps = re.sub(r'//[^\n]*', '', corps)
    valeurs = []
    for brut in corps.split(','):
        brut = brut.strip().rstrip('lL')
        if not brut:
            continue
        valeurs.append(int(brut, 0))
    return valeurs


def main() -> int:
    chemin = sys.argv[1]
    source = open(chemin, encoding='utf-8').read()
    args = sys.argv[2:]
    for k in range(0, len(args), 2):
        nom, typ = args[k], args[k + 1]
        valeurs = extraire(source, nom)
        ctor = 'longArrayOf' if typ == 'long' else 'intArrayOf'
        print(f'        private val {nom} = {ctor}(')
        ligne = '           '
        for v in valeurs:
            if typ == 'long':
                # Les constantes de Keccak posent le bit de signe : littéral
                # non signé + .toLong(), comme partout ailleurs dans le port.
                txt = (f'0x{v:016x}uL.toLong()' if v >= (1 << 63) else f'0x{v:016x}L')
            else:
                txt = str(v)
            if len(ligne) + len(txt) + 2 > 108:
                print(ligne)
                ligne = '           '
            ligne += ' ' + txt + ','
        if ligne.strip():
            print(ligne)
        print('        )')
        print(f'✓ {nom} : {len(valeurs)} valeurs', file=sys.stderr)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
