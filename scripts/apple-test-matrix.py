"""Select a native Apple matrix; pushes keep the complete default coverage."""
import argparse
import json

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--platform", choices=["all", "macos", "ios"], default="all")
parser.add_argument("--current", action="store_true")
args = parser.parse_args()
runner = "macos-26" if args.current else "macos-15"
entries = [
    {"platform": "macos", "project": "QuillMac", "scheme": "QuillMac", "runner": runner, "artifactSuffix": ""},
    {"platform": "macos", "project": "QuillMac", "scheme": "QuillMac", "runner": runner + "-intel", "artifactSuffix": "-intel"},
    {"platform": "ios", "project": "Quill", "scheme": "Quill", "runner": runner, "artifactSuffix": ""},
]
print(json.dumps([entry for entry in entries if args.platform in ("all", entry["platform"])], separators=(",", ":")))
