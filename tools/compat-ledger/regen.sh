#!/bin/bash
# نمونه‌های سازگاریِ دفتر را از نو می‌سازد: OLD (پیش‌فرض e02fe4561 = main ِ دستهٔ ۳۰) و NEW (پیش‌فرض HEAD).
#   OLD=e02fe4561 NEW=HEAD bash tools/compat-ledger/regen.sh
# خروجی: android-native/app/src/test/resources/compat/ — شرح: docs/DATA-SAFETY-fa.md §۷.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
OLD=${OLD:-e02fe4561}; NEW=${NEW:-HEAD}   # OLD: main ِ پیش از دستهٔ ۳۱ (دستهٔ ۳۰) — آخرین نسخهٔ نصب‌شده
WORK=${COMPAT_WORK:-$(mktemp -d)}
OUT=$ROOT/android-native/app/src/test/resources/compat
SRC=android-native/app/src/main/java/ir/vil3ntec/tohid/data
FILES="Models.kt ModelParts.kt SafeEntry.kt DeliveryAddress.kt SetAsideDoc.kt"
mkdir -p "$OUT"
export LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8"
run() {  # <نام> <ref> <فرمان…>
  local name=$1 ref=$2; shift 2
  local m=$WORK/models-$name/ir/vil3ntec/tohid/data
  if [ ! -d "$m" ]; then
    mkdir -p "$m"
    for f in $FILES; do git -C "$ROOT" show "$ref:$SRC/$f" > "$m/$f" 2>/dev/null || rm -f "$m/$f"; done
  fi
  COMPAT_BUILD=$WORK/build-$name gradle -p "$ROOT/tools/compat-ledger" --offline --no-daemon -q -Pmodels="$WORK/models-$name" run --args="$*"
}
run old "$OLD" maximal "$OUT/old-maximal.json"
run old "$OLD" resave "$ROOT/tools/shop-migrate/src/test/resources/golden/fixture-seed1.json" "$OUT/old-seed1.json"
run new "$NEW" maximal "$OUT/new-maximal.json"
run old "$OLD" resave "$OUT/new-maximal.json" "$OUT/old-resaved-new.json"
git -C "$ROOT" rev-parse "$OLD" > "$OUT/REFS.txt"; git -C "$ROOT" rev-parse "$NEW" >> "$OUT/REFS.txt"
echo "compat fixtures ⇒ $OUT"
