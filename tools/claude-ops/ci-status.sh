#!/bin/bash
# usage: tools/claude-ops/ci-status.sh <shop|server> <pr-number>...
#   خلاصهٔ check-run های head ِ هر PR: شمار به تفکیکِ وضعیت و هر کارِ ناتمام/سرخ با id (برای gh api .../actions/jobs/<id>/logs)
R=${1:?repo: shop|server}; shift
for pr in "$@"; do
  set -- $(curl -sS -H "Accept: application/vnd.github+json" "https://api.github.com/repos/vil3ntec-it/$R/pulls/$pr" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['head']['sha'], d.get('mergeable_state'), d['state'], d.get('merged'))")
  s=$1; echo "== $R#$pr head=${s:0:9} mergeable=$2 state=$3 merged=$4"
  curl -sS -H "Accept: application/vnd.github+json" "https://api.github.com/repos/vil3ntec-it/$R/commits/$s/check-runs?per_page=100" | python3 -c "
import json,sys
from collections import Counter
d=json.load(sys.stdin)
print('  ',d.get('total_count'),dict(Counter((r['status'],r['conclusion']) for r in d.get('check_runs',[]))))
for r in d.get('check_runs',[]):
  if r['conclusion'] not in (None,'success','skipped','neutral') or r['status']!='completed': print('    ',r['id'],r['name'],r['status'],r['conclusion'])
"
done
