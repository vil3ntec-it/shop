'use strict';
/**
 * رسانه‌های دیگر در انبارِ عکس (`lib/image-store.js`، مهاجرتِ ۲۷۵؛ docs/SCALING-fa.md §۳ «انبارِ عکس»):
 *
 *   جدول              tenant ِ کلید            سنجهٔ نوع از روی بایت (نوشتن و خواندنِ شیء)
 *   shop_chat_media   `shop:<دکان>`             `chat-media.sniff` و همان mime ِ ردیف (عکس، صدا، PDF)
 *   trust_evidence    `shop:<دکان>`             همان (فقط عکس، `putEvidence`)
 *   support_media     `support:<رشته>`          `chat-media.sniff` یا جعبهٔ MP4 (ویدیو)؛ نوعِ دیگر ⇒ بایت در پایگاه
 *   store_address_media `customer:<حساب>`       همان `chat-media.sniff` و mime ِ ردیف (عکسِ دروازه، یادداشتِ صوتی؛ CRM-09)
 *
 * همان قاعده‌های ۲۵۵: حالتِ `db` (پیش‌فرض) همان SQL و همان بایت در `data`؛ در `disk`/`s3` شیء پیش از نوشتنِ ردیف و
 * پاکِ شیء فقط پس از نشستنِ پاکِ ردیف (`drop`)؛ ردیف فقط با شناسه‌های همان دکان/گفت‌وگو/رشته پیدا می‌شود (مثلِ
 * امروز) **و** کلیدش باید بخشِ همان tenant را داشته باشد و بایتش sha256 ِ کلید و همان نوع را — وگرنه «نیست».
 * ⛔ `station_chat_media` (پمپ) این‌جا نیست. رشته‌های پشتیبانیِ پمپ همان جدول و همان درهای امروز را دارند
 *    (`lib/support.js`)؛ درهای `/api/pump/*` دست نخوردند.
 */
const store = require('./image-store');
const chatMedia = require('./chat-media');

const supportTenant = (threadId) => `support:${threadId}`;
const customerTenant = (customerId) => store.accountTenant('customer', customerId);

/** نوعِ رسانهٔ چت (و مدرک) از روی بایت؛ با [mime] فقط همان نوع (نوعِ ردیف عوض نمی‌شود) */
const chatCheck = (mime = '') => (buf) => {
  const s = chatMedia.sniff(buf);
  return s && (!mime || s.mime === mime) ? s.mime : '';
};

/** پشتیبانی نوعِ اعلام‌شده (عکس/ویدیو/صدا) را می‌پذیرد؛ انبار فقط آنچه بایتش شناخته است: عکس، صدا، PDF، MP4/WebM */
function supportCheck(buf) {
  const s = chatMedia.sniff(buf);
  if (s) return s.mime;
  if (!buf || Array.isArray(buf) || typeof buf === 'string' || buf.length < 12) return '';
  return buf.slice(4, 8).toString('latin1') === 'ftyp' ? 'video/mp4' : '';
}

/** پاکِ شیءهای ردیف‌های پاک‌شده، گروه به گروهِ tenant (پس از نشستنِ پاک؛ بی خطا) */
async function dropRows(rows, tenantOf) {
  const by = new Map();
  for (const r of rows || []) {
    if (!r || !r.store_key) continue;
    const t = tenantOf(r);
    by.set(t, [...(by.get(t) || []), r.store_key]);
  }
  for (const [t, keys] of by) await store.drop(t, keys);
}

const shopOf = (r) => store.shopTenant(r.shop_id);
const threadOf = (r) => supportTenant(r.thread_id);

const chat = {
  /** نوشتن: `fn({data, key, used})` ردیف را می‌نویسد (`data` = بایت یا null، `key` = '' یا کلید) */
  put: (shopId, mime, buf, fn, { keepUnknown = false } = {}) =>
    store.withStaged(store.shopTenant(shopId), buf, fn, { check: chatCheck(mime), keepUnknown }),
  /** بایتِ ردیف (`shop_id`، `mime`، `data`، `store_key`) — یا null */
  bytes: (r) => store.bytes(shopOf(r), r, chatCheck(r.mime)),
  drop: (rows) => dropRows(rows, shopOf),
  /** رسانه‌ای که دیگر هیچ پیامی به آن اشاره نمی‌کند (حذف برای همه)؛ ردیف‌های پاک‌شده برای `drop` پس از تراکنش */
  async dropUnused(c, ids) {
    const gone = [];
    for (const mid of (ids || []).filter(Boolean)) {
      gone.push(...(await c.query(
        `DELETE FROM shop_chat_media x WHERE x.id=$1 AND NOT EXISTS (
           SELECT 1 FROM shop_chat_messages y WHERE y.media_id = x.id OR y.thumb_id = x.id) RETURNING x.shop_id, x.store_key`,
        [mid]
      )).rows);
    }
    return gone;
  },
};

const trust = {
  put: (shopId, mime, buf, fn) => store.withStaged(store.shopTenant(shopId), buf, fn, { check: chatCheck(mime) }),
  bytes: (r) => store.bytes(shopOf(r), r, chatCheck(r.mime)),
};

const support = {
  put: (threadId, buf, fn) => store.withStaged(supportTenant(threadId), buf, fn, { check: supportCheck, keepUnknown: true }),
  bytes: (r) => store.bytes(threadOf(r), r, supportCheck),
  drop: (rows) => dropRows(rows, threadOf),
};

/** CRM-09: عکسِ دروازه و یادداشتِ صوتیِ نشانیِ حسابِ مشتری (`lib/store-address-media.js`) */
const address = {
  put: (customerId, mime, buf, fn) => store.withStaged(customerTenant(customerId), buf, fn, { check: chatCheck(mime) }),
  bytes: (r) => store.bytes(customerTenant(r.customer_id), r, chatCheck(r.mime)),
  drop: (rows) => dropRows(rows, (r) => customerTenant(r.customer_id)),
};

module.exports = { chat, trust, support, address, chatCheck, supportCheck, supportTenant, customerTenant };
