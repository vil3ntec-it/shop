#!/usr/bin/env python3
"""
دروازهٔ نسخهٔ Google Play (`-Pstore=play`) — شرح: docs/GOOGLE-PLAY-fa.md

  play-check.py <app.aab|app.apk> <merged AndroidManifest.xml>

  • مانیفستِ ساخته‌شده: targetSdkVersion دست‌کم 36 (قاعدهٔ Play از ۳۱ اوت ۲۰۲۶) و بی REQUEST_INSTALL_PACKAGES
    (سیاستِ Play: برنامه خودش را به‌روز نمی‌کند).
  • کتابخانه‌های بومیِ ۶۴بیتی (arm64-v8a، x86_64): هر بخشِ LOAD ِ ELF هم‌ترازِ ۱۶ کیلوبایت
    (developer.android.com/guide/practices/page-sizes)؛ در APK، .so ِ فشرده‌نشده باید در خودِ zip هم ۱۶KB-هم‌تراز باشد.

خروجِ ناصفر ⇒ CI سرخ. فقط کتابخانهٔ استانداردِ پایتون.
"""
import re
import struct
import sys
import zipfile

PAGE = 16384
ABIS64 = ('arm64-v8a', 'x86_64')


def load_aligns(data):
    """هم‌ترازیِ بخش‌های PT_LOAD ِ یک ELF (۳۲ یا ۶۴ بیتی، little-endian)"""
    if data[:4] != b'\x7fELF':
        raise ValueError('ELF نیست')
    out = []
    if data[4] == 2:
        phoff = struct.unpack_from('<Q', data, 0x20)[0]
        size, num = struct.unpack_from('<HH', data, 0x36)
        for i in range(num):
            o = phoff + i * size
            if struct.unpack_from('<I', data, o)[0] == 1:
                out.append(struct.unpack_from('<Q', data, o + 48)[0])
    else:
        phoff = struct.unpack_from('<I', data, 0x1C)[0]
        size, num = struct.unpack_from('<HH', data, 0x2A)
        for i in range(num):
            o = phoff + i * size
            if struct.unpack_from('<I', data, o)[0] == 1:
                out.append(struct.unpack_from('<I', data, o + 28)[0])
    return out


def check_libs(path):
    bad = []
    seen = 0
    with zipfile.ZipFile(path) as z, open(path, 'rb') as raw:
        for info in z.infolist():
            name = info.filename
            if not name.endswith('.so') or not any(f'/lib/{a}/' in f'/{name}' for a in ABIS64):
                continue
            seen += 1
            aligns = load_aligns(z.read(name))
            if not aligns or min(aligns) < PAGE:
                bad.append(f'{name}: LOAD align {sorted(set(aligns))} < 16 KB')
            if path.endswith('.apk') and info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset)
                head = raw.read(30)
                nlen, xlen = struct.unpack_from('<HH', head, 26)
                if (info.header_offset + 30 + nlen + xlen) % PAGE:
                    bad.append(f'{name}: zip offset not 16 KB aligned')
    return seen, bad


def check_manifest(path):
    text = open(path, encoding='utf-8').read()
    bad = []
    m = re.search(r'android:targetSdkVersion="(\d+)"', text)
    target = int(m.group(1)) if m else 0
    if target < 36:
        bad.append(f'targetSdkVersion {target} < 36')
    if 'android.permission.REQUEST_INSTALL_PACKAGES' in text:
        bad.append('REQUEST_INSTALL_PACKAGES در مانیفست است')
    return target, bad


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    seen, bad = check_libs(argv[1])
    target, more = check_manifest(argv[2])
    bad += more
    print(f'targetSdk={target} · 64-bit .so={seen}')
    for b in bad:
        print(f'::error::{b}')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
