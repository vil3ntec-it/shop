'use strict';
/**
 * زنجیرهٔ هش روی دیتابیس (جدولِ `hash_chain`، migration 070) — STF-09 و SEC-02.
 *
 *   append(chain, doc)   حلقهٔ تازه پسِ آخرین حلقه (بی قفل: کلیدِ اصلیِ
 *                        (chain, seq) انشعاب را رد می‌کند و دوباره می‌کوشیم)
 *   verifyChain(chain)   از seq=1 تا سر؛ نخستین حلقهٔ شکسته را می‌گوید
 *   verifyAll()          همهٔ زنجیره‌ها — همان که scripts/verify-chain.js می‌زند
 *
 * ⛔ خاموش به‌طورِ پیش‌فرض: سابقهٔ عملیات (audit.js) فقط وقتی به زنجیره می‌رود
 * که در live_config، scope = `security`، کلیدِ `hashchain.audit` = true باشد
 * (پنل: «تنظیماتِ زنده»، همان دری که `release / beta.*` دارد). تا آن موقع
 * هیچ ردیفی نوشته نمی‌شود و رفتارِ نصب‌شده عوض نمی‌شود.
 *
 * ⛔ بدنهٔ حلقه دادهٔ شخصی ندارد (بخشِ ۱۰.۳): نه IP، نه متنِ جزئیات — از
 * جزئیات فقط درهمش (`detailHash`) می‌آید تا ناشناس‌سازی (SEC-10) زنجیره را
 * نشکند ولی هر دست‌کاریِ `audit_logs` در سنجش دیده شود.
 */
const db = require('../db');
const hc = require('./hash-chain');

const FLAG_SCOPE = 'security';
const FLAG_AUDIT = 'hashchain.audit';
const CHAIN_RE = /^[A-Za-z0-9_.:/-]{1,200}$/;
const PAGE = 1000;

/**
 * پرچم به‌صورتِ زیرپرسش — audit.js آن را در `RETURNING` ِ همان INSERT ِ سابقه
 * می‌خواند. ⛔ پس وقتی خاموش است هیچ پرسشِ اضافه‌ای نیست و سابقه دقیقاً همان
 * یک دستورِ امروز است (پرسشِ جدا پس از INSERT، اگر پس از بسته شدنِ دیتابیس
 * برسد، استخر را از نو می‌سازد و فرآیند را زنده نگه می‌دارد — backup.test).
 */
const FLAG_SQL = `(SELECT value FROM live_config WHERE scope = '${FLAG_SCOPE}' AND key = '${FLAG_AUDIT}')`;

/** مقدارِ متنیِ live_config (JSON) ⇒ روشن؟ نبود یا خراب ⇒ خاموش. */
function flagOn(text) {
  if (text === null || text === undefined) return false;
  let v;
  try { v = JSON.parse(text); } catch { return false; }
  return v === true || v === 1 || v === '1' || v === 'true' || v === 'on';
}

/** آیا سابقهٔ عملیات به زنجیره می‌رود؟ (برای آزمون و ابزار؛ مسیرِ داغ از FLAG_SQL می‌خواند) */
async function auditEnabled() {
  const r = await db.one(`SELECT ${FLAG_SQL} AS v`);
  return flagOn(r && r.v);
}

function checkChain(chain) {
  const c = String(chain || '');
  if (!CHAIN_RE.test(c)) throw new hc.HashChainError('bad_chain', `نامِ زنجیره نامعتبر است: ${c.slice(0, 40)}`);
  return c;
}

const part = (v) => String(v || '').replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 60) || '-';

/** نامِ زنجیرهٔ سابقهٔ یک کاربر (STF-09): هر کاربرِ هر دکان زنجیرهٔ خودش. */
function auditChainId({ shopId = '', actorType = 'user', userId = '' } = {}) {
  return `audit/${part(shopId)}/${part(actorType || 'user')}/${part(userId)}`;
}

/** درهمِ جزئیات — مستقل از ترتیبِ کلیدها، چون jsonb ترتیب را عوض می‌کند. */
function detailHash(detail) {
  const loose = (v) => {
    if (v === null || typeof v !== 'object') return JSON.stringify(v === undefined ? null : v);
    if (Array.isArray(v)) return `[${v.map(loose).join(',')}]`;
    return `{${Object.keys(v).sort().filter((k) => v[k] !== undefined)
      .map((k) => `${JSON.stringify(k)}:${loose(v[k])}`).join(',')}}`;
  };
  return hc.sha256Hex(loose(detail && typeof detail === 'object' ? detail : {}));
}

/**
 * صفِ هر زنجیره در همین فرآیند — نوشتن‌های هم‌زمانِ یک زنجیره پشتِ هم
 * می‌روند و دوباره‌کوشیدن فقط برای چند نمونهٔ سرور می‌ماند.
 * ⚠️ انتظار سقف دارد (LOCK_WAIT_MS): اگر append درونِ یک db.tx ِ PGlite صدا
 * زده شود و نوبتِ قبلی پشتِ همان تراکنش مانده باشد، بن‌بست نمی‌شود؛ پس از
 * سقف بی نوبت می‌رود و کلیدِ اصلی + دوباره‌کوشیدن درستی را نگه می‌دارد.
 */
const LOCK_WAIT_MS = 2000;
const queues = new Map();
async function inTurn(chain, fn) {
  const before = queues.get(chain) || Promise.resolve();
  let release;
  const mine = new Promise((r) => { release = r; });
  const tail = before.then(() => mine);
  queues.set(chain, tail);
  let timer;
  await Promise.race([before, new Promise((r) => { timer = setTimeout(r, LOCK_WAIT_MS); })]);
  clearTimeout(timer);
  try {
    return await fn();
  } finally {
    release();
    if (queues.get(chain) === tail) queues.delete(chain);
  }
}

/**
 * حلقهٔ تازه. `doc` شیءِ ساده با عددِ صحیح است؛ `chain` و `seq` خودکار
 * درونش نوشته می‌شوند تا جابه‌جاییِ حلقه‌ها هم در سنجش دیده شود.
 */
async function append(chain, doc, { at = db.now() } = {}) {
  const c = checkChain(chain);
  hc.canonicalJson(doc); // سندِ نامعتبر پیش از صف رد شود
  return inTurn(c, async () => {
    for (let attempt = 0; attempt < 20; attempt++) {
      const head = await db.one(
        'SELECT seq, hash FROM hash_chain WHERE chain=$1 ORDER BY seq DESC LIMIT 1', [c]
      );
      const seq = head ? Number(head.seq) + 1 : 1;
      const prevHash = head ? head.hash : hc.GENESIS;
      const body = hc.canonicalJson({ ...doc, chain: c, seq });
      const hash = hc.sha256Hex(prevHash + body);
      const r = await db.query(
        `INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at)
         VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (chain, seq) DO NOTHING RETURNING seq`,
        [c, seq, prevHash, hash, body, at]
      );
      if (r.rows.length) return { chain: c, seq, prevHash, hash };
      //  نمونهٔ دیگری از سرور همان seq را گرفت — کمی صبر و دوباره
      await new Promise((r2) => setTimeout(r2, 5 + Math.floor(Math.random() * 20)));
    }
    throw new Error(`hash_chain ${c}: حلقه پس از چند بار کوشش نوشته نشد`);
  });
}

/** یک ردیفِ سابقه ⇒ حلقهٔ زنجیرهٔ همان کاربر. */
function appendAudit(row) {
  const doc = {
    kind: 'audit',
    id: row.id,
    shopId: row.shopId || '',
    actorType: row.actorType || 'user',
    userId: row.userId || '',
    action: row.action,
    targetType: row.targetType || '',
    targetId: row.targetId || '',
    //  همان که در jsonb می‌نشیند (Date ⇒ رشته، undefined ⇒ نیست)
    detailHash: detailHash(JSON.parse(JSON.stringify(row.detail ?? {}))),
    at: Number(row.at),
  };
  return append(auditChainId(row), doc, { at: doc.at });
}

/** حلقهٔ سابقه با ردیفِ `audit_logs` اش یکی است؟ ⇒ null یا دلیل */
function auditRowProblem(doc, row) {
  if (!row) return 'row_missing';
  const same = row.shop_id === doc.shopId && row.actor_type === doc.actorType
    && row.user_id === doc.userId && row.action === doc.action
    && row.target_type === doc.targetType && row.target_id === doc.targetId
    && Number(row.created_at) === doc.at
    && detailHash(typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail) === doc.detailHash;
  return same ? null : 'row_changed';
}

/**
 * سنجشِ یک زنجیره از ابتدا تا سر.
 * ⇒ { ok:true, chain, length, head } یا
 *   { ok:false, chain, length, seq, reason, expected?, actual? }
 * reason: seq_gap | prev_mismatch | bad_body | not_canonical | body_mismatch
 *         | hash_mismatch | row_missing | row_changed
 * `rows: false` ⇒ ردیف‌های `audit_logs` سنجیده نمی‌شوند.
 */
async function verifyChain(chain, { rows = true } = {}) {
  const c = checkChain(chain);
  let prev = hc.GENESIS;
  let expectSeq = 1;
  const broken = (seq, reason, extra = {}) => ({ ok: false, chain: c, length: seq - 1, seq, reason, ...extra });

  /** ردیف‌های `audit_logs` ِ حلقه‌های سالمِ این صفحه — نخستین مشکل یا null */
  async function rowCheck(audits) {
    if (!audits.length) return null;
    const found = new Map((await db.many(
      `SELECT id, shop_id, actor_type, user_id, action, target_type, target_id, detail, created_at
         FROM audit_logs WHERE id = ANY($1)`,
      [audits.map((a) => a.doc.id)]
    )).map((row) => [row.id, row]));
    for (const a of audits) {
      const problem = auditRowProblem(a.doc, found.get(a.doc.id));
      if (problem) return broken(a.seq, problem);
    }
    return null;
  }

  for (;;) {
    const page = await db.many(
      `SELECT seq, prev_hash, hash, body FROM hash_chain
        WHERE chain=$1 AND seq >= $2 ORDER BY seq LIMIT $3`,
      [c, expectSeq, PAGE]
    );
    if (!page.length) break;
    const audits = [];
    let failure = null;
    for (const r of page) {
      const seq = Number(r.seq);
      if (seq !== expectSeq) { failure = broken(expectSeq, 'seq_gap', { actual: seq }); break; }
      if (r.prev_hash !== prev) { failure = broken(seq, 'prev_mismatch', { expected: prev, actual: r.prev_hash }); break; }
      let doc;
      let canon;
      try {
        doc = JSON.parse(r.body);
        canon = hc.canonicalJson(doc);
      } catch (err) {
        failure = broken(seq, 'bad_body', { actual: err.code || err.message });
        break;
      }
      if (canon !== r.body) { failure = broken(seq, 'not_canonical'); break; }
      if (!doc || doc.chain !== c || doc.seq !== seq) { failure = broken(seq, 'body_mismatch'); break; }
      const h = hc.sha256Hex(prev + canon);
      if (h !== r.hash) { failure = broken(seq, 'hash_mismatch', { expected: h, actual: r.hash }); break; }
      if (rows && doc.kind === 'audit') audits.push({ seq, doc });
      prev = h;
      expectSeq++;
    }
    //  حلقه‌های پیش از شکست سالم‌اند ولی ردیفشان شاید نه — نخستین را بگو
    const rowFailure = rows ? await rowCheck(audits) : null;
    if (rowFailure || failure) return rowFailure || failure;
    if (page.length < PAGE) break;
  }
  return { ok: true, chain: c, length: expectSeq - 1, head: prev };
}

async function listChains() {
  return (await db.many('SELECT DISTINCT chain FROM hash_chain ORDER BY chain')).map((r) => r.chain);
}

/** همهٔ زنجیره‌ها (یا فقط `chains`) ⇒ { ok, results: [...] } */
async function verifyAll({ chains = null, rows = true } = {}) {
  const names = chains && chains.length ? chains : await listChains();
  const results = [];
  for (const name of names) results.push(await verifyChain(name, { rows }));
  return { ok: results.every((r) => r.ok), results };
}

module.exports = {
  FLAG_SCOPE, FLAG_AUDIT, FLAG_SQL, flagOn,
  auditEnabled, auditChainId, detailHash, append, appendAudit,
  verifyChain, verifyAll, listChains,
};
