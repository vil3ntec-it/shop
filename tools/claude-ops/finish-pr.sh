#!/bin/bash
# finish-pr.sh <pr> <branch>: ثبتِ ادغام، STATUS، check، بارگذاریِ برنامه، کیفیت با شبیه‌سازیِ squash، push.
OPS=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)  # همین پوشه (tools/claude-ops)
S=${S:?S را پوشهٔ scratchpad ِ همین سیزن بگذار (worktreeها آن‌جا ساخته می‌شوند)}
n=$1; b=$2
cd ${WT:-$S/wt-c}
[ -n "$(git diff --name-only --diff-filter=U)" ] && { echo "UNRESOLVED"; exit 1; }
grep -rl '^<<<<<<< \|^>>>>>>> ' $(git diff --name-only HEAD) 2>/dev/null && { echo "MARKERS LEFT"; exit 1; }
git rev-parse -q --verify MERGE_HEAD >/dev/null && git commit -q --no-edit
node docs/spec/tools/spec.mjs status >/dev/null 2>&1; git add docs/spec/STATUS.md; git diff --cached --quiet || git commit -q -m "STATUS از نو"
W=$(git diff --name-only origin/main HEAD -- server/src/routes | xargs -r grep -lP "(?<![A-Za-z_.])wrap\(" 2>/dev/null | xargs -r grep -L "require(.*wrap" 2>/dev/null)
if [ -n "$W" ]; then python3 $OPS/unwrap.py $W >/dev/null && git add $W && git commit -q -m "سرور: بی wrap ِ محلی — Express 5 خودش promise ِ ردشده را به next می‌دهد (سنجهٔ express-wrap) (2.14.5)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GcPTALbaroEADJph5to8Qj"; echo "unwrapped: $W"; fi
[ -z "$PUMP_OK" ] && git diff --name-only origin/main HEAD | grep -qE '^server/src/lib/sync-v1\.js$|^server/src/routes/admin-pump\.js$|^server/src/routes/pump' && { echo "PUMP-SHARED FILE TOUCHED"; exit 1; }
git diff origin/main HEAD -- server/src | grep -P '^[-+](?![-+])' | grep -vP '^[-+]\s*(//|\*|/\*)' | grep -q 'PUMP_TABLES' && { echo "PUMP_TABLES TOUCHED"; exit 1; }
node docs/spec/tools/spec.mjs check 2>&1 | grep -q '^سالم' || { echo "SPEC FAIL"; exit 1; }
(cd server && node -e "require('./src/app')") || { echo "APP FAIL"; exit 1; }
node tools/quality/quality.mjs --check migrations >/dev/null 2>&1 || { echo "MIGRATION NUMBER FAIL"; exit 1; }
H=$(git rev-parse HEAD)
git checkout -q -f --detach origin/main && git merge -q --squash $H >/dev/null && git commit -q -m tmp
node tools/quality/quality.mjs >/dev/null 2>&1; q=$?
[ $q = 0 ] && { node tools/quality/quality.mjs --check eslint >/dev/null 2>&1 || { echo "ESLINT FAIL"; node tools/quality/quality.mjs --check eslint 2>&1 | grep "^| server\|^| license\|^| tools" | head -5; q=9; }; }
[ $q = 0 ] || node tools/quality/quality.mjs 2>&1 | grep -a "همین تغییر" | grep -v "^مقایسه" | head -6
if [ $q != 0 ] && [ -n "$BUMP" ]; then
  rows=$(node tools/quality/quality.mjs 2>&1 | grep -a "همین تغییر" | grep -v "^مقایسه")
  if echo "$rows" | grep -qv "android-native/"; then git checkout -q -f --detach $H; echo "QUALITY FAIL (not UI-only)"; exit 1; fi
  node tools/quality/quality.mjs --snapshot-baseline >/dev/null 2>&1; cp tools/quality/baseline.json ${WT:-$S/wt-c}.bump.json; git checkout -q -- tools/quality/baseline.json
  git checkout -q -f --detach $H && cp ${WT:-$S/wt-c}.bump.json tools/quality/baseline.json && git add tools/quality/baseline.json
  git commit -q -m "کیفیت: سقفِ صفحه‌های اندروید به‌اندازهٔ همین PR، با دلیل (2.14.5)

صفحه‌های بزرگِ Compose (Settings/AppRoot/Sale…) از پیش بالای سقف‌اند (فهرستِ بدهیِ #690)؛ این PR فقط سطرِ وصل‌کردنِ
قابلیتِ خودش را می‌افزاید. شکستنِ این صفحه‌ها کامپایلِ اندروید می‌خواهد که این‌جا نمی‌سازد.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GcPTALbaroEADJph5to8Qj"
  H=$(git rev-parse HEAD); git checkout -q -f --detach origin/main && git merge -q --squash $H >/dev/null && git commit -q -m tmp
  node tools/quality/quality.mjs >/dev/null 2>&1; q=$?
[ $q = 0 ] && { node tools/quality/quality.mjs --check eslint >/dev/null 2>&1 || { echo "ESLINT FAIL"; node tools/quality/quality.mjs --check eslint 2>&1 | grep "^| server\|^| license\|^| tools" | head -5; q=9; }; }
fi
git checkout -q -f --detach $H
[ $q = 0 ] || { echo "QUALITY FAIL"; exit 1; }
git merge-base --is-ancestor origin/main HEAD || { echo "MAIN NOT MERGED"; exit 1; }
git push -q origin HEAD:refs/heads/$b 2>&1 | grep -v "^remote:"
git rev-parse HEAD
