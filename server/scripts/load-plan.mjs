/**
 * آزمونِ بار — کمکی‌های `load-test.mjs` برای اجرای بلندِ چندگامی (`--plan`، PERF-02؛ `docs/PERF-LOAD-fa.md` «۱۴۰۵/۰۸/۱۹ (سوم)»).
 *
 *   sessions     نشستِ دستگاه و صاحب پس از `ACCESS_TOKEN_TTL_MIN` (۶۰ دقیقه) ۴۰۱ می‌گیرد؛ برنامه‌ها (`authFetch`، `ApiClient`) روی ۴۰۱ یک بار
 *                نشست را تازه می‌کنند و همان درخواست را دوباره می‌زنند — بارساز هم (`hit` ِ `load-test.mjs`؛ زمانِ هر دو با هم ثبت می‌شود)
 *   planDefs     گام‌های `--plan` ⇒ تعریفِ نمایه‌ها با env/مهلت/sql ِ هر گام
 *   printExtras  سطرهای بارساز، «قطعِ سراسری» و دروازه در گزارشِ هر نمایه
 */

/** [baseOf]/[timeoutOf]: نشانیِ سرور و مهلتِ کلاینتِ همین لحظه (هر دو در طولِ اجرا عوض می‌شوند) */
export function sessions({ baseOf, timeoutOf }) {
  const byToken = new Map();
  let count = 0;
  function own(who, token, refreshToken) {
    if (who.token) byToken.delete(who.token);
    who.token = token;
    if (refreshToken) who.refreshToken = refreshToken;
    byToken.set(token, who);
  }
  const post = async (path, body) => {
    try {
      const res = await fetch(baseOf() + path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App': 'shop' },
        body: JSON.stringify(body), signal: AbortSignal.timeout(Math.max(timeoutOf(), 15000)) });
      return { status: res.status, json: await res.json().catch(() => null) };
    } catch { return { status: 0, json: null }; }
  };
  /** یک بار برای هر کس، هر چند درخواستش هم‌زمان ۴۰۱ گرفته باشند: اول refresh، وگرنه ورودِ دوباره با همان شناسهٔ دستگاه */
  function reauth(who) {
    if (!who.reauth) {
      who.reauth = (async () => {
        let r = who.refreshToken ? await post('/api/auth/refresh', { refreshToken: who.refreshToken }) : null;
        if (!r || r.status !== 200 || !r.json || !r.json.accessToken) {
          const acct = who.shop || who;
          r = await post('/api/auth/login', { identifier: acct.email, password: acct.password, app: 'shop',
            device: { deviceId: who.deviceId || who.id, name: 'آزمونِ بار', platform: 'test' } });
        }
        if (r.status === 200 && r.json && r.json.accessToken) { own(who, r.json.accessToken, r.json.refreshToken); count++; }
      })().finally(() => { who.reauth = null; });
    }
    return who.reauth;
  }
  return { own, reauth, whoOf: (token) => byToken.get(token), take: () => { const n = count; count = 0; return n; } };
}

/** همهٔ گام‌ها یک گروه (یک کاشتن)؛ سرورِ نخست با env ِ گامِ نخست. گامِ نادرست ⇒ خروجِ ۲ */
export function planDefs(plan, profileDefs, instances) {
  const steps = Array.isArray(plan) ? plan : plan && plan.steps;
  if (!Array.isArray(steps) || !steps.length || instances > 1) { console.error('--plan: فهرستِ گام‌ها لازم است (و نه با --instances)'); process.exit(2); }
  return steps.map((st) => {
    if (!profileDefs[st.profile]) { console.error(`--plan: نمایهٔ ناشناخته ${st.profile}`); process.exit(2); }
    const def = { name: st.label || st.profile, ...profileDefs[st.profile]() };
    return { ...def, step: { profile: st.profile, env: { ...(def.serverEnv || {}), ...(st.env || {}) }, timeoutMs: st.timeoutMs || null, sql: st.sql || [] } };
  });
}

export function printExtras(o) {
  const g = o.generator;
  if (g) console.log(`  بارساز: حلقه p99 ${g.loopP99Ms}ms (بیشینه ${g.loopMaxMs})، CPU ${g.cpuPct}%، درخواستِ باز ≤ ${g.inflightMax} (سقف ${g.maxInflight})، مهلتِ کلاینت ${g.timeoutMs}ms، نشستِ تازه ${g.reauths || 0}`);
  const b = o.burst;
  if (b) console.log(`  قطعِ سراسری: ${b.devices} دستگاه (${b.skippedBusy} سرگرم)، تا سر ${b.drained} (≤۱۰s ${b.within10s}، ≤۳۰s ${b.within30s}، ≤۶۰s ${b.within60s})، نرسیده ${b.notDrained}، دوباره ${b.retries}؛ تخلیه p50 ${b.p50Ms} p95 ${b.p95Ms} بیشینه ${b.maxMs}ms، آخرین دستگاه ${b.lastDrainedAfterMs}ms پس از آغاز`);
  for (const [k, x] of Object.entries(o.gate || {})) {
    if (!x.n) continue;
    console.log(`  دروازه/${k}: ${x.n} (پذیرفته ${x.admitted}، ۵۰۳ ${x.rejected}، اتصال تا سقف نرسید ${x.acquireFailed})؛ انتظارِ دروازه p50 ${x.wait.p50} p95 ${x.wait.p95} بیشینه ${x.wait.max}؛ گرفتنِ اتصال p95 ${x.acquire.p95} بیشینه ${x.acquire.max}؛ اجرا p95 ${x.exec.p95} بیشینه ${x.exec.max}؛ کل p95 ${x.total.p95} بیشینه ${x.total.max}ms؛ کل > ۱۰s ${x.over10s}، > ۱۵s ${x.over15s}`);
  }
}
