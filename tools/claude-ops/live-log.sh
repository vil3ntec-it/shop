#!/usr/bin/env bash
# بخشِ زنده: یک رویداد بالای «دفترِ رویدادها» ِ LIVE-fa.md می‌نشاند، زمان را تازه می‌کند و روی claude/live-handoff push می‌کند.
# استفاده: bash tools/claude-ops/live-log.sh "متنِ رویداد"     (جدولِ بخشِ ۳ را پیش از آن با دست به‌روز کن)
# LIVE_WT = worktree ِ شاخهٔ claude/live-handoff (پیش‌فرض /home/user/wt-live)؛ LIVE_SESSION = نشانیِ سیزنِ خودت برای پانوشت.
set -euo pipefail
WT=${LIVE_WT:-/home/user/wt-live}
F=$WT/tools/claude-ops/LIVE-fa.md
NOW=$(TZ=Asia/Kabul date '+%F %H:%M')
python3 - "$F" "$NOW" "$1" <<'PY'
import re, sys
f, now, msg = sys.argv[1:4]
s = open(f, encoding='utf-8').read()
s = re.sub(r'\*\*آخرین به‌روزرسانی:\*\* [^\n]*', f'**آخرین به‌روزرسانی:** {now} (کابل)', s, count=1)
h = '## ۶. دفترِ رویدادها (تازه‌ترین بالا)\n\n'
assert h in s, 'سرخطِ دفترِ رویدادها پیدا نشد'
s = s.replace(h, h + f'- {now} — {msg}\n', 1)
open(f, 'w', encoding='utf-8').write(s)
PY
cd "$WT"
git add -A tools/claude-ops
git commit -q -F - <<MSG
زنده: ${1:0:70} (2.14.5)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: ${LIVE_SESSION:-https://claude.ai/code/session_01BwqpPed1SJuwB7Q4nZUbg9}
MSG
for i in 1 2 3 4; do git push -q origin HEAD:claude/live-handoff && exit 0; sleep $((i * 2)); done
echo 'push نشد' >&2; exit 1
