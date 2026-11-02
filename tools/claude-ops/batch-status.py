# Row-level 3-way merge of docs/spec/STATUS.md while merging a PR branch INTO the batch branch.
# ours (stage 2) = batch, theirs (stage 3) = PR, base (stage 1) = merge-base.
# Rows only the PR changed => PR's row. Rows both changed => cell-wise merge:
#   status: the more advanced of the two (printed), evidence: segment union (split on "؛ "), commit/PR: union.
# Summary tables are left as ours and rebuilt by `spec.mjs status`.
import subprocess, re, sys
p = 'docs/spec/STATUS.md'
def stage(n): return subprocess.run(['git', 'show', f':{n}:{p}'], capture_output=True, text=True, check=True).stdout
base, ours, theirs = stage(1), stage(2), stage(3)
ROW = re.compile(r'^\| ([A-Za-z][A-Za-z0-9@.\-]*) \| ')
SUMMARY = re.compile(r'^\| (\*\*همه\*\*|[A-Z][A-Z0-9-]*|[۰-۹]+|بی‌فاز در سند) \| [۰-۹]')
def rows(t):
    d = {}
    for line in t.split('\n'):
        m = ROW.match(line)
        if m and not SUMMARY.match(line): d.setdefault(m.group(1), line)
    return d
b, o, t = rows(base), rows(ours), rows(theirs)
RANK = {'MISSING': 0, 'BROKEN': 0, 'NOT-VERIFIED': 0, 'BLOCKED-EXTERNAL': 1, 'PARTIAL': 2, 'IMPLEMENTED-VERIFIED': 3}
def cells(line): return line.strip()[2:-2].split(' | ')
def seg_union(bc, oc, tc, sep):
    bs, os_, ts = bc.split(sep), oc.split(sep), tc.split(sep)
    out = [x for x in os_ if x in ts or x not in bs]          # drop what theirs removed from base
    for x in ts:
        if x not in bs and x not in out: out.append(x)        # add theirs' new segments
    return sep.join(out)
def merge_row(k):
    bc, oc, tc = cells(b.get(k, o[k])), cells(o[k]), cells(t[k])
    if not (len(bc) == len(oc) == len(tc) == 10): sys.exit(f'manual: cell count {k}')
    res = []
    for i in range(10):
        x, y, z = bc[i], oc[i], tc[i]
        if y == z or z == x: res.append(y); continue
        if y == x: res.append(z); continue
        if i == 5:
            v = y if RANK.get(y, 0) >= RANK.get(z, 0) else z
            print(f'  {k}: status ours={y} theirs={z} -> {v}'); res.append(v)
        elif i == 6: res.append(seg_union(x, y, z, '؛ '))
        elif i == 9: res.append(seg_union(x, y, z, '، '))
        else:
            print(f'  {k}: cell {i} differs; taking theirs'); res.append(z)
    return '| ' + ' | '.join(res) + ' |'
changed = [k for k in t if t[k] != b.get(k)]
missing = [k for k in changed if k not in o]
if missing: sys.exit(f'manual: rows missing in batch: {missing}')
out, applied, clashes = [], 0, []
for line in ours.split('\n'):
    m = ROW.match(line)
    k = m.group(1) if m and not SUMMARY.match(line) else None
    if k in changed and line == o[k]:
        if o[k] == b.get(k): line = t[k]; applied += 1
        elif o[k] != t[k]: clashes.append(k); line = merge_row(k)
    out.append(line)
# non-row lines the PR added/changed (outside summary tables): warn
nb = set(l for l in base.split('\n') if not ROW.match(l))
no = set(l for l in ours.split('\n') if not ROW.match(l))
extra = [l for l in theirs.split('\n') if not ROW.match(l) and not re.match(r'^\| (\*\*همه\*\*|[۰-۹]+|بی‌فاز در سند) \| ', l) and l not in nb and l not in no]
if extra:
    # درج پس از نزدیک‌ترین سطرِ پیشینِ «آن‌ها» که در خروجی هست (یادداشت‌ها گم نشوند)
    tl = theirs.split('\n'); ex = set(extra)
    for i, l in enumerate(tl):
        if l not in ex or l in out: continue
        j = i - 1
        while j >= 0 and tl[j] not in out: j -= 1
        pos = out.index(tl[j]) + 1 if j >= 0 else len(out)
        # پس از سطرهای درج‌شدهٔ پیشینِ همین زنجیره
        while pos < len(out) and out[pos] in ex: pos += 1
        out.insert(pos, l)
    print('inserted non-row lines from theirs:', len(extra))
open(p, 'w', encoding='utf-8').write('\n'.join(out))
print(f'applied {applied} PR rows; merged clashes: {clashes}')
