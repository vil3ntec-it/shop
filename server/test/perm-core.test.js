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
    for (const c of V.writes) assert.equal(P.canWrite(c.role, c.table), c.out, JSON.stringify(c));
    for (const c of V.writable) assert.deepEqual(P.writable(c.role), c.out, JSON.stringify(c));
    assert.deepEqual(P.RETURN_ADJ_KINDS, V.return_adj_kinds);
    for (const c of V.row_rules) assert.equal(P.rowRule(c.role, c.table), c.out, JSON.stringify(c));
    assert.deepEqual(P.DOC_MOVE_TYPES, V.doc_move_types);
    for (const c of V.rows) {
      const got = P.checkRows(c.role, c.rows, c.refs);
      assert.deepEqual(got.map((r) => r.reason), c.out, c._);
      assert.deepEqual(got.map((r) => r.ok), c.out.map((r) => r === ''), c._);
      //  تک‌ردیف همان دسته‌ای است که فقط همان ردیف را دارد
      if (c.rows.length === 1) assert.equal(P.canWriteRow(c.role, c.rows[0].table, c.rows[0].row, c.refs), c.out[0] === '', c._);
    }
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

test('هر کلید برای هر هفت نقش پیش‌فرضِ صریح دارد و بردارها همه را می‌پوشانند (بازبینی، یافتهٔ ۴)', () => {
  //  کلیدی که فقط سه نقشِ قدیم را بدهد (مثلِ `safe.move` ِ #364) نقشِ تازه را بی پیش‌فرض می‌گذاشت و
  //  `evaluate` به «allow» می‌رسید. حالا `d()` همان‌جا می‌شکند و این آزمون پوشش را قفل می‌کند.
  const P = require(SRV);
  const covered = new Set(V.defaults.map((c) => `${c.role}/${c.key}`));
  for (const key of P.keys()) {
    for (const role of P.ROLES) {
      assert.ok(['allow', 'approve', 'deny'].includes(P.defaultMode(role, key)), `${role} / ${key}`);
      assert.ok(covered.has(`${role}/${key}`), `بردارِ پیش‌فرض نیست: ${role} / ${key}`);
    }
  }
  //  گاوصندوق در حدِ هیچ نقشِ تازه‌ای نیست
  for (const role of ['cashier', 'storekeeper', 'picker', 'accountant']) assert.equal(P.canWrite(role, 'safeEntries'), false, role);
});

test('d(): کلیدِ کم‌نقش یا حالتِ نادرست هنگامِ بارگذاری می‌شکند، نه بی‌صدا «allow» (یافتهٔ ۴)', () => {
  //  همان فایل با یک کلیدِ سه‌نقشیِ ساختگی، در محیطِ جدا
  const vm = require('node:vm');
  const src = fs.readFileSync(SRV, 'utf8');
  const anchor = "    'sales.discount': {";
  assert.ok(src.includes(anchor));
  const load = (inject) => {
    const sandbox = { module: { exports: {} } };
    sandbox.exports = sandbox.module.exports;
    vm.runInNewContext(src.replace(anchor, `${inject}\n${anchor}`), sandbox);
    return sandbox.module.exports;
  };
  assert.throws(() => load("    'x.three': { defaults: d('allow', 'allow', 'deny'), edit: 'restrict', approvable: false, caps: [], display: false },"), /d\(\) needs 7/);
  assert.throws(() => load("    'x.typo': { defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'dney'), edit: 'restrict', approvable: false, caps: [], display: false },"), /bad mode/);
  //  کنترل: کلیدِ کامل بار می‌شود و برای نقش‌های تازه «deny» است
  const ok = load("    'x.full': { defaults: d('allow', 'allow', 'deny', 'deny', 'deny', 'deny', 'deny'), edit: 'restrict', approvable: false, caps: [], display: false },");
  assert.equal(ok.evaluate('cashier', [], 'x.full').decision, 'deny');
});

test('نقش‌های هسته همان نقش‌های MATRIX‌اند و «نوشتن» در هر دو یکی است (A16)', () => {
  const P = require(SRV);
  assert.deepEqual(P.ROLES, ROLES);
  for (const role of ROLES) {
    const w = P.writable(role);
    assert.ok(w === '*' || Array.isArray(w), role);
    //  data.write ⇔ دست‌کم یک جدول؛ حسابدار هیچ
    assert.equal(can(role, 'data.write'), w === '*' || w.length > 0, role);
  }
  //  سه نقشِ قدیم همه‌چیز را مثلِ امروز می‌نویسند
  for (const role of ['owner', 'manager', 'staff']) assert.equal(P.writable(role), '*', role);
  //  پیک در هسته نیست: توکنش دامنهٔ خودش را دارد
  assert.equal(P.canWrite('courier', 'sales'), false);
});
