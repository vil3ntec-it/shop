/**
 * قاعده‌های مشترکِ «راز» برای اسکنِ مخزن (history-scan.mjs) و دروازهٔ محتوای خروجی‌ها (artifact-gate.mjs).
 *
 * ⛔ هیچ تابعی در این فایل مقدارِ پیداشده را برنمی‌گرداند: فقط نامِ قاعده، شمارهٔ خط و جای بایت.
 * گزارش‌ها هرگز مقدار را چاپ نمی‌کنند — فقط مسیر، کامیت و نوع (SEC-ART، قاعدهٔ ۵ِ برنامهٔ امنیت).
 *
 * دو دسته:
 *   • STRONG — شکلِ خودِ راز آن‌قدر مشخص است که روی فایلِ دودویی (dex، jar، class) هم سنجیده می‌شود.
 *   • TEXT   — فقط روی فایلِ متنی (بی NUL)؛ روی دودویی پر از مثبتِ کاذب است.
 */

/** کلیدِ خصوصیِ PEM با بدنه — ثابتِ رشته‌ایِ سرآیندِ PEM بی بدنه (درونِ کتابخانه‌ها) نمی‌گیرد. */
const PEM_PRIVATE = /-----BEGIN ((?:RSA |EC |DSA |OPENSSH |ENCRYPTED |PGP )?PRIVATE KEY(?: BLOCK)?)-----(?:\\[rn]|[\s"'+,])*[A-Za-z0-9+/=](?:[A-Za-z0-9+/=\s"'+,]|\\[rn]){63,}?-----END \1-----/g;

export const STRONG_RULES = Object.freeze([
  { id: 'private-key-pem', re: PEM_PRIVATE },
  { id: 'aws-access-key', re: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/g },
  { id: 'github-token', re: /\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{60,255})\b/g },
  { id: 'slack-token', re: /\bxox[abprse]-[A-Za-z0-9-]{10,}\b/g },
  { id: 'stripe-secret', re: /\b(?:sk|rk)_live_[0-9A-Za-z]{20,}\b/g },
  { id: 'npm-token', re: /\bnpm_[A-Za-z0-9]{36}\b/g },
  { id: 'telegram-bot-token', re: /\b\d{8,10}:AA[A-Za-z0-9_-]{33}\b/g },
  { id: 'google-oauth-secret', re: /\bGOCSPX-[A-Za-z0-9_-]{28}\b/g },
  { id: 'google-api-key', re: /\bAIza[0-9A-Za-z_-]{35}\b/g },
  { id: 'sendgrid-key', re: /\bSG\.[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{43}\b/g },
  { id: 'db-dump', re: /^-- (?:PostgreSQL database dump|Dumped from database version)/gm },
]);

/** شکل‌های «جای‌نگهدار» که راز نیستند — `${X}`، `<...>`، `xxxx`، `changeme`، … */
const PLACEHOLDER = /^(?:\$\{?[A-Za-z_][\w.]*\}?|\$\(.*\)|<[^>]*>|\{\{.*\}\}|%[A-Za-z_]+%|x{3,}|\*{3,}|\.{3,}|(?:change[-_]?me|your[-_].*|example.*|placeholder|dummy|secret|password|redacted|test.*|fake.*|sample.*|none|null|undefined|todo)\b.*)$/i;

/** آنتروپیِ شانون به بیت بر نویسه */
export function entropy(s) {
  if (!s) return 0;
  const freq = new Map();
  for (const ch of s) freq.set(ch, (freq.get(ch) || 0) + 1);
  let h = 0;
  for (const n of freq.values()) { const p = n / s.length; h -= p * Math.log2(p); }
  return h;
}

/** آیا مقدارِ یک انتساب شبیهِ رازِ واقعی است (نه نام، نه جای‌نگهدار)؟ */
export function looksSecret(v) {
  if (!v || v.length < 16 || v.length > 512) return false;
  if (PLACEHOLDER.test(v) || v.includes('${') || v.includes('#{')) return false;
  //  نامِ کلیدِ ذخیره یا شناسه: ‎tohid-api-key-v1‎، ‎com.vil3ntec.tohid‎ — بیشترِ تکه‌ها واژه‌اند و بقیه تکهٔ
  //  شناسه‌وار (کوتاه، یک‌دست یا واژه‌ورقم مثلِ ‎v1‎، ‎vil3ntec‎، ‎Value123‎). UUID و رازِ تصادفی نه. «واژه» یک‌دست است (همه کوچک، همه
  //  بزرگ، یا فقط حرفِ اول بزرگ)؛ تکهٔ بلندِ کوچک‌وبزرگِ درهم (base64) شناسه نیست.
  //  ⛔ پیش از این هر تکهٔ تمام‌حرفی «واژه» بود: رازِ base64 با یک ‎/‎ و تکهٔ کوتاهِ ‎aB‎ کنارش (۸٫۶٪ ِ رازهای
  //  ۲۴بایتیِ تصادفی) «شناسه» دیده و رد می‌شد (artifact-gate.test.mjs › «انتسابِ مشکوک»).
  const parts = v.split(/[-_.:/]/).filter(Boolean);
  const word = (x) => /^(?:[a-z]{2,}|[A-Z]{2,}|[A-Z][a-z]+)$/.test(x);
  const idLike = (x) => word(x) || x.length <= 4 || /^[a-z0-9]+$/.test(x) || /^[A-Z0-9]+$/.test(x) || /^[A-Z]?[a-z]+\d+$/.test(x);
  if (parts.length >= 2 && parts.filter(word).length * 2 >= parts.length && parts.every(idLike)) return false;
  //  نامِ کلید یا شناسه: فقط حروفِ کوچک/بزرگ و `_`، `-`، `.`، `/`
  if (/^[a-z_.\-/]+$/.test(v) || /^[A-Z_.\-/]+$/.test(v)) return false;
  //  نشانی بی رمز
  if (/^https?:\/\/[^@]*$/i.test(v)) return false;
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(v)).length;
  return classes >= 2 && entropy(v) >= 3.5;
}

const ASSIGN = /\b([A-Za-z0-9_.-]*(?:secret|token|passw(?:or)?d|passphrase|api[_-]?key|private[_-]?key|access[_-]?key|client[_-]?secret|auth[_-]?key)[A-Za-z0-9_.-]*)["']?\s*(?::=|=>|[:=])\s*["'`]([^"'`\s]{8,512})["'`]/gi;

export const TEXT_RULES = Object.freeze([
  {
    id: 'secret-assignment',
    re: ASSIGN,
    confirm: (m) => looksSecret(m[2]),
  },
  {
    //  ‎postgres://user:pass@host‎ — رمز در نشانی
    id: 'url-credentials',
    re: /\b[a-z][a-z0-9+.-]{1,20}:\/\/([^\s:@/'"`]{1,64}):([^\s@/'"`]{3,128})@[A-Za-z0-9.-]+/gi,
    confirm: (m) => !/[$<{]/.test(m[1] + m[2]) && !PLACEHOLDER.test(m[2]) && !/^\*+$|^x+$/i.test(m[2]) && !/^(?:pass(?:word)?|pw|secret|user)$/i.test(m[2]),
  },
  {
    //  رمزِ پیش‌فرضِ امضا در اسکریپتِ ساخت: پیش‌فرضِ ‎?:‎ پس از ‎env(…PASSWORD)‎، یا ‎storePassword/keyPassword‎ ِ ثابت
    id: 'signing-password-literal',
    re: /(?:env\("[A-Z0-9_]*PASS[A-Z0-9_]*"\)\s*\?:\s*"([^"$]{3,})"|\b(?:storePassword|keyPassword)\s*=\s*"([^"$]{3,})")/g,
  },
  {
    id: 'jwt',
    re: /\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{16,}\b/g,
  },
  {
    //  ‎.npmrc‎: ‎//registry/:_authToken=…‎ یا ‎_auth=…‎ در آغازِ خط (نه ‎push_auth = …‎ ِ SQL)
    id: 'npmrc-auth',
    re: /(?:^|:)_auth(?:Token)?\s*=\s*(?!\$\{)[^\s]{8,}/gm,
  },
  {
    //  رمز در فرمانِ keytool/jarsigner؛ «android» رمزِ همگانیِ کلیدِ debugِ اندروید است
    id: 'signing-password-literal',
    re: /-(?:store|key|srcstore|deststore)pass[ =]+(?!<|\$|\{|android\b)[^\s`'"]{3,}/g,
  },
]);

/**
 * نام و پسوندِ فایل — بی خواندنِ محتوا.
 *  `pem`/`key`/`crt` فقط وقتی کلیدِ خصوصی‌اند که محتوا بگوید (`private-key-pem`)؛ این‌جا فقط کلیدهایی که
 *  نامشان خودش «کلیدِ خصوصی» یا «انبارِ کلید» است.
 */
const ENV_EXAMPLE = /(?:^|\/)\.env\.(?:example|sample|template|dist|defaults)$/i;
export const NAME_RULES = Object.freeze([
  { id: 'env-file', test: (p) => /(?:^|\/)\.env(?:\.[^/]+)?$/i.test(p) && !ENV_EXAMPLE.test(p) },
  { id: 'keystore', test: (p) => /\.(?:jks|keystore|p12|pfx|bks|kdbx)$/i.test(p) },
  { id: 'private-key-file', test: (p) => /(?:^|\/)(?:id_(?:rsa|dsa|ecdsa|ed25519)|[^/]*\.(?:ppk|p8|pk8|key))$/i.test(p) },
  { id: 'db-backup-file', test: (p) => /\.(?:dump|pgdump|sql\.gz|sql\.gz\.enc|sqlite3?|db|db-wal|bak|backup)$/i.test(p) },
  { id: 'credentials-file', test: (p) => /(?:^|\/)(?:\.netrc|\.pgpass|\.htpasswd|\.git-credentials|credentials(?:\.json)?|service[-_]?account[^/]*\.json|secrets?\.json)$/i.test(p) },
]);

/** فایلِ متنی؟ (NUL در ۸ کیلوبایتِ اول ⇒ دودویی) */
export function isText(buf) {
  const n = Math.min(buf.length, 8192);
  for (let i = 0; i < n; i++) if (buf[i] === 0) return false;
  return true;
}

function lineOf(text, index) {
  let line = 1;
  for (let i = text.indexOf('\n'); i !== -1 && i < index; i = text.indexOf('\n', i + 1)) line++;
  return line;
}

/**
 * یافته‌های محتوا: ‎[{ rule, line? }]‎ — بی مقدار.
 * `textOnly` نیامده ⇒ از روی خودِ بایت‌ها تصمیم می‌گیرد.
 */
export function scanContent(buf, { maxText = 8 << 20 } = {}) {
  const text = buf.toString('latin1');
  const textual = buf.length <= maxText && isText(buf);
  const out = [];
  const seen = new Set();
  const add = (rule, idx) => {
    const line = textual ? lineOf(text, idx) : undefined;
    const k = `${rule}:${line ?? idx}`;
    if (seen.has(k)) return;
    seen.add(k);
    out.push(line === undefined ? { rule } : { rule, line });
  };
  for (const r of STRONG_RULES) {
    r.re.lastIndex = 0;
    for (let m = r.re.exec(text); m; m = r.re.exec(text)) {
      add(r.id, m.index);
      if (m[0].length === 0) r.re.lastIndex++;
    }
  }
  if (textual) {
    for (const r of TEXT_RULES) {
      r.re.lastIndex = 0;
      for (let m = r.re.exec(text); m; m = r.re.exec(text)) {
        if (!r.confirm || r.confirm(m)) add(r.id, m.index);
        if (m[0].length === 0) r.re.lastIndex++;
      }
    }
  }
  return out;
}

export function scanName(p) {
  return NAME_RULES.filter((r) => r.test(p)).map((r) => ({ rule: r.id }));
}

/** الگوی ساده‌ی glob (‎**‎، ‎*‎، ‎?‎) ⇒ RegExp روی مسیرِ کامل */
export function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') { re += '.*'; i++; if (glob[i + 1] === '/') i++; }
    else if (c === '*') re += '[^/]*';
    else if (c === '?') re += '[^/]';
    else re += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
  }
  return new RegExp(`^${re}$`);
}

/**
 * فهرستِ مجاز: ‎[{ rule, path, reason }]‎. هر ردیف باید دلیل داشته باشد؛ ردیفِ بی دلیل خطاست تا فهرستِ
 * مجاز به «همه را ساکت کن» تبدیل نشود.
 */
export function compileAllow(list) {
  return (list || []).map((a, i) => {
    if (!a || !a.rule || !a.path || !a.reason || String(a.reason).trim().length < 10) {
      throw new Error(`allow[${i}]: rule، path و reason (دست‌کم ۱۰ نویسه) لازم است`);
    }
    return { rule: a.rule, re: globToRegExp(a.path), path: a.path };
  });
}

export function isAllowed(allow, rule, p) {
  return allow.some((a) => (a.rule === rule || a.rule === '*') && a.re.test(p));
}
