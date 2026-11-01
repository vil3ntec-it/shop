'use strict';
/**
 * DLV-05 (§۳.۱۱، §۳.۲) — نقشهٔ آفلاینِ پیک و «فرستادنِ مقصد به مسیریاب».
 *
 *   • نقشه: فایلِ PMTilesِ برداریِ شهر (عصارهٔ OpenStreetMap، Geofabrik) که گردانندهٔ
 *     سرور در `config.courierMap.dir` می‌گذارد. سرور **فقط** فایل‌هایی را می‌شناسد که
 *     نامشان `<شناسه>.pmtiles` است و سرآیندِ PMTilesِ نسخهٔ ۳ دارند؛ هیچ مسیری از
 *     درخواست به دیسک نمی‌رسد (شناسه فقط از فهرستِ همین پوشه انتخاب می‌شود).
 *     برنامهٔ پیک یک بار کلِ فایل را می‌گیرد (با `Range` از جای بریده ادامه می‌دهد) و
 *     با `sha256` می‌سنجد؛ نقشه پس از آن بی اینترنت کار می‌کند. هیچ کاشی از
 *     `tile.openstreetmap.org` گرفته نمی‌شود (سیاستِ OSMF، §۳.۲).
 *   • مسیریاب: پینِ مقصد به سه پیوندِ استاندارد — `geo:` (هر مسیریابِ گوشی)،
 *     Google Maps و OsmAnd. فقط مختصات در پیوند می‌رود، نه نشانه، نام یا شماره.
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.delivery_map` درهای نقشه ۴۰۴ و کارتِ مأموریت
 * بیت‌به‌بیت همان پیشین است (بی کلیدِ `navigation`).
 */
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const config = require('../config');

const FLAG = 'shop.delivery_map';
const ID_RE = /^[a-z0-9][a-z0-9-]{0,39}$/;
const EXT = '.pmtiles';
const MAX_PACKS = 20;
const MAGIC = Buffer.from('PMTiles', 'ascii');
const VERSION = 3;
const ATTRIBUTION = '© OpenStreetMap contributors';

const dir = () => path.resolve(config.courierMap.dir);

/** سرآیندِ PMTiles v3: «PMTiles» و بایتِ نسخه = ۳. هر فایلِ دیگری نقشه نیست. */
async function isPmtiles(file) {
  let fh;
  try {
    fh = await fsp.open(file, 'r');
    const buf = Buffer.alloc(MAGIC.length + 1);
    const { bytesRead } = await fh.read(buf, 0, buf.length, 0);
    return bytesRead === buf.length && buf.subarray(0, MAGIC.length).equals(MAGIC) && buf[MAGIC.length] === VERSION;
  } catch {
    return false;
  } finally {
    await fh?.close().catch(() => {});
  }
}

//  جمعِ کنترل با اندازه و زمانِ تغییر در حافظه می‌ماند؛ فایلِ عوض‌شده دوباره سنجیده می‌شود
const sums = new Map();
async function sha256Of(file, st) {
  const key = `${st.size}:${st.mtimeMs}`;
  const hit = sums.get(file);
  if (hit && hit.key === key) return hit.sha256;
  const hash = crypto.createHash('sha256');
  await new Promise((resolve, reject) => {
    fs.createReadStream(file).on('data', (b) => hash.update(b)).on('error', reject).on('end', resolve);
  });
  const sha256 = hash.digest('hex');
  sums.set(file, { key, sha256 });
  return sha256;
}

/** بسته‌های نقشهٔ همین سرور: `[{ id, bytes, sha256, updatedAt }]`، به ترتیبِ شناسه. */
async function packs() {
  let names;
  try {
    names = await fsp.readdir(dir());
  } catch {
    return [];   // پوشه نیست ⇒ هنوز نقشه‌ای گذاشته نشده
  }
  const out = [];
  for (const name of names.sort()) {
    if (out.length >= MAX_PACKS) break;
    if (!name.endsWith(EXT)) continue;
    const id = name.slice(0, -EXT.length);
    if (!ID_RE.test(id)) continue;
    const file = path.join(dir(), name);
    const st = await fsp.lstat(file).catch(() => null);
    //  فقط فایلِ معمولی (نه پیوندِ نمادین به جای دیگری از دیسک) و با سرآیندِ درست
    if (!st || !st.isFile() || !(await isPmtiles(file))) continue;
    out.push({ id, bytes: st.size, sha256: await sha256Of(file, st), updatedAt: Math.floor(st.mtimeMs) });
  }
  return out;
}

/** مسیرِ فایلِ بسته — فقط اگر همین شناسه در فهرستِ بالا باشد؛ وگرنه `null`. */
async function packFile(rawId) {
  const id = typeof rawId === 'string' ? rawId : '';
  if (!ID_RE.test(id)) return null;
  const file = path.join(dir(), id + EXT);
  const st = await fsp.lstat(file).catch(() => null);
  if (!st || !st.isFile() || !(await isPmtiles(file))) return null;
  return file;
}

const fixed = (x) => String(Math.round(Number(x) * 1e6) / 1e6);

/**
 * پیوندهای مسیریاب برای پینِ مقصد؛ پینِ نامعتبر یا نبودنش ⇒ `null` (پیک با «اول نشانه» می‌رود).
 * ⛔ فقط مختصات — هیچ نشانه، نام یا شماره‌ای به برنامهٔ بیرونی نمی‌رود.
 */
function navigation(lat, lng) {
  const a = Number(lat);
  const o = Number(lng);
  if (lat === null || lat === undefined || lng === null || lng === undefined) return null;
  if (!Number.isFinite(a) || !Number.isFinite(o) || Math.abs(a) > 90 || Math.abs(o) > 180) return null;
  const p = `${fixed(a)},${fixed(o)}`;
  return {
    lat: Number(fixed(a)),
    lng: Number(fixed(o)),
    geo: `geo:${p}?q=${p}`,
    google: `https://www.google.com/maps/dir/?api=1&destination=${p}`,
    osmand: `https://osmand.net/map?pin=${p}#17/${fixed(a)}/${fixed(o)}`,
  };
}

/**
 * کارت‌های مأموریتِ پیک (`delivery.missions`) با دو کلیدِ پرچم‌دار:
 *   • `navigation` (DLV-05، `shop.delivery_map`): پیوندهای مسیریاب برای پینِ مقصد، فقط تا
 *     مأموریت زنده است (نشانیِ مأموریتِ پایان‌یافته از کارت برداشته شده، §۳۸.۴).
 *   • `tracking` (DLV-16، هر دو پرچمِ موقعیت — همان شرطِ `delivery.putLocation`): سرور به
 *     سرویسِ پیش‌زمینهٔ برنامه می‌گوید موقعیت **فقط** «در راه» خواسته است.
 * هر دو خاموش ⇒ همان شیءِ پیشین، بیت‌به‌بیت.
 */
async function decorate(shopId, out) {
  const orders = require('./orders');
  const delivery = require('./delivery');
  const nav = await orders.flagOf(shopId, FLAG);
  const tracking = (await orders.flagOf(shopId, delivery.VIEW_FLAG)) && (await orders.flagOf(shopId, delivery.LOCATION_FLAG));
  if (!nav && !tracking) return out;
  return {
    ...out,
    items: out.items.map((m) => ({
      ...m,
      ...(nav ? { navigation: m.live && m.address ? navigation(m.address.lat, m.address.lng) : null } : {}),
      ...(tracking ? {
        tracking: { location: delivery.ON_THE_WAY.includes(m.status), minIntervalMs: delivery.LOCATION_MIN_INTERVAL_MS },
      } : {}),
    })),
  };
}

module.exports = { FLAG, ATTRIBUTION, ID_RE, packs, packFile, navigation, decorate, _sums: sums };
