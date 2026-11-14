#!/bin/bash
# usage: precheck.sh <worktree>  — every static check before pushing a shop batch
W=${1:?worktree}; cd "$W" || exit 2
[ -e server/node_modules ] || { echo "server/node_modules missing — symlink a shared install (e.g. ln -s <deps>/server/node_modules server/node_modules) or npm ci in server/"; exit 2; }
fail=0
step(){ echo "== $1"; }
step api-contract; out=$(node tools/api-contract/generate.cjs --check 2>&1); rc=$?; echo "$out" | tail -1; [ $rc -eq 0 ] || { echo "api-contract exit=$rc"; fail=1; }
step spec; node docs/spec/tools/spec.mjs check 2>&1 | grep -q سالم && echo سالم || { echo 'spec NOT healthy'; fail=1; }
step whatsnew; out=$(node tools/whatsnew/check.mjs --base origin/main 2>&1); rc=$?; echo "$out" | tail -1; [ $rc -eq 0 ] || { echo "whatsnew exit=$rc"; fail=1; }
step quality; node tools/quality/quality.mjs >/tmp/precheck-q.$$ 2>&1 || fail=1; grep -c '❌' /tmp/precheck-q.$$ | sed 's/^/❌ count: /'; grep '❌' /tmp/precheck-q.$$ | head -5; rm -f /tmp/precheck-q.$$
step secret-scan; out=$(node tools/security/secret-scan.mjs --all 2>&1); rc=$?; echo "$out" | tail -1; [ $rc -eq 0 ] || { echo "secret-scan exit=$rc"; fail=1; }
step dari-words; out=$(node server/scripts/dari-words.js 2>&1); rc=$?; echo "$out" | tail -1; [ $rc -eq 0 ] || { echo "dari-words exit=$rc"; fail=1; }
step kt-names; out=$(python3 tools/claude-ops/kt-names.py android-native android core desktop customer-android ios 2>/dev/null); echo "$out" | tail -1; echo "$out" | grep -q '^total 0$' || { echo "$out" | head -5; fail=1; }
step new-Str-registered
for s in $(git diff origin/main HEAD -U0 -- '*.kt' | grep -oE '^\+.*object (Str[A-Za-z0-9]+)' | grep -oE 'Str[A-Za-z0-9]+' | sort -u); do
  git grep -qw "$s" -- '*I18nTables.kt' || { echo "MISSING in I18nTables: $s"; fail=1; }
done
echo "== precheck fail=$fail"; exit $fail
