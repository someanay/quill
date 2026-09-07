"""Resize the Android-derived master artwork into the standard macOS app icon slots."""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
folder = root / 'QuillMac/Resources/Assets.xcassets/AppIcon.appiconset'
source = Image.open(folder / 'icon_512x512@2x.png').convert('RGB')
images = []
for size in (16, 32, 128, 256, 512):
    for scale in (1, 2):
        filename = f'icon_{size}x{size}' + ('@2x' if scale == 2 else '') + '.png'
        source.resize((size * scale, size * scale), Image.Resampling.LANCZOS).save(folder / filename)
        images.append({'size': f'{size}x{size}', 'idiom': 'mac', 'filename': filename, 'scale': f'{scale}x'})
(folder / 'Contents.json').write_text(json.dumps({'images': images, 'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
(folder.parent / 'Contents.json').write_text(json.dumps({'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
