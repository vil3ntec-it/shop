#!/bin/bash
# آزمونِ دسکتاپ + هسته در یک worktree؛ خلاصهٔ شکست‌ها
WT=$1; OPS=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)  # همین پوشه (tools/claude-ops)
S=${S:?S را پوشهٔ scratchpad ِ همین سیزن بگذار (worktreeها آن‌جا ساخته می‌شوند)}
cd $WT || exit 1
export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"
gradle -p core jvmTest --offline --no-daemon --init-script $OPS/v3f/offline.init.gradle -q > $S/dc-core.log 2>&1; echo core-rc=$?
gradle -p desktop test --offline --no-daemon -Pdev=true -Pkotlin.compiler.execution.strategy=in-process --init-script $OPS/v3f/offline.init.gradle -q > $S/dc-desk.log 2>&1; echo desk-rc=$?
grep -m3 '^e: \|OutOfMemory' $S/dc-desk.log
python3 - <<'PY'
import glob,re,xml.etree.ElementTree as E
for d in ['core/build/test-results/jvmTest','desktop/build/test-results/test']:
    n=f=0; bad={}
    for x in glob.glob(d+'/*.xml'):
        r=E.parse(x).getroot(); n+=int(r.get('tests',0)); k=int(r.get('failures',0))+int(r.get('errors',0)); f+=k
        if k: bad[r.get('name').split('.')[-1]]=k
    print(d,n,f,sorted(bad.items()))
PY
df -h / | tail -1
