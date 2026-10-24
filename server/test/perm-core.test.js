'use strict';
/**
 * هستهٔ سنجشِ مجوز (STF-01..03) — بردارهای مشترکِ `core/test-vectors/perm-v1.json`.
 *
 * همان فایل را `PermCore.kt` (core/commonMain) در آزمونِ اندروید و کامپیوتر
 * می‌دواند. سه ادعا:
 *   ۱. `license/perm-core.js` (وب) و `server/src/lib/perm-core.js` بایت‌به‌بایت یکی‌اند؛
 *   ۲. هر دو روی همهٔ بردارها همان پاسخ را می‌دهند؛
 *   ۳. آزمونِ مرجع: پیش‌فرضِ هر نقش برای کلیدهای مشترک دقیقاً همان
 *      `MATRIX`ِ امروز است — بی لایه، هیچ‌کس مجوزی نمی‌گیرد و از دست نمی‌دهد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const WEB = path.join(ROOT, 'license', 'perm-core.js');
const SRV = path.join(__dirname, '..', 'src', 'lib', 'perm-core.js');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'perm-v1.json'), 'utf8'));
const { MATRIX, ROLES, can, permissionsOf } = require('../src/lib/permissions');

test('نسخهٔ سرور عیناً همان نسخهٔ وب است', () => {
  assert.equal(fs.readFileSync(SRV, 'utf8'), fs.readFileSync(WEB, 'utf8'));
});

for (const [name, file] of [['وب', WEB], ['سرور', SRV]]) {
  const P = require(file);

  test(`بردارهای perm-v1 روی نسخهٔ ${name}`, () => {
    assert.equal(P.VERSION, V.version);
    assert.deepEqual(P.keys(), V.keys);
    for (const c of V.defaults) assert.equal(P.defaultMode(c.role, c.key), c.out, JSON.stringify(c));
    for (const c of V.resolve) assert.deepEqual(P.resolve(c.role, c.layers, c.key), c.out, JSON.stringify(c));
    for (const c of V.evaluate) assert.deepEqual(P.evaluate(c.role, c.layers, c.key, c.req), c.out, JSON.stringify(c));
    for (const c of V.effective) assert.deepEqual(P.effective(c.role, c.layers), c.out, JSON.stringify(c));
    for (const c of V.validate) assert.equal(P.validate(c.role, c.key, c.override), c.out, JSON.stringify(c));
    for (const c of V.discount_bp) assert.equal(P.discountBp(c.discount, c.total), c.out, JSON.stringify(c));
  });
}

test('آزمونِ مرجع: پیش‌فرضِ هسته = MATRIXِ امروز برای هر نقش و هر کلیدِ مشترک', () => {
  const P = require(SRV);
  const shared = P.keys().filter((k) => MATRIX[k]);
  //  پنج کلید از هسته در MATRIX هستند؛ `reports.money` فقط در برنامه است
  assert.deepEqual(shared, ['audit.view', 'safe.move', 'sales.cancel', 'sales.discount', 'settings.write']);
  for (const role of ROLES) {
    for (const k of shared) {
      assert.equal(P.allows(role, [], k), can(role, k), `${role} / ${k}`);
      assert.equal(P.defaultMode(role, k), can(role, k) ? 'allow' : 'deny', `${role} / ${k}`);
    }
  }
});

test('آزمونِ مرجع: فهرستِ قدیمیِ permissions بی لایه دقیقاً همان امروز است', () => {
  const perms = require('../src/lib/staff-perms');
  for (const role of ROLES) {
    assert.deepEqual(perms.legacyPermissions(role, [{}, {}]), permissionsOf(role), role);
  }
});

test('لایه هرگز چیزی بیرون از نقش به فهرستِ قدیمی نمی‌افزاید', () => {
  const perms = require('../src/lib/staff-perms');
  const grant = {};
  for (const k of Object.keys(MATRIX)) grant[k] = { mode: 'allow' };
  for (const role of ROLES) {
    const got = perms.legacyPermissions(role, [grant, grant]);
    assert.deepEqual(got, permissionsOf(role), role);
  }
});
