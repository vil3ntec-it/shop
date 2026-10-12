'use strict';
/**
 * جای باتِ هر برنامه (lib/bots.js) — باتِ پمپ در ریپوی پمپ است.
 * بی پوشهٔ bots/pump سرورِ حساب بی‌صدا «بی بات» بالا می‌آید و هیچ مسیری نمی‌افتد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const fs = require('fs');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const bots = require('../src/lib/bots');

const HAS_BOT = !!bots.pumpDir();

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'botadmin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(() => h.stop());

test('⛔ هیچ جای سرورِ حساب lib/telegram را مستقیم صدا نمی‌زند — فقط bots.pump()', () => {
  const src = path.join(__dirname, '..', 'src');
  const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]));
  const bad = walk(src).filter((f) => f.endsWith('.js') && /require\(['"][./]*(lib\/)?telegram['"]\)/.test(fs.readFileSync(f, 'utf8')));
  assert.deepEqual(bad, []);
  assert.equal(fs.existsSync(path.join(src, 'lib', 'telegram.js')), false, 'کدِ بات در ریپوی پمپ است، نه این‌جا');
});

test('بی پوشهٔ باتِ پمپ: سرور بالا می‌آید، پنل «نصب نیست» می‌بیند و درِ عمومی خالی است', { skip: HAS_BOT && 'باتِ پمپ نصب است' }, async () => {
  const bot = bots.pump();
  assert.equal(bot.available, false);
  assert.deepEqual(await bot.relayCustomer({}), { sent: false, why: 'no_bot' });
  const login = await h.post('/api/admin/login', { username: 'botadmin', password: 'Admin!12345' });
  const st = await h.get('/api/admin/telegram', { token: login.body.token });
  assert.equal(st.status, 200);
  assert.equal(st.body.telegram.missing, true);
  assert.equal(st.body.telegram.configured, false);
  const put = await h.put('/api/admin/telegram', { enabled: true }, { token: login.body.token });
  assert.equal(put.status, 400, 'تنظیمِ باتِ نصب‌نشده پذیرفته نمی‌شود');
  const pub = await h.get('/api/pump/public/telegram');
  assert.equal(pub.status, 200);
  assert.equal(pub.body.username, '');
});
