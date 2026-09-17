'use strict';
/**
 * پشتیبانِ هر حساب — پوشهٔ خودش، سهمِ خودش.
 *
 * ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 * تا امروز سه جور پشتیبان داشتیم و هیچ‌کدام کارِ کاربر را نمی‌کرد:
 *
 *   • `lib/backup.js` — کلِ دیتابیس با `pg_dump`. مالِ صاحبِ سامانه
 *     است. برگرداندنِ یک دکان از آن یعنی برگرداندنِ **همه** به دیروز.
 *   • `AutoBackup`ِ برنامهٔ دکان — فقط روی خودِ گوشی. گوشی که رفت،
 *     کارِ چند سال هم رفت.
 *   • `BackupPusher`ِ برنامهٔ پمپ — فقط روی سرورِ خانگی. مودم که
 *     سوخت، همان‌جا سوخت.
 *
 * حالا هر دکان و هر پمپ می‌تواند پشتیبانش را روی ابر بگذارد، فهرستِ
 * **خودش** را ببیند، و هر کدام را برگرداند.
 *
 * ── سه قاعده که باید بمانند ────────────────────────────────────────
 *
 * ⛔ **خواندن هرگز قفل نمی‌شود.** همان قاعده‌ای که `requireDataWrite`
 *    دارد: دادهٔ کاربر مالِ خودش است. اشتراکِ تمام‌شده نباید بین او و
 *    پشتیبانش دیوار بکشد — گروگان گرفتنِ داده سریع‌ترین راهِ از دست
 *    دادنِ اعتماد است.
 *
 * ⛔ **نوشتن هم بسته نمی‌شود، فقط سهمش کوچک‌تر است.** دیسک پول است،
 *    ولی حسابی که اشتراک ندارد بیشتر از همه ممکن است گوشی‌اش را گم
 *    کند. پس پلهٔ رایگان کوچک است، نه صفر.
 *
 * ⛔ **هر پرس‌وجو باید `app` را هم شرط کند.** یک جدول برای دو بخش است
 *    و `app` تنها چیزی است که می‌گوید ردیف مالِ کدام دفتر است. بی آن،
 *    پمپی با شناسه‌ای برابرِ یک دکان پشتیبانِ او را می‌دید.
 */
const fsp = require('fs/promises');
const path = require('path');
const { createHash, randomBytes } = require('crypto');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const tenancy = require('./tenancy');
const { badRequest, notFound, forbidden } = require('../middleware/errors');

/**
 * پسوندهای پذیرفته‌شده.
 *
 * ⚠️ نامِ فایل را **ما** می‌سازیم، نه کاربر — این فهرست فقط می‌گوید
 * پسوند چه باشد. اگر نامِ فرستاده‌شده مستقیم روی دیسک می‌نشست، یک
 * `../../` کافی بود تا جای دیگری نوشته شود.
 */
const EXTS = ['json', 'db', 'sql', 'gz', 'zip', 'bin', 'enc'];
const DEFAULT_EXT = 'bin';

const KINDS = ['auto', 'manual'];

/** نامِ فایل از این الگو بیرون نمی‌رود. */
function stamp(d = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}`
       + `-${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}`;
}

/**
 * شناسهٔ حساب، پیش از آن‌که بخشی از یک مسیرِ فایل شود.
 *
 * شناسه‌ها را خودمان با `newId` می‌سازیم و همیشه امن‌اند — ولی این
 * تابع آخرین درِ بین «رشته‌ای که از جایی آمده» و «پوشه‌ای روی دیسک»
 * است، و چنین دری باید بسته بماند حتی وقتی به‌نظر لازم نیست.
 */
function safeTenant(id) {
  const s = String(id || '');
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(s)) throw badRequest('شناسهٔ حساب معتبر نیست', 'bad_tenant');
  return s;
}

function cleanExt(raw) {
  const s = String(raw || '').trim().toLowerCase().replace(/^\./, '');
  return EXTS.includes(s) ? s : DEFAULT_EXT;
}

function cleanKind(raw) {
  const s = String(raw || '').trim().toLowerCase();
  return KINDS.includes(s) ? s : 'auto';
}

function build(T) {
  const APP = T.app;

  /** پوشهٔ همین یک حساب. */
  function dirFor(tenantId) {
    return path.join(config.backup.dir, 'accounts', APP, safeTenant(tenantId));
  }

  /**
   * سهمِ این حساب — دو پله، از روی `entitlement`.
   *
   * ⚠️ نبودنِ اشتراک «هیچ» نیست، «کم» است. و اگر سنجیدنِ اشتراک خودش
   * خطا داد (دیتابیس یک لحظه نبود)، پلهٔ رایگان برمی‌گردد — نه خطا؛
   * پشتیبان نباید به‌خاطر یک لغزشِ گذرا رد شود.
   */
  async function tierOf(tenantId) {
    const a = config.backup.account;
    let source = 'free';
    try {
      const ent = await require('./entitlement').forApp(APP).entitlementOf(tenantId);
      source = ent.source;
    } catch { source = 'free'; }
    const paid = source === 'subscription' || source === 'trial';
    return {
      source,
      paid,
      keep: paid ? a.paidKeep : a.freeKeep,
      quotaBytes: paid ? a.paidBytes : a.freeBytes,
      maxBytes: a.maxBytes,
    };
  }

  /** ردیفِ دیتابیس → چیزی که برنامه و پنل می‌بینند. */
  function shape(row) {
    return {
      id: row.id,
      app: row.app,
      bytes: Number(row.bytes),
      sha256: row.sha256,
      label: row.label,
      kind: row.kind,
      appVersion: row.app_version,
      createdAt: Number(row.created_at),
      //  نامی که کاربر موقعِ ذخیره می‌بیند — نه نامِ روی دیسکِ ما
      name: row.file,
    };
  }

  async function list(tenantId, { limit = 100 } = {}) {
    const rows = await many(
      `SELECT * FROM account_backups
        WHERE app=$1 AND tenant_id=$2
        ORDER BY created_at DESC, id DESC
        LIMIT $3`,
      [APP, safeTenant(tenantId), Math.max(1, Math.min(500, Number(limit) || 100))]
    );
    return rows.map(shape);
  }

  /** مجموعِ چیزی که این حساب گرفته، و سقفش. */
  async function stats(tenantId) {
    const id = safeTenant(tenantId);
    const row = await one(
      `SELECT COUNT(*)::int AS n, COALESCE(SUM(bytes),0)::bigint AS used,
              COALESCE(MAX(created_at),0)::bigint AS last
         FROM account_backups WHERE app=$1 AND tenant_id=$2`,
      [APP, id]
    );
    const tier = await tierOf(id);
    const used = Number(row?.used || 0);
    return {
      app: APP,
      count: Number(row?.n || 0),
      usedBytes: used,
      lastAt: Number(row?.last || 0),
      keep: tier.keep,
      quotaBytes: tier.quotaBytes,
      maxBytes: tier.maxBytes,
      freeBytes: Math.max(0, tier.quotaBytes - used),
      paid: tier.paid,
      source: tier.source,
    };
  }

  async function find(tenantId, id) {
    return one(
      'SELECT * FROM account_backups WHERE app=$1 AND tenant_id=$2 AND id=$3',
      [APP, safeTenant(tenantId), String(id || '')]
    );
  }

  /** فایلِ یک ردیف، اگر هنوز روی دیسک باشد. */
  function fileOf(row) {
    return path.join(dirFor(row.tenant_id), row.file);
  }

  /**
   * پاک کردنِ یک ردیف — اول دیسک، بعد دیتابیس.
   *
   * ⚠️ ترتیبش عمدی است. اگر اول ردیف می‌رفت و بعد پاک کردنِ فایل
   * می‌شکست، فایلِ یتیم تا ابد جای دیسک را می‌گرفت و هیچ‌کس خبر
   * نداشت. حالا بدترین حالت ردیفی است که فایلش رفته — و آن را
   * `list` می‌گوید و کاربر می‌بیند.
   */
  async function dropRow(row) {
    try { await fsp.unlink(fileOf(row)); }
    catch (err) { if (err.code !== 'ENOENT') throw err; }
    await query('DELETE FROM account_backups WHERE id=$1', [row.id]);
  }

  async function remove(tenantId, id) {
    const row = await find(tenantId, id);
    if (!row) throw notFound('این پشتیبان پیدا نشد', 'backup_not_found');
    await dropRow(row);
    return shape(row);
  }

  /**
   * کهنه‌ها را می‌برد تا هم شمار و هم حجم زیرِ سهم بیاید.
   *
   * ⚠️ تازه‌ترین پشتیبان **هرگز** پاک نمی‌شود، حتی اگر خودش از سهم
   * بزرگ‌تر باشد. وگرنه حسابی که یک فایلِ بزرگ فرستاده بود، با
   * فرستادنِ فایلِ بعدی هر دو را از دست می‌داد.
   *
   * ⚠️ و وقتی به سهم خوردیم، **همهٔ** کهنه‌ترها می‌روند — نه این‌که
   * از میانِ آن‌ها کوچک‌ها بمانند. فهرستی که وسطش سوراخ داشته باشد
   * کاربر را گیج می‌کند: «پشتیبانِ سه‌شنبه هست، چهارشنبه نیست، پنج‌شنبه
   * هست؟» ترتیبِ زمانی باید پیوسته بماند.
   */
  async function prune(tenantId) {
    const id = safeTenant(tenantId);
    const tier = await tierOf(id);
    const rows = await many(
      `SELECT * FROM account_backups WHERE app=$1 AND tenant_id=$2
        ORDER BY created_at DESC, id DESC`,
      [APP, id]
    );
    const removed = [];
    let used = 0;
    let cutting = false;
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      if (!cutting && i > 0
          && (i >= tier.keep || used + Number(row.bytes) > tier.quotaBytes)) {
        cutting = true;
      }
      if (cutting) {
        await dropRow(row);
        removed.push(row.id);
      } else {
        used += Number(row.bytes);
      }
    }
    return { removed, usedBytes: used };
  }

  /**
   * نشاندنِ یک پشتیبانِ تازه.
   *
   * @param {Buffer} data خودِ فایل — خام، همان‌طور که برنامه فرستاده
   */
  async function save(tenantId, data, {
    label = '', kind = 'auto', ext = '', appVersion = '', deviceId = '', userId = '',
  } = {}) {
    const id = safeTenant(tenantId);
    if (!Buffer.isBuffer(data) || data.length === 0) {
      throw badRequest('فایلِ پشتیبان خالی است', 'empty_backup');
    }
    const tier = await tierOf(id);
    if (data.length > tier.maxBytes) {
      throw badRequest(
        `این فایل از سقفِ ${Math.round(tier.maxBytes / 1048576)} مگابایت بزرگ‌تر است`,
        'backup_too_large'
      );
    }
    //  ⚠️ یک فایل که خودش از کلِ سهم بزرگ‌تر باشد هیچ‌وقت جا نمی‌شود؛
    //  پس همین‌جا گفته می‌شود، نه بعد از آپلودِ کامل و پاک شدنِ همه‌چیز.
    if (data.length > tier.quotaBytes) {
      throw forbidden(
        tier.paid
          ? `این فایل از سهمِ ${Math.round(tier.quotaBytes / 1048576)} مگابایتیِ این حساب بزرگ‌تر است`
          : 'سهمِ پشتیبانِ حساب‌های بی‌اشتراک کوچک است. با اشتراک، جای بیشتری باز می‌شود.',
        'backup_quota'
      );
    }

    const dir = dirFor(id);
    await fsp.mkdir(dir, { recursive: true });

    const file = `${APP}-${stamp()}-${randomBytes(4).toString('hex')}.${cleanExt(ext)}`;
    const target = path.join(dir, file);
    const tmp = `${target}.part`;

    //  ⚠️ اول موقت، بعد جابه‌جایی — همان قاعدهٔ `AppSettings.Save`ِ
    //  برنامهٔ پمپ. نوشتنِ مستقیم یعنی قطعِ اتصال وسطِ کار، فایلِ
    //  نصفه‌ای می‌گذارد که در فهرست سالم به‌نظر می‌رسد.
    await fsp.writeFile(tmp, data);
    await fsp.rename(tmp, target);

    const row = await one(
      `INSERT INTO account_backups
         (id, app, tenant_id, file, bytes, sha256, label, kind, app_version, device_id, user_id, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
       RETURNING *`,
      [
        newId('bak'), APP, id, file, data.length,
        createHash('sha256').update(data).digest('hex'),
        String(label || '').slice(0, 200), cleanKind(kind),
        String(appVersion || '').slice(0, 40),
        String(deviceId || '').slice(0, 64), String(userId || '').slice(0, 64),
        now(),
      ]
    );

    const pruned = await prune(id);
    return { backup: shape(row), pruned: pruned.removed, stats: await stats(id) };
  }

  /** خودِ فایل، برای دانلود. اگر ردیف باشد و فایل نه، همین را می‌گوید. */
  async function read(tenantId, id) {
    const row = await find(tenantId, id);
    if (!row) throw notFound('این پشتیبان پیدا نشد', 'backup_not_found');
    const file = fileOf(row);
    try {
      const data = await fsp.readFile(file);
      return { row: shape(row), data };
    } catch (err) {
      if (err.code === 'ENOENT') {
        throw notFound('فایلِ این پشتیبان روی سرور نیست', 'backup_file_missing');
      }
      throw err;
    }
  }

  /** وقتی حسابی پاک می‌شود — پوشه هم باید برود. */
  async function removeAll(tenantId) {
    const id = safeTenant(tenantId);
    const rows = await many(
      'SELECT * FROM account_backups WHERE app=$1 AND tenant_id=$2', [APP, id]
    );
    for (const row of rows) await dropRow(row);
    try { await fsp.rm(dirFor(id), { recursive: true, force: true }); } catch { /* نبود، نبود */ }
    return rows.length;
  }

  return {
    app: APP, tenancy: T,
    dirFor, tierOf, shape, list, stats, find, read, save, remove, removeAll, prune,
    EXTS, KINDS,
  };
}

const shop = build(tenancy.SHOP);
const pump = build(tenancy.PUMP);

module.exports = {
  build, shop, pump,
  forApp: (app) => (app === 'pump' ? pump : shop),
  EXTS, KINDS, safeTenant, cleanExt, cleanKind,
  //  صادراتِ پیش‌فرض همان بخشِ دکان است، مثلِ بقیهٔ کارخانه‌ها
  ...shop,
};
