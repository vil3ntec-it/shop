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

const TAGS = new Set(['tohid-native', 'tohid-desktop']);
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

router.get('/:tag/latest', async (req, res, next) => {
  try {
    const tag = String(req.params.tag);
    if (!TAGS.has(tag)) return next(notFound('انتشاری با این نام نیست', 'release_not_found'));
    const data = await release(tag);
    res.set('Cache-Control', 'public, max-age=60');
    res.json({
      tag,
      version: await versionOf(data),
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
