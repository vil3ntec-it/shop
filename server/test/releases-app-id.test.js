'use strict';
/**
 * شناسهٔ نصبِ برنامهٔ دکان (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴): `ir.vil3ntec.tohid` ⇐ `com.vill3n.dailyshop`.
 *
 *   • `appId` ِ `/api/releases/<برچسب>/latest` از `app-id.txt` ِ انتشار؛ نبود یا ناجور ⇒ «»
 *   • هر دو شناسه پذیرفته‌اند: برنامهٔ قدیمی (بی `app`) و برنامهٔ تازه (`app=…`) هر دو همان انتشار را می‌گیرند
 *   • برنامهٔ پیشین که برگهٔ راهنما ندارد، هشدارِ گذار را سرِ `notes` می‌بیند؛ برنامه‌ای که `app` می‌فرستد نه
 *   • نصبیِ شناسهٔ قدیمی (پلِ گذار) برای هیچ‌کس هشدار ندارد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

let fake, h;
//  محتوای `app-id.txt` ِ انتشارِ پایدار — null ⇒ انتشار آن را ندارد (انتشارِ پیش از تصمیم)
let stableId = 'com.vill3n.dailyshop\n';
let stamp = 0;
const APK = Buffer.from('fake-apk-bytes-0123456789');

test.before(async () => {
  fake = http.createServer((req, res) => {
    if (req.url === '/repos/o/r/releases/tags/tohid-native') {
      res.setHeader('Content-Type', 'application/json');
      const assets = [
        { id: 1, name: 'Tohid-Native-3.2.80.apk', size: APK.length, updated_at: '2026-11-15T00:00:00Z' },
        { id: 2, name: 'version.txt', size: 7, updated_at: '2026-11-15T00:00:00Z' },
      ];
      //  زمانِ به‌روزشدن با محتوا عوض می‌شود — درست مثلِ GitHub، تا کشِ دیسکِ سرور تازه شود
      if (stableId != null) assets.push({ id: 3, name: 'app-id.txt', size: Buffer.byteLength(stableId), updated_at: `2026-11-15T00:00:0${stamp}Z` });
      return res.end(JSON.stringify({ body: 'یادداشتِ نسخه', published_at: '2026-11-15T00:00:00Z', assets }));
    }
    if (req.url === '/repos/o/r/releases/assets/1') return res.end(APK);
    if (req.url === '/repos/o/r/releases/assets/2') return res.end('3.2.80\n');
    if (req.url === '/repos/o/r/releases/assets/3') return res.end(stableId || '');
    res.statusCode = 404; res.end('{}');
  });
  await new Promise(r => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-appid-'));
  h = require('./helpers');
  await h.start();
});
test.after(async () => { await h.stop(); fake.close(); });

/** انتشارِ تازه با `app-id.txt` ِ دیگر — کشِ حافظهٔ سرور پاک و زمانِ فایل جلو می‌رود */
function publish(id) {
  stableId = id;
  stamp++;
  require('../src/routes/releases')._cache.clear();
}

const latest = (q = '') => h.api('GET', `/api/releases/tohid-native/latest${q}`);

test('انتشارِ شناسهٔ تازه: appId همان؛ برنامهٔ تازه یادداشتِ دست‌نخورده می‌گیرد', async () => {
  publish('com.vill3n.dailyshop\n');
  const r = await latest('?app=com.vill3n.dailyshop&staged=1');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.appId, 'com.vill3n.dailyshop');
  assert.equal(r.body.version, '3.2.80');
  assert.equal(r.body.notes, 'یادداشتِ نسخه');
});

test('برنامهٔ شناسهٔ قدیمی با برگهٔ راهنما (پلِ گذار، app=ir.vil3ntec.tohid): همان انتشار، بی هشدارِ سرور', async () => {
  publish('com.vill3n.dailyshop');
  const r = await latest('?app=ir.vil3ntec.tohid&staged=1');
  assert.equal(r.status, 200);
  assert.equal(r.body.appId, 'com.vill3n.dailyshop');
  assert.equal(r.body.notes, 'یادداشتِ نسخه');
  assert.deepEqual(r.body.assets.map(a => a.name), ['Tohid-Native-3.2.80.apk', 'version.txt', 'app-id.txt']);
});

test('برنامهٔ پیشین (بی app) پذیرفته است و هشدارِ گذار را سرِ یادداشت می‌بیند — در ۴۰۰ نویسهٔ اول', async () => {
  publish('com.vill3n.dailyshop');
  const r = await latest('?staged=1');
  assert.equal(r.status, 200);
  assert.equal(r.body.appId, 'com.vill3n.dailyshop');
  assert.match(r.body.notes, /^⚠️ این نسخه برنامهٔ تازه‌ای با نامِ تازه است/);
  assert.match(r.body.notes, /یادداشتِ نسخه$/);
  //  برنامهٔ پیشین فقط `notes.take(400)` را نشان می‌دهد
  assert.ok(r.body.notes.indexOf('برنامهٔ قدیمی را پاک نکنید') < 400);
  //  `app` ِ ناجور = برنامهٔ پیشین
  assert.match((await latest('?app=..%2Fx')).body.notes, /^⚠️/);
});

test('نصبیِ شناسهٔ قدیمی (پلِ گذار) برای هیچ برنامه‌ای هشدار ندارد', async () => {
  publish('ir.vil3ntec.tohid\n');
  for (const q of ['', '?app=ir.vil3ntec.tohid', '?app=com.vill3n.dailyshop']) {
    const r = await latest(q);
    assert.equal(r.status, 200);
    assert.equal(r.body.appId, 'ir.vil3ntec.tohid');
    assert.equal(r.body.notes, 'یادداشتِ نسخه', q);
  }
});

test('انتشارِ بی app-id.txt یا با محتوای ناجور: appId خالی و همان پاسخِ پیشین', async () => {
  publish(null);
  let r = await latest();
  assert.equal(r.status, 200);
  assert.equal(r.body.appId, '');
  assert.equal(r.body.notes, 'یادداشتِ نسخه');
  publish('<html>not found</html>');
  r = await latest('?app=com.vill3n.dailyshop');
  assert.equal(r.status, 200);
  assert.equal(r.body.appId, '');
  assert.equal(r.body.notes, 'یادداشتِ نسخه');
});

test('خودِ app-id.txt هم از درِ فایلِ انتشار داده می‌شود (راهِ GitHub ِ برنامه همین را می‌خواند)', async () => {
  publish('com.vill3n.dailyshop\n');
  const f = await h.download('/api/releases/tohid-native/file/app-id.txt');
  assert.equal(f.status, 200);
  assert.equal(Buffer.from(f.buffer).toString('utf8').trim(), 'com.vill3n.dailyshop');
});
