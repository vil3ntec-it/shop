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

const config = require('../src/config');
const { serverSecret } = require('../src/lib/server-secret');
const golden = require('./fixtures/server-secret-golden.json');

const vipCodes = require('../src/lib/vip-codes');
const otp = require('../src/lib/otp');
const loginCodes = require('../src/lib/login-codes');
const managedApps = require('../src/lib/managed-apps');
const stationDevices = require('../src/lib/station-devices');
const stations = require('../src/lib/stations');

const NAMES = ['api', 'jwt', 'otp'];
const original = { ...config.secrets };
const originalStationKey = process.env.STATION_KEY;

function useSecrets(set) {
  for (const name of NAMES) config.secrets[name] = set.includes(name) ? golden.secretValues[name] : '';
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

test('golden fixture covers all eight set/unset combinations', () => {
  const seen = new Set(golden.cases.map((c) => [...c.set].sort().join('+')));
  assert.equal(seen.size, 8);
});

for (const c of golden.cases) {
  const label = c.set.join('+') || 'none';

  test(`byte-identical hashes after refactor [${label}]`, () => {
    useSecrets(c.set);
    const i = golden.inputs;
    assert.equal(vipCodes.hashCode(i.vipCode), c.vipCodeHash, 'vip-codes');
    assert.equal(otp.hashCode(...i.otp), c.otpHash, 'otp');
    assert.equal(loginCodes.hashCode(...i.loginCode), c.loginCodeHash, 'login-codes');
    assert.equal(managedApps.hashKey(i.managedAppKey), c.managedAppKeyHash, 'managed-apps');
    assert.equal(stationDevices.hashJoin(i.stationJoinCode), c.stationJoinHash, 'station-devices');
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
