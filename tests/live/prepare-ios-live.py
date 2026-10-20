import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys
import uuid

assert os.environ.get('GITHUB_ACTIONS') == 'true'
udid, build, provider = sys.argv[1:]
assert provider in ['gemini', 'openrouter']
session = str(uuid.uuid4()).upper()
container = Path(subprocess.check_output(['xcrun','simctl','get_app_container',udid,'com.tihor.quill.ios','data'], text=True).strip())
directory = container / 'Library/Application Support/QuillTests/Sessions' / session
directory.mkdir(parents=True, exist_ok=True)
path = directory / 'live-provider-bootstrap.json'
path.write_text(json.dumps({'speech':provider,'cleanup':provider,'gemini':os.environ['QUILL_LIVE_GEMINI_KEY'],'openrouter':os.environ['QUILL_LIVE_OPENROUTER_KEY']}))
path.chmod(0o600)
targets = list((Path(build) / 'Build/Products').glob('*.xctestrun'))
assert len(targets) == 1
test_run = plistlib.loads(targets[0].read_bytes())
count = 0
def configure(node):
    global count
    if isinstance(node, dict):
        if node.get('IsUITestBundle') or 'TestBundlePath' in node:
            node.setdefault('EnvironmentVariables', {}).update({'QUILL_LIVE_SESSION_ID':session,'QUILL_LIVE_AUDIO_URL':'http://127.0.0.1:8765/play'})
            count += 1
        for child in list(node.values()):
            configure(child)
    elif isinstance(node, list):
        for child in node:
            configure(child)
configure(test_run)
assert count >= 1
targets[0].write_bytes(plistlib.dumps(test_run))
metadata = {'udid':udid,'session':session,'container':str(container),'directory':str(directory),'provider':provider}
(Path(os.environ['RUNNER_TEMP']) / 'quill-live-session.json').write_text(json.dumps(metadata))
print(json.dumps({'isolatedSession':session,'provider':provider,'keysExposed':False,'configuredTestTargets':count}))
