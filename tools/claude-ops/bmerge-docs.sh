#!/bin/bash
# bm2.sh <wt> <branch>...: like bmerge.sh but also auto-resolves docs/HANDOFF-fa.md (theirs-first per block); stops on anything else
W=$1; shift; cd "$W" || exit 2
for b in "$@"; do
  if ! git rev-parse -q --verify MERGE_HEAD >/dev/null; then
    git merge-base --is-ancestor origin/$b HEAD && { echo "$b already"; continue; }
    git merge -q --no-edit origin/$b >/dev/null 2>&1
  fi
  U=$(git diff --name-only --diff-filter=U)
  if echo "$U" | grep -qx docs/HANDOFF-fa.md; then python3 tools/claude-ops/cres.py docs/HANDOFF-fa.md $(yes to | head -n "$(grep -c '^<<<<<<< ' docs/HANDOFF-fa.md)") && git add docs/HANDOFF-fa.md; fi
  if echo "$U" | grep -qx docs/WHATS-NEW-fa.md; then python3 tools/claude-ops/cres.py docs/WHATS-NEW-fa.md $(yes ot | head -n "$(grep -c '^<<<<<<< ' docs/WHATS-NEW-fa.md)") && git add docs/WHATS-NEW-fa.md; fi
  if echo "$U" | grep -qx docs/spec/STATUS.md; then python3 tools/claude-ops/batch-status.py >/dev/null && git add docs/spec/STATUS.md; fi
  U=$(git diff --name-only --diff-filter=U | tr '\n' ' ')
  if [ -n "$U" ]; then echo "STOP $b U=[$U]"; exit 1; fi
  git rev-parse -q --verify MERGE_HEAD >/dev/null && git commit -q --no-edit
  echo "$b ok"
done
