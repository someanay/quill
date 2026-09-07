"""Check shared bundled-copy and font contracts; this does not prove native runtime parity."""
import argparse
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = {
    "android": "app/src/main/assets/copy/strings.default.json",
    "windows": "windows/src/Quill.Windows/Assets/copy/strings.default.json",
    "macos": "macos/QuillMac/Resources/copy/strings.default.json",
    "ios": "ios/Quill/Resources/copy/strings.default.json",
}
SPEC = re.compile(r"%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])")

def tokens(text):
    result, implicit = {}, 0
    for match in SPEC.finditer(text):
        index, conversion = match.groups()
        if conversion in ("%", "n"):
            continue
        if index:
            index = int(index)
        else:
            implicit += 1
            index = implicit
        result.setdefault(index, conversion)
    return [f"%{index}${conversion}" for index, conversion in sorted(result.items())]

def read(file):
    return json.loads((ROOT / file).read_text(encoding="utf-8-sig"))

def check(platform):
    android = read(SOURCES["android"])
    catalog = read(SOURCES[platform])
    manifest = read("copy-manifest.json" if platform == "android" else f"{platform}/copy-manifest.json")
    errors = []
    if catalog.get("schemaVersion") != 1 or catalog.get("locale") != "en":
        errors.append("unsupported catalog schema or locale")
    if catalog.get("revision") != manifest.get("revision"):
        errors.append("catalog and manifest revision differ")
    strings, keys = catalog["strings"], manifest["keys"]
    if strings.keys() != keys.keys():
        errors.append("manifest key set differs from bundled source")
    for key, value in android["strings"].items():
        if key not in strings:
            errors.append(f"missing Android copy key: {key}")
        elif tokens(strings[key]) != tokens(value):
            errors.append(f"changed Android formatting arguments: {key}")
    for key, value in strings.items():
        meta = keys.get(key, {})
        if not isinstance(value, str) or "\ufffd" in value:
            errors.append(f"invalid text: {key}")
        elif meta.get("default") != value or meta.get("placeholders") != tokens(value):
            errors.append(f"stale copy manifest: {key}")
    vectors = manifest.get("placeholderTestVectors", [])
    if not vectors:
        errors.append("missing formatter agreement vectors")
    for vector in vectors:
        if tokens(vector["input"]) != vector["tokens"]:
            errors.append(f"formatter disagreement: {vector['input']}")
    if platform != "android":
        fonts = [f for f in (ROOT / platform).rglob("*.ttf") if not any(p in ("bin", "obj", ".build", "artifacts", "DerivedData") for p in f.parts)]
        hashes = {hashlib.sha256(f.read_bytes()).hexdigest() for f in fonts}
        for name in ("ibm_plex_sans.ttf", "ibm_plex_serif.ttf"):
            expected = hashlib.sha256((ROOT / "app/src/main/res/font" / name).read_bytes()).hexdigest()
            if expected not in hashes:
                errors.append(f"missing byte-identical Android font: {name}")
    if errors:
        print(f"FAIL {platform}:\n  " + "\n  ".join(errors))
        return False
    print(f"PASS {platform}: {len(strings)} strings, {len(vectors)} formatter vectors, shared Android fonts")
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=list(SOURCES), action="append")
    args = parser.parse_args()
    success = True
    for platform in args.platform or SOURCES:
        try:
            success = check(platform) and success
        except (OSError, KeyError, ValueError) as error:
            print(f"FAIL {platform}: {error}")
            success = False
    sys.exit(0 if success else 1)
