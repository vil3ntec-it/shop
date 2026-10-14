'use strict';
/**
 * توزیعِ نسخه‌های برنامه — بندِ ۲۶ِ ممیزی (۱۴۰۵/۰۷/۲۰).
 *
 *   GET /api/releases/:tag/latest        ⇐ { version, notes, assets: [{ name, size }] }
 *   GET /api/releases/:tag/file/:name    ⇐ خودِ فایل (نصبی، SHA256SUMS.txt و امضایش)
 *
 * ── چرا ─────────────────────────────────────────────────────────────
 * برنامه‌ها تا امروز یک‌راست از github.com می‌پرسیدند و دانلود می‌کردند: نامِ
 * مخزن در هر نصبی بود، خصوصی کردنِ مخزن به‌روزرسانیِ همه را می‌کشت، و سهمیهٔ
 * ۶۰ درخواستِ بی‌نامِ GitHub پشتِ یک اینترنتِ مشترک زود تمام می‌شد.
 * حالا سرور تنها نقطهٔ توزیع است: از انتشارِ مخزن می‌خواند (با توکن اگر
 * خصوصی است — توکن هرگز به برنامه نمی‌رسد)، فایل را یک بار روی دیسکِ خودش
 * نگه می‌دارد، و از آن پس خودش می‌دهد.
 *
 * ── کانالِ آزمایشی (F0-27، ۱۴۰۵/۰۷/۲۱) ───────────────────────────────
 * push به main فقط زیرِ `<برچسب>-beta` منتشر می‌شود؛ برچسبِ پایدار فقط با
 * workflow_dispatch. کدام دکان یا دستگاه آزمایشی است را `live_config`
 * می‌گوید — scope `release`، کلیدهای `beta.shops` و `beta.devices` (فهرست
 * یا رشتهٔ جداشده با ویرگول). برنامه `?shop=&device=` می‌فرستد؛ اگر عضو بود،
 * از دو انتشار هر کدام نسخهٔ بالاتر دارد داده می‌شود و `tag` در پاسخ می‌گوید
 * فایل‌ها را از کدام برچسب بگیرد. برنامهٔ قدیمی این‌ها را نمی‌فرستد ⇒ پایدار.
 *   ⛔ شناسه‌ها راز نیستند و نباید باشند: بدترین حالتِ جعل، گرفتنِ یک نسخهٔ
 *      آزمایشیِ امضاشده است که روی GitHub هم عمومی است.
 *
 * ⛔ فقط برچسب‌ها و فایل‌هایی که در انتشار هست؛ نامِ فایل هرگز مسیر نمی‌شود.
 * ⛔ اصالت را سرور تضمین نمی‌کند — برنامه `SHA256SUMS.txt` و امضای ECDSAِ آن را
 *    با کلیدِ سنجاق‌شدهٔ خودش می‌سنجد؛ سرورِ دست‌کاری‌شده هم نمی‌تواند نصبیِ جعلی بدهد.
 */
const express = require('express');
const fs = require('fs');
const path = require('path');
const { pipeline } = require('stream/promises');
const { Readable } = require('stream');
const config = require('../config');
const { notFound } = require('../middleware/errors');

const router = express.Router();

const STABLE = ['tohid-native', 'tohid-desktop'];
const BETA = (tag) => `${tag}-beta`;
const TAGS = new Set([...STABLE, ...STABLE.map(BETA)]);
const ID = /^[A-Za-z0-9_-]{1,80}$/;
const NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$/;

/** انتشارِ هر برچسب، تا `ttlMs` در حافظه */
const cache = new Map();

function headers(accept = 'application/vnd.github+json') {
  const h = { Accept: accept, 'User-Agent': 'tohid-server' };
  if (config.releases.token) h.Authorization = `Bearer ${config.releases.token}`;
  return h;
}

async function release(tag) {
  const hit = cache.get(tag);
  if (hit && Date.now() - hit.at < config.releases.ttlMs) return hit.data;
  const url = `${config.releases.apiBase}/repos/${config.releases.repo}/releases/tags/${encodeURIComponent(tag)}`;
  try {
    const res = await fetch(url, { headers: headers(), signal: AbortSignal.timeout(15000) });
    if (!res.ok) throw new Error(`github ${res.status}`);
    const r = await res.json();
    const data = {
      tag,
      notes: String(r.body || ''),
      publishedAt: r.published_at || null,
      assets: (r.assets || []).map(a => ({ id: a.id, name: a.name, size: a.size, updatedAt: a.updated_at })),
    };
    cache.set(tag, { at: Date.now(), data });
    return data;
  } catch (err) {
    //  GitHub در دسترس نیست ⇒ آخرین چیزی که دیدیم بهتر از هیچ است
    if (hit) return hit.data;
    throw err;
  }
}

/** نسخه: اول `version.txt`ِ انتشار، وگرنه شماره‌ای که در نامِ نصبی هست */
async function versionOf(data) {
  const v = data.assets.find(a => a.name === 'version.txt');
  if (v) {
    const file = await cached(data.tag, v);
    const text = fs.readFileSync(file, 'utf8').trim().split(/\r?\n/)[0].trim();
    if (/^\d+(\.\d+)+$/.test(text)) return text;
  }
  for (const a of data.assets) {
    const m = /(\d+(?:\.\d+)+)/.exec(a.name);
    if (m && /\.(apk|msi|dmg|deb)$/i.test(a.name)) return m[1];
  }
  return '';
}

/** فایلِ یک دارایی روی دیسک — با شناسه و زمانِ به‌روزشدنش، پس نسخهٔ کهنه دوباره داده نمی‌شود */
async function cached(tag, asset) {
  const dir = path.join(config.releases.dir, tag);
  const stamp = String(asset.updatedAt || '').replace(/[^0-9A-Za-z]/g, '');
  const file = path.join(dir, `${asset.id}-${stamp}-${asset.name}`);
  if (fs.existsSync(file)) return file;
  fs.mkdirSync(dir, { recursive: true });
  const res = await fetch(`${config.releases.apiBase}/repos/${config.releases.repo}/releases/assets/${asset.id}`, {
    headers: headers('application/octet-stream'), redirect: 'follow', signal: AbortSignal.timeout(10 * 60 * 1000),
  });
  if (!res.ok || !res.body) throw new Error(`github asset ${res.status}`);
  const tmp = `${file}.part-${process.pid}-${Date.now()}`;
  await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tmp));
  if (asset.size && fs.statSync(tmp).size !== asset.size) {
    fs.rmSync(tmp, { force: true });
    throw new Error('اندازهٔ فایل با انتشار نمی‌خواند');
  }
  fs.renameSync(tmp, file);
  //  نسخه‌های کهنهٔ همین نام پاک شوند تا دیسک پر نشود
  for (const f of fs.readdirSync(dir)) {
    if (f.endsWith(`-${asset.name}`) && path.join(dir, f) !== file) fs.rmSync(path.join(dir, f), { force: true });
  }
  return file;
}

/** `1.2.10` > `1.2.9`؛ رشتهٔ خالی از همه کوچک‌تر */
function newer(a, b) {
  const x = String(a || '').split('.').map(Number);
  const y = String(b || '').split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d > 0;
  }
  return false;
}

/** فهرستِ آزمایشی‌ها از live_config (scope `release`)، تا ۶۰ ثانیه در حافظه */
let betaCache = { at: 0, shops: new Set(), devices: new Set() };
async function betaMembers() {
  if (Date.now() - betaCache.at < 60 * 1000) return betaCache;
  const ids = (v) => new Set((Array.isArray(v) ? v : String(v ?? '').split(','))
    .map((x) => String(x).trim()).filter((x) => ID.test(x)));
  try {
    const { items } = await require('../lib/live-config').list('release');
    const val = (k) => items.find((i) => i.key === k)?.value;
    betaCache = { at: Date.now(), shops: ids(val('beta.shops')), devices: ids(val('beta.devices')) };
  } catch (err) {
    //  دیتابیس در دسترس نیست ⇒ همان فهرستِ قبلی؛ هرگز خطا به‌خاطرِ کانال
    console.warn(`[releases] beta list: ${err.message}`);
  }
  return betaCache;
}

async function isBeta(q) {
  const shop = String(q.shop || '');
  const device = String(q.device || '');
  if (!shop && !device) return false;
  const m = await betaMembers();
  return (ID.test(shop) && m.shops.has(shop)) || (ID.test(device) && m.devices.has(device));
}

router.get('/:tag/latest', async (req, res, next) => {
  try {
    const tag = String(req.params.tag);
    if (!TAGS.has(tag)) return next(notFound('انتشاری با این نام نیست', 'release_not_found'));
    let use = tag;
    let data = await release(tag);
    let version = await versionOf(data);
    let channel = tag.endsWith('-beta') ? 'beta' : 'stable';
    //  عضوِ آزمایشی: از پایدار و آزمایشی، هر کدام بالاتر. انتشارِ آزمایشی
    //  نبود یا خراب بود ⇒ همان پایدار، بی خطا.
    if (STABLE.includes(tag) && await isBeta(req.query)) {
      try {
        const b = await release(BETA(tag));
        const bv = await versionOf(b);
        if (bv && newer(bv, version)) { use = BETA(tag); data = b; version = bv; channel = 'beta'; }
      } catch (err) {
        console.warn(`[releases] ${BETA(tag)}: ${err.message}`);
      }
    }
    //  پاسخ به شناسه بسته است ⇒ کشِ مشترک نباید پاسخِ یک دکان را به دیگری بدهد
    res.set('Cache-Control', req.query.shop || req.query.device ? 'private, max-age=60' : 'public, max-age=60');
    res.json({
      tag: use,
      channel,
      version,
      notes: data.notes,
      publishedAt: data.publishedAt,
      assets: data.assets.map(a => ({ name: a.name, size: a.size })),
    });
  } catch (err) {
    console.warn(`[releases] ${err.message}`);
    next(notFound('فعلاً به انتشارها دسترسی نیست', 'release_unavailable'));
  }
});

router.get('/:tag/file/:name', async (req, res, next) => {
  try {
    const tag = String(req.params.tag);
    const name = String(req.params.name);
    if (!TAGS.has(tag) || !NAME.test(name)) return next(notFound('فایلی با این نام نیست', 'release_file_not_found'));
    const data = await release(tag);
    const asset = data.assets.find(a => a.name === name);
    if (!asset) return next(notFound('فایلی با این نام نیست', 'release_file_not_found'));
    const file = await cached(tag, asset);
    res.set('Cache-Control', /\.(txt|sig)$/i.test(name) ? 'no-cache' : 'public, max-age=300');
    res.download(file, name);
  } catch (err) {
    console.warn(`[releases] ${err.message}`);
    next(notFound('فعلاً به این فایل دسترسی نیست', 'release_unavailable'));
  }
});

module.exports = router;
module.exports._cache = cache;
module.exports._forgetBeta = () => { betaCache = { at: 0, shops: new Set(), devices: new Set() }; };
