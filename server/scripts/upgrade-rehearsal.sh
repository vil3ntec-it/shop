#!/usr/bin/env bash
# تمرینِ ارتقا از نسخهٔ پیش به نسخهٔ تازه روی دادهٔ واقعی‌نما — OPS-03@29 (شرح: upgrade-rehearsal.js).
#
#   DATABASE_URL=postgres://…/shop_staging bash server/scripts/upgrade-rehearsal.sh <base-checkout> <head-checkout> [out-dir]
#
# هر دو checkout باید `server/node_modules` داشته باشند. پایگاه پاک و از نو ساخته می‌شود؛ پس نامش باید
# test، bench یا staging داشته باشد. همین اسکریپت را ورک‌فلوی `staging-rehearsal.yml` اجرا می‌کند.
set -euo pipefail

BASE=$(cd "$1" && pwd)
HEAD=$(cd "$2" && pwd)
OUT=$(mkdir -p "${3:-rehearsal-out}" && cd "${3:-rehearsal-out}" && pwd)
TOOL="$HEAD/server/scripts/upgrade-rehearsal.js"
BASE_PORT=${BASE_PORT:-4100}
HEAD_PORT=${HEAD_PORT:-4200}

case "$DATABASE_URL" in
  *test*|*bench*|*staging*) ;;
  *) echo "⛔ نامِ پایگاه باید test، bench یا staging داشته باشد"; exit 1 ;;
esac

export NODE_ENV=test BACKUP_ENABLED=false TELEGRAM_AUTOSTART=false
export API_SECRET=${API_SECRET:-rehearsal-secret-rehearsal-secret}
export OTP_SECRET=${OTP_SECRET:-rehearsal-otp-secret-rehearsal-ot}
export RATE_GENERAL_MAX=100000 RATE_AUTH_MAX=10000 RATE_OTP_MAX=10000 RATE_VERIFY_MAX=10000 RATE_LOGIN_EDGE_MAX=100000
export RATE_SYNC_MAX=1000000 RATE_SYNC_IP_MAX=1000000 LOGIN_LOCKOUT_TRIES=10000 LOGIN_LOCKOUT_GLOBAL_TRIES=10000
export OTP_RESEND_SECONDS=0 OTP_UNIFORM_MS=0 RATE_PROBE_IP_MAX=10000

PID=""
stop() { if [ -n "$PID" ]; then kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; PID=""; fi; }
trap stop EXIT
up() { # <dir> <port> <log>
  (cd "$1/server" && PORT="$2" node src/index.js >"$3" 2>&1) &
  PID=$!
  for _ in $(seq 1 120); do
    if curl -fsS "http://127.0.0.1:$2/api/ready" >/dev/null 2>&1; then return 0; fi
    if ! kill -0 "$PID" 2>/dev/null; then echo "✖ سرور بالا نیامد:"; tail -40 "$3"; return 1; fi
    sleep 1
  done
  echo "✖ سرور در ۱۲۰ ثانیه آماده نشد"; tail -40 "$3"; return 1
}

echo "── ۱) پایگاهِ تازه و مهاجرت‌های نسخهٔ پیش"
node -e "
  const db = require('$BASE/server/src/db');
  db.query('DROP SCHEMA IF EXISTS public CASCADE').then(() => db.query('CREATE SCHEMA public')).then(() => db.closeDb())
    .catch((e) => { console.error(e.message); process.exit(1); });"
(cd "$BASE/server" && node scripts/migrate.js) | tail -1

echo "── ۲) سرورِ نسخهٔ پیش و seed از راهِ HTTP"
up "$BASE" "$BASE_PORT" "$OUT/base-server.log"
node "$TOOL" seed --url "http://127.0.0.1:$BASE_PORT" --out "$OUT/state.json" \
  --shops "${SHOPS:-3}" --products "${PRODUCTS:-60}" --sales "${SALES:-300}"
stop

echo "── ۳) اثرِ انگشتِ پیش از ارتقا"
node "$TOOL" fingerprint --out "$OUT/before.json"

echo "── ۴) مهاجرت‌های نسخهٔ تازه روی همان داده"
T0=$(date +%s%3N)
(cd "$HEAD/server" && node scripts/migrate.js) | tee "$OUT/migrate.log" | tail -3
T1=$(date +%s%3N)
echo "$((T1 - T0))" > "$OUT/migrate-ms.txt"
echo "زمانِ مهاجرت: $((T1 - T0))ms"

echo "── ۵) اثرِ انگشتِ پس از ارتقا (پیش از روشن شدنِ سرورِ تازه)"
node "$TOOL" fingerprint --out "$OUT/after.json" --columns-from "$OUT/before.json"

echo "── ۶) ناوردایی‌های داده با نسخهٔ تازه (verify-data)"
(cd "$HEAD/server" && node scripts/verify-data.js) | tee "$OUT/verify-data.log" | tail -3

echo "── ۷) سرورِ نسخهٔ تازه، مقایسه و دودآزمایی"
up "$HEAD" "$HEAD_PORT" "$OUT/head-server.log"
node "$TOOL" check --before "$OUT/before.json" --after "$OUT/after.json" --state "$OUT/state.json" \
  --url "http://127.0.0.1:$HEAD_PORT" | tee "$OUT/check.log"
