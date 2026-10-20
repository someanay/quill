"""Check critical text/action contrast from the actual native theme sources.

This covers enabled text and filled actions, not a complete accessibility audit.
Native screenshot review remains a separate acceptance gate.
"""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'windows': 'windows/src/Quill.Windows/Theme.cs',
    'macos': 'macos/QuillMac/UI/Theme.swift',
    'ios': 'ios/Quill/UI/DesignSystem.swift',
    'ios-keyboard': 'ios/QuillKeyboard/KeyboardViewController.swift',
}


def theme_tokens(platform):
    source = (ROOT / SOURCES[platform]).read_text(encoding='utf-8-sig')
    tokens = {}
    if platform in ('ios', 'ios-keyboard'):
        for name, light, dark in re.findall(r'(?:static|private) let (\w+) = (?:KeyboardViewController\.)?adaptive\(0x([A-Fa-f0-9]{6}), 0x([A-Fa-f0-9]{6})\)', source):
            tokens[name] = (light, dark)
    elif platform == 'macos':
        for name, dark, light in re.findall(r'static func (\w+)[^\n]*?Color\(hex: dark \? 0x([A-Fa-f0-9]{6}) : 0x([A-Fa-f0-9]{6})\)', source):
            tokens[name] = (light, dark)
        for name, light, dark in re.findall(r'static let (\w+) = statusColor\(light: 0x([A-Fa-f0-9]{6}), dark: 0x([A-Fa-f0-9]{6})\)', source):
            tokens[name] = (light, dark)
        brand = re.search(r'static let brand = Color\(hex: 0x([A-Fa-f0-9]{6})\)', source)
        assert brand, 'Mac brand colour was not found'
        tokens['brand'] = (brand[1], brand[1])
        tokens['brandSoft'] = tokens['soft']
    else:
        for name, dark, light, fixed in re.findall(r'\["(\w+)"\] = (?:dark \? "#([A-Fa-f0-9]{6})" : "#([A-Fa-f0-9]{6})"|"#([A-Fa-f0-9]{6})")', source):
            tokens[name[0].lower() + name[1:]] = (light, dark) if dark else (fixed, fixed)
    if 'error' in tokens:
        tokens['danger'] = tokens['error']
    required = {'ink', 'muted', 'paper', 'surface', 'brand'}
    if platform != 'ios-keyboard':
        required |= {'brandInk', 'brandSoft', 'danger'}
    assert required <= tokens.keys(), 'Critical theme tokens were not found'
    return tokens


def rgb(value):
    return tuple(int(value[index:index + 2], 16) / 255 for index in (0, 2, 4))


def luminance(color):
    linear = [value / 12.92 if value <= .04045 else ((value + .055) / 1.055) ** 2.4 for value in color]
    return sum(value * weight for value, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(foreground, background):
    low, high = sorted((luminance(foreground), luminance(background)))
    return (high + .05) / (low + .05)


def verify(platform):
    tokens = theme_tokens(platform)
    checks = []
    for index, appearance in enumerate(('light', 'dark')):
        def check(label, foreground, background):
            ratio = contrast(foreground, background)
            checks.append({'appearance': appearance, 'pair': label, 'ratio': round(ratio, 3), 'passed': ratio >= 4.5})
        surfaces = ['paper', 'surface'] + (['sunken'] if 'sunken' in tokens else [])
        for foreground in [key for key in ['ink', 'muted', 'brandInk', 'danger', 'success'] if key in tokens]:
            for background in surfaces:
                check(foreground + '/' + background, rgb(tokens[foreground][index]), rgb(tokens[background][index]))
        if 'brandInk' in tokens:
            check('brandInk/brandSoft', rgb(tokens['brandInk'][index]), rgb(tokens['brandSoft'][index]))
        check('white/brand action', (1, 1, 1), rgb(tokens['brand'][index]))
        if 'dangerFill' in tokens:
            check('white/destructive action', (1, 1, 1), rgb(tokens['dangerFill'][index]))
            foreground = rgb(tokens['danger'][index])
            background = tuple(fg * .09 + bg * .91 for fg, bg in zip(foreground, rgb(tokens['paper'][index])))
            check('danger/error banner', foreground, background)
    return {'platform': platform, 'source': SOURCES[platform], 'checks': checks, 'passed': all(item['passed'] for item in checks)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=['all', *SOURCES], default='all')
    parser.add_argument('--output', type=Path)
    arguments = parser.parse_args()
    platforms = SOURCES if arguments.platform == 'all' else ['ios', 'ios-keyboard'] if arguments.platform == 'ios' else [arguments.platform]
    results = [verify(platform) for platform in platforms]
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    for result in results:
        print(json.dumps({'platform': result['platform'], 'pairs': len(result['checks']), 'minimum': min(item['ratio'] for item in result['checks']), 'passed': result['passed']}))
        for check in result['checks']:
            if not check['passed']:
                print(json.dumps(check))
    raise SystemExit(0 if all(result['passed'] for result in results) else 1)
