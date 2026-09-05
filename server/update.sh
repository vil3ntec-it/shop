#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  به‌روزرسانی سرور — یک دستور.
#
#      cd shop/server
#      ./update.sh
#
#  ── چرا این فایل هست ─────────────────────────────────────────────────
#  تا امروز هیچ‌جا ننوشته بود «سرور را چطور به‌روز کنم». نتیجه‌اش این شد
#  که کد ماه‌ها روی گیت‌هاب جلو رفت و سرورِ واقعی همان‌جا ماند — و
#  برنامه‌ها می‌گفتند «سرورِ شما قدیمی است» بی‌آنکه کسی بداند باید چه
#  کند.
#
#  ── چه کار می‌کند ────────────────────────────────────────────────────
#    ۱) از دیتابیس پشتیبان می‌گیرد — پیش از هر تغییری
#    ۲) کد تازه را می‌کشد
#    ۳) ایمیج را دوباره می‌سازد و بالا می‌آورد
#    ۴) صبر می‌کند تا سرور جواب بدهد و نسخه‌اش را نشان می‌دهد
#
#  Migrationها خودشان هنگام بالا آمدن اجرا می‌شوند؛ کارِ دستی ندارد.
#  هر Migration یک بار و فقط یک بار اجرا می‌شود، پس این اسکریپت را
#  می‌شود هر بار بی‌خطر دوباره زد.
#
#  ── اگر چیزی خراب شد ─────────────────────────────────────────────────
#  نامِ فایلِ پشتیبان در پایان چاپ می‌شود. برگرداندنش:
#      docker compose exec -T db psql -U shop shop < <همان فایل>
# ---------------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")"

BOLD=$'\033[1m'; RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; OFF=$'\033[0m'
if [ ! -t 1 ]; then BOLD=""; RED=""; GRN=""; YEL=""; OFF=""; fi

step() { printf '\n%s▸ %s%s\n' "$BOLD" "$1" "$OFF"; }
ok()   { printf '  %s✓%s %s\n' "$GRN" "$OFF" "$1"; }
bad()  { printf '  %s✗%s %s\n' "$RED" "$OFF" "$1"; }
note() { printf '  %s!%s %s\n' "$YEL" "$OFF" "$1"; }

#  docker compose (تازه) یا docker-compose (قدیمی) — هر کدام که هست
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  bad "docker پیدا نشد. اول Docker را نصب کنید."
  exit 1
fi

[ -f .env ] || { bad ".env نیست. از روی .env.example بسازیدش."; exit 1; }

#  نامِ کاربر و دیتابیس از .env، وگرنه پیش‌فرض
PGUSER=$(sed -n 's/^[[:space:]]*POSTGRES_USER=//p' .env | head -1 | tr -d '"'"'"' ' )
PGDB=$(sed -n 's/^[[:space:]]*POSTGRES_DB=//p' .env | head -1 | tr -d '"'"'"' ' )
PGUSER=${PGUSER:-shop}
PGDB=${PGDB:-shop}

# ---------------------------------------------------------------------------
step "۱ از ۴ — پشتیبان از دیتابیس"
# ---------------------------------------------------------------------------
#  پیش از هر تغییری، نه بعدش. اگر دیتابیس بالا نباشد هم جلو می‌رویم:
#  سرورِ خاموش چیزی برای از دست دادن ندارد.
mkdir -p data/backups
DUMP="data/backups/pre-update-$(date +%Y%m%d-%H%M%S).sql"
if $DC exec -T db pg_dump -U "$PGUSER" "$PGDB" > "$DUMP" 2>/dev/null; then
  ok "گرفته شد: $DUMP ($(du -h "$DUMP" | cut -f1))"
else
  rm -f "$DUMP"
  DUMP=""
  note "دیتابیس بالا نبود — پشتیبانی گرفته نشد. اگر نصبِ تازه است، طبیعی است."
fi

# ---------------------------------------------------------------------------
step "۲ از ۴ — گرفتن کد تازه"
# ---------------------------------------------------------------------------
if [ -d ../.git ]; then
  BEFORE=$(git rev-parse --short HEAD)
  git pull --ff-only
  AFTER=$(git rev-parse --short HEAD)
  if [ "$BEFORE" = "$AFTER" ]; then
    ok "کد از قبل تازه بود ($AFTER)"
  else
    ok "$BEFORE → $AFTER"
  fi
else
  note "این پوشه مخزنِ git نیست؛ از گرفتنِ کد گذشتیم."
fi

# ---------------------------------------------------------------------------
step "۳ از ۴ — ساختن و بالا آوردن"
# ---------------------------------------------------------------------------
#  `--build` لازم است: بدون آن، ایمیجِ قدیمی دوباره بالا می‌آید و هیچ
#  چیزی عوض نمی‌شود — همان تله‌ای که «به‌روز کردم ولی فرقی نکرد» را
#  می‌سازد.
PROFILE=""
grep -q '^[[:space:]]*DOMAIN=' .env 2>/dev/null && PROFILE="--profile tls"
# shellcheck disable=SC2086
$DC $PROFILE up -d --build
ok "بالا آمد"

# ---------------------------------------------------------------------------
step "۴ از ۴ — سنجیدن"
# ---------------------------------------------------------------------------
PORT=$(sed -n 's/^[[:space:]]*PORT=//p' .env | head -1 | tr -d '"'"'"' ' )
PORT=${PORT:-3000}

VERSION=""
for _ in $(seq 1 30); do
  BODY=$(curl -fsS "http://127.0.0.1:${PORT}/health" 2>/dev/null || true)
  if [ -n "$BODY" ]; then
    VERSION=$(printf '%s' "$BODY" | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    break
  fi
  sleep 2
done

if [ -z "$VERSION" ]; then
  bad "سرور جواب نداد. لاگ را ببینید:   $DC logs --tail 50 api"
  [ -n "$DUMP" ] && note "پشتیبان اینجاست: $DUMP"
  exit 1
fi

EXPECTED=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' package.json | head -1)
ok "سرور بالاست — نسخه $VERSION"

if [ -n "$EXPECTED" ] && [ "$VERSION" != "$EXPECTED" ]; then
  bad "انتظار نسخه $EXPECTED بود. ایمیجِ قدیمی هنوز سرِ جایش است."
  note "این را بزنید:   $DC build --no-cache api && $DC up -d api"
  exit 1
fi

printf '\n%s✓ تمام شد.%s\n' "$GRN" "$OFF"
printf '  حالا در برنامه‌ی مدیریت: بیشتر ← ایمیل و پوش ← تنظیمات SMTP\n'
[ -n "$DUMP" ] && printf '  پشتیبانِ پیش از به‌روزرسانی: %s\n' "$DUMP"
