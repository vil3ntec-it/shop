#!/usr/bin/env bash
#
#  پوشِ گوشی (Firebase، تصمیمِ O-01) — `app/google-services.json` از secret ِ GitHub به نامِ `GOOGLE_SERVICES_JSON`.
#  راهنمای صاحب‌کار: docs/FIREBASE-SETUP-fa.md
#
#  ⛔ ساخت هرگز به‌خاطرِ این نمی‌شکند: secret نیست، JSON نیست، یا بستهٔ لازم را ندارد ⇒ فقط یک خطِ هشدار و همان
#     برنامهٔ بی‌پوش (افزونهٔ google-services فقط وقتی فایل هست اعمال می‌شود: `app/build.gradle.kts`).
#  ⛔ محتوای secret هرگز چاپ نمی‌شود.
#
#  usage: GOOGLE_SERVICES_JSON=… google-services.sh <بستهٔ لازم>...
#         مثلاً `google-services.sh ir.vil3ntec.tohid` (انتشار) یا `google-services.sh ir.vil3ntec.tohid.preview`
#
set -euo pipefail
dest="$(cd "$(dirname "$0")/.." && pwd)/app/google-services.json"
rm -f "$dest"

if [ -z "${GOOGLE_SERVICES_JSON:-}" ]; then
  echo "::notice::secret ِ GOOGLE_SERVICES_JSON تنظیم نشده — این ساخت پوشِ گوشی (Firebase) ندارد؛ بقیهٔ برنامه همان است"
  exit 0
fi

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
printf '%s' "$GOOGLE_SERVICES_JSON" > "$tmp"
#  هم خودِ JSON، هم base64 ِ آن پذیرفته است
if ! python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$tmp" 2>/dev/null; then
  if ! { printf '%s' "$GOOGLE_SERVICES_JSON" | base64 -d > "$tmp" 2>/dev/null \
      && python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$tmp" 2>/dev/null; }; then
    echo "::warning::GOOGLE_SERVICES_JSON فایلِ JSON ِ درستی نیست — این ساخت بی پوشِ گوشی است (docs/FIREBASE-SETUP-fa.md گامِ ۳)"
    exit 0
  fi
fi

missing="$(python3 - "$tmp" "$@" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1]))
have = {((c.get('client_info') or {}).get('android_client_info') or {}).get('package_name') for c in doc.get('client') or []}
print(' '.join(p for p in sys.argv[2:] if p not in have))
PY
)"
if [ -n "$missing" ]; then
  echo "::warning::google-services.json برنامهٔ «$missing» را ندارد — در Firebase همین بسته را هم بیفزایید و فایل را دوباره بگیرید (docs/FIREBASE-SETUP-fa.md گامِ ۲)؛ این ساخت بی پوشِ گوشی است"
  exit 0
fi

cp "$tmp" "$dest"
echo "پوشِ گوشی: google-services.json گذاشته شد (بسته‌ها: $*)"
