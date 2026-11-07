'use strict';
/**
 * دو انبارِ شیءِ `lib/image-store.js`: دیسکِ محلی و هر انبارِ S3-سازگار (AWS، MinIO، R2، …).
 * هر دو یک قرارداد دارند — همه با کلیدی که `image-store` ساخته و سنجیده (`KEY_RE`):
 *
 *   put(key, buf, mime)   ⇒ نوشتنِ کامل یا خطا (نیمه‌نوشته هرگز دیده نمی‌شود)
 *   get(key)              ⇒ Buffer | null (نبود)؛ خطای دیگر ⇒ throw
 *   del(key)              ⇒ نبود هم «پاک» است
 *   list()                ⇒ [{key, mtime}] — فقط برای `gc` ِ ابزارِ جابه‌جایی
 *
 * ⛔ S3 بی SDK: وابستگیِ AWS در `package.json` نیست، پس درخواست‌ها با امضای SigV4 و `crypto` ِ خودِ Node.
 *    کلید و راز فقط از env (`envCredentials`) و هرگز در پیکربندی، پاسخ یا لاگ.
 * ⛔ دیسک: مسیر فقط از کلیدِ سنجیده (بی `..`، بی `/` ِ آغازین)؛ نوشتن در پروندهٔ گذرا و `rename` ِ اتمی.
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

/** `i1/<بخشِ دکان ۱۶ hex>/<تصادفی ۳۲ hex>.<sha256 ۱۶ hex>` — همان `image-store.KEY_RE` */
const KEY_RE = /^i1\/[0-9a-f]{16}\/[0-9a-f]{32}\.[0-9a-f]{16}$/;
const MAX_OBJECT = 16 * 1024 * 1024;

function assertKey(key) {
  if (!KEY_RE.test(String(key || ''))) throw new Error('image-store: bad key');
  return key;
}

// ════════════════════════════════════════════════════════════════════
//  دیسک
// ════════════════════════════════════════════════════════════════════

function disk({ dir }) {
  const root = path.resolve(dir);
  const file = (key) => path.join(root, ...assertKey(key).split('/'));
  return {
    kind: 'disk',
    async put(key, buf) {
      const f = file(key);
      await fs.promises.mkdir(path.dirname(f), { recursive: true, mode: 0o700 });
      const tmp = `${f}.${crypto.randomBytes(6).toString('hex')}.tmp`;
      const h = await fs.promises.open(tmp, 'wx', 0o600);
      try { await h.writeFile(buf); await h.sync(); } finally { await h.close(); }
      try { await fs.promises.rename(tmp, f); } catch (e) { await fs.promises.rm(tmp, { force: true }); throw e; }
    },
    async get(key) {
      try { return await fs.promises.readFile(file(key)); } catch (e) { if (e.code === 'ENOENT') return null; throw e; }
    },
    async del(key) { await fs.promises.rm(file(key), { force: true }); },
    async list() {
      const out = [];
      const top = path.join(root, 'i1');
      const segs = await fs.promises.readdir(top).catch((e) => { if (e.code === 'ENOENT') return []; throw e; });
      for (const seg of segs) {
        for (const name of await fs.promises.readdir(path.join(top, seg)).catch(() => [])) {
          const key = `i1/${seg}/${name}`;
          if (!KEY_RE.test(key)) continue;
          const st = await fs.promises.stat(path.join(top, seg, name)).catch(() => null);
          if (st) out.push({ key, mtime: st.mtimeMs });
        }
      }
      return out;
    },
  };
}

// ════════════════════════════════════════════════════════════════════
//  S3 (امضای SigV4)
// ════════════════════════════════════════════════════════════════════

const sha256 = (b) => crypto.createHash('sha256').update(b).digest('hex');
const hmac = (k, s) => crypto.createHmac('sha256', k).update(s).digest();
/** RFC 3986 — همان کدگذاریِ SigV4 (`encodeURIComponent` چهار نویسه را بی‌کد می‌گذارد) */
const enc = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);

/** کلید و راز فقط از env؛ نامِ ویژهٔ انبار بر نامِ همگانیِ AWS جلو می‌افتد. */
function envCredentials(env = process.env) {
  const pick = (a, b) => String(env[a] || env[b] || '').trim();
  return {
    accessKeyId: pick('IMAGE_STORE_S3_ACCESS_KEY_ID', 'AWS_ACCESS_KEY_ID'),
    secretAccessKey: pick('IMAGE_STORE_S3_SECRET_ACCESS_KEY', 'AWS_SECRET_ACCESS_KEY'),
    sessionToken: pick('IMAGE_STORE_S3_SESSION_TOKEN', 'AWS_SESSION_TOKEN'),
  };
}

/**
 * امضای SigV4 ِ یک درخواست ⇒ سرآیندِ `Authorization`. `headers` همهٔ سرآیندهای امضاشونده (نامِ کوچک) است،
 * `host` و `x-amz-date` و `x-amz-content-sha256` هم. خالص و بی env (آزمون با بردارِ مستندِ AWS).
 */
function sign({ method, path: uriPath, query = {}, headers, region, credentials, service = 's3' }) {
  const amzDate = headers['x-amz-date'];
  const day = amzDate.slice(0, 8);
  const names = Object.keys(headers).map((k) => k.toLowerCase()).sort();
  const canonHeaders = names.map((k) => `${k}:${String(headers[k]).trim().replace(/\s+/g, ' ')}\n`).join('');
  const canonQuery = Object.keys(query).sort().map((k) => `${enc(k)}=${enc(String(query[k]))}`).join('&');
  const canonical = [method, uriPath, canonQuery, canonHeaders, names.join(';'), headers['x-amz-content-sha256']].join('\n');
  const scope = `${day}/${region}/${service}/aws4_request`;
  const toSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256(canonical)].join('\n');
  const key = hmac(hmac(hmac(hmac(`AWS4${credentials.secretAccessKey}`, day), region), service), 'aws4_request');
  const signature = crypto.createHmac('sha256', key).update(toSign).digest('hex');
  return {
    canonical, toSign, signature,
    authorization: `AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/${scope}, SignedHeaders=${names.join(';')}, Signature=${signature}`,
  };
}

const xmlText = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

function s3(cfg, { env = process.env, now = () => new Date() } = {}) {
  const { endpoint, bucket, region, prefix = '', pathStyle = true } = cfg.s3;
  const base = new URL(endpoint);
  const host = pathStyle ? base.host : `${bucket}.${base.host}`;
  const lib = base.protocol === 'http:' ? http : https;
  const root = pathStyle ? `/${enc(bucket)}` : '';
  const objPath = (key) => `${root}/${`${prefix}${assertKey(key)}`.split('/').map(enc).join('/')}`;

  function request(method, uriPath, { query = {}, body = null, mime = '' } = {}) {
    const credentials = envCredentials(env);
    const payload = body || Buffer.alloc(0);
    const headers = {
      host, 'x-amz-date': now().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, ''),
      'x-amz-content-sha256': sha256(payload),
    };
    if (credentials.sessionToken) headers['x-amz-security-token'] = credentials.sessionToken;
    const { authorization } = sign({ method, path: uriPath, query, headers, region, credentials });
    const qs = Object.keys(query).sort().map((k) => `${enc(k)}=${enc(String(query[k]))}`).join('&');
    const out = { ...headers, authorization, 'content-length': String(payload.length) };
    if (mime) out['content-type'] = mime;
    return new Promise((resolve, reject) => {
      const req = lib.request({
        protocol: base.protocol, hostname: base.hostname, port: base.port || undefined, method,
        path: qs ? `${uriPath}?${qs}` : uriPath, headers: out, timeout: cfg.timeoutMs || 10000,
      }, (res) => {
        const parts = [];
        let size = 0;
        res.on('data', (c) => {
          size += c.length;
          if (size > MAX_OBJECT) { req.destroy(new Error('image-store: object too large')); return; }
          parts.push(c);
        });
        res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(parts) }));
        res.on('error', reject);
      });
      req.on('timeout', () => req.destroy(new Error('image-store: s3 timeout')));
      req.on('error', reject);
      req.end(payload);
    });
  }
  const fail = (op, r) => new Error(`image-store: s3 ${op} ${r.status}`);

  return {
    kind: 's3',
    async put(key, buf, mime) {
      const r = await request('PUT', objPath(key), { body: buf, mime: mime || 'application/octet-stream' });
      if (r.status !== 200) throw fail('put', r);
    },
    async get(key) {
      const r = await request('GET', objPath(key));
      if (r.status === 404) return null;
      if (r.status !== 200) throw fail('get', r);
      return r.body;
    },
    async del(key) {
      const r = await request('DELETE', objPath(key));
      if (![200, 204, 404].includes(r.status)) throw fail('delete', r);
    },
    async list() {
      const out = [];
      let token = '';
      for (let page = 0; page < 100000; page++) {
        const query = { 'list-type': '2', prefix: `${prefix}i1/` };
        if (token) query['continuation-token'] = token;
        const r = await request('GET', root || '/', { query });
        if (r.status !== 200) throw fail('list', r);
        const xml = r.body.toString('utf8');
        for (const m of xml.matchAll(/<Contents>([\s\S]*?)<\/Contents>/g)) {
          const key = xmlText((/<Key>([\s\S]*?)<\/Key>/.exec(m[1]) || [])[1] || '').slice(prefix.length);
          const at = Date.parse(xmlText((/<LastModified>([\s\S]*?)<\/LastModified>/.exec(m[1]) || [])[1] || ''));
          if (KEY_RE.test(key)) out.push({ key, mtime: Number.isFinite(at) ? at : 0 });
        }
        token = /<IsTruncated>true<\/IsTruncated>/.test(xml)
          ? xmlText((/<NextContinuationToken>([\s\S]*?)<\/NextContinuationToken>/.exec(xml) || [])[1] || '') : '';
        if (!token) break;
      }
      return out;
    },
  };
}

// ════════════════════════════════════════════════════════════════════
//  گزینش و سنجشِ پیکربندی
// ════════════════════════════════════════════════════════════════════

/** گرفتاری‌های پیکربندی (برای `config.validate`)؛ خالی ⇒ درست. فقط نامِ env، هرگز مقدارِ راز. */
function problems(c, env = process.env) {
  const out = [];
  if (!['db', 'disk', 's3'].includes(c.mode)) out.push(`IMAGE_STORE فقط db، disk یا s3 است (نه «${c.mode}»)`);
  if (c.read && !['disk', 's3'].includes(c.read)) out.push('IMAGE_STORE_READ فقط disk یا s3 است');
  if (c.read && c.mode !== 'db') out.push('IMAGE_STORE_READ فقط با IMAGE_STORE=db معنا دارد');
  const kinds = new Set([c.mode, c.read]);
  if (kinds.has('disk') && !c.dir) out.push('IMAGE_STORE_DIR (پوشهٔ عکس‌ها) تنظیم نشده است');
  if (kinds.has('s3')) {
    if (!c.s3.endpoint || !/^https?:\/\//.test(c.s3.endpoint)) out.push('IMAGE_STORE_S3_ENDPOINT (http(s)://…) تنظیم نشده است');
    if (!c.s3.bucket) out.push('IMAGE_STORE_S3_BUCKET تنظیم نشده است');
    const cr = envCredentials(env);
    if (!cr.accessKeyId || !cr.secretAccessKey) {
      out.push('کلیدِ S3 نیست: IMAGE_STORE_S3_ACCESS_KEY_ID و IMAGE_STORE_S3_SECRET_ACCESS_KEY (یا AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY)');
    }
  }
  return out;
}

function create(kind, c, opts) {
  if (kind === 'disk') return disk(c);
  if (kind === 's3') return s3(c, opts);
  throw new Error(`image-store: unknown backend ${kind}`);
}

module.exports = { KEY_RE, assertKey, disk, s3, sign, envCredentials, problems, create };
