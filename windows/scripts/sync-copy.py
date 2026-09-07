"""Merge Android's bundled catalog with native Windows copy defaults, preserving local edits."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = root / 'windows/src/Quill.Windows'
catalog_path = source / 'Assets/copy/strings.default.json'
android = json.loads((root / 'app/src/main/assets/copy/strings.default.json').read_text(encoding='utf-8-sig'))
existing = json.loads(catalog_path.read_text(encoding='utf-8-sig')) if catalog_path.exists() else {}
strings = dict(android['strings'])
strings.update({k:v for k,v in existing.get('strings',{}).items() if k.startswith('windows.')})
literal = re.compile(r'\bC\(\s*"([^"\n]+)"\s*,\s*("(?:[^"\\]|\\.)*")')
for code in source.glob('*.cs'):
    for key, value in literal.findall(code.read_text(encoding='utf-8')):
        strings.setdefault(key,json.loads(value))
catalog = {**android,'strings':dict(sorted(strings.items()))}
catalog_path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
pattern = re.compile(r'%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])')
def placeholders(value):
    implicit = 0
    found = {}
    for explicit, conversion in pattern.findall(value):
        if conversion in '%n': continue
        if not explicit: implicit += 1
        found.setdefault(int(explicit) if explicit else implicit,conversion)
    return [f'%{i}${c}' for i,c in sorted(found.items())]
manifest = {'revision':catalog['revision'],'keys':{k:{'default':v,'placeholders':placeholders(v),'overridable':True,'optionalBlank':not v} for k,v in catalog['strings'].items()},'placeholderTestVectors':json.loads((root/'copy-manifest.json').read_text(encoding='utf-8-sig'))['placeholderTestVectors']}
(root/'windows/copy-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Windows catalog: {len(strings)} keys, including all {len(android["strings"])} Android keys')
