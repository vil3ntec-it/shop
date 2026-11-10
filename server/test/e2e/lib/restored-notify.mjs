/**
 * «سرورِ بازیابی‌شده و صفِ اعلان» — فرایندِ جدا برای `notify.e2e.mjs` (NOTIF-08).
 *
 * همان راهِ `restored-server.mjs`: پشتیبان پیش‌تر در محیطِ جدا برگشته است (`restore-copy.mjs`: پوشهٔ PGlite یا
 * دیتابیسِ تازهٔ PostgreSQL)، سپس سرور روی همان دیتابیس. این‌جا کارگرِ اعلان دستی می‌چرخد تا ترتیب قطعی باشد:
 *   ۱) حالِ دفتر و صف همان لحظهٔ بازیابی؛
 *   ۲) دو دورِ کارگر (دومی نباید چیزی بفرستد)؛
 *   ۳) مشتری همان «ثبت» را با همان کلیدِ یکتایی و همان بلیت دوباره می‌زند (برگشتِ شبکه)؛
 *   ۴) یک دورِ دیگرِ کارگر و حالِ پایانی.
 * پوش به هیچ سرویسی نمی‌رود (`push.setDeliver`)؛ پیام‌ها در گزارش می‌آیند.
 *
 *   node restored-notify.mjs <DATABASE_URL ِ بازیابی‌شده> <ورودیِ JSON>
 *   ⇐ روی stdout یک خط: REPORT {…}
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const [url, inputFile] = process.argv.slice(2);
const input = JSON.parse(fs.readFileSync(inputFile, 'utf8'));

Object.assign(process.env, { DATABASE_URL: url, BACKUP_ENABLED: 'false' });
const { createApp } = require('../../../src/app');
const db = require('../../../src/db');
const push = require('../../../src/lib/push');
const N = require('../../../src/lib/order-notify');

const sends = [];
push.setDeliver((row, message) => { sends.push(message); });

const app = await createApp({ runMigrations: true });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

const state = async () => ({
  orders: (await db.many('SELECT id, order_no, status FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [input.shopId]))
    .map((o) => ({ id: o.id, orderNo: Number(o.order_no), status: o.status })),
  outbox: (await db.many(
    'SELECT order_id, event, status, attempts FROM order_notify_outbox WHERE shop_id=$1 ORDER BY created_at', [input.shopId]))
    .map((x) => ({ orderId: x.order_id, event: x.event, status: x.status, attempts: Number(x.attempts) })),
});

const report = { before: await state(), ticks: [] };
report.ticks.push(await N.tick());
report.ticks.push(await N.tick());
const r = await fetch(base + input.path, { method: 'POST', headers: input.headers, body: input.body });
report.retry = { status: r.status, body: await r.json().catch(() => null) };
report.ticks.push(await N.tick());
report.after = await state();
report.sends = sends;

console.log(`REPORT ${JSON.stringify(report)}`);
await new Promise((res) => server.close(res));
await db.closeDb().catch(() => {});
process.exit(0);
