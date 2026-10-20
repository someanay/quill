import http.server
from pathlib import Path
import socket
import subprocess
import sys
import threading
import time

speech = Path(sys.argv[1]).resolve()
assert speech.is_file()
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(204)
            self.end_headers()
            return
        if self.path != '/play':
            self.send_error(404)
            return
        started = time.monotonic()
        print('playback-start', round(started, 3), flush=True)
        completed = subprocess.run(['/usr/bin/afplay', str(speech)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print('playback-finish', round(time.monotonic(), 3), 'seconds', round(time.monotonic() - started, 3), 'exit', completed.returncode, flush=True)
        if completed.returncode != 0:
            self.send_error(500, 'Fixture playback failed')
            return
        self.send_response(204)
        self.end_headers()
    def log_message(self, *args):
        pass
class IPv6LoopbackServer(http.server.ThreadingHTTPServer):
    address_family = socket.AF_INET6

# Simulator resolves localhost to ::1 first. Listening on IPv4 alone introduced
# an initial connection failure and delayed speech until silence auto-stop.
ipv6 = IPv6LoopbackServer(('::1', 8765), Handler)
threading.Thread(target=ipv6.serve_forever, daemon=True).start()
http.server.ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
