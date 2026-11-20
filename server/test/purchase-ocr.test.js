'use strict';
/**
 * PUR-12 / AI-10 — «از عکسِ فاکتور» (`routes/purchase-ocr.js`، `lib/purchase-ocr.js`).
 *
 * Tesseract ِ ساختگی: یک برنامهٔ کوچکِ node در پوشهٔ موقت (`TESSERACT_BIN`) که حالتش را از `mode.json` ِ کنارش می‌خواند و
 * آنچه دید (آرگومان‌ها، سرآیند و مجوزِ فایل، کلیدهای محیط) را در `seen.json` می‌نویسد — همان راهِ واقعیِ `spawn`.
 * سنجه‌ها: پرچمِ خاموش، مجوز و اشتراک، سقفِ حجم/ابعاد/نوع، بازسازیِ بی EXIF با جهتِ درست، پوشهٔ موقتِ خصوصیِ
 * پاک‌شده، محیطِ بی راز، نبودِ برنامه یا دادهٔ فارسی، مهلت، همزمانی، سقفِ نرخ، و هیچ ردیف یا لاگی از متن.
 * آزمونِ آخر با Tesseract ِ **واقعی** روی `fixtures/purchase-ocr/invoice-fa.jpg` فقط وقتی `tesseract` با `fas` نصب است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const ocr = require('../src/lib/purchase-ocr');

const POSIX = process.platform !== 'win32';
const DAY = 24 * 60 * 60 * 1000;
const FAKE = fs.mkdtempSync(path.join(os.tmpdir(), 'fake-tess-'));
const BIN = path.join(FAKE, 'tesseract');
const TSV = [
  'level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext',
  '1\t1\t0\t0\t0\t0\t0\t0\t800\t600\t-1\t',
  '4\t1\t1\t1\t1\t0\t10\t10\t700\t40\t-1\t',
  '5\t1\t1\t1\t1\t1\t700\t10\t40\t40\t91.5\t۱',
  '5\t1\t1\t1\t1\t2\t600\t10\t80\t40\t88\tبرنج',
  '5\t1\t1\t1\t1\t3\t500\t10\t80\t40\t90\tسیلا',
  '5\t1\t1\t1\t1\t4\t400\t10\t40\t40\t93\t۱۰',
  '5\t1\t1\t1\t1\t5\t300\t10\t60\t40\t95\t۹۵۰',
  '5\t1\t1\t1\t1\t6\t200\t10\t90\t40\t87\t‎۹٬۵۰۰‏',
  '5\t1\t1\t2\t1\t1\t600\t60\t80\t40\t70\tجمع',
  '5\t1\t1\t2\t1\t2\t500\t60\t80\t40\t-1\tکل',
  '5\t1\t1\t2\t1\t3\t300\t60\t90\t40\t80\t۹٬۵۰۰',
].join('\n') + '\n';

fs.writeFileSync(BIN, `#!/usr/bin/env node
'use strict';
const fs = require('fs'), path = require('path');
const mode = JSON.parse(fs.readFileSync(path.join(__dirname, 'mode.json'), 'utf8'));
const args = process.argv.slice(2);
if (args[0] === '--list-langs') {
  fs.appendFileSync(path.join(__dirname, 'probes.log'), 'probe\\n');
  setTimeout(() => process.stdout.write('List of available languages in "/usr/share/tessdata/" (' + mode.langs.length + '):\\n' + mode.langs.join('\\n') + '\\n'), mode.probeDelay || 0);
  return;
}
const file = args[0];
const b = fs.readFileSync(file);
fs.writeFileSync(path.join(__dirname, 'seen.json'), JSON.stringify({
  args, head: b.subarray(0, 24).toString('latin1'), size: b.length, dir: path.dirname(file),
  fileMode: fs.statSync(file).mode & 0o777, dirMode: fs.statSync(path.dirname(file)).mode & 0o777,
  exif: b.includes('Exif') || b.includes('GPS') || b.includes('ICC_PROFILE') || b.includes('comment'), env: Object.keys(process.env),
}));
if (mode.run === 'sleep') { setTimeout(() => {}, 60000); }
else if (mode.run === 'fail') process.exit(3);
else if (mode.run === 'flood') {
  const chunk = 'x'.repeat(65536); let i = 0;
  const w = () => { while (i++ < 100) { if (!process.stdout.write(chunk)) { process.stdout.once('drain', w); return; } } };
  w();
} else process.stdout.write(mode.tsv);
`, { mode: 0o755 });

function setMode(m) {
  fs.writeFileSync(path.join(FAKE, 'mode.json'), JSON.stringify({ langs: ['eng', 'fas', 'osd'], run: 'ok', tsv: TSV, ...m }));
  try { fs.unlinkSync(path.join(FAKE, 'seen.json')); } catch { /* نبود */ }
  fs.rmSync(path.join(FAKE, 'probes.log'), { force: true });
  process.env.TESSERACT_BIN = BIN;
  ocr._test.reset();
}
const seen = () => { try { return JSON.parse(fs.readFileSync(path.join(FAKE, 'seen.json'), 'utf8')); } catch { return null; } };

/** پایانِ SOI و APP0 (اگر نخستین قطعه APP0 است) */
function afterApp0(buf) {
  let i = 2;
  if (buf[i] === 0xff && buf[i + 1] === 0xe0) i += 2 + buf.readUInt16BE(i + 2);
  return i;
}
/** جای نخستین SOF0 */
function sofAt(buf) {
  let i = 2;
  while (i + 4 <= buf.length && buf[i] === 0xff) {
    if (buf[i + 1] === 0xc0) return i;
    i += 2 + buf.readUInt16BE(i + 2);
  }
  throw new Error('sof');
}
/** JPEG ِ رمزگشاپذیرِ ۶۴۰×۴۸۰ با قطعه‌های EXIF (جهت یا GPS)، ICC و COM ِ `F.jpeg` پیش از داده */
function photo({ w = 640, h = 480, orientation = 0 } = {}) {
  const base = F.jpegImage(w, h, (x, y) => (((x >> 4) + (y >> 4)) % 2 ? 30 : 230), { quality: 85 });
  const meta = F.jpeg(8, 8, { orientation, gps: !orientation });
  const at = afterApp0(base);
  return Buffer.concat([base.subarray(0, at), meta.subarray(afterApp0(meta), sofAt(meta)), base.subarray(at)]);
}

let root = '';
let shop;
let seq = 0;
const send = (token, buf, headers = {}) => h.raw('POST', '/api/shop/purchase-ocr', buf, { token, headers: { 'Content-Type': 'image/jpeg', ...headers } });
const config = (token) => h.get('/api/shop/purchase-ocr/config', { token });
const code = (r) => r.body?.error?.code;

async function setFlag(scope, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: ocr.FLAG, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function team(label) {
  const owner = await h.newUser(`صاحب ${label} ${++seq}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const staffCode = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label} ${seq}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: staffCode }, { token: staff.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  const manager = await h.newUser(`مدیر ${label} ${seq}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: manager.accessToken })).status));
  return { owner: owner.accessToken, staff: staff.accessToken, manager: manager.accessToken, shopId: made.body.shop.id };
}
async function rowCounts() {
  const tables = await many(`SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY 1`);
  const out = {};
  for (const t of tables) {
    if (t.table_name === 'rate_limit_hits') continue;
    out[t.table_name] = Number((await one(`SELECT COUNT(*)::int AS n FROM "${t.table_name}"`)).n);
  }
  return out;
}
const leftovers = () => fs.readdirSync(os.tmpdir()).filter((n) => n.startsWith('tohid-ocr-'));

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  shop = await team('الف');
});
test.after(async () => {
  await h.stop();
  fs.rmSync(FAKE, { recursive: true, force: true });
  delete process.env.TESSERACT_BIN;
});
test.beforeEach(() => { resetAll(); setMode({}); });

test('پرچمِ خاموش: config فقط `enabled: false`، نوشتن ۴۰۳ ِ purchase_ocr_disabled و Tesseract صدا زده نمی‌شود', async () => {
  const c = await config(shop.owner);
  assert.equal(c.status, 200);
  assert.deepEqual(c.body, { enabled: false });
  const r = await send(shop.owner, photo());
  assert.equal(r.status, 403);
  assert.equal(code(r), 'purchase_ocr_disabled');
  const mislabeled = await h.raw('POST', '/api/v1/shop/purchase-ocr/', Buffer.from('{'), {
    token: shop.owner, headers: { 'Content-Type': 'application/json' },
  });
  assert.equal(mislabeled.status, 403);
  assert.equal(code(mislabeled), 'purchase_ocr_disabled');
  assert.equal(seen(), null);
  assert.equal(ocr.LIMITS.WORKERS, 1, 'همزمانیِ پیش‌فرض ۱');
});

test('مجوز: بی توکن ۴۰۱؛ شاگرد (بی stock.adjust) فقط می‌بیند که اجازه ندارد؛ مدیر و صاحب می‌خوانند', async () => {
  await setFlag(shop.shopId, true);
  assert.equal((await send(null, photo())).status, 401);
  assert.equal((await h.get('/api/shop/purchase-ocr/config')).status, 401);
  const cs = await config(shop.staff);
  assert.equal(cs.status, 200);
  assert.equal(cs.body.enabled, true);
  assert.equal(cs.body.permitted, false);
  const rs = await send(shop.staff, photo());
  assert.equal(rs.status, 403);
  assert.equal(code(rs), 'permission_denied');
  assert.equal(seen(), null);
  const cm = await config(shop.manager);
  assert.deepEqual({ ...cm.body, limits: undefined }, { enabled: true, permitted: true, paid: true, available: true, lang: 'fas+eng', missing: [], limits: undefined });
  const rm = await send(shop.manager, photo());
  assert.equal(rm.status, 200, JSON.stringify(rm.body));
  //  دکانِ دیگرِ بی پرچم با همان راه بسته می‌ماند (پرچم سطحِ دکان است، نه سراسری)
  const other = await team('ب');
  assert.equal(code(await send(other.owner, photo())), 'purchase_ocr_disabled');
});

test('پولی: دکانِ بی اشتراک پس از دورهٔ آزمایشی ۴۰۳ ِ subscription_expired؛ config همان را از پیش می‌گوید', async () => {
  const s = await team('بی‌اشتراک');
  await setFlag(s.shopId, true);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  const c = await config(s.owner);
  assert.equal(c.body.paid, false);
  const r = await send(s.owner, photo());
  assert.equal(r.status, 403);
  assert.equal(code(r), 'subscription_expired');
  assert.equal(seen(), null);
});

test('سقف‌ها: خالی، بزرگ‌تر از ۶ مگابایت، نوعِ نادرست (از محتوا، نه نام)، WebP، ابعادِ بزرگ و عکسِ ریز — پیش از هر اجرا', async () => {
  await setFlag(shop.shopId, true);
  const cases = [
    [Buffer.alloc(0), 400, 'image_empty'],
    [Buffer.alloc(ocr.LIMITS.MAX_BYTES + 1, 1), 413, 'too_large'],
    [Buffer.from('%PDF-1.7\n' + 'x'.repeat(400), 'latin1'), 415, 'image_type'],
    [F.webp(800, 600), 415, 'image_type'],
    [F.jpeg(9000, 100), 413, 'image_too_large_dimensions'],
    [F.jpegImage(150, 150, () => 128), 422, 'image_too_small'],
  ];
  for (const [buf, status, want] of cases) {
    const r = await send(shop.owner, buf, { 'Content-Type': 'image/jpeg' });
    assert.equal(r.status, status, `${want}: ${JSON.stringify(r.body)}`);
    assert.equal(code(r), want);
    assert.match(r.body.error.message, /[؀-ۿ]/, 'پیامِ فارسی');
  }
  assert.equal(seen(), null, 'هیچ عکسِ ردشده‌ای به Tesseract نرسید');
});

test('خواندن: عکس دوباره ساخته می‌شود (PGM ِ بی EXIF با جهتِ درست)، پوشهٔ ۰۷۰۰ و فایلِ ۰۶۰۰ پاک می‌شوند، محیطِ فرزند بی راز', async () => {
  await setFlag(shop.shopId, true);
  const before = await rowCounts();
  const logs = [];
  const orig = { log: console.log, warn: console.warn, error: console.error, info: console.info };
  for (const k of Object.keys(orig)) console[k] = (...a) => { logs.push(a.map(String).join(' ')); orig[k](...a); };
  let r;
  try { r = await send(shop.owner, photo({ orientation: 6 })); } finally { Object.assign(console, orig); }
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.lang, 'fas+eng');
  assert.deepEqual(r.body.lines, [
    { text: '۱ برنج سیلا ۱۰ ۹۵۰ ۹٬۵۰۰', conf: 91 },
    { text: 'جمع کل ۹٬۵۰۰', conf: 75 },
  ]);
  assert.match(r.body.notice, /نگه داشته نمی‌شوند/);
  const s = seen();
  assert.deepEqual(s.args.slice(1), [
    'stdout', '-l', 'fas+eng', '--psm', '6',
    '-c', 'preserve_interword_spaces=1', '-c', 'tessedit_create_tsv=1',
  ]);
  assert.equal(path.basename(s.args[0]), 'page.pgm');
  //  جهتِ ۶ ⇒ ۴۸۰×۶۴۰ روی پیکسل؛ PGM (P5) بی هیچ بایتِ EXIF/GPS/ICC/COM
  assert.ok(s.head.startsWith('P5\n480 640\n255\n'), s.head);
  assert.equal(s.size, 'P5\n480 640\n255\n'.length + 480 * 640);
  assert.equal(s.exif, false);
  if (POSIX) {
    assert.equal(s.fileMode, 0o600);
    assert.equal(s.dirMode, 0o700);
  }
  assert.ok(path.basename(s.dir).startsWith('tohid-ocr-'));
  assert.equal(fs.existsSync(s.dir), false, 'پوشهٔ موقت پاک شد');
  const allowed = new Set(['OMP_THREAD_LIMIT', 'LANG', 'LC_ALL', 'PATH', 'Path', 'SystemRoot', 'TESSDATA_PREFIX', 'PWD', 'SHLVL', '_']);
  assert.deepEqual(s.env.filter((k) => !allowed.has(k)), [], 'هیچ متغیرِ دیگری از محیطِ سرور');
  //  هیچ ردیفی در هیچ جدولی، و متن یا عکس در هیچ لاگی
  assert.deepEqual(await rowCounts(), before);
  assert.equal(logs.filter((l) => /برنج|سیلا|۹٬۵۰۰/.test(l)).length, 0, logs.join('\n'));
  assert.equal(ocr._test.active(), 0);
});

test('نبودِ برنامه یا دادهٔ فارسی: config می‌گوید چه نصب شود و خواندن خطای نوع‌دار می‌دهد', async () => {
  await setFlag(shop.shopId, true);
  process.env.TESSERACT_BIN = path.join(FAKE, 'nope', 'tesseract');
  ocr._test.reset();
  const c = await config(shop.owner);
  assert.equal(c.body.available, false);
  assert.deepEqual(c.body.missing, ['tesseract-ocr', 'tesseract-ocr-fas']);
  const r = await send(shop.owner, photo());
  assert.equal(r.status, 503);
  assert.equal(code(r), 'ocr_not_installed');
  assert.match(r.body.error.message, /tesseract-ocr-fas/);

  setMode({ langs: ['eng', 'osd'] });
  const c2 = await config(shop.owner);
  assert.equal(c2.body.available, false);
  assert.deepEqual(c2.body.missing, ['tesseract-ocr-fas']);
  const r2 = await send(shop.owner, photo());
  assert.equal(r2.status, 503);
  assert.equal(code(r2), 'ocr_lang_missing');
  assert.equal(seen(), null);

  //  فقط فارسی ⇒ `fas` تنها
  setMode({ langs: ['fas'] });
  assert.equal((await config(shop.owner)).body.lang, 'fas');
  assert.equal((await send(shop.owner, photo())).status, 200);
  assert.equal(seen().args[3], 'fas');
});

test('مهلت، خروجیِ بی‌اندازه و شکستِ Tesseract ⇒ ۵۰۳ ِ نوع‌دار، پوشه پاک، جای همزمانی آزاد', async () => {
  await setFlag(shop.shopId, true);
  for (const [run, want] of [['sleep', 'ocr_timeout'], ['flood', 'ocr_failed'], ['fail', 'ocr_failed']]) {
    setMode({ run });
    if (run === 'sleep') ocr._test.setTimeoutMs(1500);
    const t = Date.now();
    const r = await send(shop.owner, photo());
    assert.equal(r.status, 503, `${run}: ${JSON.stringify(r.body)}`);
    assert.equal(code(r), want);
    if (run === 'sleep') assert.ok(Date.now() - t < 10000, 'مهلت بُرید');
    assert.equal(fs.existsSync(seen().dir), false, `${run}: پوشه ماند`);
    assert.equal(ocr._test.active(), 0);
  }
  assert.deepEqual(leftovers(), []);
});

test('درخواست‌های هم‌زمان وضعیت موتور فقط یک بار زبان‌ها را می‌خوانند', async () => {
  await setFlag(shop.shopId, true);
  setMode({ probeDelay: 300 });
  const responses = await Promise.all(Array.from({ length: 12 }, () => config(shop.owner)));
  for (const response of responses) {
    assert.equal(response.status, 200);
    assert.equal(response.body.available, true);
  }
  assert.equal(fs.readFileSync(path.join(FAKE, 'probes.log'), 'utf8'), 'probe\n');
});

test('همزمانی ۱: درخواستِ دوم در حینِ اولی ۵۰۳ ِ ocr_busy با Retry-After، بی صف', async () => {
  await setFlag(shop.shopId, true);
  setMode({ run: 'sleep' });
  ocr._test.setTimeoutMs(2500);
  const first = send(shop.owner, photo());
  await new Promise((r) => setTimeout(r, 400));
  const second = await send(shop.manager, photo());
  assert.equal(second.status, 503);
  assert.equal(code(second), 'ocr_busy');
  assert.equal(second.headers.get('retry-after'), '5');
  assert.equal(code(await first), 'ocr_timeout');
  assert.equal(ocr._test.active(), 0);
});

test('سقفِ نرخِ دکان: بیستمین می‌گذرد، بیست‌ویکمین ۴۲۹ — پیش از خواندنِ بدنه', async () => {
  await setFlag(shop.shopId, true);
  const tiny = Buffer.from('not an image at all — just bytes', 'utf8');
  for (let i = 0; i < 20; i++) assert.equal((await send(shop.owner, tiny)).status, 415);
  const r = await send(shop.manager, tiny);
  assert.equal(r.status, 429);
  assert.equal(code(r), 'rate_limited');
});

test('tsvLines (خالص): واژه‌ها به ترتیبِ خواندن، میانگینِ اطمینان، بی نشانهٔ جهت، فقط سطحِ ۵', () => {
  assert.deepEqual(ocr.tsvLines(TSV), [
    { text: '۱ برنج سیلا ۱۰ ۹۵۰ ۹٬۵۰۰', conf: 91 },
    { text: 'جمع کل ۹٬۵۰۰', conf: 75 },
  ]);
  assert.deepEqual(ocr.tsvLines(''), []);
  assert.deepEqual(ocr.tsvLines('5\t1\t1\t1\t1\t1\t0\t0\t1\t1\t50\t   \n'), []);
  const many = Array.from({ length: 500 }, (_, i) => `5\t1\t${i}\t1\t1\t1\t0\t0\t1\t1\t50\tw${i}`).join('\n');
  assert.equal(ocr.tsvLines(many).length, ocr.LIMITS.LINES_MAX);
});

/** CI ِ PostgreSQL موتور واقعی را می‌خواهد؛ اجرای محلی بدون موتور باز هم سنجه‌های بالا را اجرا می‌کند. */
const real = (() => {
  const env = { PATH: process.env.PATH, OMP_THREAD_LIMIT: '1' };
  if (process.env.TESSDATA_PREFIX) env.TESSDATA_PREFIX = process.env.TESSDATA_PREFIX;
  const r = spawnSync('tesseract', ['--list-langs'], { encoding: 'utf8', env, timeout: 5000 });
  return r.status === 0 && /^fas$/m.test(`${r.stdout}\n${r.stderr}`);
})();
const requireReal = process.env.PURCHASE_OCR_REQUIRE_REAL === '1';
test('Tesseract ِ واقعی: عکسِ فاکتورِ فارسیِ نمونه ⇒ ردیف‌های خواندنی', { skip: !real && !requireReal && 'tesseract با داده‌ی fas نصب نیست' }, async () => {
  assert.ok(real, 'tesseract با داده‌ی fas باید نصب باشد');
  await setFlag(shop.shopId, true);
  delete process.env.TESSERACT_BIN;
  ocr._test.reset();
  const buf = fs.readFileSync(path.join(__dirname, 'fixtures', 'purchase-ocr', 'invoice-fa.jpg'));
  const r = await send(shop.owner, buf);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const text = r.body.lines.map((l) => l.text).join('\n');
  // مجموعه‌داده‌های مختلف فارسی گاهی یک نام را اشتباه می‌خوانند. دست‌کم دو نام و جمع باید در خط‌های مجزا برسند.
  const products = ['برنج', 'روغن', 'چای', 'شکر'];
  assert.ok(r.body.lines.filter((line) => products.some((word) => line.text.includes(word))).length >= 2, text);
  assert.match(text, /جمع/);
  assert.ok(r.body.lines.length >= 6, text);
  assert.deepEqual(leftovers(), []);
});
