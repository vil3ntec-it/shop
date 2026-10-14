#!/usr/bin/env bash
# bench — شروعِ سردِ برنامهٔ اندروید روی گوشیِ مرجع (F0-26، بخشِ ۹.۱ و ۹.۲).
#
#   bash bench/android-cold-start.sh [دفعه‌ها=10] [بسته=ir.vil3ntec.tohid]
#
# نیاز: adb روی کامپیوتر، گوشی با «USB debugging» وصل، و برنامه نصب.
# هر دور: برنامه کاملاً بسته می‌شود (force-stop) و با `am start -W` باز
# می‌شود؛ TotalTime همان زمانی است که اندروید تا نخستین فریمِ برنامه
# می‌شمارد. شروعِ «واقعاً سرد» (بی کشِ سیستم) فقط دورِ نخست پس از
# روشن‌کردنِ دوبارهٔ گوشی است — آن را جدا یادداشت کنید.
# هیچ دادهٔ دکانی خوانده یا فرستاده نمی‌شود؛ فقط عدد چاپ می‌شود.
set -euo pipefail
RUNS="${1:-10}"
PKG="${2:-ir.vil3ntec.tohid}"

command -v adb >/dev/null || { echo "adb پیدا نشد (Android platform-tools)"; exit 2; }
ACT="$(adb shell cmd package resolve-activity --brief "$PKG" | tr -d '\r' | tail -n 1)"
case "$ACT" in */*) ;; *) echo "برنامهٔ $PKG روی گوشی نیست"; exit 2;; esac

echo "گوشی: $(adb shell getprop ro.product.model | tr -d '\r') — اندروید $(adb shell getprop ro.build.version.release | tr -d '\r') — $(adb shell head -n 1 /proc/meminfo | tr -d '\r')"
echo "نسخهٔ برنامه: $(adb shell dumpsys package "$PKG" | tr -d '\r' | awk -F= '/versionName/{print $2; exit}')"

times=()
for i in $(seq 1 "$RUNS"); do
  adb shell am force-stop "$PKG"
  sleep 2
  t="$(adb shell am start -W -n "$ACT" | tr -d '\r' | awk -F': ' '/TotalTime/{print $2}')"
  echo "دور $i: ${t}ms"
  times+=("$t")
  sleep 4
done

echo "حافظه پس از آخرین باز شدن: $(adb shell dumpsys meminfo "$PKG" | tr -d '\r' | awk '/TOTAL PSS|TOTAL:/{print $0; exit}')"
printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END {
  i50 = int((NR + 1) / 2); i95 = int(NR * 0.95 + 0.999); if (i95 > NR) i95 = NR
  print "شروعِ سرد: p50 " a[i50] "ms  p95 " a[i95] "ms  بیشینه " a[NR] "ms  (n=" NR ")"
}'
