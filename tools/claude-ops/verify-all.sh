#!/bin/bash
# usage: tools/claude-ops/verify-all.sh <worktree> [--offline]
#   «همه چیز روی کانتینر، بی حدس» (دستورِ صاحب‌کار ۱۴۰۵/۰۸/۲۲): پشتِ هم، بی ایست روی نخستین سرخ، با خلاصه در
#   <worktree>/../verify-<نام>.log. --offline ⇒ هر گام با offline-run.sh (بی اینترنت؛ Gradle با --offline).
#   تنها اجرا شود (۴ هسته/۱۵ گیگ): بی عاملِ هم‌زمان.
W=$(cd "${1:?worktree}" && pwd); shift; OFF=""; [ "$1" = "--offline" ] && OFF=1
L="$W/../verify-$(basename "$W")${OFF:+-offline}.log"; : > "$L"
R="$(dirname "$0")/offline-run.sh"
run(){ local name=$1; shift; local t0=$(date +%s)
  if [ -n "$OFF" ]; then bash "$R" "$@" > "$L.$name" 2>&1; else "$@" > "$L.$name" 2>&1; fi
  local rc=$?; echo "$name rc=$rc $(( $(date +%s)-t0 ))s $(grep -cE '^✅|^ok |KTEST PASS' "$L.$name") ok, $(grep -cE '^❌|^not ok |KTEST FAIL' "$L.$name") bad" | tee -a "$L"; }
export LC_ALL=C.UTF-8 LANG=C.UTF-8 JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
G=""; [ -n "$OFF" ] && G="--offline"
cd "$W/server"
run server-pglite env TEST_DATABASE_URL=pglite:memory node scripts/test-pglite.js
run e2e env TEST_DATABASE_URL=pglite:memory node test/e2e/run.mjs
for f in $(node -e 'const p=require("./package.json").scripts;console.log(["test:panel","test:web","test:portal-rep","test:web-store","test:web-vitals","test:web-market"].map(k=>p[k]).join(" && "))' | sed 's/ && /\n/g' | sed 's/^node //'); do
  run "browser-$(basename "$f" .mjs)" env TEST_DATABASE_URL=pglite:memory node "$f"
done
cd "$W"
run core gradle --no-daemon $G -p core jvmTest -Dorg.gradle.jvmargs=-Xmx4g
run desktop ./desktop/gradlew --no-daemon $G -p desktop test
echo "done $(date -u +%FT%TZ)" | tee -a "$L"
