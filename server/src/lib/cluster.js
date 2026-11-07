'use strict';
/**
 * چند نمونهٔ سرور پشتِ یک load balancer روی **یک** PostgreSQL (`CLUSTER_ENABLED=1`؛ docs/SCALING-fa.md).
 *
 * پیش‌فرض خاموش ⇒ همان سرورِ تک‌نمونهٔ امروز: `publish` هیچ کاری نمی‌کند، `runsJobs()` همیشه true است و
 * `jobs()` همهٔ کارگرها را همان لحظه راه می‌اندازد — بیت‌به‌بیت رفتارِ پیشین. روی PGlite روشن نمی‌شود
 * (یک فرآیند، یک پایگاهِ درون‌فرآیندی؛ `config.validate()` رد می‌کند).
 *
 * روشن ⇒ دو چیز، هر دو با `pg` ِ خودِ سرور و بی وابستگیِ تازه:
 *
 *   ۱) **گذرگاه (LISTEN/NOTIFY)** — `publish(topic, data)` خبری کوچک (≤ ۷٬۹۰۰ بایت) به نمونه‌های دیگر
 *      می‌دهد و `subscribe(topic, fn)` آن را در هر نمونه می‌گیرد (خبرِ خودِ نمونه به خودش برنمی‌گردد؛
 *      فرستنده کارِ محلی را خودش کرده است). فقط «بیدارباش» است، نه داده: گیرنده همیشه از پایگاه
 *      می‌خواند. گم شدنِ خبر (قطعِ اتصالِ شنونده) با `resync` جبران می‌شود: پس از هر وصلِ دوباره همهٔ
 *      مشترک‌ها یک «همه را دوباره بسنج» می‌گیرند (سوکت‌ها pull می‌کنند، long-poll ها بیدار می‌شوند،
 *      حافظهٔ نسخه‌ها پاک می‌شود).
 *
 *   ۲) **رهبر برای کارهای پس‌زمینه** — قفلِ نشستیِ `pg_try_advisory_lock` روی اتصالِ جدای همین ماژول.
 *      فقط نمونه‌ای که قفل را دارد کارگرها (صفِ ایمیل، اعلان‌ها، زمان‌سنجِ سفارش، گزارشِ صاحب، تطبیقِ
 *      پرداخت، پشتیبان، خانه‌تکانی، باتِ پمپ…) را اجرا می‌کند. افتادنِ رهبر (یا قطعِ اتصالش) ⇒ پایگاه قفل
 *      را رها می‌کند و نمونهٔ دیگر حداکثر پس از `CLUSTER_LEADER_RETRY_MS` رهبر می‌شود. خودِ کارگرها هم
 *      هر ردیف را با «ادعا»ی اتمی برمی‌دارند (`UPDATE … WHERE status … RETURNING`، `ON CONFLICT DO NOTHING`،
 *      قفلِ advisory ِ هر کار) ⇒ هم‌پوشانیِ کوتاهِ دو رهبر در جابه‌جایی کارِ دوباره نمی‌سازد.
 *
 *   ۳) **نبضِ نمونه** (مهاجرتِ ۲۲۷، `cluster_instances`) — هر `CLUSTER_HEARTBEAT_MS` یک ردیف: آخرین تپش، «رهبرم؟» و
 *      برشِ `job-beat` ِ همین نمونه ⇒ پنلِ مرکزی ۴.۹ همهٔ نمونه‌ها، رهبر و کارگرهای رهبر را از هر نمونه‌ای می‌بیند
 *      (`ops-overview.clusterView`). خطای نوشتنِ تپش فقط لاگ می‌شود و هیچ چیزِ دیگری را نمی‌خواباند.
 *
 * ⛔ هیچ رازی، توکنی یا دادهٔ مشتری در خبر نمی‌رود — فقط شناسه‌ها (حساب، گفت‌وگو) و شمارنده‌ها.
 */
const os = require('node:os');
const crypto = require('node:crypto');
const config = require('../config');
const db = require('../db');

const CHANNEL = 'shop_cluster';
/** کلیدِ دوبخشیِ قفلِ رهبر: «shop» و ۱ — با `hashtext` ِ کارهای دیگر برخورد ندارد (آن‌ها کلیدِ تک‌بخشی‌اند) */
const LEADER_LOCK = [0x73686f70, 1];
const MAX_PAYLOAD = 7900;
/** ردیفِ نمونه‌ای که این‌قدر تپش نداشته (مرده، بی خاموشیِ تمیز) را رهبر برمی‌دارد */
const INSTANCE_PRUNE_MS = 10 * 60_000;
const startedAt = Date.now();

const instanceId = config.cluster.instanceId
  || `${os.hostname().slice(0, 32)}-${process.pid}-${crypto.randomBytes(3).toString('hex')}`;

const handlers = new Map();   //  topic ⇒ Set<fn(data, from)>
const resyncs = new Set();    //  fn() پس از وصلِ دوبارهٔ شنونده
const jobList = [];           //  [{ name, start, stop, on }]
const state = {
  started: false, stopping: false, listening: false, leader: false, leaderSince: 0,
  published: 0, received: 0, dropped: 0, reconnects: 0, elections: 0, beats: 0, beatFails: 0,
};
let listenClient = null;
let leaderClient = null;
let listenTimer = null;
let leaderTimer = null;
let beatTimer = null;
let listenBeat = null;
let rowTimer = null;
let rowWriting = null;
let rowAgain = false;

function enabled() { return Boolean(config.cluster.enabled) && !db.isPglite(); }

/** این نمونه کارگرهای پس‌زمینه را اجرا می‌کند؟ تک‌نمونه ⇒ همیشه. */
function runsJobs() { return !enabled() || state.leader; }

function subscribe(topic, fn) {
  let set = handlers.get(topic);
  if (!set) { set = new Set(); handlers.set(topic, set); }
  set.add(fn);
  return () => set.delete(fn);
}
function onResync(fn) { resyncs.add(fn); return () => resyncs.delete(fn); }

/**
 * خبر به نمونه‌های دیگر. تک‌نمونه ⇒ هیچ (false). خطا فقط لاگ می‌شود: گیرنده‌ها با `resync` و
 * خواندنِ دوره‌ای ِ خودشان (pull ِ ۳۰ثانیه‌ای، long-poll ِ مهلت‌دار) درست می‌مانند.
 */
function publish(topic, data = {}) {
  if (!enabled() || !state.started) return false;
  const text = JSON.stringify({ i: instanceId, t: String(topic), d: data });
  if (Buffer.byteLength(text) > MAX_PAYLOAD) {
    state.dropped++;
    console.error(`[cluster] خبرِ «${topic}» بزرگ‌تر از ${MAX_PAYLOAD} بایت بود و فرستاده نشد`);
    return false;
  }
  state.published++;
  db.query('SELECT pg_notify($1, $2)', [CHANNEL, text])
    .catch((err) => { state.dropped++; console.error(`[cluster] خبرِ «${topic}» نرفت:`, err.message); });
  return true;
}

function dispatch(msg) {
  let m;
  try { m = JSON.parse(msg.payload); } catch { return; }
  if (!m || m.i === instanceId) return;
  state.received++;
  const set = handlers.get(m.t);
  if (!set) return;
  for (const fn of set) {
    try { fn(m.d || {}, m.i); } catch (err) { console.error(`[cluster] گیرندهٔ «${m.t}»:`, err.message); }
  }
}

function newClient() {
  const { Client } = require('pg');
  const c = new Client({
    connectionString: config.cluster.dbUrl || config.db.url,
    ssl: config.db.ssl ? { rejectUnauthorized: false } : undefined,
    keepAlive: true,
    connectionTimeoutMillis: config.db.connectTimeoutMs,
    //  تپشِ رهبر نباید پشتِ شبکهٔ مرده آویزان بماند ⇒ رهبری زود رها می‌شود
    query_timeout: Math.max(1000, config.cluster.heartbeatMs * 2),
    application_name: `shop-cluster:${instanceId}`.slice(0, 63),
  });
  return c;
}

// ── گذرگاه ──────────────────────────────────────────────────────────────────
async function listen() {
  if (state.stopping) return;
  const c = newClient();
  const lost = () => {
    if (listenClient !== c) return;
    listenClient = null;
    state.listening = false;
    clearInterval(listenBeat);
    listenBeat = null;
    c.end().catch(() => {});
    if (!state.stopping) {
      listenTimer = setTimeout(() => { state.reconnects++; listen(); }, config.cluster.leaderRetryMs);
      if (listenTimer.unref) listenTimer.unref();
    }
  };
  c.on('error', (err) => { console.error('[cluster] شنونده:', err.message); lost(); });
  c.on('end', lost);
  c.on('notification', dispatch);
  try {
    listenClient = c;
    await c.connect();
    await c.query(`LISTEN ${CHANNEL}`);
    const wasListening = state.reconnects > 0;
    state.listening = true;
    //  اتصالِ بیکارِ شنونده بی صدا هم می‌میرد (keepalive ِ سیستم ساعت‌هاست) ⇒ تپشِ خودش؛ نرسید ⇒ وصلِ دوباره + resync
    listenBeat = setInterval(() => { c.query('SELECT 1').catch((err) => { if (listenClient === c) c.emit('error', err); }); }, config.cluster.heartbeatMs);
    if (listenBeat.unref) listenBeat.unref();
    //  خبرهای میانِ قطع و وصل گم شده‌اند ⇒ همه دوباره بسنجند (نخستین وصل هیچ چیزی برای جبران ندارد)
    if (wasListening) for (const fn of resyncs) { try { fn(); } catch (err) { console.error('[cluster] resync:', err.message); } }
  } catch (err) {
    console.error('[cluster] شنونده وصل نشد:', err.message);
    lost();
  }
}

// ── رهبر ────────────────────────────────────────────────────────────────────
function startJobs() {
  for (const j of jobList) {
    if (j.on) continue;
    try { j.state = j.start(); j.on = true; } catch (err) { console.error(`[cluster] کارگرِ ${j.name}:`, err.message); }
  }
  if (enabled() && state.started) writeBeat();
}

// ── نبضِ نمونه (`cluster_instances`) ──────────────────────────────────────────
/** ردیفِ همین نمونه، تازه؛ هم‌زمان فقط یکی. رهبر ردیف‌های مرده را هم برمی‌دارد. */
function writeBeat() {
  //  در حینِ نوشتن (مثلاً رهبری همین حالا عوض شد) ⇒ یک بارِ دیگر پس از آن، با حالتِ تازه
  if (rowWriting) { rowAgain = true; return rowWriting; }
  const t = Date.now();
  let version = '';
  try { version = String(require('../../package.json').version || ''); } catch { /* بی‌اهمیت */ }
  rowWriting = db.query(
    `INSERT INTO cluster_instances (instance_id, started_at, beat_at, leader, leader_since, version, jobs)
     VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb)
     ON CONFLICT (instance_id) DO UPDATE SET beat_at = EXCLUDED.beat_at, leader = EXCLUDED.leader,
       leader_since = EXCLUDED.leader_since, version = EXCLUDED.version, jobs = EXCLUDED.jobs`,
    [instanceId, startedAt, t, state.leader, state.leaderSince || null, version.slice(0, 40),
      JSON.stringify(require('./job-beat').snapshot(t))]
  ).then(() => (state.leader ? db.query('DELETE FROM cluster_instances WHERE beat_at < $1', [t - INSTANCE_PRUNE_MS]) : null))
    .then(() => { state.beats++; })
    .catch((err) => { state.beatFails++; console.error('[cluster] تپشِ نمونه نوشته نشد:', err.message); })
    .finally(() => {
      rowWriting = null;
      if (rowAgain && !state.stopping) { rowAgain = false; writeBeat(); }
    });
  return rowWriting;
}
function stopJobs() {
  for (const j of jobList) {
    if (!j.on) continue;
    j.on = false;
    try { if (j.stop) j.stop(j.state); } catch (err) { console.error(`[cluster] توقفِ ${j.name}:`, err.message); }
  }
}
function stepDown(why) {
  if (state.leader) {
    console.log(`[cluster] ${instanceId} دیگر رهبر نیست (${why})`);
    state.leader = false;
    state.leaderSince = 0;
    stopJobs();
    if (enabled() && state.started && !state.stopping) writeBeat();
  }
}
async function elect() {
  leaderTimer = null;
  if (state.stopping) return;
  const c = leaderClient || newClient();
  if (!leaderClient) {
    leaderClient = c;
    const lost = (err) => {
      if (leaderClient !== c) return;
      leaderClient = null;
      if (err) console.error('[cluster] اتصالِ رهبری:', err.message);
      stepDown('اتصالِ قفل افتاد');
      c.end().catch(() => {});
      clearInterval(beatTimer);
      beatTimer = null;
      if (!state.stopping && !leaderTimer) {
        leaderTimer = setTimeout(elect, config.cluster.leaderRetryMs);
        if (leaderTimer.unref) leaderTimer.unref();
      }
    };
    c.on('error', lost);
    c.on('end', () => lost());
    try { await c.connect(); } catch (err) { lost(err); return; }
  }
  try {
    state.elections++;
    const r = await c.query('SELECT pg_try_advisory_lock($1::int, $2::int) AS ok', LEADER_LOCK);
    if (r.rows[0] && r.rows[0].ok) {
      state.leader = true;
      state.leaderSince = Date.now();
      console.log(`[cluster] ${instanceId} رهبر شد — کارگرهای پس‌زمینه این‌جا اجرا می‌شوند`);
      startJobs();
      //  تپش: اتصالِ قفل زنده است؟ نه ⇒ کنار (`lost` بالا)
      beatTimer = setInterval(() => {
        c.query('SELECT 1').catch((err) => { if (leaderClient === c) { c.emit('error', err); } });
      }, config.cluster.heartbeatMs);
      if (beatTimer.unref) beatTimer.unref();
      return;
    }
  } catch (err) {
    c.emit('error', err);
    return;
  }
  if (!state.stopping) {
    leaderTimer = setTimeout(elect, config.cluster.leaderRetryMs);
    if (leaderTimer.unref) leaderTimer.unref();
  }
}

/**
 * کارگرهای پس‌زمینه: `[name, start, stop]` — `start()` هر چه برگرداند به `stop(آن)` می‌رسد.
 * تک‌نمونه ⇒ همه همین حالا (همان ترتیب)؛ خوشه ⇒ فقط وقتی همین نمونه رهبر است.
 */
function jobs(list) {
  for (const [name, start, stop] of list) jobList.push({ name, start, stop, on: false, state: undefined });
  if (!enabled() || state.leader) startJobs();
}

async function start() {
  if (!enabled() || state.started) return false;
  state.started = true;
  state.stopping = false;
  await listen();
  await elect();
  await writeBeat();
  rowTimer = setInterval(writeBeat, config.cluster.heartbeatMs);
  if (rowTimer.unref) rowTimer.unref();
  return true;
}

async function stop() {
  state.stopping = true;
  clearTimeout(listenTimer);
  clearTimeout(leaderTimer);
  clearInterval(beatTimer);
  clearInterval(listenBeat);
  clearInterval(rowTimer);
  beatTimer = null;
  listenBeat = null;
  rowTimer = null;
  stepDown('خاموش شدن');
  //  خاموشیِ تمیز ⇒ پنل این نمونه را «رفته» می‌بیند، نه «کهنه»
  if (state.started) {
    await rowWriting;
    await db.query('DELETE FROM cluster_instances WHERE instance_id = $1', [instanceId])
      .catch((err) => console.error('[cluster] ردیفِ نمونه پاک نشد:', err.message));
  }
  const cs = [listenClient, leaderClient].filter(Boolean);
  listenClient = null;
  leaderClient = null;
  state.listening = false;
  state.started = false;
  await Promise.all(cs.map((c) => c.end().catch(() => {})));
}

function stats() {
  return { enabled: enabled(), instanceId, ...state, jobs: jobList.filter((j) => j.on).map((j) => j.name) };
}

module.exports = {
  enabled, runsJobs, subscribe, onResync, publish, jobs, stopJobs, start, stop, stats, writeBeat, instanceId, CHANNEL, LEADER_LOCK,
};
