#!/usr/bin/env bash
#  F0-09/SEC-08 — امضای دوباره با چرخشِ v3 ِ کلیدِ امضا (`apksigner --lineage`)، فقط در CI و فقط وقتی secret ِ
#  `TOHID_SIGNING_LINEAGE_B64` هست (ورک‌فلو بی آن این گام را اصلاً اجرا نمی‌کند ⇒ APK بایت‌به‌بایت همان امروز).
#
#    bash tools/sign-rotated.sh <apk>      (از پوشهٔ android-native)
#
#  چه می‌کند:
#    • v2 با کلیدِ **پیشین** (همان کلیدی که Gradle با آن امضا کرد: `TOHID_KEYSTORE` یا `app/tohid-release.jks` با همان
#      پیش‌فرض‌های `app/build.gradle.kts`) ⇒ اندروید ۷ و ۸ (minSdk 24 تا 27) همان امضای همیشگی را می‌بینند و به‌روز می‌شوند؛
#    • v3 با کلیدِ **تازه** + دودمانِ «پیشین ⇒ تازه» ⇒ اندروید ۹+ (‎--rotation-min-sdk-version 28‎ اگر apksigner بشناسد)
#      به‌روزرسانی را می‌پذیرد و از آن پس کلیدِ تازه هویتِ برنامه است؛
#    • v1 خاموش (همان `enableV1Signing = false` ِ Gradle).
#  و پیش از جایگزینی می‌سنجد: v2 و v3 هر دو درست، امضای v2 همان گواهیِ APK ِ ورودی (دروازهٔ `apk-gates.sh` همان را
#  با نسخهٔ منتشرشده می‌سنجد)، امضای v3 همان کلیدِ تازه، و اثرِ انگشتِ کلیدِ تازه در `TOHID_SIGNING_SHA256_EXTRA` هست
#  (وگرنه بررسیِ امضای خودِ برنامه — `sync/Integrity.kt` — روی اندروید ۹+ نسخهٔ اصلی را «دست‌کاری‌شده» می‌دید).
#
#  ورودی‌ها (همه از secret/vars ِ ورک‌فلو؛ هیچ‌کدام چاپ نمی‌شود):
#    ROTATION_LINEAGE_B64, ROTATION_KEYSTORE_B64, ROTATION_KEYSTORE_PASSWORD, ROTATION_KEY_ALIAS, [ROTATION_KEY_PASSWORD]
#    [TOHID_KEYSTORE, TOHID_KEYSTORE_PASSWORD, TOHID_KEY_ALIAS, TOHID_KEY_PASSWORD]  (کلیدِ پیشین، همان قاعدهٔ Gradle)
#    TOHID_SIGNING_SHA256_EXTRA   (vars؛ اثرِ انگشتِ کلیدِ تازه)
#    [APKSIGNER]                  (برای آزمونِ محلی؛ وگرنه تازه‌ترین build-tools ِ ANDROID_HOME)
#  ⛔ خروجی فقط اثرِ انگشتِ گواهی‌ها (عمومی‌اند) — هرگز رمز، کلید یا محتوای secret.
set -euo pipefail

apk="${1:?کاربرد: sign-rotated.sh <apk>}"
[ -f "$apk" ] || { echo "::error::APK پیدا نشد: $apk"; exit 1; }
here="$(cd "$(dirname "$0")" && pwd)"
gradle_file="$here/../app/build.gradle.kts"

for v in ROTATION_LINEAGE_B64 ROTATION_KEYSTORE_B64 ROTATION_KEYSTORE_PASSWORD ROTATION_KEY_ALIAS; do
  [ -n "${!v:-}" ] || { echo "::error::چرخشِ کلید روشن است (TOHID_SIGNING_LINEAGE_B64) ولی $v نیست — secret ِ همنامش را بگذارید (docs/OWNER-ACTIONS-fa.md)"; exit 1; }
done

#  پیش‌فرض‌های کلیدِ پیشین از خودِ build.gradle.kts خوانده می‌شود (یک منبع؛ این‌جا تکرار نمی‌شود)
gradle_default() { sed -n "s/.*env(\"$1\") ?: \"\([^\"]*\)\".*/\1/p" "$gradle_file" | sed -n '1p'; }
old_ks="${TOHID_KEYSTORE:-$here/../app/$(gradle_default TOHID_KEYSTORE)}"
[ -f "$old_ks" ] || { echo "::error::کلیدِ پیشین پیدا نشد"; exit 1; }
OLD_PASS="${TOHID_KEYSTORE_PASSWORD:-$(gradle_default TOHID_KEYSTORE_PASSWORD)}"
OLD_KEY_PASS="${TOHID_KEY_PASSWORD:-$OLD_PASS}"
old_alias="${TOHID_KEY_ALIAS:-$(gradle_default TOHID_KEY_ALIAS)}"
NEW_PASS="$ROTATION_KEYSTORE_PASSWORD"
NEW_KEY_PASS="${ROTATION_KEY_PASSWORD:-$ROTATION_KEYSTORE_PASSWORD}"
export OLD_PASS OLD_KEY_PASS NEW_PASS NEW_KEY_PASS

if [ -n "${APKSIGNER:-}" ]; then
  apksigner="$APKSIGNER"
else
  apksigner="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -n1)/apksigner"
fi

work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/tohid-rotate.XXXXXX")"
chmod 700 "$work"
trap 'rm -rf "$work"' EXIT
printf '%s' "$ROTATION_LINEAGE_B64" | base64 -d > "$work/lineage.bin"
printf '%s' "$ROTATION_KEYSTORE_B64" | base64 -d > "$work/next.ks"

#  گواهیِ امضاکننده‌ای که اندروید در یک بازهٔ SDK می‌بیند («Signer … certificate SHA-256 digest»، همان قاعدهٔ `apk-gates.sh`):
#  ۲۴–۲۷ ⇐ v2، ۲۸+ ⇐ v3. apksigner بی بازه فقط امضاکنندهٔ تازه‌ترین SDK را چاپ می‌کند.
cert_in() { { "$apksigner" verify --print-certs -v "$@" 2>&1 || true; } | sed -nE 's/^.*Signer.*certificate SHA-256 digest: *([0-9a-fA-F]{64}).*$/\1/p' | tr 'A-F' 'a-f' | sed -n '1p'; }
before_fp="$(cert_in --min-sdk-version 24 --max-sdk-version 27 "$apk")"
[ -n "$before_fp" ] || { echo "::error::امضای APK ِ ورودی خوانده نشد"; exit 1; }

rot=()
if "$apksigner" sign --help 2>&1 | grep -q -- '--rotation-min-sdk-version'; then rot=(--rotation-min-sdk-version 28); fi

"$apksigner" sign \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true "${rot[@]}" \
  --ks "$old_ks" --ks-key-alias "$old_alias" --ks-pass env:OLD_PASS --key-pass env:OLD_KEY_PASS \
  --next-signer --ks "$work/next.ks" --ks-key-alias "$ROTATION_KEY_ALIAS" --ks-pass env:NEW_PASS --key-pass env:NEW_KEY_PASS \
  --lineage "$work/lineage.bin" \
  --out "$work/rotated.apk" "$apk" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true
[ -s "$work/rotated.apk" ] || { echo "::error::apksigner خروجی نساخت (رمز/نامِ کلید یا دودمان با این دو کلید جور نیست)"; exit 1; }

report="$("$apksigner" verify --print-certs -v "$work/rotated.apk" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true)"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$report" || { echo "::error::امضای v2 نیست"; exit 1; }
grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$report" || { echo "::error::امضای v3 نیست"; exit 1; }
if grep -q 'Verified using v1 scheme (JAR signing): true' <<<"$report"; then echo "::error::امضای v1 نباید باشد"; exit 1; fi
"$apksigner" verify --min-sdk-version 24 --max-sdk-version 27 "$work/rotated.apk" >/dev/null 2>&1 \
  || { echo "::error::APK روی اندروید ۷–۸ (v2) سنجیده نشد"; exit 1; }

v2_fp="$(cert_in --min-sdk-version 24 --max-sdk-version 27 "$work/rotated.apk")"
[ "$v2_fp" = "$before_fp" ] || { echo "::error::امضای v2 دیگر همان کلیدِ پیشین نیست — اندروید ۷–۸ به‌روزرسانی را رد می‌کرد"; exit 1; }
v3_fp="$(cert_in --min-sdk-version 28 "$work/rotated.apk")"
[ -n "$v3_fp" ] && [ "$v3_fp" != "$before_fp" ] || { echo "::error::امضای v3 با کلیدِ تازه نیست"; exit 1; }
extra="$(tr 'A-F' 'a-f' <<<"${TOHID_SIGNING_SHA256_EXTRA:-}" | tr -d ':')"
case ",${extra//;/,}," in
  *",$v3_fp,"*) ;;
  *) echo "::error::اثرِ انگشتِ کلیدِ تازه ($v3_fp) در متغیرِ TOHID_SIGNING_SHA256_EXTRA نیست — بررسیِ امضای خودِ برنامه روی اندروید ۹+ نسخهٔ اصلی را دست‌کاری‌شده می‌دید"; exit 1 ;;
esac
#  همان فهرست واقعاً در خودِ برنامه نشسته است؟ (`BuildConfig.SIGNING_SHA256` رشتهٔ ثابت است و در dex می‌ماند)
( set +o pipefail; unzip -p "$apk" 'classes*.dex' 2>/dev/null | LC_ALL=C grep -a -q -F "$v3_fp" ) \
  || { echo "::error::اثرِ انگشتِ کلیدِ تازه در خودِ APK (SIGNING_SHA256) نیست — گامِ ساختِ Gradle متغیرِ TOHID_SIGNING_SHA256_EXTRA را نگرفته است"; exit 1; }
#  دودمان در خودِ APK: هر دو گواهی
lineage="$("$apksigner" lineage --in "$work/rotated.apk" --print-certs 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true)"
grep -qi "$before_fp" <<<"$lineage" && grep -qi "$v3_fp" <<<"$lineage" || { echo "::error::دودمانِ درونِ APK هر دو گواهی را ندارد"; exit 1; }

mv "$work/rotated.apk" "$apk"
echo "چرخشِ v3: v2 ⇐ $before_fp (اندروید ۷–۸) · v3 ⇐ $v3_fp (اندروید ۹+، با دودمان)"
