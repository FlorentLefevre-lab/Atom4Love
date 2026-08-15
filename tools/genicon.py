"""Génère le foreground vectoriel de l'icône depuis la géométrie d'AtomLogo.kt.

Repère : viewport 108×108 (l'icône adaptative), centre (54,54).
La zone sûre est le disque central de 72 dp — rayon 36 depuis le centre.
Le rayon du contenu vaut 0,47·u (demi-grand axe des orbites), donc u ≤ 76,6.
On prend u = 74 pour laisser respirer le trait et le halo des électrons.
"""
import math

U = 74.0
CX = CY = 54.0
RX, RY = 0.47 * U, 0.165 * U          # AtomLogo : rx/ry des orbites
HEART_H = 0.34 * U                     # AtomLogo : heartH
ER = 0.016 * U * 1.9                   # électron, épaissi pour la taille d'icône
RINGS = [-60.0, 0.0, 60.0]
PHASES = [0.00, 0.38, 0.71]            # OrbitingElectrons, à l'instant 0

HEART_DARK = "E7325C"
HEART_LIGHT = "FF7B93"
# Le fond de l'icône est BLANC : on prend donc les valeurs du jour de la
# palette, pas celles de la nuit. C'est la règle n°1 d'A4LLight, mot pour mot —
# « la teinte ne bouge pas, un accent descend en luminosité jusqu'à porter sur
# blanc ». #00FFCC fait 1,3:1 sur blanc, illisible ; #00A383 en fait 3,4:1 et
# reste le même cyan. Et le cœur de l'électron, presque blanc de nuit, doit
# devenir plus sombre que son orbite pour se lire encore comme un nœud.
CYAN = "00A383"                        # A4L.Cyan du jour
ELECTRON_CORE = "00705E"               # un cran sous l'orbite, pour ressortir

STROKE = 1.7                           # 1,2 dp au splash ; épaissi, voir le KDoc
ORBIT_ALPHA = "D9"                     # 0,30 au splash ; remonté, voir le KDoc


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def ellipse(cx, cy, rx, ry):
    return (f"M{f(cx - rx)},{f(cy)} a{f(rx)},{f(ry)} 0 1,0 {f(2 * rx)},0 "
            f"a{f(rx)},{f(ry)} 0 1,0 {f(-2 * rx)},0 Z")


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def electron_xy(ring_deg, turns):
    """position() d'AtomLogo, à l'identique."""
    th = turns * 2 * math.pi
    x, y = RX * math.cos(th), RY * math.sin(th)
    a = math.radians(ring_deg)
    return (CX + x * math.cos(a) - y * math.sin(a),
            CY + x * math.sin(a) + y * math.cos(a))


def heart_path():
    """drawHeart() d'AtomLogo : grille 100×88, deux lobes en Bézier."""
    h = HEART_H
    w = h * 1.1
    left, top = CX - w / 2, CY - h * 0.46
    def x(u): return left + u / 100 * w
    def y(v): return top + v / 88 * h
    pts = [(50, 88), (20, 60, 0, 40, 0, 25), (0, 8, 14, 0, 27, 0),
           (38, 0, 46, 7, 50, 15), (54, 7, 62, 0, 73, 0),
           (86, 0, 100, 8, 100, 25), (100, 40, 80, 60, 50, 88)]
    d = f"M{f(x(50))},{f(y(88))}"
    for c in pts[1:]:
        d += ("C" + ",".join(f"{f(x(c[i]))},{f(y(c[i+1]))}" for i in (0, 2, 4)))
    return d + "Z", x, y, w, h


def build(monochrome=False):
    hp, hx, hy, hw, hh = heart_path()
    out = ['<?xml version="1.0" encoding="utf-8"?>']
    out.append('<!--')
    out.append('    Emblème atome-cœur, tracé depuis ui/components/AtomLogo.kt.')
    out.append('    NE PAS retoucher à la main : régénéré par tools/genicon.py.')
    out.append('-->')
    out.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    if not monochrome:
        out.append('    xmlns:aapt="http://schemas.android.com/aapt"')
    out.append('    android:width="108dp"')
    out.append('    android:height="108dp"')
    out.append('    android:viewportWidth="108"')
    out.append('    android:viewportHeight="108">')

    mono = "#FF000000"

    # ── Orbites : trois ellipses inclinées comme RingAngles ────────────────
    for deg in RINGS:
        out.append(f'    <group android:pivotX="{f(CX)}" android:pivotY="{f(CY)}" '
                   f'android:rotation="{f(deg)}">')
        col = mono if monochrome else f'#{ORBIT_ALPHA}{CYAN}'
        out.append(f'        <path android:pathData="{ellipse(CX, CY, RX, RY)}"')
        out.append(f'            android:strokeColor="{col}"')
        out.append(f'            android:strokeWidth="{f(STROKE)}" />')
        out.append('    </group>')

    if not monochrome:
        # Halo rouge sous le cœur — drawCircle radial d'AtomLogo, battement à 0.
        gr = HEART_H * 1.15
        out.append(f'    <path android:pathData="{circle(CX, CY, gr)}">')
        out.append('        <aapt:attr name="android:fillColor">')
        out.append(f'            <gradient android:type="radial" android:centerX="{f(CX)}" '
                   f'android:centerY="{f(CY)}" android:gradientRadius="{f(gr)}">')
        out.append(f'                <item android:offset="0" android:color="#33{HEART_DARK}" />')
        out.append(f'                <item android:offset="1" android:color="#00{HEART_DARK}" />')
        out.append('            </gradient>')
        out.append('        </aapt:attr>')
        out.append('    </path>')

    # ── Le cœur ────────────────────────────────────────────────────────────
    if monochrome:
        out.append(f'    <path android:pathData="{hp}" android:fillColor="{mono}" />')
    else:
        out.append(f'    <path android:pathData="{hp}">')
        out.append('        <aapt:attr name="android:fillColor">')
        out.append(f'            <gradient android:type="linear"')
        out.append(f'                android:startX="{f(hx(20))}" android:startY="{f(hy(0))}"')
        out.append(f'                android:endX="{f(hx(80))}" android:endY="{f(hy(88))}">')
        out.append(f'                <item android:offset="0" android:color="#FF{HEART_LIGHT}" />')
        out.append(f'                <item android:offset="1" android:color="#FF{HEART_DARK}" />')
        out.append('            </gradient>')
        out.append('        </aapt:attr>')
        out.append('    </path>')
        # Le reflet : virgule claire sur le lobe gauche, inclinée de -28°.
        px, py = hx(30), hy(18)
        out.append(f'    <group android:pivotX="{f(px)}" android:pivotY="{f(py)}" '
                   f'android:rotation="-28">')
        out.append(f'        <path android:pathData="'
                   f'{ellipse(px, py, hw * 0.10, hh * 0.045)}"')
        out.append('            android:fillColor="#BFFFFFFF" />')
        out.append('    </group>')

    # ── Les trois électrons, à l'instant zéro de l'animation ───────────────
    for ring, phase in zip(RINGS, PHASES):
        ex, ey = electron_xy(ring, phase)
        if not monochrome:
            out.append(f'    <path android:pathData="{circle(ex, ey, ER * 4.5)}">')
            out.append('        <aapt:attr name="android:fillColor">')
            out.append(f'            <gradient android:type="radial" android:centerX="{f(ex)}" '
                       f'android:centerY="{f(ey)}" android:gradientRadius="{f(ER * 4.5)}">')
            out.append(f'                <item android:offset="0" android:color="#4D{CYAN}" />')
            out.append(f'                <item android:offset="1" android:color="#00{CYAN}" />')
            out.append('            </gradient>')
            out.append('        </aapt:attr>')
            out.append('    </path>')
        col = mono if monochrome else f'#FF{ELECTRON_CORE}'
        out.append(f'    <path android:pathData="{circle(ex, ey, ER)}" '
                   f'android:fillColor="{col}" />')

    out.append('</vector>')
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    import sys
    base = sys.argv[1]
    open(base + "/ic_launcher_foreground.xml", "w").write(build())
    open(base + "/ic_launcher_monochrome.xml", "w").write(build(monochrome=True))
    r = max(math.hypot(*[c - 54 for c in electron_xy(d, p)])
            for d, p in zip(RINGS, PHASES))
    print(f"rayon du contenu : {0.47 * U:.1f} / 36 dp de zone sûre")
    print(f"électron le plus loin : {r:.1f} dp du centre")
