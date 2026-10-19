/**
 * «سرورِ تازه پس از بازیابی» — فرایندِ جدا برای `ops.e2e.mjs` (E2E-OPS-14).
 *
 * همان راهی که پیامِ `backup.restore` برای PGlite می‌گوید: پوشهٔ دیتابیسِ خالی، فایلِ
 * پشتیبان (رمزگشایی‌شده) با `PGlite.create({ loadDataDir })`، و بعد سرور روی همان پوشه.
 *
 *   node restored-server.mjs <پوشهٔ دیتابیس> <فایلِ tar.gz ِ رمزگشایی‌شده>
 *   ⇐ روی stdout: READY <port>
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const [dir, tarGz] = process.argv.slice(2);
const { PGlite } = require('@electric-sql/pglite');

const blob = new File([fs.readFileSync(tarGz)], 'restore.tar.gz', { type: 'application/x-gzip' });
const lite = await PGlite.create(dir, { loadDataDir: blob });
await lite.close();

Object.assign(process.env, { DATABASE_URL: `pglite:${dir}`, BACKUP_ENABLED: 'false' });
const { createApp } = require('../../../src/app');
const live = require('../../../src/lib/sync-v1-live');
const app = await createApp({ runMigrations: true });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
live.attach(server);
console.log(`READY ${server.address().port}`);
process.on('SIGTERM', () => { server.close(() => process.exit(0)); });
