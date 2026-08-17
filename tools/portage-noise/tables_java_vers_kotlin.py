#!/usr/bin/env python3
"""Convertit les tables de constantes Java d'un fichier en tableaux Kotlin.

Écrit pour le portage de noise-java en Kotlin : RijndaelAES et NewHope portent
des milliers de constantes hexadécimales. Les recopier à la main est le geste
où une erreur se glisse sans que rien ne la signale — une table fausse donne un
chiffrement faux, pas une erreur de compilation.

Deux choses seulement sont faites ici, et elles sont mécaniques :

  1. `private static final int[] NOM = {` devient `private val NOM = intArrayOf(`
     (idem `short[]`, `long[]`, `byte[]`) ;
  2. tout littéral qui déborde le type signé reçoit `.toInt()` / `.toLong()`.
     Java accepte `0xc66363a3` comme int et `0xCA27…L` comme long avec le bit
     de signe posé ; Kotlin les refuse et infère le type plus large. `.toInt()`
     tronque exactement comme le `(int)` de Java.

Aucune valeur n'est modifiée. Le script échoue bruyamment plutôt que de deviner.

    python3 tables_java_vers_kotlin.py <fichier.java> <première_ligne> <dernière_ligne>
"""
import re
import sys

DEBUT = re.compile(
    r'^\s*(?:private|public|static|final|\s)*\s*'
    r'(int|short|long|byte)\s*\[\s*\]\s*(\w+)\s*=\s*\{\s*$'
)
FIN = re.compile(r'^\s*\}\s*;\s*$')
HEX = re.compile(r'\b0[xX]([0-9a-fA-F]+)([lL]?)\b')

CONSTRUCTEUR = {
    'int': ('intArrayOf', 'toInt', 32),
    'short': ('shortArrayOf', 'toShort', 16),
    'long': ('longArrayOf', 'toLong', 64),
    'byte': ('byteArrayOf', 'toByte', 8),
}


def elargir(ligne: str, suffixe: str, bits: int) -> str:
    """Ajoute `.toX()` aux littéraux qui débordent le type signé."""
    def remplacer(m):
        valeur = int(m.group(1), 16)
        if valeur >= (1 << (bits - 1)):
            # Déborde le signé : Kotlin infère le type au-dessus, on tronque.
            return f'0x{m.group(1)}{"uL" if bits == 64 else ""}.{suffixe}()'
        return f'0x{m.group(1)}'
    return HEX.sub(remplacer, ligne)


def main() -> int:
    chemin, premiere, derniere = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    with open(chemin, encoding='utf-8') as f:
        lignes = f.readlines()[premiere - 1:derniere]

    sortie, dans_table, suffixe, bits = [], False, None, None
    tables = 0
    for ligne in lignes:
        ligne = ligne.rstrip('\n').replace('\t', '    ')
        m = DEBUT.match(ligne)
        if m:
            typ, nom = m.group(1), m.group(2)
            ctor, suffixe, bits = CONSTRUCTEUR[typ]
            sortie.append(f'        private val {nom} = {ctor}(')
            dans_table, tables = True, tables + 1
            continue
        if dans_table and FIN.match(ligne):
            sortie.append('        )')
            dans_table = False
            continue
        if dans_table:
            corps = elargir(ligne, suffixe, bits).strip()
            if corps and not corps.endswith(',') and not corps.startswith('//'):
                corps += ','
            sortie.append('            ' + corps if corps else '')
            continue
        sortie.append(ligne)

    if dans_table:
        print('✗ table non refermée — conversion abandonnée', file=sys.stderr)
        return 1

    print('\n'.join(sortie))
    print(f'✓ {tables} tables converties', file=sys.stderr)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
