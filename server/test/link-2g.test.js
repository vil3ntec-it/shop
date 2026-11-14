'use strict';
/**
 * پیوندِ «2G» ِ آزمون (`test/e2e/lib/link-2g.mjs`، S26/SYN-04): مدل همان است که روی سیم می‌گذرد.
 *   ۱) بدنه دست‌نخورده و به‌ترتیب (sha ِ رفت و برگشت)، زمانِ دیواری ≈ ساعتِ خط ≈ فرمولِ پهنا + RTT (±۱۵٪)
 *   ۲) `speed` = ∞ ⇒ همان ساعتِ خط بی انتظارِ واقعی (همان بایت‌ها)
 *   ۳) بریدنِ قطعی: `up` ⇒ سرور بدنهٔ کامل نگرفت؛ `down` ⇒ سرور گرفت، پاسخ نرسید
 *   ۴) مهلتِ خواندنِ دستگاه: پاسخِ دیرتر از `readTimeoutMs` ⇒ `timeout`
 *   ۵) gzip: سرآیندِ `Content-Encoding` شمرده و اندازهٔ «اگر gzip بود» حساب می‌شود
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const path = require('node:path');

const LINK = path.join(__dirname, 'e2e', 'lib', 'link-2g.mjs');
const sha = (b) => crypto.createHash('sha256').update(b).digest('hex');

/** سرورِ پژواک: بدنه را می‌گیرد، sha و اندازه‌اش را با [pad] بایتِ پاسخ برمی‌گرداند */
function echoServer({ delayMs = 0 } = {}) {
  const seen = [];
  const srv = http.createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('aborted', () => seen.push({ aborted: true }));
    req.on('end', () => {
      const body = Buffer.concat(chunks);
      seen.push({ bytes: body.length, sha: sha(body), enc: req.headers['content-encoding'] || '' });
      const pad = Number(new URL(req.url, 'http://x').searchParams.get('pad') || 0);
      const out = JSON.stringify({ sha: sha(body), bytes: body.length, pad: 'x'.repeat(pad) });
      setTimeout(() => res.end(out), delayMs);
    });
  });
  return new Promise((r) => srv.listen(0, '127.0.0.1', () => r({ srv, port: srv.address().port, seen })));
}

function request(port, p, body, headers = {}) {
  return new Promise((resolve) => {
    const req = http.request({ host: '127.0.0.1', port, path: p, method: body ? 'POST' : 'GET', agent: false,
      headers: { ...(body ? { 'Content-Type': 'application/json', 'Content-Length': body.length } : {}), ...headers } }, (res) => {
      const c = [];
      res.on('data', (d) => c.push(d));
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(c).toString('utf8') }));
      res.on('error', (e) => resolve({ error: e.code || e.message }));
    });
    req.on('error', (e) => resolve({ error: e.code || e.message }));
    if (body) req.end(body); else req.end();
  });
}

test('۱ و ۲) بدنهٔ دست‌نخورده، زمانِ دیواری = ساعتِ خط = پهنا + RTT؛ speed=∞ همان ساعت بی انتظار', async () => {
  const { createLink } = await import(LINK);
  const e = await echoServer();
  const body = crypto.randomBytes(40 * 1024);
  const cfg = { rttMs: 300, upBps: 400_000, downBps: 400_000, loss: 0, dropRate: 0, seed: 7 };
  const runs = {};
  for (const speed of [1, Infinity]) {
    const link = await createLink({ port: e.port }, { ...cfg, speed });
    const t0 = performance.now();
    const r = await request(link.port, '/api/sync/v1/push?pad=20000', body);
    const wall = performance.now() - t0;
    const st = link.stats();
    assert.equal(r.status, 200);
    assert.equal(JSON.parse(r.body).sha, sha(body), 'بدنه همان، به‌ترتیب');
    runs[speed] = { wall, v: st.vClockMs, st };
    await link.close();
  }
  const st = runs[1].st;
  const k = st.kinds.push;
  assert.equal(k.n, 1);
  assert.equal(k.reqBody, body.length);
  assert.ok(k.resBody > 20000);
  //  فرمول: دست‌دادن (RTT) + بالا (سیم ÷ پهنا) + RTT/2 + پایین + RTT/2
  const model = cfg.rttMs + (k.reqWire * 8 * 1000) / cfg.upBps + cfg.rttMs + (k.resWire * 8 * 1000) / cfg.downBps;
  assert.ok(Math.abs(runs[1].wall - model) / model < 0.15, `دیواری ${runs[1].wall.toFixed(0)} ≈ مدل ${model.toFixed(0)}`);
  assert.ok(Math.abs(runs[1].v - runs[1].wall) / runs[1].wall < 0.15, `ساعتِ خط ${runs[1].v.toFixed(0)} ≈ دیواری ${runs[1].wall.toFixed(0)}`);
  assert.ok(runs[Infinity].wall < model / 4, `بی انتظار: ${runs[Infinity].wall.toFixed(0)}ms`);
  assert.ok(Math.abs(runs[Infinity].v - model) / model < 0.15, `ساعتِ خطِ ∞ ${runs[Infinity].v.toFixed(0)} ≈ مدل ${model.toFixed(0)}`);
  assert.equal(runs[Infinity].st.wireUp, runs[1].st.wireUp);
  e.srv.close();
});

test('۳) بریدنِ قطعی: up ⇒ سرور بدنه را کامل نگرفت؛ down ⇒ سرور گرفت، پاسخ نرسید', async () => {
  const { createLink } = await import(LINK);
  const e = await echoServer();
  const link = await createLink({ port: e.port }, {
    rttMs: 100, upBps: 2_000_000, downBps: 2_000_000, loss: 0, dropRate: 0, speed: 1,
    forced: [{ kind: 'push', nth: 1, dir: 'up', frac: 0.5 }, { kind: 'push', nth: 2, dir: 'down', frac: 0.5 }],
  });
  const body = crypto.randomBytes(30 * 1024);
  const a = await request(link.port, '/api/sync/v1/push', body);
  assert.ok(a.error, 'بریده در میانهٔ بدنهٔ درخواست');
  await new Promise((r) => setTimeout(r, 100));
  assert.ok(!e.seen.some((s) => s.sha === sha(body)), 'سرور بدنهٔ کامل را نگرفت');
  const b = await request(link.port, '/api/sync/v1/push?pad=30000', body);
  assert.ok(b.error || b.body.length < 30000, 'پاسخ بریده');
  assert.equal(e.seen.filter((s) => s.sha === sha(body)).length, 1, 'سرور دومی را گرفت');
  const c = await request(link.port, '/api/sync/v1/push', body);
  assert.equal(c.status, 200);
  const k = link.stats().kinds.push;
  assert.deepEqual([k.n, k.dropUp, k.dropDown, k.ok], [3, 1, 1, 1]);
  await link.close();
  e.srv.close();
});

test('۴ و ۵) مهلتِ خواندن و شمارِ gzip', async () => {
  const { createLink } = await import(LINK);
  const e = await echoServer({ delayMs: 400 });
  const link = await createLink({ port: e.port }, { rttMs: 50, upBps: 1_000_000, downBps: 1_000_000, loss: 0, dropRate: 0, speed: 1, readTimeoutMs: 300 });
  const slow = await request(link.port, '/api/sync/v1/pull?device_id=x', null);
  assert.ok(slow.error, 'پاسخِ دیر ⇒ اتصال بسته');
  assert.equal(link.stats().kinds.pull.timeouts, 1);
  link.set({ readTimeoutMs: 30_000 });
  const json = Buffer.from(JSON.stringify({ ops: Array.from({ length: 200 }, (_, i) => ({ op_id: `op${i}`, table: 'sales', fields: { total: i, note: 'فروشِ نقد' } })) }));
  const gz = zlib.gzipSync(json);
  assert.equal((await request(link.port, '/api/sync/v1/push', gz, { 'Content-Encoding': 'gzip' })).status, 200);
  assert.equal((await request(link.port, '/api/sync/v1/push', json)).status, 200);
  const k = link.stats().kinds.push;
  assert.equal(k.reqGzipped, 1);
  assert.equal(k.reqBody, gz.length + json.length);
  assert.ok(k.reqGzEst < json.length / 3, `اندازهٔ gzip ِ برآوردی ${k.reqGzEst} از ${gz.length + json.length}`);
  await link.close();
  e.srv.close();
});
