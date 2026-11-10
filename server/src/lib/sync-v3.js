'use strict';
/**
 * VILL3N Sync v3 — سمتِ سرور، فازِ ۱ (بخشِ ۳.۸؛ SYN-01..08، 10، 11، 13).
 *
 * ── یک نگاه ─────────────────────────────────────────────────────────
 *   push      opهای v3 (یا صفِ v2ِ همان برنامه با `format: "v2"`)، یک تراکنش؛
 *             opهای یک `tx` (سند + حرکتِ کالا + پرداخت) همه یا هیچ (SYN-02)
 *   pull      اثرِ opهای دستگاه‌های دیگر، با سقفِ بایت (۶۴ تا ۲۵۶KB) و ادامه
 *             از cursor (SYN-04)
 *   snapshot  ردیف‌های زنده + cursor، برای دستگاهِ تازه
 *   status    نقطهٔ وضعیت: منتظر، آخرین همگام‌سازیِ موفق، آخرین خطا (SYN-08)
 *   conflicts صندوقِ تعارض: دیدن، نگه داشتن، برگرداندن (SYN-07)
 *
 * ── قاعده‌هایی که این‌جا زنده‌اند ──────────────────────────────────
 *   • **فقط `app=shop`.** v1 و پمپ دست نمی‌خورند؛ از `sync-v1.js` فقط
 *     `canonical` و (در مبدل) `validateOp` خوانده می‌شود.
 *   • **حساب از توکن** (`ctx` را `sync-v1-auth.js` می‌سازد) و هر پرس‌وجو
 *     `app + account_kind + account_id` را شرط می‌کند.
 *   • **op_id یکتاست** ⇒ تکرار «تکراری» است، نه دو بار.
 *   • **داوری با HLC است، نه ترتیبِ رسیدن** ⇒ opی که دیر برسد ولی قدیمی‌تر
 *     باشد نمی‌برد، و نتیجه به ترتیبِ رسیدن بستگی ندارد.
 *   • **ساعتِ کج** (بیش از ۵ دقیقه جلوتر از سرور): ثبت می‌شود، ساعتِ سرور را
 *     جلو نمی‌برد و در «آخرین نوشتن» نمی‌برد ⇒ صندوقِ تعارض.
 *   • **سیاستِ هر جدول** در `sync-v3-schema.js` است.
 */
//  جداشده‌ها (رفتار همان): سنجش و ادغامِ خالص `sync-v3-merge.js`، SQL ِ اعمال `sync-v3-store.js`،
//  اعمالِ یک op و ساعت `sync-v3-apply.js`، درونِ پلِ v1 ⇄ v3 `sync-v3-bridge.js`؛ راهِ دسته‌ای `sync-v3-batch.js`.
const { one, many, tx, now, query } = require('../db');
const { ApiError, badRequest, notFound, conflict: conflictError, forbidden } = require('../middleware/errors');
const { can } = require('./permissions');
const v1migrations = require('./sync-v1-migrations');
const hlc = require('./hlc'), schema = require('./sync-v3-schema'), convert = require('./sync-v3-convert');
const toV1 = require('./sync-v3-to-v1');
const approvals = require('./staff-approval');
const store = require('./sync-v3-store');
const batch = require('./sync-v3-batch');
const { validate, merge, normPhone, isPlainObject } = require('./sync-v3-merge');
const { SERVER_DEVICE, clock, ready, resetClock, applyOne, applyServerOp } = require('./sync-v3-apply');
const { BRIDGE_BATCH, importV1, openToV1, drainToV1 } = require('./sync-v3-bridge');

const PROTOCOL = 3;
const MAX_OPS = 500;
const MAX_BATCH_BYTES = 256 * 1024;
const PULL_MIN_BYTES = 64 * 1024;
const PULL_MAX_BYTES = 256 * 1024;
const PULL_MAX_OPS = 2000;
//  برگرداندنِ مقدارِ بازنده همان اختیارِ «حذف و اصلاحِ دادهٔ دیگران» است (مالک و مدیر)
const RESOLVE_PERMISSION = 'data.delete.any';
const REVERTIBLE = new Set(['lww', 'clock_skew', 'stale_after_delete', 'stale_delete']);
//  وظیفه‌های بررسی؛ سه‌تای آخر opِ پولیِ ردشده‌اند (STF-02/03، `staff-approval.js`)
const TASKS = new Set(['negative_stock', 'duplicate_barcode', 'duplicate_customer',
  'approval_required', 'approval_rejected', 'perm_denied']);

const { ACC, acc, touchDevice, headSeq } = store;

const lockKey = (ctx) => `sync-v3:${ctx.app}:${ctx.accountKind}:${ctx.accountId}`;
function lock(c, ctx) {
  return c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [lockKey(ctx)]);
}
//  تراکنش زیرِ قفلِ حساب: اول نوبت در صفِ فرآیند، بی اتصال ([heavy]: جای push هم؛ `sync-v3-gate.js`)، بعد همان قفلِ advisory
const lockedTx = (ctx, fn, heavy = false) => require('./sync-v3-gate').run(lockKey(ctx), heavy, (o) => tx((c) => lock(c, ctx).then(() => fn(c)), o));

function requireShop(ctx) {
  if (!ctx || ctx.app !== 'shop' || ctx.accountKind !== 'shop') {
    throw forbidden('همگام‌سازیِ v3 فقط برای دکان است', 'v3_shop_only');
  }
}

const dbq = { query: (s, p) => query(s, p) };

function intOrNull(v, min, max) {
  if (v === undefined || v === null || v === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n)) return null;
  return Math.min(max, Math.max(min, Math.trunc(n)));
}

function rawId(raw) {
  return isPlainObject(raw) ? String(raw.op_id ?? raw.opId ?? '').slice(0, 80) : '';
}

function errorText(v) {
  if (v === undefined || v === null) return null;
  return String(v).replace(/[\u0000-\u001f\u007f]/g, ' ').slice(0, 300);
}

// ---------- Push ----------

/**
 * @param body {device_id, protocol?, format?: 'v3'|'v2', schema_version?, ops, queued?, last_error?, clock_offset_ms?}
 * @throws 426 اگر برنامه از سرور جلوتر باشد — و هیچ چیزی اعمال نمی‌شود (opها روی برنامه می‌مانند)
 */
async function push(ctx, { deviceId, protocol = PROTOCOL, format = 'v3', schemaVersion = 0, ops, appVersion = '',
  queued = null, lastError, clockOffsetMs = null, hideContact = false }) {
  requireShop(ctx);
  if (deviceId === SERVER_DEVICE) throw badRequest('این شناسهٔ دستگاه رزرو است', 'bad_device');
  const proto = Number(protocol) || PROTOCOL;
  if (proto > PROTOCOL) {
    throw new ApiError(426, 'UPGRADE_REQUIRED', `سرور پروتکلِ ${PROTOCOL} را می‌شناسد و برنامه ${proto} می‌فرستد؛ سرور باید به‌روز شود`);
  }
  if (!Array.isArray(ops)) throw badRequest('فهرستِ opها درست نیست', 'bad_ops');
  if (ops.length > MAX_OPS) throw badRequest(`حداکثر ${MAX_OPS} op در هر دسته`, 'batch_too_large');
  if (Buffer.byteLength(JSON.stringify(ops), 'utf8') > MAX_BATCH_BYTES) {
    throw badRequest('دسته بزرگ‌تر از ۲۵۶ کیلوبایت است', 'batch_too_large');
  }

  //  ورودی ⇒ فهرستِ واحدهای اعمال: هر واحد چند op که همه یا هیچ
  const results = new Array(ops.length);
  const units = [];                    //  [{tx, items: [{i, op}]}]
  const byTx = new Map();
  const origin = format === 'v2' ? 'v2' : 'v3';
  if (origin === 'v2') {
    const sv = Number(schemaVersion);
    const cur = v1migrations.currentVersion('shop', ctx);
    if (!Number.isInteger(sv) || sv < 1) throw badRequest('schema_version لازم است', 'bad_schema_version');
    if (sv > cur) {
      throw new ApiError(426, 'UPGRADE_REQUIRED', `سرور schema نسخهٔ ${cur} را می‌شناسد و برنامه نسخهٔ ${sv} می‌فرستد`);
    }
  }
  ops.forEach((raw, i) => {
    let list;
    if (origin === 'v2') {
      const conv = convert.fromV2(raw, { schemaVersion, deviceId, index: i });
      if (!conv.ok) { results[i] = { op_id: conv.op_id, status: 'rejected', reason: conv.reason }; return; }
      if (!conv.ops.length) {
        //  فقط مانده یا نشانِ محلی داشت — چیزی برای دفتر نیست
        results[i] = { op_id: rawId(raw), status: 'applied', converted: [], dropped: conv.dropped };
        return;
      }
      list = conv.ops.map((o) => validate(o, 'v2', ctx));
      const bad = list.find(v => !v.ok);
      if (bad) { results[i] = { op_id: rawId(raw), status: 'rejected', reason: bad.reason }; return; }
      units.push({ tx: '', items: [{ i, ops: list.map(v => v.op), v2: true, dropped: conv.dropped }] });
      return;
    }
    const v = validate(raw, 'v3', ctx);
    if (!v.ok) {
      results[i] = { op_id: v.op_id, status: 'rejected', reason: v.reason };
      //  یک op ِ نادرست کلِ tx را رد می‌کند
      const t = isPlainObject(raw) && raw.tx ? String(raw.tx) : '';
      if (t) {
        const u = byTx.get(t) || { tx: t, items: [], broken: true };
        u.broken = true;
        if (!byTx.has(t)) { byTx.set(t, u); units.push(u); }
      }
      return;
    }
    if (v.op.tx) {
      let u = byTx.get(v.op.tx);
      if (!u) { u = { tx: v.op.tx, items: [] }; byTx.set(v.op.tx, u); units.push(u); }
      u.items.push({ i, ops: [v.op] });
    } else {
      units.push({ tx: '', items: [{ i, ops: [v.op] }] });
    }
  });

  //  سندهای برگشتِ این دسته (یک برگه و یک تصمیم؛ سقفِ تجمعیِ فاکتور)، و برنامهٔ کهنهٔ بی tx:
  //  هر سندِ پولی با همراه‌هایش یک واحد (همه یا هیچ)
  const groups = approvals.returnGroupsOf(units.flatMap((u) => u.items.flatMap((it) => it.ops)));
  const plan = approvals.bundleV3(units, groups, { credit: await require('./credit-cap').onUnits(ctx, units), price: await require('./price-cap').onUnits(ctx, units) });   //  STF-03: فاکتورِ قرض‌دار هم
  //  همراهِ بی tx ِ سندی که در دستهٔ پیشینِ همین دستگاه رد شد ⇒ همان رد
  await approvals.holdV3Units(ctx, deviceId, plan);

  //  پلِ v3 ⇒ v1 (پرچمِ دکان): بیشترِ عقب‌ماندگیِ v1 ⇒ v3 بیرونِ تراکنشِ push
  const toV1On = await toV1.enabled(ctx);
  if (toV1On) await catchUp(ctx, 3, true);
  await ready();
  const t = now();

  const out = await lockedTx(ctx, async (c) => {
    //  روشن: قفلِ v1 هم، و v1 ⇒ v3 کامل پیش از اعمال ⇒ داوریِ این opها همهٔ v1 را دیده است
    const v1 = toV1On ? await openToV1(c, ctx, t) : null;
    //  سقفِ تجمعیِ برگشت و سقفِ قلم (`return-line-cap`): از خواندنِ جمع تا نوشتن در همین تراکنش (با پایانش آزاد).
    //  ⛔ پس از قفلِ v1: push ِ v1 همین قفل را پس از قفلِ خودش می‌گیرد — ترتیبِ یکی، بی بن‌بست
    if (groups) await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [approvals.returnsLockKey(ctx.accountId)]);
    const dev = (await c.query(
      `SELECT cursor FROM sync_v3_devices WHERE ${ACC} AND device_id=$4`, [...acc(ctx), deviceId]
    )).rows[0];
    const cursor = dev ? Number(dev.cursor) : 0;
    let applied = 0;
    let sp = 0;

    //  دنبالهٔ tx ای که سرش در دستهٔ پیشینِ همین دستگاه با سقف/تأیید رد شد (tx ِ بزرگ‌تر از یک
    //  دسته) ⇒ همان رد — فقط صندوقِ باز، تا یک روز، و تا وقتی سرش ننشسته
    const txs = ctx.role === 'owner' ? [] : plan.filter((u) => u.tx && !u.broken).map((u) => u.tx);
    if (txs.length) {
      const held = await approvals.heldTxs(c, ctx, deviceId, txs);
      for (const u of plan) {
        const heads = u.tx ? held.get(u.tx) : null;
        if (!heads) continue;
        //  همان tx دوباره (پاسخ گم شد) ⇒ دوباره داوری می‌شود، نه «دنباله»
        const again = u.items.some((it) => it.ops.some((o) => heads.has(o.op_id)));
        if (!again) u.broken = true;
      }
    }

    //  پرچمِ `SYNC_V3_BATCH` (پیش‌فرض خاموش): همان واحدها و همان داوری با رفت‌وبرگشتِ کمتر (`sync-v3-batch.js`)
    const s = batch.on() ? await batch.open(c, ctx, plan) : store.direct(c);
    for (const unit of plan) {
      if (unit.broken) {
        for (const it of unit.items) results[it.i] = { op_id: it.ops[0].op_id, status: 'rejected', reason: 'tx_rejected' };
        continue;
      }
      const name = `u${++sp}`;
      await s.begin(name);
      const local = [];
      let failed = null;
      for (const it of unit.items) {
        const parts = [];
        for (const op of it.ops) {
          const r = await applyOne(s, ctx, op, { deviceId, origin, cursor, t, groups, plan, hideContact });
          parts.push(r);
          if (r.status === 'rejected') { failed = r; break; }
        }
        local.push({ it, parts });
        if (failed) break;
      }
      if (failed) {
        await s.undo(name);
        //  opِ پولیِ ردشده هرگز بی‌صدا نمی‌رود: صندوقِ تعارض برای صاحب دکان
        if (failed.box) {
          const b = failed.box;
          //  DATA-SAFETY: سند و همهٔ همراه‌هایش کامل (`conflict-payload`) — قلم‌ها فقط روی دستگاه نمی‌مانند
          await s.conflict(ctx, { table: b.table, row_id: b.row_id, loser_op_id: b.op_id, loser_device: deviceId,
            loser_value: require('./conflict-payload').payload('v3', unit.items.flatMap((it) => it.ops)) },
            b.kind, t, { dedupe: `perm:${b.op_id}`, detail: {
              ...b.detail,
              //  نشانی‌های سند برای همراهِ دستهٔ بعد (v3 ِ بی tx، `holdV3Units`) و دنبالهٔ همان tx
              ...approvals.holdInfoV3(unit.items.flatMap((it) => it.ops).find((o) => o.op_id === b.op_id) || {}, groups),
              ...(unit.tx ? { tx: unit.tx } : {}),
            } });
        }
        for (const it of unit.items) {
          const mine = local.find(l => l.it === it);
          const culprit = mine && mine.parts.find(p => p.status === 'rejected');
          const opId = it.v2 ? rawId(ops[it.i]) : it.ops[0].op_id;
          results[it.i] = { op_id: opId, status: 'rejected', reason: culprit ? culprit.reason : 'tx_rejected' };
        }
        continue;
      }
      await s.commit(name);
      for (const { it, parts } of local) {
        if (!it.v2) {
          results[it.i] = parts[0];
        } else {
          //  یک op v2 ⇒ یک پاسخ، با جزئیاتِ opهای v3ی که از آن ساخته شد
          const statuses = parts.map(p => p.status);
          const status = statuses.every(s => s === 'duplicate') ? 'duplicate'
            : statuses.every(s => s === 'applied' || s === 'duplicate') ? 'applied'
              : statuses.every(s => s === 'conflict') ? 'conflict' : 'partial';
          results[it.i] = { op_id: rawId(ops[it.i]), status, converted: parts };
          if (it.dropped.length) results[it.i].dropped = it.dropped;
        }
        for (const p of parts) if (p.status === 'applied' || p.status === 'partial') applied++;
      }
    }

    await s.flush();
    //  همان تراکنش: اثرِ همین opها در `oplog`/`sync_rows`ِ v1 (گوشی‌های v1 در pullِ بعدی می‌بینند)
    if (v1 && v1.ready) await drainToV1(c, ctx, t);

    await touchDevice(c, ctx, deviceId, {
      lastPushAt: t, lastOkAt: t, queued: intOrNull(queued, 0, 10_000_000), appVersion,
      lastError: errorText(lastError), clockOffsetMs: intOrNull(clockOffsetMs, -1e12, 1e12),
    });
    return { results, applied, head: await headSeq(c, ctx) };
  }, true);
  return { ...out, protocol: PROTOCOL, server_time: now(), hlc: clock.peek() };
}

// ---------- پلِ v1 ⇒ v3 ----------


async function bridge(ctx, { max = BRIDGE_BATCH, toV1On } = {}) {
  requireShop(ctx);
  const out = toV1On === undefined ? await toV1.enabled(ctx) : toV1On;
  //  پیش‌سنجِ بی قفل: بیشترِ وقت‌ها چیزی تازه نیست و قفلِ حساب لازم نیست
  const fresh = await one(
    `SELECT 1 AS x FROM oplog WHERE ${ACC} AND server_seq > COALESCE(
       (SELECT v1_seq FROM sync_v3_bridge WHERE ${ACC}), 0) LIMIT 1`, acc(ctx)
  );
  if (!fresh && !(out && await toV1.pending(dbq, ctx))) return { imported: 0, behind: false };
  await ready();
  const t = now();
  return lockedTx(ctx, async (c) => {
    if (!out) return importV1(c, ctx, { max, t });
    const o = await openToV1(c, ctx, t, { rounds: 1, max });
    if (!o.ready) return { imported: o.imported, behind: true };
    return { imported: o.imported, behind: false, to_v1: await drainToV1(c, ctx, t) };
  });
}

async function catchUp(ctx, rounds = 3, toV1On) {
  let r = { behind: false };
  if (toV1On === undefined) toV1On = await toV1.enabled(ctx);
  for (let i = 0; i < rounds; i++) {
    r = await bridge(ctx, { toV1On });
    if (!r.behind) break;
  }
  return r;
}

// ---------- Pull ----------

async function pull(ctx, { deviceId, since = 0, maxBytes = PULL_MIN_BYTES, limit = PULL_MAX_OPS }) {
  requireShop(ctx);
  const bridged = await catchUp(ctx);
  const from = Math.max(0, Number(since) || 0);
  const budget = Math.min(Math.max(Number(maxBytes) || PULL_MIN_BYTES, PULL_MIN_BYTES), PULL_MAX_BYTES);
  const cap = Math.min(Math.max(1, Number(limit) || PULL_MAX_OPS), PULL_MAX_OPS);
  //  سرِ دفتر **پیش از** خواندن (همان درسِ v1): opی که وسطِ خواندن برسد دفعهٔ بعد می‌آید
  const head = await headSeq(dbq, ctx);
  const rows = await many(
    `SELECT op_id, server_seq, device_id, origin, hlc, table_name, row_id, kind, effect
       FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND server_seq <= $5 AND device_id <> $6
        AND effect IS NOT NULL
      ORDER BY server_seq ASC LIMIT $7`,
    [...acc(ctx), from, head, deviceId, cap + 1]
  );
  const page = [];
  let bytes = 0;
  let hasMore = false;
  for (const r of rows) {
    if (page.length >= cap) { hasMore = true; break; }
    const item = {
      op_id: r.op_id, seq: Number(r.server_seq), device_id: r.device_id, origin: r.origin, hlc: r.hlc,
      table: r.table_name, row_id: r.row_id, kind: r.kind, effect: r.effect,
    };
    const size = Buffer.byteLength(JSON.stringify(item), 'utf8');
    if (page.length && bytes + size > budget) { hasMore = true; break; }
    page.push(item);
    bytes += size;
  }
  const cursor = hasMore ? page[page.length - 1].seq : Math.max(head, from);
  const t = now();
  await touchDevice(dbq, ctx, deviceId, { cursor, lastPullAt: t, lastOkAt: t });
  return {
    ops: page, cursor, has_more: hasMore || !!bridged.behind, head, bytes,
    protocol: PROTOCOL, server_time: now(), hlc: clock.peek(),
  };
}

// ---------- Snapshot ----------

async function snapshot(ctx) {
  requireShop(ctx);
  const bridged = await catchUp(ctx, 10);
  return tx(async (c) => {
    try { await c.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ'); } catch { /* بی این هم می‌خواند */ }
    const head = await headSeq(c, ctx);
    const rows = (await c.query(
      `SELECT table_name, row_id, data FROM sync_v3_rows WHERE ${ACC} AND deleted_at IS NULL AND inserted
        ORDER BY table_name, row_id`, acc(ctx)
    )).rows;
    const tables = {};
    for (const r of rows) (tables[r.table_name] ||= []).push({ id: r.row_id, data: r.data });
    return {
      protocol: PROTOCOL, cursor: head, rows: rows.length, tables,
      complete: !bridged.behind, server_time: now(), hlc: clock.peek(),
    };
  });
}

// ---------- وضعیت (SYN-08، SYN-10) ----------

function shapeDevice(d, head) {
  return {
    device_id: d.device_id, cursor: Number(d.cursor),
    behind: Math.max(0, head - Number(d.cursor)),
    last_push_at: d.last_push_at === null ? null : Number(d.last_push_at),
    last_pull_at: d.last_pull_at === null ? null : Number(d.last_pull_at),
    last_ok_at: d.last_ok_at === null ? null : Number(d.last_ok_at),
    last_error: d.last_error || '',
    last_error_at: d.last_error_at === null ? null : Number(d.last_error_at),
    queued: Number(d.queued),
    //  SYN-10: تا صفر نشده، خروج و حذفِ دستگاه فقط با خروجیِ اجباری
    unsynced: Number(d.queued) > 0,
    clock_offset_ms: Number(d.clock_offset_ms),
    app_version: d.app_version || '',
    last_seen_at: Number(d.last_seen_at),
  };
}

async function status(ctx, deviceId = '') {
  requireShop(ctx);
  const toV1On = await toV1.enabled(ctx);
  await bridge(ctx, { toV1On });
  const head = await headSeq(dbq, ctx);
  const devs = await many(`SELECT * FROM sync_v3_devices WHERE ${ACC} ORDER BY last_seen_at DESC LIMIT 200`, acc(ctx));
  const mine = devs.find(d => d.device_id === deviceId) || null;
  const cursor = mine ? Number(mine.cursor) : 0;
  const counts = await one(
    `SELECT
       (SELECT COUNT(*)::int FROM sync_v3_ops WHERE ${ACC} AND server_seq > $4 AND device_id <> $5
          AND effect IS NOT NULL) AS pending,
       (SELECT COUNT(*)::int FROM sync_v3_conflicts WHERE ${ACC} AND status='open') AS open,
       (SELECT COALESCE(MAX(server_seq),0)::bigint FROM oplog WHERE ${ACC}) AS v1_head,
       (SELECT COALESCE(MAX(v1_seq),0)::bigint FROM sync_v3_bridge WHERE ${ACC}) AS v1_seq`,
    [...acc(ctx), cursor, deviceId]
  );
  const seeAll = can(ctx.role, 'members.view');
  return {
    protocol: PROTOCOL, head, server_time: now(), hlc: clock.peek(), skew_limit_ms: hlc.SKEW_MS,
    device: mine ? { ...shapeDevice(mine, head), pending: counts.pending } : { device_id: deviceId, pending: counts.pending },
    conflicts_open: counts.open,
    bridge: {
      v1_head: Number(counts.v1_head), v1_seq: Number(counts.v1_seq), caught_up: Number(counts.v1_seq) >= Number(counts.v1_head),
      //  پلِ v3 ⇒ v1 (پرچمِ دکانیِ `shop.sync_v3_to_v1`)
      to_v1: toV1On ? { enabled: true, caught_up: !(await toV1.pending(dbq, ctx)) } : { enabled: false },
    },
    devices: (seeAll ? devs : devs.filter(d => d.device_id === deviceId)).map(d => shapeDevice(d, head)),
  };
}

/** گزارشِ برنامه: تعدادِ منتظر، خطای آخر، اختلافِ ساعت — بی op. */
async function report(ctx, deviceId, { queued, lastError, clockOffsetMs, appVersion = '' } = {}) {
  requireShop(ctx);
  await touchDevice(dbq, ctx, deviceId, {
    queued: intOrNull(queued, 0, 10_000_000), lastError: errorText(lastError),
    clockOffsetMs: intOrNull(clockOffsetMs, -1e12, 1e12), appVersion,
  });
  return { server_time: now(), hlc: clock.peek() };
}

// ---------- صندوقِ تعارض (SYN-07) ----------

function shapeConflict(r) {
  return {
    id: r.id, kind: r.kind, task: TASKS.has(r.kind), revertible: REVERTIBLE.has(r.kind) && r.status === 'open',
    table: r.table_name, row_id: r.row_id, field: r.field,
    winner_value: r.winner_value, loser_value: r.loser_value,
    winner_hlc: r.winner_hlc, loser_hlc: r.loser_hlc,
    winner_op_id: r.winner_op_id, loser_op_id: r.loser_op_id,
    winner_device: r.winner_device, loser_device: r.loser_device,
    detail: r.detail || {}, status: r.status, at: Number(r.at),
    resolved_at: r.resolved_at === null ? null : Number(r.resolved_at),
    resolved_by: r.resolved_by || '', resolution_op_id: r.resolution_op_id || '',
  };
}

async function listConflicts(ctx, { status: st = 'open', limit = 100 } = {}) {
  requireShop(ctx);
  const cap = Math.min(Math.max(1, Number(limit) || 100), 500);
  const all = st === 'all';
  const rows = await many(
    `SELECT * FROM sync_v3_conflicts WHERE ${ACC} AND ($4::boolean OR status='open')
      ORDER BY at DESC, id DESC LIMIT $5`,
    [...acc(ctx), all, cap]
  );
  return rows.map(shapeConflict);
}

/** opِ برگرداندن: مقدارِ بازنده با HLCِ تازهٔ سرور، که از همه بزرگ‌تر است. */
function revertOp(cf, stamp) {
  const base = { op_id: `srv.rv.${cf.id}`.slice(0, 80), table: cf.table_name, row_id: cf.row_id, hlc: stamp };
  if (cf.field === '*delete*' || cf.kind === 'stale_delete') return { ...base, kind: 'delete' };
  if (cf.field === '*') return { ...base, kind: 'update', fields: cf.loser_value || {} };
  return { ...base, kind: 'update', fields: { [cf.field]: cf.loser_value } };
}

async function resolveConflict(ctx, id, action, { userId = '' } = {}) {
  requireShop(ctx);
  if (!['keep', 'revert', 'dismiss'].includes(action)) throw badRequest('کار باید keep یا revert باشد', 'bad_action');
  if (!can(ctx.role, RESOLVE_PERMISSION)) throw forbidden('فقط مالک یا مدیر تعارض را می‌بندد', 'resolve_not_allowed');
  await ready();
  const t = now();
  const toV1On = await toV1.enabled(ctx);
  return lockedTx(ctx, async (c) => {
    const v1 = toV1On ? await openToV1(c, ctx, t) : null;
    const cf = (await c.query(
      `SELECT * FROM sync_v3_conflicts WHERE ${ACC} AND id=$4 FOR UPDATE`, [...acc(ctx), String(id || '')]
    )).rows[0];
    if (!cf) throw notFound('این مورد پیدا نشد', 'conflict_not_found');
    if (cf.status !== 'open') throw conflictError('این مورد پیش‌تر بسته شده است', 'already_resolved');

    let op = null;
    let final = TASKS.has(cf.kind) || action === 'dismiss' ? 'dismissed' : 'kept';
    if (action === 'revert') {
      if (!REVERTIBLE.has(cf.kind)) throw badRequest('برای این مورد برگرداندن معنا ندارد', 'not_revertible');
      op = await applyServerOp(store.direct(c), ctx, revertOp(cf, clock.tick()), t);
      final = 'reverted';
    }
    const upd = (await c.query(
      `UPDATE sync_v3_conflicts SET status=$2, resolved_at=$3, resolved_by=$4, resolution_op_id=$5
        WHERE id=$1 RETURNING *`,
      [cf.id, final, t, String(userId || ctx.userId || ''), op ? op.op_id : '']
    )).rows[0];
    if (v1 && v1.ready && op) await drainToV1(c, ctx, t);
    return { conflict: shapeConflict(upd), op };
  });
}

/** آنچه برنامه برای راه افتادن لازم دارد — `/hello` (جدولِ پشتِ پرچمِ خاموش نه، `safe-flag.js`). */
function describe(ctx = null) {
  return {
    protocol: PROTOCOL,
    accepts: { v3: true, v2_schema_max: v1migrations.currentVersion('shop', ctx) },
    skew_limit_ms: hlc.SKEW_MS,
    max_ops: MAX_OPS, max_batch_bytes: MAX_BATCH_BYTES,
    pull_bytes: { min: PULL_MIN_BYTES, max: PULL_MAX_BYTES },
    units: { money: 'Minor (1/100 AFN, integer)', qty: 'Milli (1/1000, integer)' },
    tables: schema.describe(ctx),
    server_time: now(), hlc: clock.peek(),
  };
}

function _resetClockForTests() { resetClock(); }

module.exports = {
  PROTOCOL, MAX_OPS, MAX_BATCH_BYTES, PULL_MIN_BYTES, PULL_MAX_BYTES, SERVER_DEVICE,
  validate, merge, normPhone, push, pull, snapshot, status, report, bridge,
  listConflicts, resolveConflict, describe, clock, _resetClockForTests,
};
