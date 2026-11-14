'use strict';
/**
 * نشانی‌های دیگرِ ویترینِ یک دکان (دامنهٔ خودِ دکان) — خواستهٔ صاحب محصول ۱۴۰۵/۰۸/۲۲: «چند دامنه برای هر برنامه».
 * فقط دکان (`shop.*`)؛ پمپ و همگام‌سازی دست نخورده‌اند. شرح: `docs/SERVERS-fa.md` §۳.
 *
 * امروز هر دکان یک نشانیِ ریشه دارد: `<slug>.<STOREFRONT_DOMAIN>` (`storefront.slugFromHost`) و مسیرِ `/store/<slug>`.
 * این پرونده نشانیِ سوم و بیشتر را می‌افزاید — `shop.example.af`، `www.shop.example.af` — که همان ویترین را در ریشه نشان می‌دهد.
 *
 * ⛔ دو کلید، هر دو پیش‌فرض خاموش:
 *   • سرور: `STOREFRONT_ALIAS_DOMAINS` (دامنه‌هایی که گردانندهٔ سرور واقعاً به همین سرور رسانده، با کاما). خالی ⇒ هیچ
 *     پرسشی از دفتر، هیچ رفتارِ تازه‌ای (بیت‌به‌بیت همان امروز).
 *   • دکان: پرچمِ `shop.storefront.host_alias` (`live_config`). خاموش ⇒ نشانی‌های همان دکان کار نمی‌کنند و درِ تنظیم بسته.
 * ⛔ نشانی فقط زیرِ یکی از همان دامنه‌های گرداننده (پس هیچ دکانی دامنهٔ دیگران را «ادعا» نمی‌کند)، هرگز زیرِ دامنهٔ
 *   ویترین (`<slug>.<domain>` همان قاعدهٔ امروز را دارد و زیرِ پا نمی‌رود)، هرگز نشانیِ خودِ API (`SERVER_URL`،
 *   `PUBLIC_BASE_URLS`)، هرگز IP، و فقط نامِ میزبانِ سالم (حرف، رقم، «-»، «.»؛ بی درگاه، بی نویسهٔ دیگر). یکتا در کلِ سرور.
 * ⛔ سرآیندِ Host دشمنی است: فقط برابریِ کاملِ نامِ مرتب‌شده با یک ردیفِ دفتر؛ هیچ پیوند و هیچ ریدایرکتی از Host ساخته
 *   نمی‌شود (ریدایرکتِ «نشانیِ پیشین» همان `STOREFRONT_DOMAIN` ِ پیکربندی است).
 */
const config = require('../config');
const { one, many, tx, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');

const FLAG = 'shop.storefront.host_alias';
const MAX_PER_SHOP = 5;
const CACHE_MS = 30 * 1000;
const CACHE_MAX = 2000;

/** دامنه‌های گردانندهٔ سرور — مرتب، بی تکرار؛ نادرست کنار می‌رود */
function aliasDomains() {
  return [...new Set(String(config.storefront.aliasDomains || '').split(',')
    .map((d) => d.trim().toLowerCase().replace(/^\.+|\.+$/g, ''))
    .filter((d) => validName(d)))];
}

const enabled = () => aliasDomains().length > 0;

/** نامِ میزبانِ سالم: دست‌کم دو بخش، هر بخش ۱–۶۳ نویسهٔ [a-z0-9-] بی «-» در دو سر، بخشِ آخر تنها رقم نه (IP نه) */
function validName(h) {
  if (typeof h !== 'string' || h.length < 3 || h.length > 253) return false;
  const labels = h.split('.');
  if (labels.length < 2) return false;
  if (!labels.every((l) => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(l))) return false;
  return !/^[0-9]+$/.test(labels[labels.length - 1]);
}

const under = (h, d) => h === d || h.endsWith(`.${d}`);

/** نام‌های میزبانِ خودِ API (`SERVER_URL`، `PUBLIC_BASE_URLS`) — هیچ ویترینی جای آن‌ها نمی‌نشیند */
function apiHosts() {
  const identity = require('./server-identity');
  const out = new Set();
  for (const u of [identity.publicUrl(), ...identity.publicUrls()]) {
    try { if (u) out.add(new URL(u).hostname.toLowerCase()); } catch { /* نادرست همان‌جا کنار رفته */ }
  }
  return out;
}

/**
 * نامِ مرتبِ یک نشانی برای ذخیره — یا خطای فارسیِ روشن. فقط رشته؛ فاصلهٔ دو سر و یک «.» ِ پایانی بریده، حروفِ کوچک.
 * هیچ چیزِ دیگری «درست» نمی‌شود: `https://`، درگاه، مسیر، `@`، نویسهٔ یونیکد (پیش از punycode) ⇒ رد.
 */
function normalizeHost(raw) {
  if (typeof raw !== 'string') throw badRequest('نشانی باید نوشته باشد', 'host_invalid');
  const h = raw.trim().toLowerCase().replace(/\.$/, '');
  if (!validName(h)) throw badRequest(`«${raw.slice(0, 80)}» نامِ دامنهٔ درستی نیست (فقط مثلِ shop.example.af)`, 'host_invalid');
  const domain = config.storefront.domain;
  if (domain && under(h, domain)) {
    throw badRequest(`نشانیِ زیرِ ${domain} همان نشانیِ دکان است و جدا افزوده نمی‌شود`, 'host_reserved');
  }
  if (apiHosts().has(h)) throw badRequest('این نشانیِ خودِ سرور است', 'host_reserved');
  if (!aliasDomains().some((d) => under(h, d))) {
    throw badRequest('این دامنه هنوز به این سرور وصل نشده است؛ گردانندهٔ سرور باید آن را در STOREFRONT_ALIAS_DOMAINS بیفزاید', 'host_domain_unknown');
  }
  return h;
}

/** نامِ میزبانِ یک درخواست برای جست‌وجو — هر چیزِ نادرست ⇒ '' (بی خطا؛ Host ِ دشمنی همان مسیرِ امروز را می‌رود) */
function hostOfRequest(host) {
  const h = String(host || '').toLowerCase().replace(/\.$/, '');
  return validName(h) ? h : '';
}

/* ------------------------------ جست‌وجو (حافظهٔ کوتاه) ------------------------------ */

const cache = new Map();
function forget() { cache.clear(); }

/**
 * slug ِ ویترینی که این Host نشانیِ دیگرِ آن است — یا ''. سرورِ بی دامنهٔ گرداننده ⇒ همیشه '' بی هیچ پرسشی.
 * پرچمِ دکان خاموش، ویترینِ نبوده یا نامِ ناشناس ⇒ ''.
 */
async function slugOfHost(host) {
  if (!enabled()) return '';
  const h = hostOfRequest(host);
  if (!h || !aliasDomains().some((d) => under(h, d))) return '';
  const hit = cache.get(h);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.slug;
  const row = await one(
    `SELECT s.slug, h.shop_id FROM storefront_hosts h JOIN storefronts s ON s.shop_id = h.shop_id WHERE h.host = $1`, [h]);
  let slug = '';
  if (row && (await require('./orders').flagOf(row.shop_id, FLAG))) slug = String(row.slug || '');
  if (cache.size >= CACHE_MAX) cache.delete(cache.keys().next().value);
  cache.set(h, { slug, at: Date.now() });
  return slug;
}

/* ------------------------------ تنظیمِ دکان ------------------------------ */

async function list(shopId) {
  const rows = await many('SELECT host, created_at FROM storefront_hosts WHERE shop_id=$1 ORDER BY created_at, host', [shopId]);
  return rows.map((r) => ({ host: r.host, createdAt: Number(r.created_at) }));
}

/**
 * جایگزینیِ کاملِ فهرست. ⛔ یکتا در کلِ سرور: نشانیِ دکانِ دیگر ⇒ ۴۰۹ بی گفتنِ این‌که کدام دکان.
 * @returns {Promise<{hosts: Array, added: string[], removed: string[]}>}
 */
async function replace(shopId, rawHosts, { userId = null } = {}) {
  if (!Array.isArray(rawHosts)) throw badRequest('فهرستِ نشانی‌ها (hosts) لازم است', 'hosts_required');
  if (rawHosts.length > MAX_PER_SHOP) throw badRequest(`بیش از ${MAX_PER_SHOP} نشانی نمی‌شود`, 'hosts_too_many');
  const wanted = [...new Set(rawHosts.map(normalizeHost))];
  const out = await tx(async (c) => {
    const before = (await c.query('SELECT host FROM storefront_hosts WHERE shop_id=$1', [shopId])).rows.map((r) => r.host);
    if (wanted.length) {
      const taken = (await c.query('SELECT host FROM storefront_hosts WHERE host = ANY($1::text[]) AND shop_id <> $2', [wanted, shopId])).rows;
      if (taken.length) throw conflict(`نشانیِ ${taken[0].host} از پیش مالِ ویترینِ دیگری است`, 'host_taken');
    }
    const removed = before.filter((h) => !wanted.includes(h));
    const added = wanted.filter((h) => !before.includes(h));
    if (removed.length) await c.query('DELETE FROM storefront_hosts WHERE shop_id=$1 AND host = ANY($2::text[])', [shopId, removed]);
    for (const h of added) {
      //  هم‌زمانیِ دو دکان روی یک نام: کلیدِ اصلیِ `host` دومی را می‌شکند ⇒ همان ۴۰۹
      const r = await c.query(
        `INSERT INTO storefront_hosts (host, shop_id, created_at, created_by) VALUES ($1,$2,$3,$4) ON CONFLICT (host) DO NOTHING`,
        [h, shopId, now(), userId]);
      if (!r.rowCount) throw conflict(`نشانیِ ${h} از پیش مالِ ویترینِ دیگری است`, 'host_taken');
    }
    return { added, removed };
  });
  forget();
  return { hosts: await list(shopId), ...out };
}

module.exports = { FLAG, MAX_PER_SHOP, aliasDomains, enabled, validName, normalizeHost, hostOfRequest, slugOfHost, list, replace, forget };
