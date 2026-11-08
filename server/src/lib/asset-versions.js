'use strict';
/**
 * نشانیِ نسخه‌دارِ پرونده‌های ایستای ویترین، بازار و کیت — برای CDN و حافظهٔ مرورگر (docs/SCALING-fa.md).
 *
 * امروز `/kit/kit.js`، `/store/store.js`، … با `max-age=0` و ETag می‌آیند: هر بار باز کردنِ ویترین یک
 * پرسشِ «عوض شد؟» (۳۰۴) برای هر پرونده به سرور (یا CDN تا سرور). با نشانیِ نسخه‌دار (`?v=<هشِ محتوا>`)
 * همان پرونده یک سال و `immutable` در حافظه می‌ماند و CDN هم بی پرسش از سرور می‌دهد؛ هر تغییرِ محتوا هشِ
 * تازه و نشانیِ تازه است، پس پروندهٔ کهنه هرگز با پوستهٔ تازه قاطی نمی‌شود.
 *
 *   • `immutableIfVersioned` (همیشه): فقط درخواستی که `?v=` اش **همان** هشِ امروزِ پرونده است سرآیندِ
 *     یک‌ساله می‌گیرد؛ بی `v` یا با `v` ِ کهنه همان `max-age=0` + ETag ِ امروز ⇒ نشانی‌های امروز بی‌تغییر.
 *   • `versioned(html)` (فقط با `ASSET_VERSIONS=1`): در پوستهٔ HTML نشانیِ `/kit|store|market/…` ِ
 *     `src`/`href` همان با `?v=`. خاموش (پیش‌فرض) ⇒ پوسته بایت‌به‌بایت همان پرونده.
 *   ⚠️ ماژول‌هایی که خودِ اسکریپت بعداً بار می‌کند (`import('./catalog.js')`) نسخه ندارند و همان ETag را
 *      دارند. CSP دست نمی‌خورد (همان مبدأ، `'self'`) و بایت‌های JS هم (بودجهٔ بارِ نخست همان).
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const PUB = path.join(__dirname, '..', '..', 'public');
const YEAR = 'public, max-age=31536000, immutable';
const memo = new Map();   //  مسیر ⇒ { mtimeMs, size, v }

/** هشِ کوتاهِ محتوا (۱۲ نویسهٔ base64url ِ sha256)؛ با تغییرِ زمان یا اندازهٔ پرونده دوباره ساخته می‌شود */
function versionOf(abs) {
  const file = path.resolve(abs);
  if (!file.startsWith(PUB + path.sep)) return '';
  //  همان پروندهٔ بازشده هم سنجیده و هم خوانده می‌شود (بی فاصله میانِ stat و read)
  let fd;
  try { fd = fs.openSync(file, 'r'); } catch { return ''; }
  try {
    const st = fs.fstatSync(fd);
    if (!st.isFile()) return '';
    const m = memo.get(file);
    if (m && m.mtimeMs === st.mtimeMs && m.size === st.size) return m.v;
    const v = crypto.createHash('sha256').update(fs.readFileSync(fd)).digest('base64url').slice(0, 12);
    if (memo.size > 2000) memo.clear();
    memo.set(file, { mtimeMs: st.mtimeMs, size: st.size, v });
    return v;
  } finally {
    fs.closeSync(fd);
  }
}

/** `setHeaders` ِ `express.static`: پیش از سرآیندِ پیش‌فرض صدا زده می‌شود و آن را جایگزین می‌کند */
function immutableIfVersioned(res, file) {
  const v = res.req && res.req.query ? res.req.query.v : undefined;
  if (typeof v === 'string' && v.length && v === versionOf(file)) res.setHeader('Cache-Control', YEAR);
}

const REF = /(\s(?:src|href)=")(\/(?:kit|store|market)\/[A-Za-z0-9_-][A-Za-z0-9._/-]*\.(?:js|css|woff2|png|svg|webmanifest))(")/g;
/** پوستهٔ HTML با نشانی‌های نسخه‌دار؛ پرونده‌ای که نیست دست نمی‌خورد */
function versioned(html) {
  return String(html).replace(REF, (all, a, url, b) => {
    if (url.includes('..')) return all;
    const v = versionOf(path.join(PUB, url));
    return v ? `${a}${url}?v=${v}${b}` : all;
  });
}

module.exports = { versionOf, immutableIfVersioned, versioned, YEAR };
