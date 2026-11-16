#!/usr/bin/env bash
#  F0-09/SEC-08 — چرخشِ کلیدِ امضای برنامهٔ اندروید، **فقط روی کامپیوترِ خودِ صاحبِ مخزن** (نه CI، نه سرور).
#  راهنمای گام‌به‌گام: docs/OWNER-ACTIONS-fa.md («چرخشِ کلیدِ امضای اندروید»). نسخهٔ ویندوز: rotate-signing-key.ps1
#
#    bash android-native/tools/rotate-signing-key.sh [--old-keystore <مسیر>] [--out <پوشهٔ تازه>]
#
#  ۱) کلیدِ تازه (PKCS12، RSA 4096، ۳۰ سال) در پوشهٔ تازهٔ فقط-صاحب می‌سازد؛ رمزش را خودتان می‌دهید یا (Enter) تصادفی ساخته
#     می‌شود و فقط یک بار نشان داده می‌شود.
#  ۲) با `apksigner rotate` دودمانِ v3 ِ «کلیدِ پیشین ⇒ کلیدِ تازه» را می‌سازد (کلیدِ پیشین همان app/tohid-release.jks،
#     با رمز و نامِ پیش‌فرضِ app/build.gradle.kts اگر چیزِ دیگری ندهید).
#  ۳) نام و مقدارِ دقیقِ هر secret و متغیرِ GitHub را چاپ می‌کند (و در همان پوشه هم می‌نویسد).
#  ⛔ پوشهٔ خروجی را هرگز در مخزن نگذارید و کامیت نکنید؛ یک رونوشت آفلاین (USB) نگه دارید. کلیدِ تازه گم شود ⇒ دیگر هیچ
#     به‌روزرسانی‌ای روی گوشی‌ها نمی‌نشیند.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
gradle_file="$here/../app/build.gradle.kts"
old_ks="$here/../app/tohid-release.jks"
out="tohid-signing-rotation-$(date +%Y%m%d-%H%M%S)"
while [ $# -gt 0 ]; do
  case "$1" in
    --old-keystore) old_ks="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "گزینهٔ ناشناخته: $1" >&2; exit 2 ;;
  esac
done

die() { echo "❌ $*" >&2; exit 1; }
command -v keytool >/dev/null || die "keytool پیدا نشد — JDK 17 را نصب کنید (Android Studio هم دارد)."
apksigner="${APKSIGNER:-}"
if [ -z "$apksigner" ]; then
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || continue
    apksigner="$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -n1)/apksigner"; break
  done
fi
if [ -z "$apksigner" ] || [ ! -x "$apksigner" ]; then apksigner="$(command -v "${apksigner:-apksigner}" || command -v apksigner || true)"; fi
[ -n "$apksigner" ] || die "apksigner پیدا نشد — Android SDK build-tools (۲۸ یا تازه‌تر) را نصب کنید یا APKSIGNER=<مسیر> بدهید."
[ -f "$old_ks" ] || die "کلیدِ پیشین پیدا نشد: $old_ks"
[ ! -e "$out" ] || die "پوشهٔ خروجی از پیش هست: $out — نامِ دیگری بدهید (--out)."
case "$(cd "$(dirname "$out")" 2>/dev/null && pwd)/" in
  "$(cd "$here/../.." && pwd)/"*) die "پوشهٔ خروجی درونِ مخزن است — جای دیگری بدهید (مثلاً --out ~/tohid-rotation)." ;;
esac
umask 077
mkdir -p "$out"; chmod 700 "$out"

gradle_default() { sed -n "s/.*env(\"$1\") ?: \"\([^\"]*\)\".*/\1/p" "$gradle_file" | sed -n '1p'; }
#  کلیدِ پیشین: همان قاعدهٔ Gradle — مقدارِ محیط، وگرنه پیش‌فرضِ build.gradle.kts
OLD_PASS="${TOHID_KEYSTORE_PASSWORD:-$(gradle_default TOHID_KEYSTORE_PASSWORD)}"
OLD_KEY_PASS="${TOHID_KEY_PASSWORD:-$OLD_PASS}"
old_alias="${TOHID_KEY_ALIAS:-$(gradle_default TOHID_KEY_ALIAS)}"

new_alias="${NEW_KEY_ALIAS:-tohid2}"
NEW_PASS="${NEW_KEYSTORE_PASSWORD:-}"
shown_pass=0
if [ -z "$NEW_PASS" ]; then
  read -r -s -p "رمزِ کلیدِ تازه (دست‌کم ۱۶ نویسه؛ Enter = تصادفی): " NEW_PASS; echo
  if [ -z "$NEW_PASS" ]; then
    NEW_PASS="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"; shown_pass=1
  else
    read -r -s -p "دوباره: " again; echo
    [ "$again" = "$NEW_PASS" ] || die "دو رمز یکی نیستند."
  fi
fi
[ "${#NEW_PASS}" -ge 16 ] || die "رمزِ کلیدِ تازه کوتاه است (دست‌کم ۱۶ نویسه)."
export OLD_PASS OLD_KEY_PASS NEW_PASS

echo "۱) ساختِ کلیدِ تازه…"
keytool -genkeypair -noprompt -keystore "$out/tohid-next.p12" -storetype PKCS12 -alias "$new_alias" \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Daily Shop, OU=vil3ntec-it, O=vil3ntec-it, C=AF" \
  -storepass:env NEW_PASS -keypass:env NEW_PASS >/dev/null

echo "۲) دودمانِ v3 (کلیدِ پیشین ⇒ کلیدِ تازه)…"
"$apksigner" rotate --out "$out/tohid-lineage.bin" \
  --old-signer --ks "$old_ks" --ks-key-alias "$old_alias" --ks-pass env:OLD_PASS --key-pass env:OLD_KEY_PASS \
  --new-signer --ks "$out/tohid-next.p12" --ks-key-alias "$new_alias" --ks-pass env:NEW_PASS --key-pass env:NEW_PASS \
  2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true
[ -s "$out/tohid-lineage.bin" ] || die "دودمان ساخته نشد (رمز یا نامِ کلیدِ پیشین درست نیست؟ TOHID_KEYSTORE_PASSWORD/TOHID_KEY_ALIAS را بدهید)."

fp() { keytool -list -keystore "$1" -alias "$2" -storepass:env "$3" -v 2>/dev/null | sed -nE 's/^[[:space:]]*SHA256: *([0-9A-F:]+).*/\1/p' | tr -d ':' | tr 'A-F' 'a-f' | sed -n '1p'; }
old_fp="$(fp "$old_ks" "$old_alias" OLD_PASS)"
new_fp="$(fp "$out/tohid-next.p12" "$new_alias" NEW_PASS)"
#  دودمان با همین دو گواهی است؟
check="$("$apksigner" lineage --in "$out/tohid-lineage.bin" --print-certs 2>&1 || true)"
grep -qi "$old_fp" <<<"$check" && grep -qi "$new_fp" <<<"$check" || die "دودمانِ ساخته‌شده این دو گواهی را ندارد."

b64() { base64 < "$1" | tr -d '\n\r'; }
printf '%s' "$(b64 "$out/tohid-next.p12")" > "$out/TOHID_ROTATION_KEYSTORE_B64.txt"
printf '%s' "$(b64 "$out/tohid-lineage.bin")" > "$out/TOHID_SIGNING_LINEAGE_B64.txt"
printf '%s' "$new_alias" > "$out/TOHID_ROTATION_KEY_ALIAS.txt"
printf '%s' "$new_fp" > "$out/TOHID_SIGNING_SHA256_EXTRA.txt"
[ "$shown_pass" = 1 ] && printf '%s' "$NEW_PASS" > "$out/TOHID_ROTATION_KEYSTORE_PASSWORD.txt"
chmod 600 "$out"/*

cat <<EOF

✅ ساخته شد در: $out   (فقط برای شما؛ هرگز در مخزن، ایمیل یا چت)

۳) در GitHub ← مخزنِ shop ← Settings ← Secrets and variables ← Actions ← «Secrets» این پنج secret را بسازید:
   TOHID_ROTATION_KEYSTORE_B64       = محتوای $out/TOHID_ROTATION_KEYSTORE_B64.txt
   TOHID_SIGNING_LINEAGE_B64         = محتوای $out/TOHID_SIGNING_LINEAGE_B64.txt
   TOHID_ROTATION_KEY_ALIAS          = $new_alias
   TOHID_ROTATION_KEYSTORE_PASSWORD  = رمزِ کلیدِ تازه$( [ "$shown_pass" = 1 ] && printf ' (تصادفی؛ در %s/TOHID_ROTATION_KEYSTORE_PASSWORD.txt)' "$out" || printf ' (همان که زدید)')
   TOHID_ROTATION_KEY_PASSWORD       = همان رمز
   و در زبانهٔ «Variables» این متغیر (راز نیست):
   TOHID_SIGNING_SHA256_EXTRA        = $new_fp

   اثرِ انگشتِ کلیدِ پیشین (برای مقایسه با نسخهٔ منتشرشده): $old_fp

مقدارِ دو secret ِ بلند (base64):
--- TOHID_ROTATION_KEYSTORE_B64 ---
$(cat "$out/TOHID_ROTATION_KEYSTORE_B64.txt")
--- TOHID_SIGNING_LINEAGE_B64 ---
$(cat "$out/TOHID_SIGNING_LINEAGE_B64.txt")
---
EOF
if [ "$shown_pass" = 1 ]; then
  echo "رمزِ تصادفیِ کلیدِ تازه (فقط همین یک بار؛ جایی امن یادداشت کنید): $NEW_PASS"
fi
echo "۴) پوشهٔ $out را روی یک USB ِ جدا رونوشت کنید و از این کامپیوتر پاک کنید."
