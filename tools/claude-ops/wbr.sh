#!/bin/bash
# wbr.sh <branch> <sha8> <label>: تا پایانِ «توحید بومی» روی این sha صبر
OPS=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)  # همین پوشه (tools/claude-ops)
S=${S:?S را پوشهٔ scratchpad ِ همین سیزن بگذار (worktreeها آن‌جا ساخته می‌شوند)}
for i in $(seq 1 160); do
  r=$(curl -s -H "User-Agent: x" "https://api.github.com/repos/vil3ntec-it/shop/actions/runs?branch=$1&per_page=20" | python3 -c "
import json,sys;d=json.load(sys.stdin)
x=[r for r in d['workflow_runs'] if r['name']=='توحید بومی' and r['head_sha'].startswith('$2')]
print(x[0]['id'],x[0]['status'],x[0]['conclusion']) if x else print('none')" 2>/dev/null)
  echo "$(date +%H:%M) $3 $r" >> $S/wbr-$3.log
  case "$r" in *completed*) set -- $r; echo "$3 native $1: $2 $3"; curl -s -H "User-Agent: x" "https://api.github.com/repos/vil3ntec-it/shop/actions/runs/$1/jobs" | python3 -c "import json,sys;d=json.load(sys.stdin);[print(j['name'],j['conclusion'],j['id']) for j in d.get('jobs',[])]"; exit 0;; esac
  sleep 90
done
