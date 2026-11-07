'use strict';
/**
 * نمای خوشه برای پنلِ مرکزی ۴.۹ (`GET /api/admin/ops/health`، `ops-overview.health`) — همهٔ نمونه‌ها، رهبر و
 * کارگرهای رهبر، از **هر** نمونه‌ای که درخواست به آن رسید (مهاجرتِ ۲۲۷، `cluster.writeBeat`).
 *
 *   رهبر ──── دارندهٔ واقعیِ قفلِ advisory ِ رهبری در همین لحظه (`pg_locks` + `application_name` ِ اتصالِ قفل،
 *             `shop-cluster:<نمونه>`)؛ اگر پایگاه آن را نشان ندهد (نقشِ بی دسترسی به `pg_stat_activity`)، همان
 *             «رهبرم» ِ تازه‌ترین تپش. هر دو نبود ⇒ `leader: null` و هشدار (کارگرها جایی اجرا نمی‌شوند).
 *   نمونه ─── `ok` اگر تپشش کمتر از ۳ × `CLUSTER_HEARTBEAT_MS` + ۵ ثانیه پیش رسیده، وگرنه `stale` (مرده بی خاموشیِ
 *             تمیز، یا پایگاهش بریده)؛ رهبر ردیفِ ۱۰ دقیقه بی تپش را برمی‌دارد.
 *   کارها ─── `jobs` ِ بالای پاسخ در خوشه = برشِ `job-beat` ِ **رهبر** (وضعیت با همان `statusOf` و ساعتِ همین لحظه
 *             از نو)، با `instanceId`؛ کارِ شناخته‌ای که رهبر ندارد `unknown`. خودِ همین نمونه رهبر است ⇒ برشِ زندهٔ خودش.
 *
 * تک‌نمونه (پیش‌فرض) ⇒ `null` و پاسخِ سلامت بیت‌به‌بیت همان پیشین.
 */
const config = require('../config');
const { one, many } = require('../db');
const beat = require('./job-beat');

const staleAfter = () => 3 * config.cluster.heartbeatMs + 5000;

/** کارهای یک برش با وضعیتِ همین لحظه + کارهای شناختهٔ ناپیدا (`unknown`). */
function jobsOf(list, t, instanceId) {
  const seen = (Array.isArray(list) ? list : []).filter((j) => j && typeof j.name === 'string').map((j) => ({
    name: j.name, everyMs: Number(j.everyMs) || 0, registeredAt: j.registeredAt ?? null, lastRunAt: j.lastRunAt ?? null,
    lastOkAt: j.lastOkAt ?? null, lastFailAt: j.lastFailAt ?? null, lastError: String(j.lastError || '').slice(0, 80),
    runs: Number(j.runs) || 0, fails: Number(j.fails) || 0, running: Boolean(j.running),
    status: beat.statusOf({ everyMs: Number(j.everyMs) || 0, lastFailAt: j.lastFailAt, lastOkAt: j.lastOkAt, registeredAt: Number(j.registeredAt) || t }, t),
    instanceId,
  }));
  const names = new Set(seen.map((j) => j.name));
  const missing = beat.KNOWN.filter((n) => !names.has(n)).map((name) => ({ name, status: 'unknown', registeredAt: null, instanceId }));
  return [...seen, ...missing];
}

/** نامِ نمونهٔ دارندهٔ قفلِ رهبری (از پایگاه) — یا null. */
async function lockHolder(rows) {
  const cluster = require('./cluster');
  const [k1, k2] = cluster.LEADER_LOCK;
  const r = await one(
    `SELECT a.application_name AS app FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid
      WHERE l.locktype = 'advisory' AND l.granted AND l.classid = $1::bigint::oid AND l.objid = $2::bigint::oid AND l.objsubid = 2
      LIMIT 1`,
    [k1, k2]
  ).catch(() => null);
  const app = r && typeof r.app === 'string' ? r.app : '';
  if (!app.startsWith('shop-cluster:')) return null;
  const hit = rows.find((x) => `shop-cluster:${x.instance_id}`.slice(0, 63) === app);
  return hit ? hit.instance_id : app.slice('shop-cluster:'.length);
}

async function clusterView(t = Date.now()) {
  const cluster = require('./cluster');
  if (!cluster.enabled()) return null;
  const self = cluster.instanceId;
  const rows = await many(
    'SELECT instance_id, started_at, beat_at, leader, leader_since, version, jobs FROM cluster_instances ORDER BY instance_id LIMIT 200'
  );
  const fresh = (r) => t - Number(r.beat_at) <= staleAfter();
  const holder = await lockHolder(rows);
  const claimed = rows.filter((r) => r.leader && fresh(r)).sort((a, b) => Number(b.beat_at) - Number(a.beat_at))[0];
  const leaderId = holder || (claimed ? claimed.instance_id : null);
  const instances = rows.map((r) => ({
    instanceId: r.instance_id, self: r.instance_id === self, leader: r.instance_id === leaderId,
    startedAt: Number(r.started_at), beatAt: Number(r.beat_at), ageMs: Math.max(0, t - Number(r.beat_at)),
    status: fresh(r) ? 'ok' : 'stale', version: r.version,
    jobs: r.instance_id === self ? jobsOf(beat.snapshot(t), t, self) : jobsOf(r.jobs, t, r.instance_id),
  }));
  if (!instances.some((i) => i.self)) {
    //  همین نمونه هنوز تپشی ننوشته (تازه بالا آمده یا نوشتن شکست خورد): خودش را از حافظه نشان می‌دهد
    instances.push({ instanceId: self, self: true, leader: self === leaderId, startedAt: null, beatAt: null, ageMs: null,
      status: 'stale', version: '', jobs: jobsOf(beat.snapshot(t), t, self) });
  }
  const lead = instances.find((i) => i.leader) || null;
  const status = !lead ? 'warn' : instances.some((i) => i.status !== 'ok') ? 'warn' : 'ok';
  return {
    status,
    self,
    leader: lead ? { instanceId: lead.instanceId, since: Number(rows.find((r) => r.instance_id === lead.instanceId)?.leader_since) || null, source: holder ? 'lock' : 'beat' } : null,
    instances,
    //  کارهای پس‌زمینه فقط روی رهبر اجرا می‌شوند ⇒ «کارها» ِ پنل همان‌ها است
    jobs: lead ? lead.jobs : jobsOf([], t, null),
    limits: { staleAfterMs: staleAfter(), heartbeatMs: config.cluster.heartbeatMs },
  };
}

module.exports = { clusterView, jobsOf, staleAfter };
