# Les scripts du portage de Noise en Kotlin

`PROVENANCE.md` du paquet `com.southernstorm.noise` affirme que les milliers de
constantes des tables **n'ont pas été recopiées à la main**. Ces trois scripts
sont ce qui rend l'affirmation vérifiable.

Ils ont servi une fois, le 17 août 2026, et ne font pas partie du build. Ils
sont gardés pour qu'on puisse rejouer la conversion et comparer, plutôt que
d'avoir à croire sur parole.

| Script | Ce qu'il a converti |
|---|---|
| `tables_java_vers_kotlin.py` | les 10 tables d'AES (`Te0`..`Td4`, `rcon`) — **2570 constantes**, compte vérifié des deux côtés |
| `extraire_tables.py` | les 6 tables de New Hope — 496 + 24 + 512 + 512 + 1024 + 1024 = **3592 valeurs** |
| `batcher.py` | les **904 énoncés** du réseau de tri `batcher84` de NewHopeTor |

Chacun échoue bruyamment plutôt que de deviner : `batcher.py` refuse toute
ligne qui s'écarte du motif attendu et vérifie que les indices concordent,
`extraire_tables.py` relit chaque valeur comme un nombre.

Ce qu'ils font, et rien d'autre : traduire la syntaxe de déclaration, et
ajouter `.toInt()` / `.toLong()` aux littéraux qui débordent le type signé —
Java accepte `0xc66363a3` comme `int`, Kotlin le refuse et infère le type
au-dessus. Aucune valeur n'est modifiée.

Le vrai filet reste ailleurs : `PortageDifferentielTest` compare le port au
Java d'origine octet par octet. Une table fausse ne casse pas la compilation —
elle chiffre faux, et c'est le test qui le dit.
