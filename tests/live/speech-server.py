import http.server
from pathlib import Path
import socket
import signal
import subprocess
import sys
import threading
import time

speech = Path(sys.argv[1]).resolve()
assert speech.is_file()
health_only = len(sys.argv) == 3 and sys.argv[2] == '--health-only'
player = None
if not health_only:
    assert len(sys.argv) == 3 and Path(sys.argv[2]).is_file()
    player = subprocess.Popen([sys.argv[2],str(speech)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,bufsize=1)
    assert player.stdout.readline().strip() == 'ready', 'Native playback engine failed to prepare'
playback_lock = threading.Lock()
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(204)
            self.end_headers()
            return
        if self.path != '/play':
            self.send_error(404)
            return
        if player is None:
            self.send_error(503, 'Health-check-only server')
            return
        started = time.monotonic()
        print('playback-start', round(started, 3), flush=True)
        with playback_lock:
            player.stdin.write('play\n'); player.stdin.flush()
            completed = player.stdout.readline().strip()
        print('playback-finish', round(time.monotonic(), 3), 'seconds', round(time.monotonic() - started, 3), completed, flush=True)
        if not completed.startswith('done '):
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
server = http.server.ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
try:
    server.serve_forever()
finally:
    ipv6.shutdown(); ipv6.server_close(); server.server_close()
    if player is not None:
        player.terminate(); player.wait(timeout=5)
