'use strict';
/**
 * انبارِ عکس — تنها راهِ نوشتن و خواندنِ بایت‌های عکس (docs/SCALING-fa.md §۳ «انبارِ عکس»، مهاجرتِ ۲۵۵).
 *
 * جدول‌ها (`TABLES` ِ `lib/image-store-move.js`): عکسِ کالا و صندوقِ عکس، لوگو/سرِ ویترین، بانکِ تصاویر و لوگوی
 * فاکتور (`image_blobs`)، و عکس‌های مدرکِ سفارش (گزارشِ مشکل، بسته‌بندی، تحویل). هر ردیف یا بایت را در `data`
 * دارد (حالتِ `db`، همان پیشین) یا نشانیِ شیء را در `store_key` (حالتِ `disk`/`s3`) — یا هر دو، پس از رونوشتِ
 * ابزار و پیش از `purge`.
 *
 *   حالت ── `config.imageStore.mode` (env ِ `IMAGE_STORE`): فقط جای نوشتنِ تازه. پیش‌فرض `db` ⇒ هیچ شیئی، هیچ
 *           درخواستی، همان SQL و همان بایت.
 *   خواندن ─ `bytes(tenant, row)`: در حالتِ شیء اول شیء (رونوشت ⇒ پایگاه نخوانده می‌ماند)، شکست ⇒ همان `data` اگر
 *           هست؛ در حالتِ `db` اول `data`، وگرنه شیء با `IMAGE_STORE_READ` (پنجرهٔ برگشت).
 *   کلید ── `i1/<sha256(tenant) ۱۶>/<۱۲۸ بیتِ تصادفی>.<sha256(بایت) ۱۶>`: حدس‌ناپذیر، و هرگز به کاربر داده نمی‌شود
 *           (عکس همیشه از همان درهای امروز و پس از همان سنجشِ مالکیتِ ردیف سرو می‌شود).
 *
 * ⛔ مالکیت دو بار: (۱) ردیف فقط با شناسهٔ دکانِ نشست/ویترین پیدا می‌شود (مثلِ امروز)؛ (۲) کلیدِ ردیف باید بخشِ
 *    همان tenant را داشته باشد — ردیفِ دستکاری‌شده‌ای که به شیءِ دکانِ دیگر اشاره کند هیچ بایتی نمی‌گیرد.
 * ⛔ سنجشِ سخت در خواندن هم: بایتِ شیء باید sha256 ِ درونِ کلید و نوعِ JPEG/PNG/WebP داشته باشد، وگرنه «نیست».
 * ⛔ پاک‌کردنِ شیء فقط پس از نوشتنِ ردیف (`drop`)؛ شیءِ یتیم (تراکنشِ برگشته، پاکیِ آبشاری) را `gc` برمی‌دارد.
 */
const crypto = require('crypto');
const config = require('../config');
const adapters = require('./image-store-adapters');

const { KEY_RE } = adapters;
const sha256 = (b) => crypto.createHash('sha256').update(b).digest('hex');

/** نوع فقط از محتوا: JPEG، PNG یا WebP — وگرنه ''. (همان `product-photos.sniff`) */
function sniff(buf) {
  if (!buf || buf.length < 3) return '';
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.length >= 8 && buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.length >= 12 && buf.slice(0, 4).toString('latin1') === 'RIFF' && buf.slice(8, 12).toString('latin1') === 'WEBP') return 'image/webp';
  return '';
}

const mode = () => config.imageStore.mode;
const objectMode = () => mode() === 'disk' || mode() === 's3';

let cached = null;
/** انبارِ شیءِ پیکربندی‌شده (برای نوشتن در حالتِ شیء، یا خواندن با `IMAGE_STORE_READ`)، یا null. */
function backend() {
  const c = config.imageStore;
  const kind = objectMode() ? c.mode : c.read;
  if (kind !== 'disk' && kind !== 's3') return null;
  const sig = JSON.stringify([kind, c.dir, c.s3, c.timeoutMs]);
  if (!cached || cached.sig !== sig) cached = { sig, adapter: adapters.create(kind, c) };
  return cached.adapter;
}
/** فقط آزمون: انبارِ ساختگی (همان قرارداد) به جای دیسک/S3 ِ پیکربندی. */
function useBackend(adapter) { cached = adapter ? { sig: '__test__', adapter, fixed: true } : null; }
const current = () => (cached && cached.fixed ? cached.adapter : backend());

/** tenant ِ ردیف‌های حساب (`app`، `account_id`)؛ دکان همان `shop:<شناسه>` ِ جدول‌های `shop_id` است. */
const accountTenant = (app, accountId) => `${app}:${accountId}`;
const shopTenant = (shopId) => accountTenant('shop', shopId);
/** بانکِ تصاویر و لوگوی فاکتور: `image_blobs` با هش نشانی می‌شود و دسترسی همیشه از ردیف‌های دیگر است. */
const BLOBS = 'blobs';
const segOf = (tenant) => sha256(`image-store:${tenant}`).slice(0, 16);
const owns = (tenant, key) => KEY_RE.test(String(key || '')) && key.split('/')[1] === segOf(tenant);

/** کلیدِ تازه؛ `seed` (فقط ابزارِ جابه‌جایی) ⇒ همان کلید در هر اجرای دوباره، پس ادامه شیءِ یتیم نمی‌سازد. */
function keyFor(tenant, buf, seed = null) {
  const rnd = seed ? crypto.createHmac('sha256', seed.secret).update(seed.id).digest('hex').slice(0, 32)
    : crypto.randomBytes(16).toString('hex');
  return `i1/${segOf(tenant)}/${rnd}.${sha256(buf).slice(0, 16)}`;
}

const counters = { reads: 0, readMisses: 0, writes: 0, drops: 0, errors: 0 };
const warned = new Map();
function warn(code, err) {
  counters.errors++;
  const last = warned.get(code) || 0;
  if (Date.now() - last < 60_000) return;
  warned.set(code, Date.now());
  console.warn(`[image-store] ${code}${err ? `: ${err.message}` : ''}`);
}

/** شیءِ یک کلید پس از سنجشِ مالکیت، درستی و نوع — یا null. */
async function fetchObject(tenant, key) {
  if (!owns(tenant, key)) { warn('foreign_key'); return null; }
  const a = current();
  if (!a) { warn('no_backend'); return null; }
  let buf;
  try { buf = await a.get(key); } catch (e) { warn('read_failed', e); return null; }
  counters.reads++;
  if (!buf) { counters.readMisses++; return null; }
  if (sha256(buf).slice(0, 16) !== key.slice(-16) || !sniff(buf)) { warn('integrity'); return null; }
  return buf;
}

/** بایت‌های یک ردیف (`{data, store_key}`) — یا null. */
async function bytes(tenant, row) {
  if (!row) return null;
  const inDb = row.data === null || row.data === undefined ? null : Buffer.from(row.data);
  const key = row.store_key || '';
  if (key && (objectMode() || !inDb)) {
    const b = await fetchObject(tenant, key);
    if (b) return b;
  }
  return inDb;
}

/**
 * نوشتن: بایت را آماده می‌کند و `fn({data, key})` را صدا می‌زند تا ردیف را بنویسد (`data` = بایت یا null،
 * `key` = '' یا نشانیِ شیء) و `s.used = true` بگذارد. حالتِ `db` ⇒ فقط `{data: buf, key: ''}`. حالتِ شیء ⇒ اول شیء
 * (بیرون از تراکنشِ پایگاه)؛ اگر `fn` خطا داد یا ردیف را ننوشت (تکرار) شیء همان دم پاک می‌شود.
 */
async function withStaged(tenant, buf, fn) {
  if (!objectMode()) return fn({ data: buf, key: '', used: false });
  const mime = sniff(buf);
  if (!mime) throw new Error('image-store: only JPEG/PNG/WebP');
  const s = { data: null, key: keyFor(tenant, buf), used: false };
  await current().put(s.key, buf, mime);
  counters.writes++;
  let out;
  try { out = await fn(s); } catch (e) { await drop(tenant, [s.key]); throw e; }
  if (!s.used) await drop(tenant, [s.key]);
  return out;
}

/** پاک‌کردنِ شیءهای ردیف‌هایی که دیگر نیستند — پس از نوشتنِ پایگاه، بی خطا (یتیم را `gc` برمی‌دارد). */
async function drop(tenant, keys) {
  const list = (keys || []).filter(Boolean);
  if (!list.length) return;
  const a = current();
  for (const k of list) {
    if (!owns(tenant, k)) { warn('foreign_key'); continue; }
    if (!a) { warn('no_backend'); return; }
    try { await a.del(k); counters.drops++; } catch (e) { warn('delete_failed', e); }
  }
}

/** «بایت دارد؟» در SQL — همان `data IS NOT NULL` ِ پیشین، به‌علاوهٔ شیء. `a` = پیشوندِ ستون (مثلاً `ph.`). */
const has = (a = '') => `(${a}data IS NOT NULL OR ${a}store_key <> '')`;

/** نمای سلامت (بی راز): حالت و شمارنده‌ها. */
const status = () => ({ mode: mode(), read: config.imageStore.read || null, ...counters });

module.exports = {
  KEY_RE, BLOBS, sniff, mode, objectMode, backend: current, useBackend, shopTenant, accountTenant, segOf, owns, keyFor,
  fetchObject, bytes, withStaged, drop, has, status, sha256,
};
