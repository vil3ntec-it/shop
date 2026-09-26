'use strict';
/**
 * حالِ زندهٔ هر پمپ — «برنامه به سرور بگوید، سرور به بات دستور بدهد».
 *
 * ── گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۴، با سه عکس) ─────────────────────
 * «دو سه تا هشدار استن اما سرور و برنامه بی‌توجهی می‌کنن… بات دنبالِ
 *  چیزی نمی‌گرده توی سرور یا برنامه… بزار برنامه به سرور و بات بگه و
 *  سرور به بات دستور بده و بات هم لایو آپدیت توی گروه بره، درجا.»
 *
 * ── چه چیزی نبود ───────────────────────────────────────────────────
 * برنامهٔ کامپیوتر فقط هشدارِ **تازه** را می‌فرستاد (`/device/events`) و
 * «تازه» را از حافظهٔ خودش حساب می‌کرد. دو زیان داشت:
 *
 *   ۱) سرور هیچ تصویری از «همین حالا چه باز است» نداشت؛ «وضعیت»ِ بات
 *      فقط تاریخچه بود و برای پمپی که هشدارش پیش از وصل شدنِ بات آمده
 *      بود می‌گفت «هنوز هیچ هشداری نیامده است».
 *   ۲) با هر بار بسته و باز شدنِ برنامه حافظه خالی می‌شد و همان هشدارها
 *      دوباره «تازه» می‌رفتند — همان «حرف‌های تکراری».
 *
 * ── حالا ───────────────────────────────────────────────────────────
 *
 *   برنامه ──POST /api/pump/device/state {alerts, tank, debtors?}──▶ این‌جا
 *        سرور مقایسه می‌کند با آن‌چه داشت:
 *          بازِ تازه  ⇒ station_events ⇒ پوش + تلگرام (همان `events.notify`)
 *          بسته‌شده   ⇒ «✅ برطرف شد» در تلگرام
 *          بی‌تغییر   ⇒ هیچ پیامی — بستن و باز کردنِ برنامه هیچ زنگی نمی‌زند
 *
 * ⛔ **هیچ قاعده‌ای این‌جا ساخته نمی‌شود.** کدام حساب «تمام شد» است و کدام
 * «کم مانده» را برنامه از `StationSnapshot.Alerts` می‌گوید — همان چیزی که
 * رنگِ کارتِ قرض‌دار از آن می‌آید. این فایل فقط مقایسه و نگه‌داری می‌کند.
 *
 * ⛔ **هیچ‌وقت پشتِ اشتراک نمی‌رود** — همان قاعدهٔ خبرها.
 */
const { one, query, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const MAX_ALERTS = 300;
const MAX_DEBTORS = 30000; // ≈ ۳ مگابایت؛ پمپی با این‌همه قرض‌دار هم کامل جست‌وجو می‌شود

function str(v, max) {
  return String(v == null ? '' : v).trim().slice(0, max);
}

function num(v) {
  const n = Number(v);
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : 0;
}

/** نوعِ خبر از روی کلید — همان `CloudEvents.KindOf`ِ برنامهٔ پمپ. */
function kindOf(key, state) {
  if (key.startsWith('tank-')) return state === 'out' ? 'stock_out' : 'low_stock';
  return 'debt';
}

/** یک هشدار از برنامه ⇒ شکلِ تمیز. کلیدِ بدشکل بی‌صدا رد می‌شود. */
function cleanAlert(a) {
  const k = str(a?.k, 80);
  if (!/^[A-Za-z0-9:_.-]{2,80}$/.test(k)) return null;
  const s = a?.s === 'out' ? 'out' : 'low';
  return {
    k,
    kind: kindOf(k, s),
    s,
    t: str(a?.t, 200),
    //  دستورِ کار — «به او دیگر تیل ندهید»، «امروز دیزل سفارش بدهید». از خودِ برنامه.
    a: str(a?.a, 200),
    n: str(a?.n, 80),
    f: str(a?.f, 20),
  };
}

const MAX_OWE = 200;

/** بدهیِ پمپ به هر شرکت — همان الباقی‌ای که برنامه حساب کرده. */
function cleanOwe(list) {
  if (!Array.isArray(list)) return [];
  const out = [];
  for (const o of list.slice(0, MAX_OWE)) {
    const n = str(o?.n, 80);
    const afn = num(o?.afn);
    const usd = num(o?.usd);
    if (!n || (afn <= 0 && usd <= 0)) continue;
    out.push({ n, afn, usd });
  }
  return out;
}

/**
 * کلیدِ یک هشدار بی حالش — «d7-stP-w70» و «d7-stP-over» یک حساب‌اند.
 * ⚠️ همان فهرستِ حال‌های `StationSnapshot.Alerts`ِ برنامهٔ پمپ.
 */
function subjectOf(key) {
  return String(key || '').replace(/-(out|low|w70|w90|over)$/, '');
}

function cleanTank(raw) {
  const out = {};
  if (!raw || typeof raw !== 'object') return out;
  for (const key of ['petrol', 'diesel']) {
    const t = raw[key];
    if (!t || typeof t !== 'object') continue;
    out[key] = {
      show: num(t.show),
      threshold: num(t.threshold),
      low: t.low === true,
      near: t.near === true,
    };
  }
  return out;
}

/** خلاصهٔ هر قرض‌دار: نام، حالِ سه دفتر و الباقی — برای جست‌وجوی بات. */
function cleanDebtors(list) {
  if (!Array.isArray(list)) return null;
  const st = (x) => (['out', 'low', 'ok'].includes(x) ? x : 'none');
  const out = [];
  for (const d of list.slice(0, MAX_DEBTORS)) {
    const n = str(d?.n, 80);
    if (!n) continue;
    out.push({ n, sp: st(d?.sp), sd: st(d?.sd), sm: st(d?.sm), p: num(d?.p), d: num(d?.d), m: num(d?.m) });
  }
  return out;
}

async function get(stationId) {
  const r = await one('SELECT * FROM station_live_state WHERE station_id=$1', [stationId]);
  if (!r) return null;
  return {
    alerts: Array.isArray(r.alerts) ? r.alerts : [],
    tank: r.tank && typeof r.tank === 'object' ? r.tank : {},
    debtors: Array.isArray(r.debtors) ? r.debtors : null,
    debtorsAt: r.debtors_at ? Number(r.debtors_at) : 0,
    owe: Array.isArray(r.owe) ? r.owe : [],
    updatedAt: Number(r.updated_at),
  };
}

/*
 *  ⚠️ دو فرستادنِ هم‌زمانِ یک پمپ (دو کامپیوتر، یا تلاشِ دوباره) نباید
 *  هر دو «بازِ تازه» ببینند و دو زنگ بزنند. پس نوشتن‌های یک پمپ پشتِ سرِ
 *  هم‌اند — همان الگوی گروهِ کارکنانِ سرورِ خانگی.
 */
const chains = new Map();
function serial(stationId, fn) {
  const prev = chains.get(stationId) || Promise.resolve();
  const next = prev.catch(() => {}).then(fn);
  chains.set(stationId, next);
  next.finally(() => { if (chains.get(stationId) === next) chains.delete(stationId); }).catch(() => {});
  return next;
}

/**
 * حالِ تازهٔ یک پمپ را می‌نشاند و فرقش با قبل را خبر می‌دهد.
 *
 * @param ctx {stationId, deviceUid, who}
 * @param body {alerts, tank, debtors?} — `debtors` نیامد ⇒ قبلی می‌ماند
 */
async function publish(ctx, body) {
  const stationId = ctx?.stationId;
  if (!stationId) throw badRequest('پمپ معلوم نیست', 'no_station');
  if (!Array.isArray(body?.alerts)) throw badRequest('فهرستِ هشدارها نیامد', 'bad_state');
  if (body.alerts.length > MAX_ALERTS) {
    throw badRequest(`حداکثر ${MAX_ALERTS} هشدار`, 'too_many_alerts');
  }

  return serial(stationId, async () => {
    const t = now();
    const seen = new Set();
    const alerts = [];
    for (const raw of body.alerts) {
      const a = cleanAlert(raw);
      if (!a || seen.has(a.k)) continue;
      seen.add(a.k);
      alerts.push(a);
    }

    const prev = await get(stationId);
    const before = new Map((prev?.alerts || []).map(a => [a.k, a]));

    const opened = [];
    for (const a of alerts) {
      const old = before.get(a.k);
      a.since = old ? Number(old.since) || t : t;
      if (!old) opened.push(a);
    }
    const now_ = new Set(alerts.map(a => a.k));
    const closed = [...before.values()].filter(a => !now_.has(a.k));

    const tank = body.tank === undefined && prev ? prev.tank : cleanTank(body.tank);
    const debtors = body.debtors === undefined ? undefined : cleanDebtors(body.debtors);
    const owe = body.owe === undefined ? (prev?.owe || []) : cleanOwe(body.owe);

    await query(
      `INSERT INTO station_live_state (station_id, alerts, tank, debtors, debtors_at, updated_at, owe)
       VALUES ($1, $2::jsonb, $3::jsonb, $4::jsonb, $5, $6, $8::jsonb)
       ON CONFLICT (station_id) DO UPDATE SET
         alerts = excluded.alerts,
         tank = excluded.tank,
         owe = excluded.owe,
         debtors = CASE WHEN $7 THEN excluded.debtors ELSE station_live_state.debtors END,
         debtors_at = CASE WHEN $7 THEN excluded.debtors_at ELSE station_live_state.debtors_at END,
         updated_at = excluded.updated_at`,
      [stationId, JSON.stringify(alerts), JSON.stringify(tank || {}),
        debtors === undefined ? null : JSON.stringify(debtors),
        debtors === undefined ? null : t, t, debtors !== undefined, JSON.stringify(owe)]
    );

    //  ⚠️ خبرِ تازه از همان درِ همیشگی می‌رود — پوش و تلگرام هر دو از
    //  `events.notify`. `clientId` کلید و زمانِ بازشدن را دارد، پس همان
    //  هشدار در همان دوره هرگز دو ردیف نمی‌شود.
    if (opened.length) {
      const events = require('./events').pump;
      //  ⚠️ دفترِ خبر در هر درخواست حداکثر ۵۰ خبر می‌پذیرد؛ «بی سقف» یعنی دسته‌دسته، نه بریدن
      const batch = events.MAX_BATCH || 50;
      for (let i = 0; i < opened.length; i += batch) await events.record({
        tenantId: stationId, userId: '', userName: ctx.who || '', deviceUid: ctx.deviceUid || '',
      }, opened.slice(i, i + batch).map(a => ({
        kind: a.kind,
        title: a.t,
        clientId: `ls:${a.k}:${a.since}`,
        data: { key: a.k, who: a.n, fuel: a.f, state: a.s, action: a.a || '' },
      })));
    }
    //  ⚠️ «تمام شد ⇒ کم مانده» برطرف شدن نیست: همان حساب هنوز هشدار دارد
    //  (کلیدش فقط حالش را عوض کرده). برطرف یعنی آن حساب دیگر هیچ هشداری ندارد.
    const subject = subjectOf;
    const stillOpen = new Set(alerts.map(a => subject(a.k)));
    const resolved = closed.filter(a => !stillOpen.has(subject(a.k)));
    if (resolved.length) {
      await require('./telegram').notifyResolved(stationId, resolved);
    }
    return { opened: opened.length, closed: closed.length, resolved: resolved.length, open: alerts.length };
  });
}

module.exports = { publish, get, cleanAlert, cleanDebtors, cleanOwe, subjectOf, kindOf, MAX_ALERTS, MAX_DEBTORS, MAX_OWE };
