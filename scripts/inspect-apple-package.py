"""Verify an Apple ZIP against this checkout without extracting or executing it.

Usage: python scripts/inspect-apple-package.py path/to/Quill-macOS.zip
Run from the source revision that produced the artifact. Signing and runtime
acceptance are separate checks; this script does not establish either.
"""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CPU = {0x01000007: "x86_64", 0x0100000C: "arm64"}
PLATFORM = {1: "macOS", 2: "iOS device", 7: "iOS simulator"}


def binary_targets(binary):
    if binary[:4] == b"\xca\xfe\xba\xbe":
        count = struct.unpack_from(">I", binary, 4)[0]
        offsets = [struct.unpack_from(">I", binary, 8 + i * 20 + 8)[0] for i in range(count)]
    else:
        offsets = [0]
    targets = []
    for offset in offsets:
        assert binary[offset:offset + 4] == b"\xcf\xfa\xed\xfe", "Expected 64-bit Mach-O"
        cpu = CPU[struct.unpack_from("<I", binary, offset + 4)[0]]
        commands = struct.unpack_from("<I", binary, offset + 16)[0]
        cursor, platform = offset + 32, None
        for _ in range(commands):
            command, size = struct.unpack_from("<II", binary, cursor)
            assert size >= 8 and cursor + size <= len(binary), "Invalid Mach-O command"
            if command == 0x32:
                platform = PLATFORM[struct.unpack_from("<I", binary, cursor + 8)[0]]
            cursor += size
        assert platform, "Missing target platform"
        targets.append({"architecture": cpu, "platform": platform})
    return targets


def inspect(path):
    result = {"file": str(path.resolve()), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    with zipfile.ZipFile(path) as package:
        names = package.namelist()
        assert len(names) == len(set(names)), "Duplicate ZIP paths"
        assert package.testzip() is None, "Corrupt ZIP entry"
        hosts = [n for n in names if n.endswith((".app/Contents/Info.plist", ".app/Info.plist"))]
        assert len(hosts) == 1, "Expected exactly one host app"
        info_name = hosts[0]
        info = plistlib.loads(package.read(info_name))
        mac = "/Contents/" in info_name
        prefix = info_name.rsplit("Info.plist", 1)[0]
        resources = prefix + ("Resources/" if mac else "")
        binary = package.read(prefix + ("MacOS/" if mac else "") + info["CFBundleExecutable"])
        targets = binary_targets(binary)
        device = ".xcarchive/" in info_name
        expected_platform = "macOS" if mac else "iOS device" if device else "iOS simulator"
        assert all(t["platform"] == expected_platform for t in targets), "Wrong binary platform"
        expected_cpu = {"x86_64", "arm64"} if mac else {"arm64"} if device else None
        if expected_cpu:
            assert {t["architecture"] for t in targets} == expected_cpu, "Wrong architectures"
        assert info["CFBundleIdentifier"] == ("com.tihor.quill.mac" if mac else "com.tihor.quill.ios")
        source = ROOT / ("macos/QuillMac/Resources/copy/strings.default.json" if mac else "ios/Quill/Resources/copy/strings.default.json")
        expected_copy = json.loads(source.read_text(encoding="utf-8-sig"))

        def verify_resources(location):
            catalog_names = [n for n in names if n.startswith(location) and n.endswith("strings.default.json") and "/PlugIns/" not in "/" + n[len(location):]]
            assert len(catalog_names) == 1, "Expected exactly one catalog per target"
            assert json.loads(package.read(catalog_names[0])) == expected_copy, "Catalog differs from checkout"
            for font in ("ibm_plex_sans.ttf", "ibm_plex_serif.ttf"):
                matches = [n for n in names if n.startswith(location) and n.endswith(font) and "/PlugIns/" not in "/" + n[len(location):]]
                assert len(matches) == 1, "Expected one font per target"
                assert package.read(matches[0]) == (ROOT / "app/src/main/res/font" / font).read_bytes(), "Font differs from Android"

        verify_resources(resources)
        if mac or device:
            flag = b"--ui-onboarding-stage" if mac else b"--ui-provider-fixtures"
            assert flag not in binary, "Debug presentation/provider fixture in Release executable"
            for debug_flag in [b"--ui-history-fixtures", b"--ui-onboarding-stage", b"--ui-dark"]:
                assert debug_flag not in binary, "Debug presentation fixture in Release executable"
            result["releaseDebugFixturesAbsent"] = True
        if not mac:
            keyboard = next(n for n in names if n.endswith("QuillKeyboard.appex/Info.plist"))
            extension = plistlib.loads(package.read(keyboard))
            assert extension["NSExtension"]["NSExtensionPointIdentifier"] == "com.apple.keyboard-service"
            assert extension["CFBundleIdentifier"] == "com.tihor.quill.ios.keyboard"
            extension_root = keyboard.rsplit("Info.plist", 1)[0]
            assert binary_targets(package.read(extension_root + extension["CFBundleExecutable"])) == targets
            verify_resources(extension_root)
            result["keyboardBundleId"] = extension["CFBundleIdentifier"]
        result.update(bundleId=info["CFBundleIdentifier"], version=info["CFBundleShortVersionString"],
                      minimumOS=info.get("LSMinimumSystemVersion", info.get("MinimumOSVersion")),
                      targets=targets, catalogStrings=len(expected_copy["strings"]),
                      catalogMatchesCheckout=True, androidFontsVerified=True, zipEntries=len(names))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    args = parser.parse_args()
    print(json.dumps(inspect(args.package), indent=2))
