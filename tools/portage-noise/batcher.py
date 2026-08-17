import re, sys
src = open(sys.argv[1], encoding='utf-8').read().splitlines()
debut = next(i for i,l in enumerate(src) if 'private static void batcher84' in l)
i = debut + 2                      # sauter la signature et l'accolade
while 'int c, t;' not in src[i]:
    i += 1
i += 1
MOTIF = re.compile(
    r'^\s*c = 61444 - x\[offset \+\s*(\d+)\];\s*'
    r't = \(x\[offset \+\s*(\d+)\] \^ x\[offset \+\s*(\d+)\]\) & \(c >> 31\);\s*'
    r'x\[offset \+\s*(\d+)\] \^= t;\s*x\[offset \+\s*(\d+)\] \^= t;\s*$'
)
sortie, n = [], 0
while i < len(src):
    ligne = src[i]
    if ligne.strip() in ('}', ''):
        break
    m = MOTIF.match(ligne)
    if not m:
        sys.exit(f'✗ ligne {i+1} hors motif, conversion abandonnée :\n{ligne}')
    a1, a2, b, a3, b2 = (int(g) for g in m.groups())
    if not (a1 == a2 == a3) or b != b2:
        sys.exit(f'✗ ligne {i+1} : indices incohérents {a1},{a2},{a3} / {b},{b2}')
    sortie.append(
        f'            c = 61444 - x[offset + {a1}]; '
        f't = (x[offset + {a1}] xor x[offset + {b}]) and (c shr 31); '
        f'x[offset + {a1}] = x[offset + {a1}] xor t; '
        f'x[offset + {b}] = x[offset + {b}] xor t')
    n += 1
    i += 1
print('\n'.join(sortie))
print(f'✓ {n} énoncés convertis, tous conformes au motif', file=sys.stderr)
