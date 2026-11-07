'use strict';
/**
 * یک نمونهٔ سرور برای `test/cluster-instances.test.js` — فرآیندِ جدا (`fork`) روی همان PostgreSQL ِ آزمون.
 *
 * همان `createApp` ِ آزمون‌ها (`helpers.start({ reset: false })`: پدر پایگاه را ساخته)، همان سوکتِ زنده و
 * همان فهرستِ کارگرهای `src/index.js` (`src/workers.js`) از راهِ `cluster.jobs`، با `CLUSTER_ENABLED=1`.
 * پدر با IPC می‌پرسد: حالِ خوشه (رهبر؟ کدام کارگرها؟) و long-poll ِ چت (صبر این‌جا، بیدارباش از نمونهٔ دیگر).
 */
const h = require('../helpers');
const config = require('../../src/config');
const cluster = require('../../src/lib/cluster');
const chatWait = require('../../src/lib/chat-wait');

process.on('disconnect', () => process.exit(0));

(async () => {
  //  سقفِ همگانیِ هر IP — `helpers` آن را باز می‌گذارد؛ این‌جا کوچک تا مشترک بودنش دیده شود
  if (process.env.CLUSTER_TEST_GENERAL_MAX) config.rateLimit.generalMax = Number(process.env.CLUSTER_TEST_GENERAL_MAX);
  const base = await h.start({ reset: false });
  //  همان `live.attach(server)` ِ `src/index.js`
  require('../../src/lib/sync-v1-live').attach(h.server());
  cluster.jobs(require('../../src/workers').backgroundJobs());
  await cluster.start();
  process.on('message', async (m) => {
    let out;
    try {
      if (m.type === 'stats') out = cluster.stats();
      else if (m.type === 'chatWait') {
        const t0 = Date.now();
        const w = chatWait.listen(m.conv, m.ms);
        process.send({ id: m.id, type: 'listening' });
        out = { woke: await w.promise, ms: Date.now() - t0 };
      } else if (m.type === 'chatNotify') { chatWait.notify(m.conv); out = { ok: true }; }
    } catch (e) { out = { error: String(e && e.message) }; }
    process.send({ id: m.id, type: 'reply', out });
  });
  process.send({ type: 'ready', base, instanceId: cluster.instanceId });
})().catch((e) => { console.error('cluster-child:', e); process.exit(1); });
