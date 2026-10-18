# سرورِ آزمونیِ CI برای دانلودِ ادامه‌دار (tohid-desktop.yml، آزمون‌های ۳ب و ۵).
#
# مثلِ سرورِ ما (res.download) و GitHub: Range/206/416 و ETag. هر فایلِ بزرگ‌تر از
# ۱ مگابایت در CUTS پاسخِ اولش وسطِ راه بریده می‌شود (نصفِ باقی‌مانده و بستنِ
# اتصال، کمتر از Content-Length) تا معلوم شود نصاب و install.ps1 از همان بایت
# ادامه می‌دهند، نه از صفر. هر درخواست در LOG: «نام Range» (بی Range ⇐ «-»).
#
#   python test-range-server.py ROOT PORT CUTS LOG
import http.server
import os
import re
import sys

ROOT, PORT, CUTS, LOG = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
left = {}


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def do_GET(self):
        name = os.path.basename(self.path.split('?')[0])
        path = os.path.join(ROOT, name)
        if not name or not os.path.isfile(path):
            self.send_error(404)
            return
        size = os.path.getsize(path)
        rng = self.headers.get('Range') or ''
        with open(LOG, 'a', encoding='ascii') as f:
            f.write(f'{name} {rng or "-"}\n')
        m = re.fullmatch(r'bytes=(\d+)-', rng)
        start = int(m.group(1)) if m else 0
        etag = f'"{size}-{int(os.path.getmtime(path))}"'
        if m and start >= size:
            self.send_response(416)
            self.send_header('Content-Range', f'bytes */{size}')
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        if m and self.headers.get('If-Range') not in (None, etag):
            m, start = None, 0
        self.send_response(206 if m else 200)
        if m:
            self.send_header('Content-Range', f'bytes {start}-{size - 1}/{size}')
        self.send_header('Content-Length', str(size - start))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('ETag', etag)
        self.send_header('Connection', 'close')
        self.end_headers()
        n = size - start
        if size > 1_000_000 and left.setdefault(name, CUTS) > 0:
            left[name] -= 1
            n //= 2
        with open(path, 'rb') as f:
            f.seek(start)
            while n > 0:
                chunk = f.read(min(1 << 16, n))
                if not chunk:
                    break
                self.wfile.write(chunk)
                n -= len(chunk)
        self.close_connection = True

    def log_message(self, *args):
        pass


http.server.ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
