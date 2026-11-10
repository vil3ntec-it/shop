/**
 * «محیطِ جدا» برای پشتیبانِ سرور — REC-01 (`recovery`)، E2E-OPS-14 (`ops`) و NOTIF-08 (`notify`).
 *
 * هر دو قالبِ `src/lib/backup.js`، از روی نامِ فایل (همان قاعدهٔ `backup.restore`):
 *   • PGlite (`*.pglite.tar.gz[.enc]`، سرورِ خانگی): رمزگشایی ⇒ tar.gz ⇒ پوشهٔ تازه با `PGlite.create({ loadDataDir })`
 *     در فرایندِ جدا (`control-sums.mjs load`) — همان راهی که پیامِ `backup.restore` برای PGlite می‌گوید؛
 *   • PostgreSQL (`*.sql.gz[.enc]`، VPS؛ `E2E_DATABASE_URL`): دیتابیسِ تازه و خالی روی همان سرور (`<نام>_<سناریو>_rtest`) و
 *     `node scripts/restore.js <فایل> <نشانی>` در فرایندِ جدا — همان دستورِ بازیابیِ صاحب‌کار (psql، همه یا هیچ).
 * ⇒ DATABASE_URL ِ مقصد (`pglite:<پوشه>` یا `postgres://…`). دیتابیسِ PostgreSQL در `E.finish` می‌افتد؛ ماندهٔ اجرای پیشین پیش از ساختن.
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const require = createRequire(import.meta.url);
const SERVER = path.join(import.meta.dirname, '..', '..', '..');
const SUMS = path.join(import.meta.dirname, 'control-sums.mjs');

export const isPgliteBackup = (file) => /\.pglite\.tar\.gz(\.enc)?$/.test(String(file));
export const isPgUrl = (url) => /^postgres(ql)?:\/\//.test(String(url));

/** فرایندِ node ِ جدا ⇒ {code, out} */
function node(args, ms = 180_000) {
  return new Promise((resolve, reject) => {
    const c = spawn(process.execPath, args, { env: process.env, stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    c.stdout.on('data', (d) => { out += d; });
    c.stderr.on('data', (d) => { out += d; });
    const t = setTimeout(() => { c.kill('SIGKILL'); reject(new Error(`بیش از ${ms / 1000} ثانیه: ${args.join(' ')}\n${out.slice(-600)}`)); }, ms);
    c.on('exit', (code) => { clearTimeout(t); resolve({ code, out }); });
  });
}

export async function pgQuery(url, text, params) {
  const { Client } = require('pg');
  const c = new Client({ connectionString: url });
  await c.connect();
  try { return (await c.query(text, params)).rows; } finally { await c.end(); }
}

/** نشانیِ دیتابیسِ مقصد روی همان سرورِ PostgreSQL؛ نام همیشه `test` دارد (قاعدهٔ دیتابیسِ مشترک) */
export function pgTarget(base, label) {
  const u = new URL(base);
  const name = `${decodeURIComponent(u.pathname.slice(1)).slice(0, 40)}_${label}_rtest`.toLowerCase().replace(/[^a-z0-9_]/g, '_');
  u.pathname = `/${name}`;
  return { name, url: u.toString() };
}

/**
 * پشتیبانِ `file` را در محیطِ جدا برمی‌گرداند.
 * @param {object} E هارنس (`boot`)
 * @param {string} file فایلِ پشتیبان (`backup.run(...).file`)
 * @param {string} dir پوشهٔ تازه برای قالبِ PGlite (نباید وجود داشته باشد یا باید خالی باشد)
 */
export async function restoreCopy(E, file, dir) {
  if (isPgliteBackup(file)) {
    const backup = require('../../../src/lib/backup');
    fs.mkdirSync(path.dirname(dir), { recursive: true });
    const tgz = `${dir}.tar.gz`;
    fs.writeFileSync(tgz, zlib.gzipSync(await backup.decode(file)));
    const r = await node([SUMS, 'load', dir, tgz]);
    if (r.code !== 0) throw new Error(`بارِ پشتیبانِ PGlite (${r.code}): ${r.out.slice(-600)}`);
    return `pglite:${dir}`;
  }
  const base = process.env.DATABASE_URL;
  if (!isPgUrl(base)) throw new Error(`پشتیبانِ PostgreSQL (${path.basename(file)}) بی سرورِ PostgreSQL`);
  const t = pgTarget(base, E.name);
  const drop = () => pgQuery(base, `DROP DATABASE IF EXISTS "${t.name}" WITH (FORCE)`);
  await drop();
  await pgQuery(base, `CREATE DATABASE "${t.name}"`);
  E.onFinish(drop);
  const r = await node([path.join(SERVER, 'scripts', 'restore.js'), file, t.url]);
  if (r.code !== 0) throw new Error(`scripts/restore.js (${r.code}): ${r.out.slice(-600)}`);
  return t.url;
}

/**
 * «برق رفت» روی PostgreSQL سرورِ دیتابیس را هم می‌برد: جلسه‌های بازِ فرایندِ کشته (تراکنشِ نیمه‌کاره) بسته و برگشت
 * داده می‌شوند — بی این، جلسهٔ یتیم تا پایانِ دستورش قفل‌ها را نگه می‌دارد. PGlite درونِ همان فرایندِ کشته بود (هیچ).
 */
export async function cutPower(url) {
  if (!isPgUrl(url)) return;
  const name = decodeURIComponent(new URL(url).pathname.slice(1));
  const admin = process.env.DATABASE_URL;
  await pgQuery(admin, 'SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = $1 AND pid <> pg_backend_pid()', [name]);
  for (let i = 0; i < 100; i++) {
    const [r] = await pgQuery(admin, 'SELECT count(*)::int AS n FROM pg_stat_activity WHERE datname = $1', [name]);
    if (!r.n) return;
    await new Promise((res) => setTimeout(res, 100));
  }
  throw new Error(`جلسه‌های ${name} بسته نشدند`);
}
