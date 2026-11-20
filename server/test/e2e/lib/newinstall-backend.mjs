/**
 * سرورِ آزمونِ «نصبِ تازه کنارِ برنامهٔ قدیم» (عاملِ e-newinstall) — همان سرورِ واقعیِ مخزن برای
 * `NewInstallSyncDesktopTest` ِ کامپیوتر.
 *
 *   node server/test/e2e/lib/newinstall-backend.mjs <خروجی.json>
 *
 * • همان پایهٔ سناریوهای سرتاسری (`harness.mjs` ⇐ `createApp`، جدولِ schema) روی PGlite ِ در حافظه.
 * • «برنامهٔ قدیم» (`ir.vil3ntec.tohid`) این‌جا یک دستگاهِ پروتکل است (`NativeDevice`، همان قاعده‌های `LedgerDiff` ِ امروز):
 *   حساب و دکان می‌سازد، کالا/ورودِ انبار/مشتری/فروش ثبت می‌کند و همه را به سرور می‌فرستد — دقیقاً همان دکانی که صاحب
 *   پیش از نصبِ برنامهٔ تازه داشت. برنامهٔ تازه (`com.vill3n.dailyshop`) همان کدِ کاتلین ِ آزمون است.
 * • درِ کنترل (`ctl`، فقط 127.0.0.1): ساختنِ دکانِ پُر، حسابِ تازهٔ بی دکان، کدِ ایمیلِ ورود، پرچم، نگاه به `oplog`.
 * • نشانی‌ها در `<خروجی.json>`؛ با `POST /stop` یا بسته شدنِ stdin خاموش می‌شود.
 *
 * ⛔ فقط برای آزمون؛ جزوِ `src/` نیست.
 */
import fs from 'node:fs';
import http from 'node:http';

const out = process.argv[2];
if (!out) { console.error('کاربرد: node newinstall-backend.mjs <خروجی.json>'); process.exit(2); }

const { boot } = await import('./harness.mjs');
const { NativeDevice } = await import('./native.mjs');
//  همهٔ ورودها و ثبت‌نام‌های آزمون از 127.0.0.1 می‌آیند (پنج سناریو × چند ورود) — سقفِ نرخِ یک آدم برای صد دستگاهِ آزمون نیست
const E = await boot({ name: 'newinstall', env: {
  RATE_AUTH_MAX: '100000', RATE_OTP_MAX: '100000', RATE_OTP_IP_MAX: '100000', RATE_VERIFY_MAX: '100000',
  RATE_PASSWORD_SESSION_MAX: '100000', RATE_SESSION_MAX: '100000', RATE_PROBE_IP_MAX: '100000',
} });

const COLLECTIONS = ['products', 'warehouseEntries', 'debtors', 'sales', 'saleItems', 'transactions', 'stockMovements'];
const seededDevices = new Map();

/** دکانِ پُرِ برنامهٔ قدیم: صاحب + دکان + کالا، موجودی، مشتری و یک فروشِ قرضی — همه روی سرور */
async function seedShop({ email, name = 'صاحبِ دکان', shop = 'دکانِ قدیمی', products = 3 }) {
  const owner = await E.owner(email, name, shop);
  const old = new NativeDevice(E, 'old-app', { seller: name });
  await old.login(email);
  const ids = [];
  for (let i = 1; i <= products; i++) ids.push(old.stockIn({ name: `کالای ${i}`, price: 100 * i, cost: 60 * i, units: 10 * i, category: 'خوراکی' }));
  const debtor = old.addDebtor('مشتریِ قدیمی', '0700000001');
  const sold = old.sell({ lines: [{ id: ids[0], qty: 2 }], payment: 'debt', debtorId: debtor, paid: 0 });
  if (sold && sold.ok === false) throw new Error(`فروشِ برنامهٔ قدیم: ${sold.message}`);
  const pushed = await old.sync();
  if (!pushed.ok) throw new Error(`push ِ برنامهٔ قدیم: ${JSON.stringify(pushed)}`);
  seededDevices.set(String(owner.shopId), old);
  const rows = Object.fromEntries(COLLECTIONS.map((c) => [c, (old.ledger[c] || []).length]));
  return {
    email, password: owner.password, userId: String(owner.user.id), shopId: String(owner.shopId),
    products: ids, debtor, rows, oldDevice: old.deviceId, queue: old.queue.length,
  };
}

async function oplog(shopId) {
  const r = await E.db.query(
    `SELECT device_id, op_id, table_name, row_id FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [String(shopId)]);
  return r.rows;
}

const routes = {
  'POST /seed': async (b) => seedShop(b),
  'POST /product-price': async ({ shopId, productId, price }) => {
    const device = seededDevices.get(shopId);
    if (!device || !Number.isFinite(price) || !device.ledger.products.some((p) => p.id === productId)) {
      throw new Error('دکان، کالا یا قیمتِ آزمون معتبر نیست');
    }
    const products = device.ledger.products.map((p) => p.id === productId ? { ...p, salePrice: price } : p);
    device.save({ ...device.ledger, products });
    const result = await device.sync();
    if (!result.ok || device.queue.length) throw new Error('قیمتِ دستگاهِ دوم به سرور نرسید');
    return { cursor: device.cursor };
  },
  /** حسابِ تازهٔ بی دکان (ثبت‌نامِ سه‌پله‌ای ِ برنامه) */
  'POST /account': async (b) => {
    const a = await E.register(String(b.email), String(b.name || 'حسابِ تازه'));
    return { email: a.email, password: a.password, userId: String(a.user.id) };
  },
  /** آخرین کدِ ورودِ ایمیلی به این نشانی پس از [after] */
  'POST /code': async (b) => ({ code: await E.mailCode(String(b.email), Number(b.after || 0)) }),
  'POST /flag': async (b) => { await E.flag(String(b.shopId), String(b.key), b.value); return { ok: true }; },
  /** opهای این دکان: شمارِ هر op_id (تکراری ⇒ بیش از یک) و ردیف‌های هر جدول */
  'POST /oplog': async (b) => {
    const rows = await oplog(b.shopId);
    const perOp = new Map();
    for (const r of rows) perOp.set(r.op_id, (perOp.get(r.op_id) || 0) + 1);
    return { ops: rows.length, duplicates: [...perOp.values()].filter((n) => n > 1).length, devices: [...new Set(rows.map((r) => r.device_id))] };
  },
};

const ctl = http.createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  let body = {};
  try { body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}; } catch { body = {}; }
  const key = `${req.method} ${req.url.split('?')[0]}`;
  if (key === 'POST /stop') { res.end('{"ok":true}'); await stop(); return; }
  const fn = routes[key];
  try {
    if (!fn) throw Object.assign(new Error(`نامعلوم: ${key}`), { status: 404 });
    const r = await fn(body);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(r));
  } catch (e) {
    res.writeHead(e.status || 500, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: String(e.stack || e.message || e) }));
  }
});
await new Promise((r) => ctl.listen(0, '127.0.0.1', r));

let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  ctl.closeAllConnections?.();
  ctl.close();
  E.outbox.stop();
  await E.cluster.stop().catch(() => {});
  E.api.closeAllConnections?.();
  await new Promise((r) => E.api.close(r));
  await E.db.closeDb().catch(() => {});
  process.exit(0);
}
process.stdin.on('end', stop);
process.stdin.resume();

fs.writeFileSync(out, JSON.stringify({ api: E.apiBase, ctl: `http://127.0.0.1:${ctl.address().port}` }));
console.log(`newinstall: api=${E.apiBase} ctl=http://127.0.0.1:${ctl.address().port}`);
