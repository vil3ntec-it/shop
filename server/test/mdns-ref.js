'use strict';
/**
 * پیاده‌سازیِ مرجعِ mDNS/DNS-SD ِ HW-12 (همان قاعدهٔ `core/hw/Mdns.kt`، جدا نوشته شده) — «پیشگو» برای
 * `core/test-vectors/mdns-v1.json` که بسته‌هایش را dnspython ساخته است. برنامهٔ وب این را به کار نمی‌برد:
 * مرورگر UDP ندارد؛ این فقط دومین پیاده‌سازی است تا بردارها روی Node هم سنجیده شوند.
 *
 *   query(service, id)  ⇒ پرسشِ PTR ِ یک‌باره (RFC 6762 §5.1؛ پاسخِ تک‌پخش به همان درگاه، §6.7)
 *   parse(bytes)        ⇒ { id, response, records } یا null برای بستهٔ بدشکل/خطا
 *   printers(messages)  ⇒ [{ link: 'net:ip:port', name }] — فقط نشانیِ خصوصیِ RFC1918، به ترتیبِ Discovery.merge
 */

const TYPE = { A: 1, PTR: 12, TXT: 16, AAAA: 28, SRV: 33 };
const CLASS_IN = 1;
const MAX_JUMPS = 64;

const u16 = (p, i) => (p[i] << 8) | p[i + 1];
const u32 = (p, i) => ((p[i] << 24) >>> 0) + (p[i + 1] << 16) + (p[i + 2] << 8) + p[i + 3];
const utf8 = new TextDecoder('utf-8', { fatal: false });

function query(service, id = 0) {
  const out = [(id >> 8) & 255, id & 255, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0];
  for (const label of service.split('.')) {
    const b = Buffer.from(label, 'utf8');
    if (b.length < 1 || b.length > 63) throw new Error('label');
    out.push(b.length, ...b);
  }
  out.push(0, 0, TYPE.PTR, 0, CLASS_IN);
  return Uint8Array.from(out);
}

/** نام با فشرده‌سازی (RFC 1035 §4.1.4): فقط اشاره به پیش‌تر، سقفِ ۲۵۵ بایت ⇒ { labels, end } یا null */
function name(p, start) {
  const labels = [];
  let at = start, end = -1, jumps = 0, wire = 0;
  for (;;) {
    if (at >= p.length) return null;
    const len = p[at];
    const kind = len & 0xC0;
    if (kind === 0) {
      if (len === 0) return { labels, end: end < 0 ? at + 1 : end };
      if (at + 1 + len > p.length) return null;
      wire += len + 1;
      if (wire > 254) return null;
      labels.push(utf8.decode(p.subarray(at + 1, at + 1 + len)));
      at += 1 + len;
    } else if (kind === 0xC0) {
      if (at + 1 >= p.length) return null;
      const ptr = ((len & 0x3F) << 8) | p[at + 1];
      if (ptr >= at) return null;
      if (end < 0) end = at + 2;
      if (++jumps > MAX_JUMPS) return null;
      at = ptr;
    } else return null;
  }
}

function parse(p) {
  if (p.length < 12) return null;
  const flags = u16(p, 2);
  if ((flags & 0x7800) !== 0 || (flags & 0x000F) !== 0) return null;
  const qd = u16(p, 4), total = u16(p, 6) + u16(p, 8) + u16(p, 10);
  let at = 12;
  for (let i = 0; i < qd; i++) {
    const n = name(p, at);
    if (!n) return null;
    at = n.end + 4;
    if (at > p.length) return null;
  }
  const records = [];
  for (let i = 0; i < total; i++) {
    const n = name(p, at);
    if (!n) return null;
    at = n.end;
    if (at + 10 > p.length) return null;
    const type = u16(p, at), cls = u16(p, at + 2) & 0x7FFF, ttl = u32(p, at + 4), len = u16(p, at + 8);
    at += 10;
    if (at + len > p.length) return null;
    const rd = at, stop = at + len;
    at = stop;
    if (cls !== CLASS_IN) continue;
    if (type === TYPE.PTR) {
      const t = name(p, rd);
      if (!t || t.end > stop) return null;
      records.push({ name: n.labels, type, ttl, ptr: t.labels });
    } else if (type === TYPE.SRV) {
      if (len < 7) return null;
      const t = name(p, rd + 6);
      if (!t || t.end > stop) return null;
      records.push({ name: n.labels, type, ttl, port: u16(p, rd + 4), target: t.labels });
    } else if (type === TYPE.A) {
      if (len !== 4) return null;
      records.push({ name: n.labels, type, ttl, ipv4: `${p[rd]}.${p[rd + 1]}.${p[rd + 2]}.${p[rd + 3]}` });
    }
  }
  return { id: u16(p, 0), response: (flags & 0x8000) !== 0, records };
}

/** مقایسهٔ نام بی بزرگ/کوچکِ ASCII (RFC 6762 §16)، برچسب به برچسب */
const lowerAscii = (s) => s.replace(/[A-Z]/g, (c) => c.toLowerCase());
const same = (a, b) => a.length === b.length && a.every((l, i) => lowerAscii(l) === lowerAscii(b[i]));

/** RFC1918 — همان `Subnet.isPrivate` */
function isPrivate(ip) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(ip);
  if (!m) return false;
  const [a, b] = [Number(m[1]), Number(m[2])];
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

/** نامِ نمایشی: نویسهٔ کنترلی و جهت‌دهندهٔ پنهان ⇒ فاصله */
function clean(s) {
  let out = '';
  for (const ch of s) {
    const c = ch.codePointAt(0);
    const bad = c < 32 || (c >= 127 && c <= 159) || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069) || c === 0x200E || c === 0x200F;
    out += bad ? ' ' : ch;
  }
  return out.trim();
}

function printers(messages, service) {
  const svc = service.split('.');
  const all = messages.filter((m) => m.response).flatMap((m) => m.records);
  const instances = [];
  for (const r of all) {
    if (r.type !== TYPE.PTR || r.ttl === 0 || !same(r.name, svc)) continue;
    if (r.ptr.length !== svc.length + 1 || !same(r.ptr.slice(1), svc)) continue;
    if (!instances.some((i) => same(i, r.ptr))) instances.push(r.ptr);
  }
  const found = [];
  for (const inst of instances) {
    const srv = all.find((r) => r.type === TYPE.SRV && r.ttl > 0 && r.port > 0 && same(r.name, inst));
    if (!srv) continue;
    const a = all.find((r) => r.type === TYPE.A && r.ttl > 0 && same(r.name, srv.target) && isPrivate(r.ipv4));
    if (!a) continue;
    found.push({ link: `net:${a.ipv4}:${srv.port}`, name: clean(inst[0]) || a.ipv4 });
  }
  //  همان `Discovery.merge`: یکتا با نشانی (نامِ واقعی بر نشانیِ خالی برتری دارد)، بعد به ترتیبِ نام
  const key = (l) => l.toLowerCase();
  const named = (f) => f.name.trim() !== '' && !f.link.toLowerCase().includes(f.name.trim().toLowerCase());
  const by = new Map();
  for (const f of found) {
    const k = key(f.link);
    const prev = by.get(k);
    by.set(k, !prev ? f : (!named(prev) && named(f) ? f : prev));
  }
  return [...by.values()].sort((x, y) => {
    const a = x.name.toLowerCase(), b = y.name.toLowerCase();
    return a < b ? -1 : a > b ? 1 : 0;
  });
}

module.exports = { TYPE, query, parse, printers, isPrivate, name };
