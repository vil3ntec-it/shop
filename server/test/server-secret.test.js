'use strict';
/**
 * رازِ سرور — `lib/server-secret.js` و هر ماژولی که از آن می‌خواند.
 *
 * چیزی که باید ثابت شود: جایگزین کردنِ کپی‌های `pepper()` با یک تابع،
 * هیچ هشِ ذخیره‌شده‌ای را عوض نکرده است. مقدارهای مرجع در
 * `fixtures/server-secret-golden.json` از کدِ پیش از این تغییر گرفته شده‌اند،
 * برای هر هشت حالتِ پُر/خالی بودنِ API_SECRET، JWT_SECRET و OTP_SECRET.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const config = require('../src/config');
const { serverSecret } = require('../src/lib/server-secret');
const golden = require('./fixtures/server-secret-golden.json');

const vipCodes = require('../src/lib/vip-codes');
const otp = require('../src/lib/otp');
const loginCodes = require('../src/lib/login-codes');
const managedApps = require('../src/lib/managed-apps');
const stationDevices = require('../src/lib/station-devices');
const stations = require('../src/lib/stations');

const staffCodes = require('../src/lib/staff-codes');

const NAMES = ['api', 'jwt', 'otp'];
const original = { ...config.secrets };
const originalStationKey = process.env.STATION_KEY;

/** `set` ⇒ مقدارِ مرجع؛ `blank` ⇒ رازِ فقط‌فاصله؛ بقیه خالی */
function useSecrets(set, blank = []) {
  for (const name of NAMES) {
    config.secrets[name] = blank.includes(name) ? golden.blankValue : set.includes(name) ? golden.secretValues[name] : '';
  }
}

/**
 * delivery.js هنوز pepper()ِ خودش را دارد و codeHash را بیرون نمی‌دهد؛ چون
 * شاخه‌های باز آن را عوض می‌کنند، این‌جا دست نمی‌خورد. پس همان دو تابعِ
 * خصوصی از متنِ خودِ فایل، در دامنهٔ خودِ ماژول، خوانده می‌شوند — تا وقتی
 * delivery.js به serverSecret برود و codeHash را بیرون بدهد.
 */
const deliveryCodeHash = (() => {
  const file = require.resolve('../src/lib/delivery');
  const m = new Module(file, module);
  m.filename = file;
  m.paths = Module._nodeModulePaths(path.dirname(file));
  m._compile(`${fs.readFileSync(file, 'utf8')}\nmodule.exports = { codeHash };\n`, file);
  return m.exports.codeHash;
})();

/** هر صدازننده با همان ورودی‌های مرجع */
function assertGolden(c) {
  const i = golden.inputs;
  assert.equal(vipCodes.hashCode(i.vipCode), c.vipCodeHash, 'vip-codes');
  assert.equal(otp.hashCode(...i.otp), c.otpHash, 'otp');
  assert.equal(loginCodes.hashCode(...i.loginCode), c.loginCodeHash, 'login-codes');
  assert.equal(managedApps.hashKey(i.managedAppKey), c.managedAppKeyHash, 'managed-apps');
  assert.equal(stationDevices.hashJoin(i.stationJoinCode), c.stationJoinHash, 'station-devices');
  //  دو کپیِ هنوز بازسازی‌نشده: مقدارِ مرجع پیش از بازسازی‌شان گرفته شد
  assert.equal(deliveryCodeHash(...i.deliveryCode), c.deliveryCodeHash, 'delivery');
  assert.equal(staffCodes.hashCode(i.staffCode), c.staffCodeHash, 'staff-codes hashCode');
  assert.equal(staffCodes.deriveStanding(...i.staffStanding), c.staffStanding, 'staff-codes deriveStanding');
}

test.after(() => {
  Object.assign(config.secrets, original);
  if (originalStationKey === undefined) delete process.env.STATION_KEY;
  else process.env.STATION_KEY = originalStationKey;
  stations._forgetKey();
});

test('serverSecret: first non-empty secret in the given order, else the fallback', () => {
  useSecrets(['api', 'jwt', 'otp']);
  assert.equal(serverSecret(['otp', 'api'], 'fb'), golden.secretValues.otp);
  assert.equal(serverSecret(['api', 'otp'], 'fb'), golden.secretValues.api);
  useSecrets(['jwt']);
  assert.equal(serverSecret(['api', 'otp', 'jwt'], 'fb'), golden.secretValues.jwt);
  assert.equal(serverSecret(['otp', 'api'], 'fb'), 'fb');
  useSecrets([]);
  assert.equal(serverSecret(['api', 'jwt'], 'fb'), 'fb');
});

test('serverSecret: reads config at call time, not at load time', () => {
  useSecrets([]);
  assert.equal(serverSecret(['api'], 'fb'), 'fb');
  useSecrets(['api']);
  assert.equal(serverSecret(['api'], 'fb'), golden.secretValues.api);
});

test('serverSecret: an unknown secret name is a programming error', () => {
  assert.throws(() => serverSecret(['API'], 'fb'), /unknown server secret: API/);
  assert.throws(() => serverSecret(['toString'], 'fb'), /unknown server secret/);
});

test('serverSecret: every name is checked before reading — a typo after a set secret still throws', () => {
  useSecrets(['api']);
  assert.throws(() => serverSecret(['api', 'APi'], 'fb'), /unknown server secret: APi/);
  assert.throws(() => serverSecret(['api', 'jwt', 'otpp', 'x'], 'fb'), /unknown server secret: otpp, x/);
});

test('serverSecret: a whitespace-only secret is still a secret (not trimmed, not skipped)', () => {
  useSecrets(['jwt'], ['api']);
  assert.equal(serverSecret(['api', 'jwt'], 'fb'), golden.blankValue);
});

test('golden fixture covers all eight set/unset combinations', () => {
  const seen = new Set(golden.cases.map((c) => [...c.set].sort().join('+')));
  assert.equal(seen.size, 8);
});

for (const c of golden.cases) {
  const label = c.set.join('+') || 'none';

  test(`byte-identical hashes after refactor [${label}]`, () => {
    useSecrets(c.set);
    assertGolden(c);
  });

  test(`stations: a row sealed with the old legacy key still opens [${label}]`, async () => {
    useSecrets(c.set);
    //  کلیدِ تازه هرگز این ردیف را باز نمی‌کند؛ پس فقط کلیدِ نسلِ اول می‌تواند.
    process.env.STATION_KEY = 'server-secret-test-current-key';
    stations._forgetKey();
    assert.equal(await stations.decryptKey(c.stationLegacySealed), golden.inputs.stationReadKey);
  });
}

test('stations: the legacy key of another secret set does not open the row', async () => {
  const sealedWithApi = golden.cases.find((c) => c.set.length === 1 && c.set[0] === 'api').stationLegacySealed;
  useSecrets(['jwt']);
  process.env.STATION_KEY = 'server-secret-test-current-key';
  stations._forgetKey();
  assert.equal(await stations.decryptKey(sealedWithApi), '');
});

for (const c of golden.whitespaceCases) {
  const label = `${c.whitespace.join('+')} blank, ${c.set.join('+') || 'none'} set`;

  test(`whitespace-only secret: same hashes as before the refactor [${label}]`, async () => {
    useSecrets(c.set, c.whitespace);
    assertGolden(c);
    process.env.STATION_KEY = 'server-secret-test-current-key';
    stations._forgetKey();
    assert.equal(await stations.decryptKey(c.stationLegacySealed), golden.inputs.stationReadKey);
  });
}
