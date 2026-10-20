import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

assert os.environ.get('GITHUB_ACTIONS') == 'true'
destination = Path(sys.argv[1])
destination.mkdir(parents=True, exist_ok=True)
metadata = json.loads((Path(os.environ['RUNNER_TEMP']) / 'quill-live-session.json').read_text())
def current_session_directory(metadata):
    session = str(uuid.UUID(metadata['session'])).upper()
    directories = {Path(metadata['directory'])}
    devices = {metadata['udid']}
    for manifest in Path('ios/live-keyboard-evidence').rglob('manifest.json'):
        for test in json.loads(manifest.read_text()):
            for attachment in test.get('attachments', []):
                try:
                    devices.add(str(uuid.UUID(attachment['deviceId'])).upper())
                except (KeyError, ValueError, TypeError):
                    pass
    # Xcode can replace the application's data-container UUID on installation.
    # Resolve after execution; also handle an actual destination in the manifest.
    for device in devices:
        found = subprocess.run(['xcrun','simctl','get_app_container',device,'com.tihor.quill.ios','data'],capture_output=True,text=True)
        if found.returncode == 0:
            directories.add(Path(found.stdout.strip()) / 'Library/Application Support/QuillTests/Sessions' / session)
    audits = []
    for directory in directories:
        assert directory.name.upper() == session and directory.parent.name == 'Sessions'
        bootstrap = directory / 'live-provider-bootstrap.json'
        if bootstrap.exists():
            bootstrap.unlink()
        path = directory / 'live-http-audit.json'
        if path.is_file():
            audits.append((directory,json.loads(path.read_text())))
    assert audits, 'Missing actual provider evidence in the current test container'
    directory, events = max(audits,key=lambda value:len(value[1]))
    assert all(events[:len(prior)] == prior for _,prior in audits), 'Conflicting live session evidence'
    return directory

directory = current_session_directory(metadata)
bootstrap = directory / 'live-provider-bootstrap.json'
if bootstrap.exists():
    bootstrap.unlink()  # Never preserve an unconsumed credential file after a failed launch.
audit = directory / 'live-http-audit.json'
assert audit.is_file(), 'Missing actual provider HTTP evidence'
events = json.loads(audit.read_text())
shutil.copyfile(audit, destination / 'http-audit.json')
(destination / 'result.json').write_text(json.dumps({'passed':False,'provider':metadata['provider'],'httpAttempts':len(events),'resolvedContainerAfterTest':True},indent=2) + '\n')
readbacks = []
for path in Path('ios/live-keyboard-evidence').rglob('*.json'):
    try:
        data = json.loads(path.read_text())
    except (ValueError, OSError):
        continue
    if isinstance(data, list) and data and all(isinstance(x, dict) and 'stage' in x and 'text' in x for x in data):
        readbacks.append(data)
assert len(readbacks) == 1, 'Missing/ambiguous exact separate-editor readbacks'
readback = {x['stage']: x['text'] for x in readbacks[0]}
successful = [event for event in events if event['status'] == 200]
assert len(successful) == 8, 'Expected four successful real transcription/cleanup pairs'
assert all(event['status'] in [200,503] for event in events), 'A required live provider call failed'
clean = []
for index in range(0, 8, 2):
    speech, cleanup = successful[index:index + 2]
    assert speech['status'] == 200 and cleanup['status'] == 200
    assert speech['path'].endswith((':generateContent', '/audio/transcriptions'))
    assert cleanup['path'].endswith('/chat/completions') and cleanup['finish'] == 'stop'
    assert all(word in speech['output'].lower() for word in ['project','notes','alex','tomorrow','budget','thank'])
    clean.append(cleanup['output'].strip())
expected = {'practice':clean[0], 'foreground':'Before ' + clean[1]}
expected['manual-reinsert'] = expected['foreground'] + ' ' + clean[1]
expected['background-1'] = expected['manual-reinsert'] + ' ' + clean[2]
expected['background-2'] = expected['background-1'] + ' ' + clean[3]
expected['ended'] = expected['background-2']
assert readback == expected, 'Actual editor differs from the real cleanup output (including possible raw fallback)'
result = {'passed':True,'provider':metadata['provider'],'successfulProviderStages':len(successful),'httpAttempts':len(events),'takes':4,'backgroundTakes':2,'realRecorder':True,'audioInput':'Synthetic speech through actual Core Audio loopback','readbacks':readbacks[0]}
(destination / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'passed':True,'provider':metadata['provider'],'successfulProviderStages':len(events),'takes':4,'exactReadbacks':len(readback)}))
