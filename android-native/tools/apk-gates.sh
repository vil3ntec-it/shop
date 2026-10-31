#!/usr/bin/env bash
#
#  دروازه‌های APK — معیارِ «قدم صفر»ِ بخشِ ۳.۳ِ مشخصات (docs/spec/03-architecture.md).
#
#  APKِ تازه با آخرین نسخهٔ منتشرشده (انتشارِ آزمایشی، وگرنه پایدار) سنجیده می‌شود:
#    • applicationId همان `ir.vil3ntec.tohid`               — همیشه سخت
#    • versionCode همان «۱۰۰ + شمارهٔ ساخت» و کمتر از منتشرشده نیست — همیشه سخت
#    • اثر انگشتِ گواهیِ امضا (SHA-256) همان منتشرشده         — همیشه سخت
#    • حجم در ±۵٪ِ منتشرشده — سخت فقط وقتی PR فایلِ ساخت را عوض کرده
#      (build/settings.gradle.kts، gradle.properties، هر proguard*.pro، wrapper)؛
#      در بقیه فقط هشدار، چون قابلیتِ تازه حق دارد APK را بزرگ کند.
#  شمارِ آزمون‌ها هم (اگر پوشهٔ نتیجه داده شود) در خلاصهٔ اجرا می‌آید.
#
#  usage: apk-gates.sh <apk> <expected-versionCode> [test-results-dir]
#  env:   ANDROID_HOME, GH_TOKEN, GITHUB_REPOSITORY, PR_NUMBER (خالی بیرون از PR)
#
set -euo pipefail

apk=$1
want_code=$2
results=${3:-}
want_id=ir.vil3ntec.tohid
summary=${GITHUB_STEP_SUMMARY:-/dev/stdout}

bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -n1)
aapt="$bt/aapt"
apksigner="$bt/apksigner"

#  sed، نه head: head زودتر می‌بندد و aapt با SIGPIPE زیرِ pipefail می‌افتد
badge() { "$aapt" dump badging "$1" | sed -n '1p'; }
field() { sed -n "s/.* $1='\([^']*\)'.*/\1/p" <<<"$2"; }
#  apksigner بسته به نسخه و طرحِ امضا «Signer #1 certificate …»،
#  «Signer (minSdkVersion=…) certificate …» یا «V3.0 Signer: certificate …»
#  می‌نویسد — هر سه پذیرفته است؛ فقط اثر انگشتِ **گواهی** (نه کلیدِ عمومی)
cert() { { "$apksigner" verify --print-certs -v "$1" 2>&1 || true; } | sed -nE 's/^.*Signer.*certificate SHA-256 digest: *([0-9a-fA-F]{64}).*$/\1/p' | sed -n '1p'; }

fail=0
err() { echo "::error::$1"; fail=1; }

b=$(badge "$apk")
id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$b")
code=$(field versionCode "$b")
fp=$(cert "$apk")
size=$(stat -c %s "$apk")

[ "$id" = "$want_id" ] || err "applicationId عوض شد: «$id» (باید $want_id بماند)"
[ "$code" = "$want_code" ] || err "versionCode «$code» است، باید «$want_code» (۱۰۰ + شمارهٔ ساخت) باشد"
if [ -z "$fp" ]; then
  err "اثر انگشتِ امضا از APK خوانده نشد"
  "$apksigner" verify --print-certs -v "$apk" 2>&1 | sed -n '1,40p' || true
fi

#  PRی که فایلِ ساخت را عوض کرده، دروازهٔ حجمِ سخت دارد
strict=0
if [ -n "${PR_NUMBER:-}" ]; then
  files=$(gh api "repos/$GITHUB_REPOSITORY/pulls/$PR_NUMBER/files" --paginate --jq '.[].filename')
  if grep -qE '(^|/)(build|settings)\.gradle\.kts$|(^|/)gradle\.properties$|(^|/)proguard[^/]*\.pro$|gradle-wrapper\.properties$|libs\.versions\.toml$' <<<"$files"; then
    strict=1
  fi
fi

ref_dir=$(mktemp -d)
ref=""
if gh release download tohid-native-beta -R "$GITHUB_REPOSITORY" -p '*.apk.beta' -D "$ref_dir" 2>/dev/null; then
  ref_from=tohid-native-beta
elif gh release download tohid-native -R "$GITHUB_REPOSITORY" -p 'Tohid-Native.apk' -D "$ref_dir" 2>/dev/null; then
  ref_from=tohid-native
fi
src=$(ls "$ref_dir"/* 2>/dev/null | head -n1 || true)
if [ -n "$src" ]; then
  ref="$ref_dir/ref.apk"
  cp "$src" "$ref"
fi

ref_id="—"; ref_code="—"; ref_fp="—"; ref_size=0; ratio="—"
if [ -n "$ref" ]; then
  rb=$(badge "$ref")
  ref_id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$rb")
  ref_code=$(field versionCode "$rb")
  ref_fp=$(cert "$ref")
  ref_size=$(stat -c %s "$ref")
  [ "$ref_id" = "$id" ] || err "applicationId با نسخهٔ منتشرشده ($ref_id) یکی نیست"
  [ "$ref_fp" = "$fp" ] || err "اثر انگشتِ امضا با نسخهٔ منتشرشده یکی نیست — به‌روزرسانی روی گوشی‌ها نصب نمی‌شود"
  [ "$code" -ge "$ref_code" ] || err "versionCode ($code) از نسخهٔ منتشرشده ($ref_code) کمتر است"
  ratio=$(awk -v a="$size" -v b="$ref_size" 'BEGIN { printf "%+.2f", (a - b) * 100 / b }')
  if awk -v r="$ratio" 'BEGIN { exit !(r > 5 || r < -5) }'; then
    if [ "$strict" = 1 ]; then
      err "حجمِ APK ${ratio}٪ با نسخهٔ منتشرشده فرق دارد (مرز ±۵٪ برای تغییرِ ساخت)"
    else
      echo "::warning::حجمِ APK ${ratio}٪ با نسخهٔ منتشرشده فرق دارد"
    fi
  fi
else
  echo "::warning::نسخهٔ منتشرشده‌ای برای سنجش پیدا نشد — فقط applicationId و versionCode سنجیده شد"
fi

tests="—"
if [ -n "$results" ] && ls "$results"/*.xml >/dev/null 2>&1; then
  tests=$(grep -ho '<testsuite [^>]*' "$results"/*.xml | awk '
    { for (i = 1; i <= NF; i++) if (match($i, /^(tests|failures|errors|skipped)="[0-9]+"/)) {
        split($i, kv, "\""); k = substr($i, 1, index($i, "=") - 1); s[k] += kv[2] } }
    END { printf "%d آزمون، %d شکست، %d خطا، %d ردشده", s["tests"], s["failures"], s["errors"], s["skipped"] }')
fi

{
  echo "### دروازه‌های APK (قدم صفرِ ۳.۳)"
  echo
  echo "| | این ساخت | منتشرشده (${ref_from:-—}) |"
  echo "|---|---|---|"
  echo "| applicationId | \`$id\` | \`$ref_id\` |"
  echo "| versionCode | $code | $ref_code |"
  echo "| امضا SHA-256 | \`$fp\` | \`$ref_fp\` |"
  echo "| حجم (بایت) | $size | $ref_size |"
  echo "| اختلافِ حجم | ${ratio}٪ (مرزِ سخت: $([ "$strict" = 1 ] && echo بله || echo خیر)) | |"
  echo "| آزمون‌ها | $tests | |"
} >> "$summary"

exit "$fail"
