'use strict';
/**
 * پشتیبان و بازگردانیِ **جدای** پیام و رسانهٔ چتِ مشتری ↔ دکان (v3 §۳۳.۳؛ CHAT-12).
 *
 *   پیام  ── `chat-messages-<زمان>.json.gz` : گفت‌وگوها، پیام‌ها (با سنگِ قبرِ
 *            «حذف برای همه»)، «حذف برای من»، بلاک، گزارش (با رونوشت) و سنجاق
 *            (اگر جدولش هست). بی بایتِ رسانه؛ به‌جایش فهرستِ رسانه‌ها با sha256.
 *   رسانه ── `chat-media-<زمان>.jsonl.gz`  : هر خط یک رسانه با بایت‌ها (base64) و sha256.
 *
 * دو فایل جدا ساخته، جدا جابه‌جا و جدا بازگردانده می‌شوند (رسانه بزرگ است و
 * پیام کوچک؛ هر کدام می‌تواند جای دیگری نگه داشته شود). پس از بازگردانی،
 * `verify()` رابطهٔ پیام ⇐ رسانه را می‌سنجد: هر پیامِ زنده‌ای که رسانه دارد،
 * رسانه‌اش هست، در همان گفت‌وگوست، نقش و نوعش می‌خواند و بایت‌هایش همان sha256 است.
 *
 * ⛔ بازگردانی هرگز ردیفِ موجود را بازنویسی نمی‌کند (`ON CONFLICT DO NOTHING`)
 *    و دوباره‌اجراشدنی است؛ هر فایل در یک تراکنش: یا همه یا هیچ.
 * ⛔ رسانه‌ای که sha256 ِ بایت‌هایش با برچسبش نخواند، یا گفت‌وگویش نیست، نوشته
 *    نمی‌شود و در گزارش می‌آید — فایلِ دست‌خورده رابطهٔ غلط نمی‌سازد.
 * ⛔ دکانی که در دیتابیسِ مقصد نیست ⇒ خطا و هیچ ردیف (اول پشتیبانِ اصلی برگردد).
 * ⛔ رمز: همان `BACKUP_PASSPHRASE` و همان قالبِ `lib/backup.js` (SHOPBK1،
 *    AES-256-GCM، scrypt)؛ خواندن با همان `backup.decode`. بی رمز ⇒ همان هشدارِ F0-08.
 * ⛔ متنِ پیام در لاگ نوشته نمی‌شود؛ فقط شمارها.
 */
const fsp = require('fs/promises');
const path = require('path');
const zlib = require('zlib');
const { createCipheriv, createHash, randomBytes, scryptSync } = require('crypto');
const config = require('../config');
const { many, one, tx } = require('../db');
const backup = require('./backup');

const MESSAGES_FORMAT = 'shop-chat-messages';
const MEDIA_FORMAT = 'shop-chat-media';
const VERSION = 1;
const MAGIC = 'SHOPBK1';

/** جدول‌های بخشِ پیام، به ترتیبِ وابستگی (بازگردانی به همین ترتیب). */
const TABLES = [
  { name: 'shop_chat_conversations', key: 'id' },
  { name: 'shop_chat_messages', key: 'id' },
  { name: 'shop_chat_hidden', key: 'message_id, viewer', viaMessage: true },
  { name: 'shop_chat_blocks', key: 'shop_id, customer_phone, blocker' },
  { name: 'shop_chat_reports', key: 'id' },
  { name: 'shop_chat_pins', key: 'conversation_id, side, message_id', optional: true },
];

const sha256 = (buf) => createHash('sha256').update(buf).digest('hex');
const stamp = () => new Date().toISOString().replace(/[-:]/g, '').replace(/\..*$/, '').replace('T', '-');

//  `run` = اتصالِ تراکنشِ بازگردانی (`(sql, params) ⇒ {rows}`)؛ نیامده ⇒ استخر
async function tableExists(name, run = null) {
  const sql = "SELECT 1 AS x FROM information_schema.tables WHERE table_schema='public' AND table_name=$1";
  return !!(run ? (await run(sql, [name])).rows[0] : await one(sql, [name]));
}

async function columnsOf(name, run = null) {
  const sql = "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='public' AND table_name=$1";
  const rows = run ? (await run(sql, [name])).rows : await many(sql, [name]);
  return new Map(rows.map((r) => [r.column_name, r.data_type]));
}

/** همان قالبِ رمزِ `lib/backup.js` (خواندن با `backup.decode`). */
function seal(gz) {
  const passphrase = config.backup.passphrase;
  if (!passphrase) return gz;
  const salt = randomBytes(16);
  const iv = randomBytes(12);
  const key = scryptSync(passphrase, salt, 32, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  return Buffer.concat([Buffer.from(MAGIC), salt, iv, cipher.update(gz), cipher.final(), cipher.getAuthTag()]);
}

async function writeArchive(file, raw) {
  const out = seal(zlib.gzipSync(raw, { level: 6 }));
  await fsp.writeFile(`${file}.part`, out, { mode: 0o600 });
  await fsp.rename(`${file}.part`, file);
  return { file, bytes: out.length, sha256: sha256(out) };
}

function plain(v) {
  if (v === null || v === undefined) return null;
  if (typeof v === 'bigint') return v.toString();
  if (v instanceof Date) return v.toISOString();
  return v;
}

function rowOut(row) {
  const o = {};
  for (const [k, v] of Object.entries(row)) o[k] = plain(v);
  return o;
}

/**
 * پشتیبانِ چت — دو فایلِ جدا در `dir` (پیش‌فرض `<BACKUP_PATH>/chat`).
 * `shopId` ⇒ فقط همان دکان.
 */
async function exportChat({ dir = path.join(config.backup.dir, 'chat'), shopId = null } = {}) {
  await fsp.mkdir(dir, { recursive: true });
  const encrypted = !!config.backup.passphrase;
  if (!encrypted) console.warn('[chat-backup]', backup.UNENCRYPTED_WARNING);
  const at = Date.now();
  const tag = stamp();
  const tables = {};
  for (const t of TABLES) {
    if (t.optional && !(await tableExists(t.name))) continue;
    let rows;
    if (t.viaMessage) {
      rows = await many(
        `SELECT x.* FROM ${t.name} x JOIN shop_chat_messages m ON m.id = x.message_id
          ${shopId ? 'WHERE m.shop_id = $1' : ''} ORDER BY ${t.key.split(',').map((k) => `x.${k.trim()}`).join(', ')}`,
        shopId ? [shopId] : []
      );
    } else {
      rows = await many(`SELECT * FROM ${t.name} ${shopId ? 'WHERE shop_id = $1' : ''} ORDER BY ${t.key}`, shopId ? [shopId] : []);
    }
    tables[t.name] = rows.map(rowOut);
  }
  const media = await many(
    `SELECT id, shop_id, conversation_id, uploader, uploader_user_id, role, kind, mime, size, sha256, created_at, data
       FROM shop_chat_media ${shopId ? 'WHERE shop_id = $1' : ''} ORDER BY id`,
    shopId ? [shopId] : []
  );
  const manifest = media.map((m) => ({ id: m.id, conversation_id: m.conversation_id, sha256: m.sha256, size: Number(m.size) }));
  const messagesDoc = {
    format: MESSAGES_FORMAT, version: VERSION, createdAt: at, shopId, tables, media: manifest,
  };
  const lines = [JSON.stringify({ format: MEDIA_FORMAT, version: VERSION, createdAt: at, shopId, count: media.length })];
  for (const m of media) {
    const data = Buffer.from(m.data);
    lines.push(JSON.stringify({ ...rowOut({ ...m, data: undefined }), data: data.toString('base64') }));
  }
  const suffix = encrypted ? '.enc' : '';
  const messages = await writeArchive(path.join(dir, `chat-messages-${tag}.json.gz${suffix}`), Buffer.from(JSON.stringify(messagesDoc)));
  const mediaOut = await writeArchive(path.join(dir, `chat-media-${tag}.jsonl.gz${suffix}`), Buffer.from(`${lines.join('\n')}\n`));
  const counts = Object.fromEntries(Object.entries(tables).map(([k, v]) => [k, v.length]));
  counts.shop_chat_media = media.length;
  return { messages, media: mediaOut, encrypted, counts, createdAt: at };
}

async function readJson(file) {
  return backup.decode(file);
}

/** مقدار برای پارامترِ ستون — jsonb ⇒ متنِ JSON. */
function param(value, type) {
  if (value === null || value === undefined) return null;
  if ((type === 'jsonb' || type === 'json') && typeof value !== 'string') return JSON.stringify(value);
  return value;
}

async function insertRows(c, table, rows, cols) {
  let inserted = 0;
  for (const row of rows) {
    const names = Object.keys(row).filter((k) => cols.has(k));
    if (!names.length) continue;
    const sql = `INSERT INTO ${table} (${names.map((n) => `"${n}"`).join(', ')})
      VALUES (${names.map((_, i) => `$${i + 1}`).join(', ')}) ON CONFLICT DO NOTHING`;
    const r = await c.query(sql, names.map((n) => param(row[n], cols.get(n))));
    inserted += r.rowCount || 0;
  }
  return inserted;
}

/** بازگردانیِ بخشِ پیام. رسانه جداست (`restoreMedia`). */
async function restoreMessages(file) {
  let doc = null;
  try { doc = JSON.parse((await readJson(file)).toString('utf8')); } catch (err) { if (!(err instanceof SyntaxError)) throw err; }
  if (!doc || doc.format !== MESSAGES_FORMAT || doc.version !== VERSION) throw new Error('این فایل پشتیبانِ پیام‌های چت نیست');
  const convs = doc.tables.shop_chat_conversations || [];
  const shopIds = [...new Set(convs.map((c) => c.shop_id).concat((doc.tables.shop_chat_blocks || []).map((b) => b.shop_id)))];
  if (shopIds.length) {
    const have = new Set((await many('SELECT id FROM shops WHERE id = ANY($1::text[])', [shopIds])).map((r) => r.id));
    const missing = shopIds.filter((s) => !have.has(s));
    if (missing.length) {
      throw new Error(`${missing.length} دکانِ این پشتیبان در دیتابیسِ مقصد نیست؛ اول پشتیبانِ اصلیِ سرور را برگردانید`);
    }
  }
  const restored = {};
  await tx(async (c) => {
    const run = (s, p) => c.query(s, p);
    for (const t of TABLES) {
      const rows = doc.tables[t.name];
      if (!rows) continue;
      if (t.optional && !(await tableExists(t.name, run))) { restored[t.name] = 0; continue; }
      restored[t.name] = await insertRows(c, t.name, rows, await columnsOf(t.name, run));
    }
  });
  return { restored, media: (doc.media || []).length };
}

/**
 * بازگردانیِ بخشِ رسانه. هر رسانه پیش از نوشتن سنجیده می‌شود: sha256 ِ بایت‌ها
 * = برچسب، اندازه درست، و گفت‌وگویش در مقصد هست. نخواند ⇒ نوشته نمی‌شود.
 */
async function restoreMedia(file) {
  const lines = (await readJson(file)).toString('utf8').split('\n').filter(Boolean);
  let head = null;
  try { head = JSON.parse(lines.shift() || '{}'); } catch (err) { if (!(err instanceof SyntaxError)) throw err; }
  if (!head || head.format !== MEDIA_FORMAT || head.version !== VERSION) throw new Error('این فایل پشتیبانِ رسانهٔ چت نیست');
  const cols = await columnsOf('shop_chat_media');
  const report = { restored: 0, existing: 0, corrupt: [], noConversation: [] };
  await tx(async (c) => {
    for (const line of lines) {
      const m = JSON.parse(line);
      const data = Buffer.from(String(m.data || ''), 'base64');
      if (sha256(data) !== m.sha256 || data.length !== Number(m.size)) { report.corrupt.push(m.id); continue; }
      const conv = (await c.query('SELECT 1 AS x FROM shop_chat_conversations WHERE id=$1 AND shop_id=$2', [m.conversation_id, m.shop_id])).rows[0];
      if (!conv) { report.noConversation.push(m.id); continue; }
      const n = await insertRows(c, 'shop_chat_media', [{ ...m, data }], cols);
      if (n) report.restored += 1; else report.existing += 1;
    }
  });
  return report;
}

/**
 * رابطهٔ پیام ⇐ رسانه پس از بازگردانی (یا هر وقت). فقط شمار و شناسه، بی متن.
 *   missing       — پیامِ زنده‌ای که رسانه/پیش‌نمایشش در دیتابیس نیست
 *   mismatched    — رسانه هست ولی گفت‌وگو، نقش (main/thumb) یا نوعش نمی‌خواند
 *   corrupt       — بایت‌های رسانه با sha256 ِ ثبت‌شده‌اش نمی‌خوانند
 */
async function verify({ shopId = null } = {}) {
  const args = shopId ? [shopId] : [];
  const scope = shopId ? 'AND m.shop_id = $1' : '';
  const refs = await many(
    `SELECT m.id AS message_id, m.conversation_id, m.kind, r.ref_id, r.role,
            x.id AS media_id, x.conversation_id AS media_conv, x.role AS media_role, x.kind AS media_kind
       FROM shop_chat_messages m
       CROSS JOIN LATERAL (VALUES (m.media_id, 'main'), (m.thumb_id, 'thumb')) AS r(ref_id, role)
       LEFT JOIN shop_chat_media x ON x.id = r.ref_id
      WHERE m.deleted_at IS NULL AND r.ref_id IS NOT NULL ${scope}
      ORDER BY m.id, r.role`,
    args
  );
  const missing = [];
  const mismatched = [];
  for (const r of refs) {
    if (!r.media_id) { missing.push({ messageId: r.message_id, mediaId: r.ref_id, role: r.role }); continue; }
    const kindOk = r.role === 'thumb' ? r.media_kind === 'image' : r.media_kind === r.kind;
    if (r.media_conv !== r.conversation_id || r.media_role !== r.role || !kindOk) {
      mismatched.push({ messageId: r.message_id, mediaId: r.ref_id, role: r.role });
    }
  }
  const corrupt = [];
  const media = await many(`SELECT id, sha256, size, data FROM shop_chat_media ${shopId ? 'WHERE shop_id = $1' : ''}`, args);
  for (const x of media) {
    const data = Buffer.from(x.data);
    if (sha256(data) !== x.sha256 || data.length !== Number(x.size)) corrupt.push(x.id);
  }
  const messages = Number((await one(`SELECT COUNT(*)::int AS n FROM shop_chat_messages m WHERE true ${scope}`, args)).n);
  return {
    ok: !missing.length && !mismatched.length && !corrupt.length,
    messages, media: media.length, references: refs.length, missing, mismatched, corrupt,
  };
}

module.exports = { exportChat, restoreMessages, restoreMedia, verify, TABLES, MESSAGES_FORMAT, MEDIA_FORMAT };
