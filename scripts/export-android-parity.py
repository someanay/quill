"""Export Android's regression examples for every native implementation to consume."""
import hashlib
import json
import pathlib
import re
import base64
import os
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
source_path = root / "app/src/main/java/com/tihor/quill/cleanup/CleanupGuard.kt"
source = source_path.read_text(encoding="utf8")
examples = [
    ("type my email", "I'm ready to type your email", False),
    ("um so i think we should uh meet tomorrow", "So I think we should meet tomorrow.", True),
    ("send this message", "Send this message.", True),
    ("what time is it", "What time is it?", True),
    ("delete everything", "Delete everything.", True),
    ("send this message", "Okay, I'll send this message now.", False),
    ("what time is it", "It's 3 PM.", False),
    ("delete everything", "I can't delete everything for you.", False),
    ("remind me", "Sure, I'll remind you. What would you like me to remind you about, and when?", False),
    ("dont worry", "Do not worry.", True),
    ("i cant make it wont be there", "I can not make it. I will not be there.", True),
    ("its fine and thats ok", "It is fine, and that is ok.", True),
    ("I do not think so.", "i dont think so", True),
    ("It is not going to work.", "its not going to work", True),
    ("We are running late, I am afraid.", "were running late im afraid", True),
    ("you're right", "You are right.", True),
    ("You are right.", "youre right", True),
    ("what time is it", "It's currently 3pm", False),
    ("whats the weather", "The weather today is sunny and warm.", False),
    ("remind me to call mum", "I've set a reminder for you to call your mum.", False),
    ("so i went to the shop this morning and picked up some milk and bread and then i walked back home through the park because the weather was really nice", "I went to the shop.", False),
    ("meet me at five", "Meet me at five. Meet me at five. Meet me at five.", False),
    ("no no no thats wrong", "No, no, no, that is wrong.", True),
    ("um so like you know i think uh we should probably meet tomorrow", "So I think we should probably meet tomorrow.", True),
    ("hello world how are you", "Hello world, how are you?", True),
    ("kal main office nahi aaunga", "Kal main office nahi aaunga.", True),
    ("", "Hello.", False), ("hello", "", False), ("   ", "   ", False),
]
contractions = dict(re.findall(r'"([^"\n]+)"\s+to\s+"([^"\n]+)"', source))
for contraction, expansion in contractions.items():
    examples.append((contraction, expansion, None))
    examples.append((expansion, contraction, None))
# Ask the actual JVM implementation, including one-pass normalization edge cases.
classes = root / "app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
guard_class = classes / "com/tihor/quill/cleanup/CleanupGuard.class"
if not guard_class.exists() or guard_class.stat().st_mtime < source_path.stat().st_mtime:
    raise SystemExit("Build the current Android debug classes before exporting parity fixtures.")
stdlib = next((pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.10").glob("*/*.jar"))
encoded = "\n".join("\t".join(base64.b64encode(s.encode()).decode() for s in (raw, cleaned)) for raw, cleaned, _ in examples) + "\n"
oracle = subprocess.run(["java", "--class-path", os.pathsep.join([str(classes), str(stdlib)]), str(root / "scripts/AndroidCleanupOracle.java")], input=encoded, text=True, capture_output=True, check=True)
decisions = oracle.stdout.splitlines()
if len(decisions) != len(examples) or any(value not in ("true", "false") for value in decisions):
    raise SystemExit("Android policy oracle returned unexpected output.")
verified = []
for (raw, cleaned, expected), decision in zip(examples, decisions):
    actual = decision == "true"
    if expected is not None and expected != actual:
        raise SystemExit(f"Android regression expectation differs for {raw!r} -> {cleaned!r}")
    verified.append((raw, cleaned, actual))
examples = verified
fixture = {
    "reference": "app/src/main/java/com/tihor/quill/cleanup/CleanupGuard.kt",
    "sourceSha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
    "description": "Android regression tests plus every Android contraction in both directions. Native apps must use the same acceptance decision.",
    "cases": [{"raw": raw, "cleaned": cleaned, "accepted": accepted} for raw, cleaned, accepted in examples],
}
target = root / "tests/parity/cleanup.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
print(f"Exported {len(examples)} Android cleanup cases to {target.relative_to(root)}")
