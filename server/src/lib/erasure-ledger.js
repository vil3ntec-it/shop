'use strict';
/**
 * SEC-10 — دفترِ حذفِ مشتری **بیرون از دیتابیس** (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۱۵: «پاک‌شدنِ دوبارهٔ مشتریِ پاک‌شده پس از
 * بازگردانیِ کلِ سرور: بله، خودکار»).
 *
 * چرا بیرون: بازگردانیِ کلِ سرور (pg_dump یا پوشهٔ PGlite) از پشتیبانِ پیش از حذف، دادهٔ شخصیِ پاک‌شده را برمی‌گرداند و
 * سابقهٔ حذف (`audit_logs`) را هم با همان پشتیبان پس می‌برد. این پرونده با دیتابیس برنمی‌گردد: کنارِ پوشهٔ پشتیبان‌ها
 * (`BACKUP_PATH`، یا `ERASURE_LEDGER_PATH`) می‌ماند، هر جا پشتیبان‌ها رونوشت می‌شوند همراهشان می‌رود، و پشتیبانِ کهنه آن را
 * کهنه نمی‌کند (پشتیبان فقط دیتابیس است).
 *
 * هر خط یک حذف (JSON): `{v, id, shop, at, key, orders, customer}`
 *   id        شناسهٔ همان حذف (`era…`) — همان `target_id` ِ سابقهٔ `customer_erasure` در دیتابیس
 *   key       HMAC ِ شماره (رازِ سرور، کلیدِ جدای همین کار؛ شکلِ یکتای `validate.phoneKey`) یا '' (سفارشِ بی شماره)
 *   orders    فقط شناسهٔ سفارش‌های بی شماره (حالتِ «همین سفارش»)؛ customer: شناسهٔ حسابِ ویترین (حالتِ «خودِ مشتری»)
 * ⛔ هیچ شماره، نام، نشانی یا متنی. کلید بی رازِ سرور به شماره برنمی‌گردد.
 *
 * نوشتن **پیش از commit ِ همان حذف** است (`append`، با fsync): نوشته نشد ⇒ استثنا ⇒ تراکنشِ حذف برمی‌گردد. پس حذفی نیست که
 * این‌جا نباشد؛ خطِ حذفی که commit نشد (نادر: افتادنِ اتصال میانِ نوشتن و commit) فقط یعنی درخواستِ همان مشتری بعداً اجرا شود.
 */
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const config = require('../config');
const { serverSecret } = require('./server-secret');
const { phoneKey } = require('./validate');

const VERSION = 1;
const MAX_LINE = 8192;
const ID = /^[A-Za-z0-9_-]{1,80}$/;

/** جای پرونده: `ERASURE_LEDGER_PATH`، وگرنه کنارِ پشتیبان‌ها */
function file() {
  return process.env.ERASURE_LEDGER_PATH || path.join(config.backup.dir, 'erasure-ledger.jsonl');
}

/** HMAC ِ شماره با کلیدِ همین کار (نه کلیدِ اعلانِ مشتری) — همهٔ شکل‌های یک شماره یک کلید */
function keyOf(phone) {
  const k = crypto.createHash('sha256').update(`customer-erasure-phone\n${serverSecret(['api', 'jwt'], 'customer-erasure')}`).digest();
  return crypto.createHmac('sha256', k).update(phoneKey(phone)).digest('hex');
}

const ids = (a) => (Array.isArray(a) ? a.filter((x) => typeof x === 'string' && ID.test(x)).slice(0, 50) : []);

/** خطِ سالم ⇒ ورودی؛ خطِ بدشکل ⇒ null (نادیده؛ شمارش در `read`) */
function parse(line) {
  if (!line || line.length > MAX_LINE) return null;
  let e;
  try { e = JSON.parse(line); } catch { return null; }
  if (!e || e.v !== VERSION || !ID.test(String(e.id)) || !ID.test(String(e.shop))) return null;
  const key = typeof e.key === 'string' && /^[0-9a-f]{64}$/.test(e.key) ? e.key : '';
  const orders = ids(e.orders);
  if (!key && !orders.length) return null;
  return {
    id: e.id, shop: e.shop, at: Number(e.at) || 0, key, orders,
    customer: typeof e.customer === 'string' && ID.test(e.customer) ? e.customer : '',
  };
}

/** یک خط، پیش از commit ِ حذف؛ شکست ⇒ throw (تراکنشِ حذف برمی‌گردد) */
function append({ id, shop, at, phone = null, orders = [], customer = '' }) {
  const entry = { v: VERSION, id, shop, at, key: phone ? keyOf(phone) : '', orders: ids(orders), customer: customer || '' };
  if (!parse(JSON.stringify(entry))) throw new Error('erasure ledger: bad entry');
  const f = file();
  fs.mkdirSync(path.dirname(f), { recursive: true, mode: 0o700 });
  const fd = fs.openSync(f, 'a', 0o600);
  try {
    fs.writeSync(fd, `${JSON.stringify(entry)}\n`);
    fs.fsyncSync(fd);
  } finally {
    fs.closeSync(fd);
  }
  return entry;
}

/** همهٔ خط‌های سالم به ترتیبِ نوشتن (تکرارِ یک شناسه یک بار)؛ پروندهٔ نبوده ⇒ [] */
async function read() {
  let text;
  try { text = await fsp.readFile(file(), 'utf8'); } catch (err) {
    if (err && err.code === 'ENOENT') return { entries: [], bad: 0 };
    throw err;
  }
  const seen = new Set();
  const entries = [];
  let bad = 0;
  for (const line of text.split('\n')) {
    if (!line.trim()) continue;
    const e = parse(line);
    if (!e) { bad++; continue; }
    if (seen.has(e.id)) continue;
    seen.add(e.id);
    entries.push(e);
  }
  return { entries, bad };
}

module.exports = { VERSION, file, keyOf, parse, append, read };
