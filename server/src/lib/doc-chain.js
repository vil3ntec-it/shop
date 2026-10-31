'use strict';
/**
 * SEC-02 — زنجیرهٔ هشِ اسنادِ مالیِ هر دستگاه، لنگرشده در سرور (بخشِ ۱۰.۳).
 *
 *   GET  /api/sync/v1/chain?device_id=  ⇐ { enabled, series:[{series, lastEpoch, epochs}] }
 *   POST /api/sync/v1/chain             ⇐ { format, heads:[…], links:[…] }
 *                                        ⇒ { enabled, acks:[…], problems:[…] }
 *
 * دستگاه (`core/.../audit/DocChain.kt`) برای سریِ خودش (F0-03) هر فاکتورِ ثبت‌شده را یک حلقه می‌کند:
 *     hash = SHA-256( prevHash ‖ canonicalJson(سند) )        (قالبِ tohid-hashchain-v1)
 * و در هر دورِ همگام‌سازی «سر» و حلقه‌های تأییدنشده را می‌فرستد. این‌جا:
 *   ۱) سری باید همان سریِ همین دستگاه در `shop_doc_series` باشد (`series_owner`)؛
 *   ۲) حلقه‌ای که سرور دارد باید همان هش را داشته باشد (`fork` — تاریخچه بازنویسی شده)؛
 *   ۳) حلقهٔ تازه درست پسِ سرِ سرور، با بدنهٔ canonical که `chain` و `seq` ِ خودش را دارد و هشش
 *      دوباره ساخته می‌شود، در `hash_chain` (`doc/<دکان>/<سری>/<دوره>`) می‌نشیند — همان جدولِ
 *      فقط‌افزودنیِ ۰۷۰ که پنل و `npm run verify-chain` می‌سنجند؛
 *   ۴) سرِ گزارش‌شده عقب‌تر از سرور (`rollback`) یا همان‌جا با هشِ دیگر (`fork`)؛ دورهٔ کهنه‌تر از
 *      آخرین دوره هم `rollback` است؛ آغازِ دورهٔ تازه `restarted`؛ دفترِ محلیِ شکسته `local_broken`.
 * هر کدام **رویدادِ امنیتی** می‌شود (`audit_logs`، کارِ `security.doc_chain`؛ با `hashchain.audit`
 * روشن، در زنجیرهٔ سابقهٔ همان کاربر هم) — یک بار برای هر (مشکل، حلقه)، نه هر دور.
 *
 * ⛔ هرگز فروش را نمی‌بندد: این مسیر جدا از push است، هیچ op ای را رد نمی‌کند و شکستش فقط
 *    زنجیره را می‌ایستاند. حلقه‌ای که «جا افتاده» (`seq` جلوتر از سر) مشکل نیست: پاسخ می‌گوید سرور
 *    تا کجا دارد و دستگاه از همان‌جا دوباره می‌فرستد (مثلاً پس از بازگشتِ سرور از پشتیبان).
 * ⛔ پشتِ پرچمِ خاموشِ `shop.doc_chain` (live_config، دکان یا `all`): خاموش ⇒ `{enabled:false}` و
 *    هیچ نوشتنی. دستگاه هم فقط با پاسخِ روشن زنجیره می‌سازد.
 * ⛔ بدنه دادهٔ شخصی ندارد (فقط شناسهٔ مشتری/کالا، مقدار و مبلغ) و این‌جا فقط سنجیده می‌شود.
 */
const db = require('../db');
const hc = require('./hash-chain');
const audit = require('./audit');
const { badRequest } = require('../middleware/errors');

const FLAG = 'shop.doc_chain';
const ACTION = 'security.doc_chain';
const SERIES_RE = /^[A-Z][1-9][0-9]{0,3}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const MAX_HEADS = 4;
const MAX_LINKS = 200;
const MAX_BODY = 1024 * 1024;
const MAX_EPOCH = 999999999;
/** دستگاه دورهٔ تازه را «آخرینِ سرور + ۱» می‌گیرد (و از فایلِ دوره‌ای که هرگز نرسید رد می‌شود)؛ بیش از این جهش ⇐ `bad_epoch` */
const MAX_EPOCH_STEP = 16;
const BROKEN = new Set(['local_broken', 'truncated']);

/** متنِ فارسیِ هر مشکل — پنل و سابقه همین را نشان می‌دهند */
const PROBLEMS = Object.freeze({
  series_owner: 'این سری مالِ دستگاهِ دیگری است',
  fork: 'حلقه‌ای که سرور پیش‌تر گرفته بود عوض شده (تاریخچه بازنویسی شده)',
  rollback: 'زنجیرهٔ دستگاه از آنچه سرور ثبت کرده عقب‌تر است',
  prev_mismatch: 'حلقهٔ تازه به سرِ ثبت‌شده پیوند نمی‌خورد',
  not_canonical: 'متنِ سند دست‌کاری شده (قالب)',
  body_mismatch: 'زنجیره، شماره یا نوعِ درونِ سند با حلقه نمی‌خواند',
  hash_mismatch: 'هش با متنِ سند نمی‌خواند',
  local_broken: 'دستگاه می‌گوید دفترِ محلیِ زنجیره شکسته است',
  truncated: 'دستگاه می‌گوید فایلِ زنجیره کوتاه شده است',
  restarted: 'دورهٔ تازه برای همان سری آغاز شد',
  bad_epoch: 'شمارهٔ دوره نامعقول است (رویداد نیست؛ دستگاه دورهٔ درست را از نو می‌گیرد)',
});

const chainOf = (shopId, series, epoch) => `doc/${shopId}/${series}/${epoch}`;

/** پرچمِ دکان — همان قاعدهٔ `orders.flagOf` (مقدارِ دکان بر `all` جلو؛ فقط `true` روشن) */
const enabled = (shopId) => require('./orders').flagOf(shopId, FLAG);

const isInt = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;

/** گزارشِ دستگاه ⇐ شکلِ سنجیده؛ نادرست ⇐ ۴۰۰ (خطای برنامه، نه رویدادِ امنیتی) */
function parseReport(input) {
  const b = input && typeof input === 'object' ? input : {};
  if (b.format !== hc.FORMAT) throw badRequest(`قالبِ زنجیره باید ${hc.FORMAT} باشد`);
  const heads = Array.isArray(b.heads) ? b.heads : [];
  const links = Array.isArray(b.links) ? b.links : [];
  if (heads.length > MAX_HEADS) throw badRequest(`حداکثر ${MAX_HEADS} سر در هر گزارش`);
  if (links.length > MAX_LINKS) throw badRequest(`حداکثر ${MAX_LINKS} حلقه در هر گزارش`);
  const head = (h) => {
    if (!h || typeof h !== 'object' || !SERIES_RE.test(String(h.series || '')) || !isInt(h.epoch, 1, MAX_EPOCH)
        || !isInt(h.seq, 0, Number.MAX_SAFE_INTEGER) || !HEX64.test(String(h.hash || ''))) {
      throw badRequest('سرِ زنجیره نادرست است');
    }
    const broken = h.broken == null ? '' : String(h.broken).slice(0, 40);
    return { series: h.series, epoch: h.epoch, seq: h.seq, hash: h.hash, broken };
  };
  const link = (l) => {
    if (!l || typeof l !== 'object' || !SERIES_RE.test(String(l.series || '')) || !isInt(l.epoch, 1, MAX_EPOCH)
        || !isInt(l.seq, 1, Number.MAX_SAFE_INTEGER) || !HEX64.test(String(l.prevHash || ''))
        || !HEX64.test(String(l.hash || '')) || typeof l.body !== 'string' || !l.body || l.body.length > MAX_BODY) {
      throw badRequest('حلقهٔ زنجیره نادرست است');
    }
    return { series: l.series, epoch: l.epoch, seq: l.seq, prevHash: l.prevHash, hash: l.hash, body: l.body };
  };
  const out = { heads: heads.map(head), links: links.map(link) };
  const keys = new Set(out.heads.map((h) => `${h.series}/${h.epoch}`));
  if (keys.size !== out.heads.length) throw badRequest('هر سری و دوره فقط یک سر');
  for (const l of out.links) if (!keys.has(`${l.series}/${l.epoch}`)) throw badRequest('حلقهٔ بی سر');
  return out;
}

/**
 * بدنهٔ حلقهٔ تازه ⇐ null یا کدِ مشکل. خالص؛ همان قاعدهٔ `DocChain.saleDoc` و `verifyChain` ِ
 * `hash-chain-store`: canonical، `kind = doc`، `chain` و `seq` همین حلقه، و هش از نو.
 */
function bodyProblem(chain, link) {
  let doc;
  let canon;
  try {
    doc = JSON.parse(link.body);
    canon = hc.canonicalJson(doc);
  } catch {
    return 'not_canonical';
  }
  if (canon !== link.body) return 'not_canonical';
  if (!doc || typeof doc !== 'object' || Array.isArray(doc) || doc.kind !== 'doc'
      || doc.chain !== chain || doc.seq !== link.seq || typeof doc.type !== 'string') return 'body_mismatch';
  if (hc.sha256Hex(link.prevHash + link.body) !== link.hash) return 'hash_mismatch';
  return null;
}

async function seriesOf(shopId, deviceId) {
  const r = await db.one('SELECT series FROM shop_doc_series WHERE shop_id=$1 AND device_id=$2', [shopId, deviceId]);
  return r ? r.series : '';
}

async function lastLink(chain) {
  const r = await db.one('SELECT seq, hash FROM hash_chain WHERE chain=$1 ORDER BY seq DESC LIMIT 1', [chain]);
  return r ? { seq: Number(r.seq), hash: r.hash } : { seq: 0, hash: hc.GENESIS };
}

async function hashesAt(chain, seqs) {
  if (!seqs.length) return new Map();
  const rows = await db.many('SELECT seq, hash FROM hash_chain WHERE chain=$1 AND seq = ANY($2)', [chain, seqs]);
  return new Map(rows.map((r) => [Number(r.seq), r.hash]));
}

/**
 * حلقه‌های یک (سری، دوره) پسِ سرِ سرور ⇐ { server, problem, problemSeq }.
 * فقط حلقه‌های پیوسته می‌نشینند؛ نخستین مشکل می‌ایستاند.
 */
async function ingest(chain, links) {
  let server = await lastLink(chain);
  const known = links.filter((l) => l.seq <= server.seq);
  const stored = await hashesAt(chain, known.map((l) => l.seq));
  for (const l of known) {
    if (stored.get(l.seq) !== l.hash) return { server, problem: 'fork', problemSeq: l.seq };
  }
  for (const l of links) {
    if (l.seq <= server.seq) continue;
    if (l.seq > server.seq + 1) break; // جا افتاده — دستگاه از تأییدِ همین پاسخ دوباره می‌فرستد
    if (l.prevHash !== server.hash) return { server, problem: 'prev_mismatch', problemSeq: l.seq };
    const p = bodyProblem(chain, l);
    if (p) return { server, problem: p, problemSeq: l.seq };
    let wrote = false;
    try {
      const r = await db.query(
        `INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at)
         VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (chain, seq) DO NOTHING RETURNING seq`,
        [chain, l.seq, l.prevHash, l.hash, l.body, db.now()]
      );
      wrote = r.rows.length > 0;
    } catch {
      //  تریگرِ پیوند (۰۷۰): گزارشِ هم‌زمانِ دیگری سر را جابه‌جا کرد — پایین از نو سنجیده می‌شود
    }
    if (!wrote) {
      const have = (await hashesAt(chain, [l.seq])).get(l.seq);
      if (have !== l.hash) return { server: await lastLink(chain), problem: have ? 'fork' : 'prev_mismatch', problemSeq: l.seq };
    }
    server = { seq: l.seq, hash: l.hash };
  }
  return { server, problem: '', problemSeq: 0 };
}

/** ردیفِ (سری، دوره) — اگر نیست ساخته می‌شود ⇐ { row, created } */
async function headRow(shopId, series, epoch, deviceId, at) {
  const made = await db.one(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at)
     VALUES ($1,$2,$3,$4,$5,$5) ON CONFLICT (shop_id, series, epoch) DO NOTHING RETURNING *`,
    [shopId, series, epoch, deviceId, at]
  );
  if (made) return { row: made, created: true };
  return {
    row: await db.one('SELECT * FROM device_chain_heads WHERE shop_id=$1 AND series=$2 AND epoch=$3', [shopId, series, epoch]),
    created: false,
  };
}

async function lastEpochOf(shopId, series) {
  const r = await db.one('SELECT MAX(epoch) AS e FROM device_chain_heads WHERE shop_id=$1 AND series=$2', [shopId, series]);
  return Number(r && r.e) || 0;
}

/** رویدادِ امنیتی — یک بار برای هر (مشکل، حلقه)؛ شکستِ ثبت هیچ چیزی را نمی‌خواباند */
async function flag(ctx, deviceId, ip, { chain, series, epoch, problem, seq }) {
  try {
    await audit.log({
      shopId: ctx.accountId, actorType: 'user', userId: ctx.userId || '', ip,
      action: ACTION, targetType: 'doc_chain', targetId: chain,
      detail: { problem, series, epoch, seq, deviceId, text: PROBLEMS[problem] || problem },
    });
  } catch (err) {
    console.error('[doc-chain] رویداد ثبت نشد:', err.message);
  }
}

/**
 * گزارشِ یک دستگاه. `ctx` همان `req.sync` (فقط دکان). هیچ‌گاه برای مشکلِ زنجیره خطا نمی‌دهد —
 * مشکل در `problems` برمی‌گردد و رویداد می‌شود؛ فقط ورودیِ بدشکل ۴۰۰ است.
 */
async function report(ctx, deviceId, input, { ip = '' } = {}) {
  const shopId = ctx.accountId;
  if (!(await enabled(shopId))) return { enabled: false };
  const { heads, links } = parseReport(input);
  const mine = await seriesOf(shopId, deviceId);
  const acks = [];
  const problems = [];
  for (const head of heads) {
    const { series, epoch } = head;
    const chain = chainOf(shopId, series, epoch);
    const at = db.now();
    let problem = '';
    let problemSeq = 0;
    let server = { seq: 0, hash: hc.GENESIS };

    if (series !== mine) {
      problem = 'series_owner';
    } else {
      const last = await lastEpochOf(shopId, series);
      if (epoch < last) {
        problem = 'rollback';
        problemSeq = head.seq;
      } else if (epoch > last + MAX_EPOCH_STEP) {
        //  بی ردیف و بی رویداد: ساختنِ دوره‌های بی‌شمار سابقه و جدول را پر می‌کرد
        problems.push({ series, epoch, problem: 'bad_epoch', seq: 0 });
        continue;
      } else {
        const { row, created } = await headRow(shopId, series, epoch, deviceId, at);
        if (created && epoch > 1 && last > 0) {
          await flag(ctx, deviceId, ip, { chain, series, epoch, problem: 'restarted', seq: 0 });
        }
        if (row.device_id !== deviceId) {
          problem = 'series_owner';
        } else {
          const mineLinks = links.filter((l) => l.series === series && l.epoch === epoch).sort((a, b) => a.seq - b.seq);
          const got = await ingest(chain, mineLinks);
          server = got.server;
          problem = got.problem;
          problemSeq = got.problemSeq;
          if (!problem && head.seq < server.seq) { problem = 'rollback'; problemSeq = head.seq; }
          if (!problem && head.seq === server.seq && head.hash !== server.hash) { problem = 'fork'; problemSeq = head.seq; }
          if (!problem && head.broken) { problem = BROKEN.has(head.broken) ? head.broken : 'local_broken'; problemSeq = head.seq; }
          const isNew = problem && (row.problem !== problem || Number(row.problem_seq) !== problemSeq);
          await db.query(
            `UPDATE device_chain_heads SET reported_seq=$4, reported_hash=$5, reported_at=$6,
                    problem = CASE WHEN $7 = '' THEN problem ELSE $7 END,
                    problem_seq = CASE WHEN $7 = '' THEN problem_seq ELSE $8 END,
                    problem_at = CASE WHEN $7 = '' THEN problem_at ELSE $6 END
              WHERE shop_id=$1 AND series=$2 AND epoch=$3`,
            [shopId, series, epoch, head.seq, head.hash, at, problem, problemSeq]
          );
          if (isNew) await flag(ctx, deviceId, ip, { chain, series, epoch, problem, seq: problemSeq });
          acks.push({ series, epoch, seq: server.seq, hash: server.hash });
          if (problem) problems.push({ series, epoch, problem, seq: problemSeq });
          continue;
        }
      }
    }
    //  سریِ دیگری یا دورهٔ کهنه: هیچ حلقه‌ای نمی‌نشیند؛ رویداد یک بار در هر (مشکل، حلقه)
    problems.push({ series, epoch, problem, seq: problemSeq });
    const key = `${ACTION}:${chain}:${problem}:${problemSeq}`;
    if (!seenRecently(key)) await flag(ctx, deviceId, ip, { chain, series, epoch, problem, seq: problemSeq });
  }
  return { enabled: true, acks, problems };
}

/*
 *  سریِ دیگری یا دورهٔ کهنه ردیفی برای «یک بار» ندارد (ساختنِ ردیف برای سریِ دیگران یعنی
 *  نوشتن به نامِ دستگاهی که نیست) — پس تکرارِ همان گزارش در همین فرآیند یک بار ثبت می‌شود.
 *  سقف دارد تا حافظه پر نشود.
 */
const RECENT = new Map();
const RECENT_MS = 24 * 60 * 60 * 1000;
function seenRecently(key, at = Date.now()) {
  const t = RECENT.get(key);
  if (t && at - t < RECENT_MS) return true;
  if (RECENT.size >= 5000) RECENT.clear();
  RECENT.set(key, at);
  return false;
}

/** پرسشِ دستگاه: پرچم، و برای سریِ خودش آخرین دوره و سرِ هر دوره (برای آغازِ دورهٔ تازه) */
async function status(ctx, deviceId) {
  const shopId = ctx.accountId;
  if (!(await enabled(shopId))) return { enabled: false };
  const series = await seriesOf(shopId, deviceId);
  if (!series) return { enabled: true, format: hc.FORMAT, series: [] };
  const rows = await db.many(
    'SELECT epoch, device_id FROM device_chain_heads WHERE shop_id=$1 AND series=$2 ORDER BY epoch', [shopId, series]
  );
  const epochs = [];
  for (const r of rows) {
    const s = await lastLink(chainOf(shopId, series, Number(r.epoch)));
    epochs.push({ epoch: Number(r.epoch), seq: s.seq, hash: s.hash, mine: r.device_id === deviceId });
  }
  return {
    enabled: true,
    format: hc.FORMAT,
    series: [{ series, lastEpoch: epochs.length ? epochs[epochs.length - 1].epoch : 0, epochs }],
  };
}

module.exports = {
  FLAG, ACTION, PROBLEMS, MAX_LINKS, MAX_HEADS, chainOf, parseReport, bodyProblem, report, status,
};
