'use strict';
/**
 * نگهبانِ پرونده‌ای که بی‌صدا ماند — برای `scripts/test-run.js` (PostgreSQL) و
 * `scripts/test-pglite.js` (PGlite و Electron).
 *
 * چرا: `node --test` برای هر پرونده سقفی ندارد و `--test-force-exit` فقط پس از پایانِ همهٔ
 * قلاب‌ها می‌رسد. کارِ «آزمون‌های سرور زیرِ Electron» چند بار درست پس از آخرین آزمونِ یک پرونده
 * بی‌صدا ماند تا سقفِ ۴۵ دقیقه لغوش کرد (دستهٔ نهم: پس از آزمونِ ۱۴۰ = آخرینِ api.test.js؛ main
 * 171c7d65: پس از sync-epoch) — بی آن‌که بدانیم کدام پرونده، کدام مرحله و چه چیزی باز مانده بود.
 *
 * دو بخش:
 *   ۱) گزارشگرِ اضافیِ `node --test` (همین ماژول، `--test-reporter`): پیشرفت را خط‌به‌خط در پروندهٔ
 *      وضعیت می‌نویسد — آغازِ پرونده، آغاز و پایانِ هر آزمون، `test:summary` ِ پرونده (همهٔ آزمون‌ها
 *      و قلاب‌ها تمام)، بسته شدنِ فرآیندش؛ و برای هر رویدادِ دیگر (خروجیِ console) یک تپش، حداکثر
 *      ثانیه‌ای یک بار. گزارشگرهای دیگر (TAP/spec، شکست‌ها، شواهد) دست نمی‌خورند.
 *   ۲) `run(args)`: همان `node --test` (همان پرچم‌ها، همان stdio، همان کدِ خروج) و پاییدنِ آن پرونده.
 *      اگر `TEST_WATCHDOG_SECONDS` ثانیه (پیش‌فرض ۱۸۰) هیچ پیشرفتی نرسید: نامِ پرونده، مرحله
 *      (بارگذاری، درونِ آزمونی، پس از آخرین آزمون = قلابِ after، یا «همه تمام ولی فرآیند بیرون
 *      نرفت / stdout اش باز ماند»)، آخرین آزمون، فرآیندهای زنده (و حالتِ رشتهٔ اصلی در لینوکس) و
 *      — اگر رسید — گزارشِ Node ِ همان فرآیند (`--report-on-signal`: دستگیره‌های فعال و ref‌دار)
 *      چاپ می‌شود؛ درختِ فرآیندِ همان پرونده کشته می‌شود؛ `node --test` آن پرونده را «not ok»
 *      می‌نویسد و سراغِ پروندهٔ بعد می‌رود؛ کدِ خروج ۱. بارِ دوم، یا اگر فرآیندِ پرونده‌ای برای کشتن
 *      نیست، کلِ اجرا (`node --test` و همهٔ زیرفرآیندهایش) بسته می‌شود.
 *
 * ۱۸۰ ثانیه: در اجرای سبزِ main زیرِ Electron در CI (171c7d65، تلاشِ دوم) طولانی‌ترین سکوت ۲۴ ثانیه
 * (load-smoke) و کندترین پرونده ۳۵ ثانیه (move-db) بود ⇒ بیش از ۷ و ۵ برابر؛ و یک گیر با آن هنوز زیرِ
 * سقفِ ۴۵ دقیقهٔ کار (۳۵ دقیقه) جا می‌شود. `TEST_WATCHDOG_SECONDS=0` نگهبان را برمی‌دارد (مثلاً برای
 * `--inspect-brk`).
 *
 * ⛔ هیچ آزمونی رد، تکرار یا خاموش نمی‌شود. اجرای بی‌گیر همان خروجی و همان کدِ خروج را دارد،
 * به‌اضافهٔ یک خطِ «نگهبان: …» (طولانی‌ترین سکوت و کندترین پرونده) پیش از خلاصهٔ شکست‌ها.
 */
const { spawn, execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const DEFAULT_SECONDS = 180;
const rel = (f) => (f ? path.relative(ROOT, f) || f : '');

// ── ۱) گزارشگر (درونِ فرآیندِ `node --test`) ─────────────────────────────────

//  `node --test` هر گزارشگر را با چند شنوندهٔ «end» به یک جریانِ مشترک وصل می‌کند و از سه گزارشگر به
//  بعد MaxListenersExceededWarning ِ بی‌معنا می‌دهد (همین حالا هم با شواهد در کارهای PG/PGlite). فقط
//  در همان فرآیندِ `node --test` (پرچمِ --test به فرآیندِ پرونده‌ها نمی‌رسد)؛ کدِ آزمونی آن‌جا نیست.
if (process.execArgv.includes('--test')) {
  const events = require('events');
  events.defaultMaxListeners = Math.max(events.defaultMaxListeners, 20);
}

async function* reporter(source) {
  let last = 0;
  const line = (rec) => { last = Date.now(); return JSON.stringify({ t: last, ...rec }) + '\n'; };
  for await (const ev of source) {
    const d = ev.data || {};
    //  رویدادِ خودِ پرونده: نامش همان مسیری است که به `node --test` داده شد
    const fileLevel = d.file && d.nesting === 0 && typeof d.name === 'string' && path.resolve(d.name) === d.file;
    if (ev.type === 'test:dequeue') yield line(fileLevel ? { k: 'file', f: rel(d.file) } : { k: 'start', f: rel(d.file), n: d.name });
    else if (ev.type === 'test:complete') {
      yield line(fileLevel ? { k: 'done', f: rel(d.file) } : { k: 'end', f: rel(d.file), n: d.name });
    } else if (ev.type === 'test:summary' && d.file) yield line({ k: 'summary', f: rel(d.file) });
    else if (Date.now() - last >= 1000) yield line({ k: 'beat' });
  }
}

// ── فرآیندها ───────────────────────────────────────────────────────────────

/** [{pid, ppid, args}] از /proc (لینوکس) یا `ps` (مک)؛ ویندوز ⇒ null. */
function listProcesses() {
  if (process.platform === 'win32') return null;
  try {
    if (fs.existsSync('/proc/self/stat')) {
      const out = [];
      for (const d of fs.readdirSync('/proc')) {
        if (!/^\d+$/.test(d)) continue;
        try {
          const stat = fs.readFileSync(`/proc/${d}/stat`, 'utf8');
          const rest = stat.slice(stat.lastIndexOf(')') + 2).split(' ');
          const args = fs.readFileSync(`/proc/${d}/cmdline`, 'utf8').split('\0').filter(Boolean).join(' ');
          out.push({ pid: Number(d), ppid: Number(rest[1]), state: rest[0], args });
        } catch { /* همین حالا بیرون رفت */ }
      }
      return out;
    }
    return execFileSync('ps', ['-A', '-o', 'pid=,ppid=,state=,args='], { encoding: 'utf8' }).split('\n')
      .map((l) => l.trim().match(/^(\d+)\s+(\d+)\s+(\S+)\s+(.*)$/)).filter(Boolean)
      .map((m) => ({ pid: Number(m[1]), ppid: Number(m[2]), state: m[3], args: m[4] }));
  } catch { return null; }
}

function descendants(list, root) {
  const seen = new Set([root]);
  const out = [];
  for (let grew = true; grew;) {
    grew = false;
    for (const p of list) {
      if (!seen.has(p.pid) && seen.has(p.ppid)) { seen.add(p.pid); out.push(p); grew = true; }
    }
  }
  return out;
}

/** لینوکس: حالت و wchan ِ رشتهٔ اصلی — «ep_poll» یعنی حلقهٔ رویداد بیکار است، «futex…» یعنی گیرِ بومی. */
function nativeState(p) {
  try {
    const wchan = fs.readFileSync(`/proc/${p.pid}/wchan`, 'utf8').trim() || '-';
    const threads = fs.readdirSync(`/proc/${p.pid}/task`).length;
    return ` [حالت ${p.state}، wchan ${wchan}، ${threads} رشته]`;
  } catch { return p.state ? ` [حالت ${p.state}]` : ''; }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** SIGUSR2 ⇒ گزارشِ Node (فقط فرآیندی که با `--report-on-signal` بالا آمده) — یا null. */
async function nodeReport(pid, dir, waitMs = 3000) {
  try { process.kill(pid, 'SIGUSR2'); } catch { return null; }
  for (const until = Date.now() + waitMs; Date.now() < until;) {
    await sleep(100);
    let name;
    //  report.<تاریخ>.<ساعت>.<pid>.<tid>.<شماره>.json
    try { name = fs.readdirSync(dir).find((n) => n.startsWith('report.') && n.split('.')[3] === String(pid)); } catch { return null; }
    if (!name) continue;
    try { return JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8')); } catch { /* هنوز نیمه‌نوشته */ }
  }
  return null;
}

function handleLabel(h) {
  const ep = (e) => (e && typeof e === 'object' ? `${e.host || ''}:${e.port}` : '');
  const bits = [h.type];
  if (h.type === 'timer') bits.push(`${h.firesInMsFromNow}ms دیگر${h.repeat ? `، هر ${h.repeat}ms` : ''}`);
  if (h.localEndpoint) bits.push(`${ep(h.localEndpoint)}${h.remoteEndpoint ? ` ⇄ ${ep(h.remoteEndpoint)}` : ''}`);
  if (h.pid) bits.push(`pid ${h.pid}`);
  if (h.filename) bits.push(h.filename);
  if (h.signal) bits.push(h.signal);
  if (h.fd !== undefined && h.fd !== -1 && !h.localEndpoint) bits.push(`fd ${h.fd}`);
  return bits.join(' ');
}

function reportLines(r) {
  const out = [];
  const handles = (r.libuv || []).filter((h) => h.is_active && h.is_referenced);
  out.push(`    دستگیره‌های فعال و ref‌دار (${handles.length}): ${handles.map(handleLabel).join('؛ ') || '—'}`);
  const js = r.javascriptStack || {};
  const frames = (js.stack || []).map((f) => f.trim()).filter((f) => f && !/\(node:|node:internal/.test(f));
  if (frames.length) out.push(`    پشتهٔ JS: ${js.message || ''}`, ...frames.slice(0, 5).map((f) => `      ${f}`));
  return out;
}

// ── ۲) اجرا ────────────────────────────────────────────────────────────────

function readSeconds() {
  const raw = process.env.TEST_WATCHDOG_SECONDS;
  if (raw === undefined || raw === '') return DEFAULT_SECONDS;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : DEFAULT_SECONDS;
}

/** وضعیت از خط‌های پروندهٔ گزارشگر. */
function tracker() {
  const s = { cur: null, files: new Map(), lastT: 0, gap: { ms: 0, file: '' }, slowest: { ms: 0, file: '' } };
  s.feed = (rec) => {
    if (s.lastT && rec.t - s.lastT > s.gap.ms) s.gap = { ms: rec.t - s.lastT, file: s.cur ? s.cur.f : '' };
    s.lastT = rec.t;
    if (rec.k === 'file') {
      s.cur = { f: rec.f, t: rec.t, running: [], ended: null, summary: false, done: false };
      s.files.set(rec.f, s.cur);
      return;
    }
    const f = rec.f && s.files.get(rec.f);
    if (!f) return;
    if (rec.k === 'start') f.running.push(rec.n);
    else if (rec.k === 'end') {
      f.ended = rec.n;
      const i = f.running.lastIndexOf(rec.n);
      if (i >= 0) f.running.splice(i, 1);
    }
    else if (rec.k === 'summary') f.summary = true;
    else if (rec.k === 'done') {
      f.done = true;
      if (rec.t - f.t > s.slowest.ms) s.slowest = { ms: rec.t - f.t, file: f.f };
    }
  };
  return s;
}

function phase(f) {
  if (!f) return 'پیش از آغازِ نخستین پرونده';
  if (f.summary) return 'همهٔ آزمون‌ها و قلاب‌ها تمام شدند (test:summary رسید) ولی فرآیندِ پرونده بیرون نرفت یا stdout اش باز ماند';
  if (f.running.length) return `درونِ آزمونِ «${f.running.join(' › ')}» (یا قلاب‌های before/beforeEach ِ آن)`;
  if (f.ended) return `پس از پایانِ آزمونِ «${f.ended}»: قلابِ after/afterEach (یا آزمونِ بعدی هنوز آغاز نشده)`;
  return 'بارگذاریِ پرونده — پیش از آغازِ نخستین آزمون';
}

/**
 * `node --test …args` را با نگهبان اجرا می‌کند ⇒ { status, hangs: [متنِ کوتاهِ هر گیر] }.
 * `args` همان آرگومان‌هایی است که پیش‌تر به `spawnSync(process.execPath, args)` می‌رفت.
 */
async function run(args, { env = process.env, seconds = readSeconds(), out = process.stdout } = {}) {
  if (!seconds) {
    const code = await new Promise((resolve) => {
      const c = spawn(process.execPath, args, { stdio: 'inherit', env });
      c.on('error', () => resolve(1));
      c.on('exit', (code) => resolve(code ?? 1));
    });
    return { status: code, hangs: [], stats: '' };
  }

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'test-watchdog-'));
  const stateFile = path.join(dir, 'state.jsonl');
  const posix = process.platform !== 'win32';
  const silenceMs = seconds * 1000;
  //  پیش از `args` (که با نامِ پرونده‌ها تمام می‌شود)؛ گزارشگر و مقصد به همان ترتیب جفت می‌شوند
  const child = spawn(process.execPath, [
    //  به فرآیندِ هر پرونده هم می‌رسد (`node --test` پرچم‌های خودش را به فرزندان می‌دهد) و تا SIGUSR2
    //  نیاید کاری نمی‌کند؛ دستگیرهٔ سیگنالش unref است و فرآیند را نگه نمی‌دارد
    ...(posix ? ['--report-on-signal', `--report-directory=${dir}`] : []),
    `--test-reporter=${__filename}`, `--test-reporter-destination=${stateFile}`,
    ...args,
  ], { stdio: 'inherit', env });

  const s = tracker();
  const hangs = [];
  let offset = 0;
  let partial = '';
  let lastProgress = Date.now();
  let busy = false;
  let exited = false;

  const poll = () => {
    let size = 0;
    try { size = fs.statSync(stateFile).size; } catch { return; }
    if (size <= offset) return;
    const fd = fs.openSync(stateFile, 'r');
    try {
      const buf = Buffer.alloc(size - offset);
      fs.readSync(fd, buf, 0, buf.length, offset);
      offset = size;
      const lines = (partial + buf.toString('utf8')).split('\n');
      partial = lines.pop();
      for (const l of lines) { try { s.feed(JSON.parse(l)); } catch { /* خطِ ناقص */ } }
    } finally { fs.closeSync(fd); }
    lastProgress = Date.now();
  };

  const say = (lines) => out.write(`\n${lines.join('\n')}\n`);

  const fire = async () => {
    const f = s.cur && !s.cur.done ? s.cur : null;
    const name = f ? f.f : '(نامعلوم)';
    const head = `⏱ نگهبانِ آزمون: پروندهٔ ${name} ${seconds} ثانیه هیچ پیشرفتی نداشت — ${phase(f)}`;
    const lines = [`── ${head} ──`];
    if (f && f.ended && (f.running.length || f.summary)) lines.push(`  آخرین آزمونِ تمام‌شده: «${f.ended}»`);

    const list = listProcesses();
    let targets = [];
    let fileProc = null;
    if (list) {
      const tree = descendants(list, child.pid);
      fileProc = (f && tree.find((p) => p.ppid === child.pid && (p.args.includes(f.f) || p.args.includes(path.join(ROOT, f.f))))) || null;
      targets = fileProc ? [fileProc, ...descendants(list, fileProc.pid)] : tree;
      if (!fileProc) {
        lines.push(f && f.summary
          ? '  فرآیندِ پرونده بیرون رفته ولی node --test هنوز منتظرِ بسته شدنِ stdout ِ آن است (زیرفرآیندی که آن را به ارث برده؟).'
          : '  فرآیندِ پرونده پیدا نشد.');
      }
      for (const p of targets) lines.push(`  ${p === fileProc ? 'فرآیندِ پرونده' : 'زیرفرآیند'}: pid ${p.pid}${nativeState(p)} ${p.args.slice(0, 200)}`);
      if (fileProc && posix) {
        const r = await nodeReport(fileProc.pid, dir);
        lines.push(r ? '  گزارشِ Node (حلقهٔ رویداد هنوز می‌چرخد):' : '  گزارشِ Node نرسید — رشتهٔ اصلی JS اجرا نمی‌کند (گیرِ بومی یا حلقهٔ همگام).');
        if (r) lines.push(...reportLines(r));
      }
    }
    //  بی فرآیندِ پرونده کشتنِ چیزِ دیگری `node --test` را جلو نمی‌برد
    const abort = !fileProc || hangs.length >= 1;
    hangs.push(`${name} — ${phase(f)}`);
    lines.push(abort
      ? '  ⛔ کلِ اجرا بسته می‌شود (گیرِ دوم، یا فرآیندِ پرونده‌ای برای کشتن نیست).'
      : `  فرآیندِ پرونده و زیرفرآیندهایش کشته می‌شوند؛ node --test آن را «not ok» می‌نویسد و پروندهٔ بعدی را می‌آزماید.`);
    say(lines);

    for (const p of targets) { try { process.kill(p.pid, 'SIGKILL'); } catch { /* رفته */ } }
    if (abort) {
      if (!posix) {
        try { execFileSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' }); } catch { /* رفته */ }
      } else {
        for (const p of descendants(listProcesses() || [], child.pid)) { try { process.kill(p.pid, 'SIGKILL'); } catch { /* رفته */ } }
        try { process.kill(child.pid, 'SIGKILL'); } catch { /* رفته */ }
      }
    }
    lastProgress = Date.now();
  };

  const timer = setInterval(async () => {
    if (busy || exited) return;
    busy = true;
    try {
      poll();
      if (Date.now() - lastProgress >= silenceMs) await fire();
    } finally { busy = false; }
  }, Math.max(100, Math.min(5000, silenceMs / 8)));

  const status = await new Promise((resolve) => {
    child.on('error', () => resolve(1));
    child.on('exit', (code) => resolve(code ?? 1));
  });
  exited = true;
  clearInterval(timer);
  poll();
  try { fs.rmSync(dir, { recursive: true, force: true }); } catch { /* بماند */ }

  const sec = (ms) => (ms / 1000).toFixed(1);
  const stats = `نگهبان: طولانی‌ترین سکوت ${sec(s.gap.ms)} ثانیه${s.gap.file ? ` (${s.gap.file})` : ''}، کندترین پرونده ${sec(s.slowest.ms)} ثانیه${s.slowest.file ? ` (${s.slowest.file})` : ''}؛ سقفِ سکوت ${seconds} ثانیه\n`;
  return { status: hangs.length && !status ? 1 : status, hangs, stats };
}

/** پایانِ اجرا: یادآوریِ کوتاهِ گیرها (بی گیر ⇒ ''). */
function summarize(hangs) {
  if (!hangs.length) return '';
  return `\n── ⏱ نگهبانِ آزمون: ${hangs.length} پرونده بی‌صدا ماند و کشته شد ──\n${hangs.map((h) => `✖ ${h}`).join('\n')}\n`;
}

module.exports = reporter;
module.exports.run = run;
module.exports.summarize = summarize;
module.exports.listProcesses = listProcesses;
