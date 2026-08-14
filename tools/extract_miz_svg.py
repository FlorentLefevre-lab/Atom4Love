#!/usr/bin/env python3
"""Extrait les planches SVG de miz.html vers les assets de l'application.

    curl -sLO https://u.copylaradio.com/earth/miz.html
    python3 tools/extract_miz_svg.py miz.html

Les planches de l'onglet Zion sont celles de la page, pas des copies
redessinées : quand Fred corrige un schéma, on rejoue ce script. Aucun trait
n'est déplacé ici. Ce qui est retouché :

  - un attribut écrit deux fois passe en HTML, jamais en XML ;
  - rgba(r,g,b,a) n'existe pas en SVG 1.1 : on le scinde en couleur + opacité,
    ce qu'AndroidSVG (le décodeur de Coil) sait lire à coup sûr ;
  - le style racine porte width:100%, qui n'a de sens que dans une page HTML ;
    hors d'elle il fausse la mise à l'échelle. Le viewBox suffit ;
  - le rectangle de fond part : la planche prend le fond de l'écran qui la
    montre, dans la lumière du moment ;
  - **le viewBox s'élargit à ce que la planche dessine vraiment.** Plusieurs
    d'entre elles écrivent hors de leur cadre, et la page web les rogne sans
    le dire : la légende des quatre familles de sceaux, « publiée dans
    ATOM4LOVE (kind 30078) », la définition de l'Analogue. Élargir la fenêtre
    ne déplace rien — ça cesse seulement de couper.
"""
import colorsys
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DST = ROOT / "app/src/main/assets/miz"

# index dans miz.html -> nom de fichier
PLATES = {
    8: "goldberg_sphere",
    9: "goldberg_phase",
    10: "goldberg_precession",
    11: "goldberg_rendezvous",
    12: "goldberg_trust_path",
    15: "tzolkin_cycle",
    16: "tzolkin_oracle",
    21: "body_water_antenna",
    22: "body_mudras",
    23: "body_binaural",
    26: "forge_pipeline",
}

RGBA = re.compile(r'(\b[a-zA-Z-]+)="rgba\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*\)"')


def split_rgba(svg: str) -> str:
    def sub(m):
        attr, r, g, b, a = m.group(1), *m.groups()[1:]
        hexa = "#%02x%02x%02x" % (int(float(r)), int(float(g)), int(float(b)))
        return f'{attr}="{hexa}" {attr}-opacity="{float(a)}"'
    return RGBA.sub(sub, svg)


def dedupe_attrs(svg: str) -> str:
    """Le HTML tolère un attribut écrit deux fois, le XML le refuse.

    `miz.html` en compte quelques-uns (des `font-family` doublés). On garde le
    premier, comme le ferait un navigateur.
    """
    def sub(m):
        seen, kept = set(), []
        for attr in re.finditer(r'([a-zA-Z:_-]+)="[^"]*"', m.group(2)):
            if attr.group(1) in seen:
                continue
            seen.add(attr.group(1))
            kept.append(attr.group(0))
        tail = "/" if m.group(2).rstrip().endswith("/") else ""
        return f"<{m.group(1)}{(' ' + ' '.join(kept)) if kept else ''}{tail}>"
    return re.sub(r"<([a-zA-Z][a-zA-Z0-9]*)((?:\s+[a-zA-Z:_-]+=\"[^\"]*\")+\s*/?)>", sub, svg)


def clean_root_style(svg: str) -> str:
    def sub(m):
        keep = [d for d in m.group(1).split(";")
                if d.strip() and not d.strip().startswith(("width", "min-width", "max-width", "display"))]
        return f'style="{";".join(keep)}"' if keep else ""
    return re.sub(r'style="([^"]*)"', sub, svg, count=1)


MARGIN = 3.0        # respiration ajoutée autour du contenu, en unités du viewBox
CHAR_WIDTH = 0.55   # largeur moyenne d'un caractère, en fraction du corps

# L'encre qui remplace le blanc dans la variante de jour.
LIGHT_INK = "#12121c"
WHITE = re.compile(r'((?:fill|stroke)=")(#fff|#ffffff|white)(")', re.I)


# Le plafond porte sur la luminance PERÇUE, celle du calcul de contraste du
# W3C — pas sur la clarté HSL. Un or à 42 % de clarté HSL reste éblouissant :
# l'œil pèse le vert huit fois plus que le bleu, et le jaune est presque tout
# vert. Sous 0,16, n'importe laquelle de ces teintes tient ses 4,5:1 sur le
# fond clair de la station.
TEXT_LUMINANCE = 0.16
TEXT_MIN_ALPHA = 0.85


def _rgb(hexa):
    hexa = hexa.lstrip("#")
    if len(hexa) == 3:
        hexa = "".join(c * 2 for c in hexa)
    return tuple(int(hexa[i:i + 2], 16) / 255 for i in (0, 2, 4))


def _luminance(rgb):
    """Luminance relative WCAG — ce que l'œil voit, pas ce que HSL raconte."""
    def channel(c):
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _darken(hexa, target=TEXT_LUMINANCE):
    """La même teinte, assez sombre pour se lire sur un fond clair.

    La clarté HSL descend par dichotomie jusqu'à ce que la luminance perçue
    passe sous le plafond : c'est ce qui garde l'or doré et le violet violet
    au lieu de les ramener tous les deux à un gris de compromis.
    """
    rgb = _rgb(hexa)
    if _luminance(rgb) <= target:
        return hexa
    hue, lightness, saturation = colorsys.rgb_to_hls(*rgb)
    low, high = 0.0, lightness
    for _ in range(20):
        middle = (low + high) / 2
        if _luminance(colorsys.hls_to_rgb(hue, middle, saturation)) > target:
            high = middle
        else:
            low = middle
    r, g, b = colorsys.hls_to_rgb(hue, low, saturation)
    return "#%02x%02x%02x" % tuple(round(v * 255) for v in (r, g, b))


def _readable_text(tag):
    """Une balise <text> rendue lisible sur fond clair : plus sombre, plus opaque."""
    tag = WHITE.sub(rf"\1{LIGHT_INK}\3", tag)
    tag = re.sub(
        r'(fill=")(#[0-9a-fA-F]{3,6})(")',
        lambda m: m.group(1) + _darken(m.group(2)) + m.group(3),
        tag,
    )
    return re.sub(
        r'(fill-opacity=")([\d.]+)(")',
        lambda m: m.group(1) + f"{max(float(m.group(2)), TEXT_MIN_ALPHA):g}" + m.group(3),
        tag,
    )


def dark_panels(svg):
    """Les cartouches sombres que la planche pose elle-même sous du texte.

    Fred encadre certaines formules d'un rectangle noir à moitié opaque. Ce
    qui est écrit dedans reste sur du noir quel que soit le thème : l'y
    assombrir le rendrait illisible là où il ne l'était pas.
    """
    found = []
    for m in re.finditer(r"<rect\b[^>]*>", svg):
        a = _attrs(m.group(0))
        fill = a.get("fill", "")
        if not fill.startswith("#") or _num(a, "fill-opacity", 1.0) < 0.4:
            continue
        if _luminance(_rgb(fill)) > 0.15:
            continue
        x, y = _num(a, "x"), _num(a, "y")
        found.append((x, y, x + _num(a, "width"), y + _num(a, "height")))
    return found


def darken_whites(svg):
    """La même planche, pour un fond clair.

    Les schémas sont nés sur le noir de la page web : ce qui devait s'y voir
    y est blanc, ou pâle, ou à moitié transparent — les libellés de la sphère,
    les noms des doigts, l'axe du spectre binaural et ses graduations, l'année
    sidérale, le seuil de super-cohérence. Sans fond à eux, tout cela s'efface
    sous le thème jour.

    Trois gestes, et pas un de plus. Hors des textes, le blanc devient encre à
    opacité inchangée : un trait à 18 % reste un trait à 18 %. Chaque écriture
    posée à même la planche est ramenée sous un plafond de luminance, teinte
    gardée — l'or reste de l'or, le cyan reste du cyan, ils cessent seulement
    de se confondre avec la page. Et celles qui reposent sur un cartouche noir
    de Fred ne bougent pas : leur fond, lui, n'a pas changé de lumière.

    Les formes ne sont jamais touchées : un disque jaune se voit des deux côtés.
    """
    panels = dark_panels(svg)

    def on_dark_panel(tag):
        a = _attrs(tag)
        x, y = _num(a, "x"), _num(a, "y")
        return any(x0 <= x <= x1 and y0 <= y <= y1 for x0, y0, x1, y1 in panels)

    texts = []

    def stash(m):
        texts.append(m.group(0) if on_dark_panel(m.group(0)) else _readable_text(m.group(0)))
        return f"\x00{len(texts) - 1}\x00"

    svg = re.sub(r"<text\b[^>]*>", stash, svg)
    svg = WHITE.sub(rf"\1{LIGHT_INK}\3", svg)
    return re.sub(r"\x00(\d+)\x00", lambda m: texts[int(m.group(1))], svg)


def _attrs(tag):
    return {k: v for k, v in re.findall(r'([a-zA-Z:_-]+)="([^"]*)"', tag)}


def _num(a, k, default=0.0):
    try:
        return float(a.get(k, default))
    except ValueError:
        return default


def content_boxes(svg):
    """Les boîtes de chaque élément dessiné, en unités du viewBox.

    Le texte est estimé, faute de moteur de rendu ici : sa largeur vaut le
    nombre de caractères fois le corps fois [CHAR_WIDTH]. L'estimation sert
    seulement à agrandir une fenêtre — trop large ne coûte que du vide.
    """
    pattern = r"<(rect|circle|ellipse|line|polygon|polyline|text)\b([^>]*)>(?:(.*?)</\1>)?"
    for m in re.finditer(pattern, svg, re.S):
        kind, a, inner = m.group(1), _attrs(m.group(2)), (m.group(3) or "")
        edge = _num(a, "stroke-width", 0) / 2
        if kind == "rect":
            x, y = _num(a, "x"), _num(a, "y")
            yield x - edge, y - edge, x + _num(a, "width") + edge, y + _num(a, "height") + edge
        elif kind == "circle":
            cx, cy, r = _num(a, "cx"), _num(a, "cy"), _num(a, "r") + edge
            yield cx - r, cy - r, cx + r, cy + r
        elif kind == "ellipse":
            cx, cy = _num(a, "cx"), _num(a, "cy")
            rx, ry = _num(a, "rx") + edge, _num(a, "ry") + edge
            yield cx - rx, cy - ry, cx + rx, cy + ry
        elif kind == "line":
            xs = sorted((_num(a, "x1"), _num(a, "x2")))
            ys = sorted((_num(a, "y1"), _num(a, "y2")))
            yield xs[0] - edge, ys[0] - edge, xs[1] + edge, ys[1] + edge
        elif kind in ("polygon", "polyline"):
            pts = [float(v) for v in re.findall(r"-?[\d.]+", a.get("points", ""))]
            if len(pts) >= 4:
                xs, ys = pts[0::2], pts[1::2]
                yield min(xs) - edge, min(ys) - edge, max(xs) + edge, max(ys) + edge
        elif kind == "text":
            size = _num(a, "font-size", 10)
            width = len(re.sub(r"<[^>]+>", "", inner).strip()) * size * CHAR_WIDTH
            x, y, anchor = _num(a, "x"), _num(a, "y"), a.get("text-anchor", "start")
            left = x - width / 2 if anchor == "middle" else (x - width if anchor == "end" else x)
            yield left, y - size, left + width, y + size * 0.3


def widen_viewbox(svg, name):
    """Ouvre la fenêtre jusqu'à tout ce que la planche dessine."""
    found = re.search(r'viewBox="([^"]+)"', svg)
    vx, vy, vw, vh = (float(v) for v in found.group(1).split())
    boxes = list(content_boxes(svg))
    if not boxes:
        return svg
    x0 = min(min(b[0] for b in boxes) - MARGIN, vx)
    y0 = min(min(b[1] for b in boxes) - MARGIN, vy)
    x1 = max(max(b[2] for b in boxes) + MARGIN, vx + vw)
    y1 = max(max(b[3] for b in boxes) + MARGIN, vy + vh)
    if (x0, y0, x1, y1) == (vx, vy, vx + vw, vy + vh):
        return svg
    print(f"   {name}: fenêtre {vw:.0f}×{vh:.0f} → {x1-x0:.0f}×{y1-y0:.0f}")
    return svg.replace(found.group(0), f'viewBox="{x0:.0f} {y0:.0f} {x1-x0:.0f} {y1-y0:.0f}"', 1)


def drop_backdrop(svg):
    """Retire le rectangle de fond pleine surface, s'il y en a un.

    Six planches en portent un — `#000014` à moitié opaque, hérité du noir de
    la page web. Retiré, la planche se pose sur le fond de l'application et
    suit ses deux lumières au lieu d'imposer la nuit.
    """
    found = re.search(r'viewBox="([^"]+)"', svg)
    _, _, vw, vh = (float(v) for v in found.group(1).split())
    def is_backdrop(m):
        a = _attrs(m.group(0))
        return (_num(a, "x") == 0 and _num(a, "y") == 0
                and _num(a, "width") == vw and _num(a, "height") == vh
                and a.get("fill", "").startswith("#"))
    for m in re.finditer(r"<rect\b[^>]*/?>", svg):
        if is_backdrop(m):
            return svg.replace(m.group(0), "", 1)
    return svg


DEFINABLE = "marker|linearGradient|radialGradient|pattern|clipPath|mask|filter|symbol"


def adopt_missing_defs(svg: str, html: str) -> str:
    """Rapatrie les définitions que la planche référence sans les porter.

    Dans la page, les `id` sont communs à tout le document : une flèche définie
    par un schéma sert aux suivants. Détachée, la planche perdrait ses flèches.
    """
    missing = [ref for ref in sorted(set(re.findall(r"url\(#([^)]+)\)", svg)))
               if f'id="{ref}"' not in svg]
    if not missing:
        return svg
    adopted = []
    for ref in missing:
        found = re.search(rf'<({DEFINABLE})\b[^>]*\bid="{re.escape(ref)}".*?</\1>', html, re.S)
        if found is None:
            print(f"!! définition introuvable : #{ref}", file=sys.stderr)
            continue
        adopted.append(found.group(0))
    if not adopted:
        return svg
    return re.sub(r"(<svg\b[^>]*>)", r"\1<defs>" + "".join(adopted) + "</defs>", svg, count=1)


def main() -> int:
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "miz.html")
    if not src.is_file():
        print(f"{src} introuvable — voir l'en-tête de ce fichier.", file=sys.stderr)
        return 1
    html = src.read_text(encoding="utf-8")
    svgs = [m.group(0) for m in re.finditer(r"<svg\b.*?</svg>", html, re.S)]
    normalized = split_rgba(dedupe_attrs(html))  # le vivier des définitions adoptées
    DST.mkdir(parents=True, exist_ok=True)
    for index, name in PLATES.items():
        svg = clean_root_style(split_rgba(dedupe_attrs(svgs[index])))
        svg = adopt_missing_defs(svg, normalized)
        svg = drop_backdrop(svg)
        svg = widen_viewbox(svg, name)
        if "rgba(" in svg:
            print(f"!! {name}: rgba résiduel (probablement dans un style)", file=sys.stderr)
        ET.fromstring(svg)  # échoue si le XML n'est pas valide
        out = DST / f"{name}.svg"
        out.write_text(svg, encoding="utf-8")
        light = DST / "light" / f"{name}.svg"
        light.parent.mkdir(parents=True, exist_ok=True)
        light.write_text(darken_whites(svg), encoding="utf-8")
        vb = re.search(r'viewBox="([^"]*)"', svg).group(1)
        print(f"{out.name:26s} {len(svg):6d} o  viewBox={vb}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
