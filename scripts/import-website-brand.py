"""Import the supplied website fonts and outline its lowercase launcher wordmark.

Requires fonttools and brotli. Run from the project root.
"""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import shutil

root = Path(__file__).resolve().parent.parent
assets = root / 'Quill_Website/assets'
res = root / 'app/src/main/res'
(res / 'font').mkdir(exist_ok=True)
for family in ('sans', 'serif'):
    font = TTFont(assets / f'ibm-plex-{family}.woff2')
    font.flavor = None
    font.save(res / f'font/ibm_plex_{family}.ttf')
shutil.copyfile(assets / 'FONT-LICENSE.txt', root / 'app/src/main/assets/FONT-LICENSE.txt')

font = TTFont(res / 'font/ibm_plex_sans.ttf')
glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
units = font['head'].unitsPerEm
# Website: Plex Sans 34px, weight 600 (browser-synthesized), tracking -2px.
tracking = -2 / 34 * units
advance = sum(glyphs[cmap[ord(c)]].width + tracking for c in 'quill.') - tracking
scale = 56 / advance
x = (108 - 56) / 2
paths = []
for char in 'quill.':
    pen = SVGPathPen(glyphs)
    glyph = glyphs[cmap[ord(char)]]
    glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, x, 61)))
    color = '#234DCE' if char == '.' else '#202522'
    paths.append(f'    <path android:fillColor="{color}" android:strokeColor="{color}" android:strokeWidth="0.42" android:strokeLineJoin="round" android:pathData="{pen.getCommands()}" />')
    x += (glyph.width + tracking) * scale
header = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
(res / 'drawable/ic_launcher_foreground.xml').write_text(header + '\n'.join(paths) + '\n</vector>\n', encoding='utf-8')
(res / 'drawable/ic_launcher_background.xml').write_text(header + '    <path android:fillColor="#F5F7F5" android:pathData="M0,0h108v108h-108z" />\n</vector>\n', encoding='utf-8')
