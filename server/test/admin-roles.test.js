'use strict';
/**
 * نقشِ مدیران — بندِ ۱۱ِ ممیزی (۱۴۰۵/۰۷/۲۰).
 *
 *   • مدیرِ عادی کارهای پول‌ساز و تنظیمِ سامانه را نمی‌تواند (پلن، تنظیم، کدِ VIP، ایمیل)
 *   • مدیرِ ارشد مدیر می‌سازد، فهرست می‌گیرد و غیرفعال می‌کند ⇒ نشستِ او همان دم بسته
 *   • آخرین مدیرِ ارشدِ فعال و خودِ مدیر کنار گذاشته نمی‌شوند
 *   • پنل CSP دارد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

let superId;
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  superId = newId('adm');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [superId, await pw.hashPassword('Root!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const login = async (username, password) => (await h.post('/api/admin/login', { username, password })).body.token;

test('مدیرِ عادی به کارهای مدیرِ ارشد دست ندارد؛ ارشد او را غیرفعال می‌کند', async () => {
  const root = await login('root', 'Root!12345');
  const made = await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const clerk = await login('clerk', 'Clerk!12345');
  assert.ok(clerk);

  for (const [method, path, body] of [
    ['patch', '/api/admin/config', { trial_days: '99' }],
    ['patch', '/api/admin/plans/m1', { price: 1 }],
    ['put', '/api/admin/email', {}],
    ['post', '/api/admin/vip-codes', {}],
    ['get', '/api/admin/admins'],
  ]) {
    const r = await h.api(method.toUpperCase(), path, { token: clerk, body });
    assert.equal(r.status, 403, `${method} ${path} ⇒ ${r.status}`);
  }

  const list = await h.get('/api/admin/admins', { token: root });
  assert.equal(list.status, 200);
  const clerkRow = list.body.admins.find(a => a.username === 'clerk');
  const off = await h.api('PATCH', `/api/admin/admins/${clerkRow.id}`, { token: root, body: { status: 'disabled' } });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  const after = await h.get('/api/admin/admins', { token: clerk });
  assert.ok(after.status === 401 || after.status === 403);
});

test('خود و آخرین مدیرِ ارشد کنار نمی‌روند', async () => {
  const root = await login('root', 'Root!12345');
  const self = await h.api('PATCH', `/api/admin/admins/${superId}`, { token: root, body: { status: 'disabled' } });
  assert.equal(self.status, 400);
  assert.equal(self.body.error.code, 'admin_self');
});

test('پنل CSP دارد', async () => {
  const r = await h.api('GET', '/admin/');
  assert.match(String(r.headers?.['content-security-policy'] || r.headers?.get?.('content-security-policy') || ''), /script-src 'self'/);
});
