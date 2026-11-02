#!/bin/bash
# bmerge.sh <wt> <branch>...: ادغامِ پیاپی؛ STATUS با batch-status؛ تعارضِ دیگر ⇒ توقف
OPS=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)  # همین پوشه (tools/claude-ops)
S=${S:?S را پوشهٔ scratchpad ِ همین سیزن بگذار (worktreeها آن‌جا ساخته می‌شوند)}
cd $1; shift
for b in "$@"; do
  if ! git rev-parse -q --verify MERGE_HEAD >/dev/null; then
    git merge-base --is-ancestor origin/$b HEAD && { echo "$b already"; continue; }
    git merge -q --no-edit origin/$b >/dev/null 2>&1
  fi
  U=$(git diff --name-only --diff-filter=U)
  if echo "$U" | grep -qx docs/spec/STATUS.md; then python3 $OPS/batch-status.py >/dev/null && git add docs/spec/STATUS.md; fi
  U=$(git diff --name-only --diff-filter=U | tr '\n' ' ')
  if [ -n "$U" ]; then echo "STOP $b U=[$U]"; exit 1; fi
  git rev-parse -q --verify MERGE_HEAD >/dev/null && git commit -q --no-edit
  echo "$b ok"
done
