/**
 * «سرورِ تازه پس از بازیابی» — فرایندِ جدا برای `ops.e2e.mjs` (E2E-OPS-14).
 *
 * پشتیبان پیش‌تر در محیطِ جدا برگشته است (`restore-copy.mjs`: پوشهٔ PGlite یا دیتابیسِ تازهٔ PostgreSQL)؛
 * این‌جا فقط سرور روی همان دیتابیس بالا می‌آید.
 *
 *   node restored-server.mjs <DATABASE_URL ِ بازیابی‌شده>
 *   ⇐ روی stdout: READY <port>
 */
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const [url] = process.argv.slice(2);

Object.assign(process.env, { DATABASE_URL: url, BACKUP_ENABLED: 'false' });
const { createApp } = require('../../../src/app');
const live = require('../../../src/lib/sync-v1-live');
const app = await createApp({ runMigrations: true });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
live.attach(server);
console.log(`READY ${server.address().port}`);
process.on('SIGTERM', () => { server.close(() => process.exit(0)); });
