/**
 * پیوندِ «2G» ِ آزمون (S26، SYN-04) — یک پراکسیِ TCP ِ واقعی جلوی سرور برای **یک** دستگاه (یک رادیو، یک خط).
 *
 *   createLink({ host, port }, { rttMs: 600, upBps: 50000, downBps: 50000, loss: 0.02, dropRate: 0.02, speed: 1, seed })
 *
 * مدل (همه پارامتر؛ پیش‌فرض‌ها یک خطِ GPRS/EDGE ِ بد):
 *   • **پهنای باند** جدا برای بالا و پایین (bit/s)، مشترک میانِ همهٔ اتصال‌های همان دستگاه؛ هر تکهٔ `mss` بایتی
 *     پشتِ قبلی سریالی می‌شود (`len·8/bps`)؛ **تأخیرِ یک‌سویه** `rtt/2`؛ اتصالِ تازه یک RTT دست‌دادن.
 *   • **گم شدنِ بسته**: هر تکه با احتمالِ `loss` یک `rtoMs` دیرتر می‌رسد و چون TCP به‌ترتیب می‌دهد، پشتِ سری‌ها هم.
 *   • **بریدن در میانهٔ درخواست**: با احتمالِ `dropRate` (یا فهرستِ قطعیِ `forced`) اتصال در نقطه‌ای تصادفی از
 *     بدنهٔ درخواست (`up` — سرور چیزی نگرفت) یا پاسخ (`down` — سرور کار را کرد، دستگاه نفهمید) بریده می‌شود.
 *   • **مهلتِ خواندنِ دستگاه** (`readTimeoutMs`، همان `ApiConfig.READ_TIMEOUT_MS` = ۳۰ ثانیه): زمانش از وقتی شمرده
 *     می‌شود که نوشتنِ دستگاه برمی‌گردد — وقتی کم‌تر از `sendBufBytes` (بافرِ فرستِ سیستم‌عامل) از بدنه مانده باشد.
 *     نخستین بایتِ پاسخ دیرتر از آن ⇒ اتصال همان دم بسته (`timeout`)، چون با `speed` > ۱ ساعتِ خودِ دستگاه
 *     فشرده نیست و مهلتش دیر می‌زند.
 * **ساعتِ خط** (`vClock`): زمانِ «2G» ِ همین دستگاه — کارِ دستگاه و سرور (میانِ دو رفت‌وبرگشت و پاسخ‌سازیِ سرور)
 * با ساعتِ واقعی، و هر چه روی سیم است با مدل. `speed` = ۱ ⇒ همان زمانِ واقعی (سنجهٔ مدل)؛ `speed` > ۱ ⇒ تأخیرها
 * واقعاً `speed` برابر کوتاه‌ترند ولی `vClock` همان زمانِ 2G را می‌شمارد؛ `Infinity` ⇒ بی درنگ (فقط حساب).
 * شمارش: بایتِ سیم (سرآیند + بدنه) در هر سو، بدنهٔ هر درخواست/پاسخ، `Content-Encoding` ِ هر کدام و اندازهٔ
 * «اگر gzip بود» (`zlib.gzipSync`) — به تفکیکِ نوعِ درخواست (push، pull، snapshot، …).
 *
 * ⛔ فقط آزمون؛ هیچ‌جای `src/` این را نمی‌بیند.
 */
import net from 'node:net';
import zlib from 'node:zlib';

/** عددِ شبه‌تصادفیِ تکرارپذیر (mulberry32) */
export function rng(seed) {
  let a = (Number(seed) || 1) >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const G2_DEFAULTS = Object.freeze({
  rttMs: 600, upBps: 50_000, downBps: 50_000, loss: 0.02, rtoMs: 1200, dropRate: 0.02,
  readTimeoutMs: 30_000, sendBufBytes: 64 * 1024, mss: 1460, speed: 1, seed: 1, forced: [],
});

/** نوعِ درخواست از خطِ نخست */
export function kindOf(line) {
  const m = /^(\w+) (\S+)/.exec(line || '');
  if (!m) return 'other';
  const p = m[2].split('?')[0];
  if (/\/sync\/v1\/push$/.test(p)) return 'push';
  if (/\/sync\/v1\/pull$/.test(p)) return 'pull';
  if (/\/sync\/v1\/snapshot$/.test(p)) return 'snapshot';
  if (/\/sync\/v3\/push$/.test(p)) return 'v3push';
  if (/\/sync\/v3\/pull$/.test(p)) return 'v3pull';
  if (/\/auth\/refresh$/.test(p)) return 'refresh';
  return 'other';
}

/** پی‌گیرِ یک پیامِ HTTP/1.1 روی جریانِ خام: سرآیند، `Content-Length`، کدگذاری، بدنه (برای اندازهٔ gzip) */
class Msg {
  constructor(keepBody) { this.head = []; this.headLen = -1; this.cl = 0; this.got = 0; this.body = keepBody ? [] : null; this.headers = {}; this.line = ''; }
  get total() { return this.headLen < 0 ? Infinity : this.headLen + this.cl; }
  get done() { return this.headLen >= 0 && this.got >= this.total; }
  feed(c) {
    const start = this.got;
    this.got += c.length;
    if (this.headLen < 0) {
      this.head.push(c);
      const all = Buffer.concat(this.head);
      const at = all.indexOf('\r\n\r\n');
      if (at < 0) return;
      this.headLen = at + 4;
      const lines = all.subarray(0, at).toString('latin1').split('\r\n');
      this.line = lines[0];
      for (const l of lines.slice(1)) { const i = l.indexOf(':'); if (i > 0) this.headers[l.slice(0, i).trim().toLowerCase()] = l.slice(i + 1).trim(); }
      const status = /^HTTP\/\d\.\d (\d+)/.exec(this.line);
      this.status = status ? Number(status[1]) : 0;
      this.cl = Number(this.headers['content-length'] || 0) || 0;
      if (this.status === 204 || this.status === 304) this.cl = 0;
      if (this.body) this.body.push(all.subarray(this.headLen));
      this.head = null;
      return;
    }
    if (this.body) this.body.push(c);
    void start;
  }
  /** اندازهٔ بدنهٔ ساده و gzip ِ آن (اگر خودش فشرده نیست) */
  sizes() {
    const encoded = Boolean(this.headers['content-encoding']);
    if (!this.body) return { body: this.cl, gz: this.cl, encoded };
    const raw = Buffer.concat(this.body).subarray(0, this.cl);
    return { body: raw.length, gz: encoded || raw.length === 0 ? raw.length : zlib.gzipSync(raw).length, encoded };
  }
}

function kindStats() {
  return { n: 0, ok: 0, reqWire: 0, resWire: 0, reqBody: 0, resBody: 0, reqGzipped: 0, resGzipped: 0, reqGzEst: 0, resGzEst: 0,
    maxReqBody: 0, maxResBody: 0, dropUp: 0, dropDown: 0, timeouts: 0, closed: 0, status: {} };
}

/**
 * @param {{host?:string, port:number}} target
 * @param {Partial<typeof G2_DEFAULTS>} opts
 */
export function createLink(target, opts = {}) {
  const cfg = { ...G2_DEFAULTS, ...opts };
  const rand = rng(cfg.seed);
  const st = {
    cfg: { ...cfg, forced: undefined }, conns: 0, exchanges: 0, wireUp: 0, wireDown: 0, lostUp: 0, lostDown: 0,
    drops: 0, timeouts: 0, kinds: {}, log: [], serverMs: 0,
  };
  const forced = (cfg.forced || []).map((f) => ({ ...f, seen: 0 }));
  const seenKind = {};
  const sockets = new Set();
  const now = () => performance.now();
  const born = now();
  let vClock = 0;
  let lastReal = born;
  let inFlight = 0;
  const realDelay = (vms) => (cfg.speed === Infinity ? 0 : Math.max(0, vms) / cfg.speed);
  const ser = (bytes, bps) => (bytes * 8 * 1000) / bps;

  /**
   * صفِ زمان‌بندیِ یک سوی یک اتصال: کارها به همان ترتیبِ ورود و هرگز زودتر از `at` (واقعی) — تکه‌ها جابه‌جا نمی‌رسند،
   * و پایان/بریدن پس از آخرین تکهٔ پیش از خودش می‌نشیند.
   */
  function lane() {
    const q = [];
    let armed = false;
    let last = 0;
    const pump = () => {
      armed = false;
      const t = now();
      while (q.length && q[0].at <= t + 0.5) q.shift().fn();
      arm();
    };
    const arm = () => {
      if (armed || !q.length) return;
      armed = true;
      const d = q[0].at - now();
      if (d <= 0.5) setImmediate(pump); else setTimeout(pump, d);
    };
    return {
      push(at, fn) { last = Math.max(last, at); q.push({ at: last, fn }); arm(); },
      get last() { return Math.max(last, now()); },
    };
  }

  function finish(conn, ex, outcome, vEnd, rEnd) {
    if (ex.finished) return;
    ex.finished = true;
    inFlight = Math.max(0, inFlight - 1);
    vClock = Math.max(vClock, vEnd);
    lastReal = Math.max(lastReal, rEnd);
    const k = st.kinds[ex.kind] || (st.kinds[ex.kind] = kindStats());
    const q = ex.req.sizes();
    const s = ex.res.headLen >= 0 ? ex.res.sizes() : { body: 0, gz: 0, encoded: false };
    k.n++;
    if (outcome === 'ok') k.ok++;
    if (outcome === 'drop-up') k.dropUp++;
    if (outcome === 'drop-down') k.dropDown++;
    if (outcome === 'timeout') k.timeouts++;
    if (outcome === 'closed') k.closed++;
    k.reqWire += ex.upWire; k.resWire += ex.downWire;
    k.reqBody += q.body; k.resBody += s.body;
    k.reqGzEst += q.gz; k.resGzEst += s.gz;
    if (q.encoded) k.reqGzipped++;
    if (s.encoded) k.resGzipped++;
    k.maxReqBody = Math.max(k.maxReqBody, q.body);
    k.maxResBody = Math.max(k.maxResBody, s.body);
    if (ex.res.status) k.status[ex.res.status] = (k.status[ex.res.status] || 0) + 1;
    st.serverMs += ex.serverMs || 0;
    st.log.push([ex.kind, Math.round(ex.vStart), Math.round(vEnd), outcome, q.body, s.body, ex.res.status || 0, q.encoded ? 1 : 0, s.encoded ? 1 : 0]);
    if (conn.ex === ex) conn.ex = null;
  }

  /** بریدن در زمانِ مدلِ [vAt] (پس از تکه‌های پیش از خودش در همان سو) */
  function kill(conn, ex, outcome, vAt) {
    if (outcome === 'timeout') st.timeouts++; else st.drops++;
    ex.killed = true;
    const down = ex.phase === 'down';
    const at = down ? ex.rR + realDelay(vAt - ex.vResStart) : ex.r0 + realDelay(vAt - ex.vStart);
    (down ? conn.toClient : conn.toServer).push(at, () => { finish(conn, ex, outcome, vAt, now()); conn.client.destroy(); conn.up.destroy(); });
  }

  function decideDrop(kind) {
    seenKind[kind] = (seenKind[kind] || 0) + 1;
    const f = forced.find((x) => x.kind === kind && x.nth === seenKind[kind]);
    if (f) return { dir: f.dir, frac: f.frac ?? 0.5 };
    if (cfg.dropRate > 0 && rand() < cfg.dropRate) return { dir: rand() < 0.5 ? 'up' : 'down', frac: 0.05 + 0.9 * rand() };
    return null;
  }

  function onClientData(conn, c) {
    const r = now();
    let ex = conn.ex;
    if (!ex || ex.res.headLen >= 0) {
      if (ex && !ex.finished) finish(conn, ex, 'ok', ex.vLastDown ?? vClock, r);
      //  دستگاه کار کرد (یا خوابید) — همان زمانِ واقعی روی ساعتِ خط
      if (inFlight === 0) vClock += Math.max(0, r - lastReal);
      lastReal = r;
      inFlight++;
      st.exchanges++;
      ex = conn.ex = {
        kind: 'other', r0: r, vStart: vClock, vUp: vClock + (conn.fresh ? cfg.rttMs : 0), upWire: 0, downWire: 0,
        req: new Msg(true), res: new Msg(true), phase: 'up', drop: undefined, sentUp: 0, sentDown: 0,
      };
      conn.fresh = false;
    }
    if (ex.killed) return;
    const wasHead = ex.req.headLen >= 0;
    ex.req.feed(c);
    if (!wasHead && ex.req.headLen >= 0) {
      ex.kind = kindOf(ex.req.line);
      ex.drop = decideDrop(ex.kind);
    }
    for (let i = 0; i < c.length; i += cfg.mss) {
      const piece = c.subarray(i, i + cfg.mss);
      ex.vUp += ser(piece.length, cfg.upBps);
      if (cfg.loss > 0 && rand() < cfg.loss) { ex.vUp += cfg.rtoMs; st.lostUp++; }
      ex.upWire += piece.length;
      st.wireUp += piece.length;
      const vAt = ex.vUp + cfg.rttMs / 2;
      ex.sentUp += piece.length;
      if (ex.drop?.dir === 'up' && ex.req.total !== Infinity && ex.sentUp >= ex.drop.frac * ex.req.total) { kill(conn, ex, 'drop-up', vAt); return; }
      ex.vReqAtServer = vAt;
      conn.toServer.push(ex.r0 + realDelay(vAt - ex.vStart), () => { if (!conn.up.destroyed) conn.up.write(piece); ex.rS = now(); });
    }
    if (ex.req.done && ex.readStartV === undefined) {
      //  نوشتنِ دستگاه وقتی برمی‌گردد که کم‌تر از بافرِ فرستِ سیستم‌عامل مانده باشد ⇒ مهلتِ خواندن از همان دم
      ex.readStartV = Math.max(ex.vStart, ex.vUp - ser(cfg.sendBufBytes, cfg.upBps));
    }
  }

  function onServerData(conn, c) {
    const ex = conn.ex;
    if (!ex || ex.killed) return;
    const r = now();
    if (ex.phase === 'up') {
      ex.phase = 'down';
      ex.rR = r;
      ex.serverMs = Math.max(0, r - (ex.rS ?? r));
      ex.vResStart = (ex.vReqAtServer ?? ex.vUp) + ex.serverMs;
      ex.vDown = ex.vResStart;
    }
    ex.res.feed(c);
    for (let i = 0; i < c.length; i += cfg.mss) {
      const piece = c.subarray(i, i + cfg.mss);
      ex.vDown += ser(piece.length, cfg.downBps);
      if (cfg.loss > 0 && rand() < cfg.loss) { ex.vDown += cfg.rtoMs; st.lostDown++; }
      ex.downWire += piece.length;
      st.wireDown += piece.length;
      const vAt = ex.vDown + cfg.rttMs / 2;
      if (ex.vFirstDown === undefined) {
        ex.vFirstDown = vAt;
        const limit = (ex.readStartV ?? ex.vStart) + cfg.readTimeoutMs;
        if (vAt > limit) { kill(conn, ex, 'timeout', limit); return; }
      }
      ex.sentDown += piece.length;
      if (ex.drop?.dir === 'down' && ex.res.total !== Infinity && ex.sentDown >= ex.drop.frac * ex.res.total) { kill(conn, ex, 'drop-down', vAt); return; }
      ex.vLastDown = vAt;
      const last = ex.res.done && i + cfg.mss >= c.length;
      conn.toClient.push(ex.rR + realDelay(vAt - ex.vResStart), () => {
        if (!conn.client.destroyed) conn.client.write(piece);
        if (last) finish(conn, ex, 'ok', vAt, now());
      });
    }
  }

  const server = net.createServer((client) => {
    st.conns++;
    const up = net.connect(target.port, target.host || '127.0.0.1');
    const conn = { client, up, fresh: true, ex: null, toServer: lane(), toClient: lane() };
    for (const s of [client, up]) { sockets.add(s); s.on('close', () => sockets.delete(s)); s.on('error', () => {}); }
    client.on('data', (c) => onClientData(conn, c));
    up.on('data', (c) => onServerData(conn, c));
    //  پایان و بسته شدن پس از آخرین تکهٔ زمان‌بندی‌شدهٔ همان سو
    client.on('end', () => conn.toServer.push(conn.toServer.last, () => up.end()));
    up.on('end', () => conn.toClient.push(conn.toClient.last, () => client.end()));
    const closed = () => {
      const ex = conn.ex;
      if (ex && !ex.finished && !ex.killed) conn.toClient.push(conn.toClient.last, () => finish(conn, ex, ex.res.done ? 'ok' : 'closed', ex.vLastDown ?? vClock, now()));
    };
    client.on('close', () => { closed(); up.destroy(); });
    up.on('close', () => { closed(); conn.toClient.push(conn.toClient.last, () => client.destroy()); });
  });

  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    port: server.address().port,
    url: `http://127.0.0.1:${server.address().port}`,
    /** `vClockMs`: ساعتِ خط در پایانِ آخرین رفت‌وبرگشت (بی بیکاریِ پس از آن)؛ `realMs`: همان، واقعی */
    stats: () => ({ ...st, vClockMs: vClock, realMs: lastReal - born, log: st.log.slice() }),
    /** پارامترِ تازه (مثلاً `dropRate: 0` برای دورِ پایانی) */
    set(next = {}) { Object.assign(cfg, next); st.cfg = { ...cfg, forced: undefined }; if (next.forced) { forced.length = 0; forced.push(...next.forced.map((f) => ({ ...f, seen: 0 }))); } },
    close: () => new Promise((r) => { for (const s of sockets) s.destroy(); server.close(() => r()); }),
  })));
}
