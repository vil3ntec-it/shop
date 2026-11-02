#!/bin/bash
# wprs.sh <pr>...: تا پایانِ همهٔ چک‌های سرِ این PRها صبر؛ خلاصه چاپ
for i in $(seq 1 120); do
  out=""; busy=0
  for n in "$@"; do
    sha=$(curl -s -H "User-Agent: x" https://api.github.com/repos/vil3ntec-it/shop/pulls/$n | python3 -c "import json,sys;print(json.load(sys.stdin)['head']['sha'])" 2>/dev/null)
    l=$(curl -s -H "User-Agent: x" "https://api.github.com/repos/vil3ntec-it/shop/commits/$sha/check-runs?per_page=100" | python3 -c "
import json,sys,collections;d=json.load(sys.stdin)['check_runs'];busy=sum(r['status']!='completed' for r in d);bad=[r['name'] for r in d if r['conclusion'] in('failure','timed_out','cancelled')]
print(busy,len(d),'|'.join(bad))" 2>/dev/null)
    set -- "$@"; b=${l%% *}; [ "${b:-1}" != 0 ] && busy=1
    out="$out #$n ${sha:0:8} $l;"
  done
  echo "$(date +%H:%M)$out" >> $S/wprs.log
  [ $busy = 0 ] && { echo "$out"; exit 0; }
  sleep 120
done
echo "timeout $out"
