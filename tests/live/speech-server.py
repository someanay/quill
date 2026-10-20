import http.server
from pathlib import Path
import subprocess
import sys

speech = Path(sys.argv[1]).resolve()
assert speech.is_file()
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != '/play':
            self.send_error(404)
            return
        completed = subprocess.run(['/usr/bin/afplay', str(speech)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if completed.returncode != 0:
            self.send_error(500, 'Fixture playback failed')
            return
        self.send_response(204)
        self.end_headers()
    def log_message(self, *args):
        pass
http.server.ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
