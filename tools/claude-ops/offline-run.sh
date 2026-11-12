#!/bin/bash
# usage: tools/claude-ops/offline-run.sh <command...>
#   فرمان را در یک فضای شبکهٔ تازه اجرا می‌کند: **هیچ اینترنتی** (نه DNS، نه پراکسی)، فقط 127.0.0.1.
#   برای «بی اینترنت هم کار می‌کند؟» ِ واقعی، نه شبیه‌سازی (دستورِ صاحب‌کار ۱۴۰۵/۰۸/۲۲). نیازِ root/unshare.
#   پیش از اجرا خودش می‌سنجد که اینترنت واقعاً بسته و localhost باز است؛ وگرنه کدِ ۳.
[ $# -gt 0 ] || { echo "usage: $0 <command...>"; exit 2; }
exec unshare -n bash -c '
python3 - <<PY || exit 3
import socket,fcntl,struct
s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
ifr=struct.pack("16sH14s",b"lo",0,b"\0"*14)
flags=struct.unpack("16sH14s",fcntl.ioctl(s,0x8913,ifr))[1]
fcntl.ioctl(s,0x8914,struct.pack("16sH14s",b"lo",flags|1,b"\0"*14))
PY
unset HTTPS_PROXY HTTP_PROXY https_proxy http_proxy ALL_PROXY
node -e "require(\"https\").get(\"https://github.com\",()=>process.exit(1)).on(\"error\",()=>process.exit(0))" || { echo "اینترنت هنوز باز است — آزمون بی‌معنا"; exit 3; }
echo "== بی اینترنت (فقط 127.0.0.1): $*"
"$@"' bash "$@"
