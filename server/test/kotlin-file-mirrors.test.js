'use strict';
/**
 * آینهٔ JS ِ آزمون‌های کاتلینی که پرونده‌های سرور/وب/docs را می‌سنجند — تا سرخِ آن‌ها در CI ِ سرور (چند
 * دقیقه) دیده شود، نه دیر در CI ِ اندروید/کامپیوتر (Gradle این‌جا ساخته نمی‌شود). هر آزمون همان سنجشِ آزمونِ
 * کاتلینِ هم‌نامش است، و فهرست‌های مشترک از خودِ پروندهٔ کاتلین خوانده می‌شوند (`test/kt-source.js`، سخت:
 * شکلِ تازه ⇒ خطا)، نه رونوشت. آزمونِ کاتلین عوض نمی‌شود؛ اگر آن عوض شد، این آینه هم.
 *   ۱) `core/…/guide/WhatsNewFileTest` و بخشِ پرونده‌ایِ `WhatsNewTest.bundledLog…` — `docs/WHATS-NEW-fa.md`
 *      (پارسرِ `tools/whatsnew/check.mjs`). `bundledTextIsTheFileOnDisk` آینه ندارد: `WhatsNewBundled` را Gradle
 *      هنگامِ ساخت از همین پرونده می‌سازد.
 *   ۲) (`WebPaletteParityTest` و آینه‌اش — پالتِ منجمدِ `index.html` ِ وبِ دکان — با برداشتنِ وبِ دکان به تصمیمِ صاحب رفتند،
 *      ۱۴۰۵/۰۸/۲۰.)
 *   ۳) `android-native/…/data/CustomerOnlineOrdersTest` — وضعیت‌های باز/لغوِ برنامه در `server/src/lib/orders.js`.
 * بی پایگاه و بی شبکه؛ فهرستِ کامل و دلیلِ آینه‌نشدنِ بقیه: RULES-fa «v3 · CI و آزمون».
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const KT = require('./kt-source');

test('kt-source: شکلِ ناشناختهٔ کاتلین خطاست، نه رد شدنِ بی‌صدا', () => {
  const parse = (src) => KT.seq(src, KT.at(src, 'val x = listOf(', 't'), 't').items;
  assert.deepEqual(parse('val x = listOf(\n  "a", // توضیح\n  "b\\u200c\\"",\n)'), ['a', 'b‌"']);
  assert.throws(() => parse('val x = listOf("a", B)'), /رشتهٔ کاتلین نیست/);
  assert.throws(() => parse('val x = listOf("a$b")'), /قالب/);
  assert.throws(() => parse('val x = listOf("""a""")'), /خام/);
  assert.throws(() => parse('val x = listOf("a" "b")'), /نشانهٔ ناشناخته/);
  assert.throws(() => parse('val x = listOf("\\q")'), /گریزِ ناشناخته/);
  assert.throws(() => parse('val x = listOf("a")\nval x = listOf("b")'), /بیش از یک بار/);
  assert.throws(() => parse('val y = listOf("a")'), /پیدا نشد/);
  assert.throws(() => KT.one('aa', /a/, 't'), /2 بار/);
});

/* ------------------------------------ ۱) WhatsNewFileTest ------------------------------------ */

const WN_TEST = 'core/src/jvmTest/kotlin/ir/vil3ntec/tohid/core/guide/WhatsNewFileTest.kt';
const WN = 'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/guide/WhatsNew.kt';
const scanOf = async () => (await import(pathToFileURL(path.join(KT.ROOT, 'tools', 'whatsnew', 'check.mjs')).href)).scan;

/** کانال‌های `for (ch in listOf(WhatsNew.CHANNEL_…, …))` ِ آزمون، با مقدارشان در WhatsNew.kt */
function channels() {
  const names = KT.one(KT.read(WN_TEST), /for \(ch in listOf\(((?:WhatsNew\.\w+(?:, )?)+)\)\)/, 'WhatsNewFileTest.kt')[1]
    .split(', ').map((n) => n.replace('WhatsNew.', ''));
  return names.map((n) => KT.one(KT.read(WN), new RegExp(`const val ${n} = "([a-z]+)"`), 'WhatsNew.kt')[1]);
}

/** هر چهار سنجشِ WhatsNewFileTest (جز bundled) روی یک متن ⇒ فهرستِ شکست‌ها (خالی ⇒ سبز) */
function whatsNewFailures(scan, text, gradle, chans) {
  const log = scan(text);
  const out = [];
  //  changelogParsesWithoutProblems
  for (const p of log.problems) out.push(`docs/WHATS-NEW-fa.md: ${p}`);
  if (!log.sections.length) return out.concat('هیچ بخشی نیست');
  //  newestSectionMatchesTheAppVersionLine (`find` ِ کاتلین: نخستین)
  const base = /val versionBase = "(\d+\.\d+)"/.exec(gradle)?.[1];
  if (!base) out.push('versionBase در android-native/app/build.gradle.kts پیدا نشد');
  else if (log.sections[0].line !== base) out.push(`تازه‌ترین بخشِ «تازه‌ها» باید خطِ نسخهٔ برنامه (${base}) باشد، نه ${log.sections[0].line}`);
  //  shippedNumbersGoDownTheFileAndOnlyKnownChannels
  for (const ch of chans) {
    const nums = log.sections.map((s) => s.shipped[ch]).filter((x) => x !== undefined);
    if (nums.some((x, i) => i && x > nums[i - 1])) out.push(`شمارهٔ ${ch} باید از بالا به پایین کم شود: ${nums.join(' ')}`);
    if (new Set(nums).size !== nums.length) out.push(`شمارهٔ تکراریِ ${ch}`);
  }
  const used = [...new Set(log.sections.flatMap((s) => Object.keys(s.shipped)))];
  if (!used.every((c) => chans.includes(c))) out.push(`کانالِ ناشناخته: ${used.join(' ')}`);
  const first = log.sections.findIndex((s) => Object.keys(s.shipped).length);
  if (first >= 0 && !log.sections.slice(first).every((s) => Object.keys(s.shipped).length)) out.push('بخشِ بی شماره زیرِ بخشِ شماره‌دار');
  return out;
}

test('آینهٔ WhatsNewFileTest: «تازه‌ها» بی مشکل، تازه‌ترین بخش خطِ versionBase، shipped رو به پایین و فقط دو کانال', async () => {
  const scan = await scanOf();
  const chans = channels();
  assert.deepEqual(chans, ['preview', 'beta']);
  const text = KT.read('docs/WHATS-NEW-fa.md');
  const gradle = KT.read('android-native/app/build.gradle.kts');
  assert.deepEqual(whatsNewFailures(scan, text, gradle, chans), []);
  //  خودِ سنجه سرخ می‌شود: هر چهار شکستِ کاتلین، و دو مشکلی که فقط WhatsNew.parse می‌گرفت (سطرِ تکراری، shipped=0)
  const sec = (head, ...lines) => `## ${head}\n${lines.join('\n')}\n`;
  const ok = sec('3.2 · ب', '<!-- shipped: preview=2 beta=20 -->', '- تازه: دو') + sec('3.2 · الف', '<!-- shipped: preview=1 beta=10 -->', '- تازه: یک');
  assert.deepEqual(whatsNewFailures(scan, ok, 'val versionBase = "3.2"', chans), []);
  const bad = (t, g = 'val versionBase = "3.2"') => whatsNewFailures(scan, t, g, chans).join('\n');
  assert.match(bad(ok, 'val versionBase = "3.3"'), /خطِ نسخهٔ برنامه \(3\.3\)/);
  assert.match(bad(sec('3.2 · الف', '<!-- shipped: preview=1 -->', '- تازه: یک') + sec('3.2 · ب', '<!-- shipped: preview=2 -->', '- تازه: دو')), /از بالا به پایین/);
  assert.match(bad(sec('3.2 · الف', '<!-- shipped: alpha=1 -->', '- تازه: یک')), /کانالِ ناشناخته/);
  assert.match(bad(sec('3.2 · الف', '<!-- shipped: preview=1 -->', '- تازه: یک') + sec('3.2 · ب', '- تازه: دو')), /بی شماره زیرِ/);
  assert.match(bad(sec('3.2 · الف', '- تازه: یک', '- رفع: دو') + sec('3.2 · ب', '- تازه:  یک ')), /پیش‌تر آمده/);
  assert.match(bad(sec('3.2 · الف', '<!-- shipped: preview=0 -->', '- تازه: یک')), /preview=0/);
});

const WN_COMMON_TEST = 'core/src/commonTest/kotlin/ir/vil3ntec/tohid/core/guide/WhatsNewTest.kt';

//  `WhatsNewTest.bundledLogParsesAndAnnouncesTheOldLookRemovalWithWhereToGoNow` (commonTest) همان پرونده را از
//  `WhatsNew.bundled` می‌خواند؛ بخشِ پرونده‌ایِ آن این‌جاست. بخشِ `WhatsNew.plan`/`versionOf` (کدامین بخش روی
//  پیش‌نمایشِ ۶۰ دیده می‌شود) منطقِ کاتلین است و همتای JS ندارد — آینه نمی‌شود.
test('آینهٔ WhatsNewTest.bundledLog…: «برداشته» ِ شکلِ قدیم یک بار، با «رابطِ تازه» و «به‌جایش»', async () => {
  const kt = KT.read(WN_COMMON_TEST);
  const has = KT.one(kt, /\.single \{ it\.kind == Kind\.REMOVED && it\.text\.contains\(("[^"\\]*")\) \}/, 'WhatsNewTest.kt')[1];
  const also = KT.one(kt, /assertTrue\(removed\.text\.contains\(("[^"\\]*")\), removed\.text\)/, 'WhatsNewTest.kt')[1];
  const [a, b] = [has, also].map((x) => KT.literal(x, 0, 'WhatsNewTest.kt').value);
  const log = (await scanOf())(KT.read('docs/WHATS-NEW-fa.md'));
  const removed = log.sections.flatMap((s) => s.entries).filter((e) => e.kind === 'برداشته' && e.text.includes(a));
  assert.equal(removed.length, 1, `«برداشته» با «${a}» باید درست یک بار بیاید`);
  assert.ok(removed[0].text.includes(b), removed[0].text);
  assert.ok(removed[0].instead && removed[0].instead.trim(), 'بی «به‌جایش»');
});

/* ------------------------------- ۳) CustomerOnlineOrdersTest -------------------------------- */

const ORDERS_TEST = 'android-native/app/src/test/java/ir/vil3ntec/tohid/data/CustomerOnlineOrdersTest.kt';
const ORDERS_KT = 'android-native/app/src/main/java/ir/vil3ntec/tohid/data/CustomerOnlineOrders.kt';

test('آینهٔ CustomerOnlineOrdersTest: هر وضعیتِ باز و لغوِ پروندهٔ مشتری در server/src/lib/orders.js هست', () => {
  //  همان دو فهرستی که آزمون می‌گرداند (`CustomerOnlineOrders.OPEN + CustomerOnlineOrders.CANCELLED`)
  const m = KT.one(KT.read(ORDERS_TEST),
    /for \(s in CustomerOnlineOrders\.(\w+) \+ CustomerOnlineOrders\.(\w+)\) assertTrue\(s, server\.contains\("'\$s'"\)\)/, 'CustomerOnlineOrdersTest.kt');
  const statuses = [m[1], m[2]].flatMap((n) => KT.list(ORDERS_KT, `val ${n}: Set<String> = setOf(`));
  assert.ok(statuses.length >= 10 && statuses.includes('new') && statuses.includes('cancelled'), statuses.join(' '));
  const server = KT.read('server/src/lib/orders.js');
  assert.deepEqual(statuses.filter((s) => !server.includes(`'${s}'`)), [], 'وضعیتِ برنامه در orders.js ِ سرور نیست');
});
