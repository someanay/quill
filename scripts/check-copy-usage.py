"""Catch unresolved literal copy keys in native UI code before they reach a screen."""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
layouts = {
    "windows": ("windows/src/Quill.Windows", "Assets/copy/strings.default.json", "*.cs"),
    "macos": ("macos/QuillMac", "Resources/copy/strings.default.json", "*.swift"),
    "ios": ("ios/Quill", "Resources/copy/strings.default.json", "*.swift"),
}
pattern = re.compile(r'(?:\b(?:C|c|Copy\.text|CopyText|HelpText|SectionLabel)\s*\(\s*(?:key:\s*)?|\bQuillButton\s*\(\s*key:\s*)"([^"\n]+)"')
failures = []
for platform, (folder, catalog, extension) in layouts.items():
    base = root / folder
    keys = json.loads((base / catalog).read_text(encoding="utf8"))["strings"]
    count = 0
    for file in base.rglob(extension):
        if any(part in ("bin", "obj") for part in file.parts):
            continue
        source = file.read_text(encoding="utf8")
        for match in pattern.finditer(source):
            key = match.group(1)
            if "\\(" in key or source[match.end():].lstrip().startswith("+"):
                continue
            count += 1
            if key not in keys:
                line = source.count("\n", 0, match.start()) + 1
                failures.append(f"{file.relative_to(root)}:{line}: missing {key}")
    print(f"{platform}: checked {count} literal copy references")
if failures:
    print("\n".join(failures))
    sys.exit(1)
print("All literal native copy references resolve.")
