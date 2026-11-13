'use strict';
/**
 * POS-04 و WEB-05 — پیش‌نویسِ فهرستِ مترادف (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۶: «Claude پیش‌نویس می‌سازد، صاحب اصلاح می‌کند»).
 *
 * داده: `scripts/search-synonyms-draft.json` (همان شکلِ `groups` ِ `lib/search-synonyms.js`، یک گروه در هر خط) و سندِ خوانا
 * `docs/SYNONYMS-draft-fa.md`. ⛔ پیش‌فرض خاموش: هیچ چیز خودکار بار نمی‌شود — فقط `node scripts/synonyms-draft.js --apply
 * --reason "…"` (یا چسباندن در پنل ⇐ «مترادف‌ها»). پس از بار هم ویترین فقط با پرچمِ دکانِ `shop.storefront.synonyms` آن را
 * می‌گیرد؛ صندوقِ برنامه‌ها روزی یک بار از `GET /api/search/synonyms`.
 *
 * بار کردن **افزودن** است، نه جایگزینی: فهرستِ کنونیِ پنل (ویرایشِ صاحب‌کار) دست نمی‌خورد و هر گروهِ پیش‌نویس که حتی یک واژه‌اش
 * در فهرستِ کنونی هست کنار می‌رود و در گزارش نام برده می‌شود (هر واژه فقط در یک گروه). نوشتن همان `replace` ِ پنل است: همان
 * سنجش، قفلِ نسخه و سابقهٔ `admin.synonyms_updated` با دلیل. تکرار ⇒ `unchanged`.
 */
const fs = require('fs');
const path = require('path');
const syn = require('./search-synonyms');

const FILE = path.join(__dirname, '..', '..', 'scripts', 'search-synonyms-draft.json');

/** پرونده ⇒ گروه‌های سنجیده (همان `validate` ِ پنل؛ خطا ⇒ throw) */
function readDraft(file = FILE) {
  const doc = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!doc || !Array.isArray(doc.groups)) throw new Error('پروندهٔ پیش‌نویس groups ندارد');
  return syn.validate(doc.groups);
}

/** کنونی + پیش‌نویس ⇒ {groups, added, skipped}؛ گروهِ هم‌پوشان با کنونی کنار (فهرستِ کنونی همان) */
function merge(current, draft) {
  const taken = new Set(current.flat().map(syn.synonymKey));
  const added = [];
  const skipped = [];
  for (const g of draft) {
    const clash = g.filter((t) => taken.has(syn.synonymKey(t)));
    if (clash.length) { skipped.push({ group: g, clash }); continue; }
    added.push(g);
    for (const t of g) taken.add(syn.synonymKey(t));
  }
  return { groups: [...current, ...added], added, skipped };
}

/**
 * پیش‌نمایش یا بار کردن.
 * @param {{apply?: boolean, reason?: string, file?: string, userId?: string}} o
 * @returns {Promise<{apply:boolean, draft:number, added:number, skipped:Array, version:number, unchanged?:boolean}>}
 */
async function load({ apply = false, reason = '', file = FILE, userId = 'script:synonyms-draft' } = {}) {
  const draft = readDraft(file);
  const cur = await syn.get();
  const m = merge(cur.groups, draft);
  const out = { apply: Boolean(apply), draft: draft.length, added: m.added.length, skipped: m.skipped, version: cur.version };
  if (!apply) return out;
  const why = String(reason || '').trim();
  if (why.length < 3 || why.length > 200) throw new Error('دلیل لازم است (۳ تا ۲۰۰ نویسه)، مثلاً --reason "پیش‌نویسِ بازبینی‌شدهٔ صاحب‌کار"');
  const r = await syn.replace(m.groups, cur.version, { userId, reason: why });
  return { ...out, version: r.version, unchanged: r.unchanged };
}

module.exports = { FILE, readDraft, merge, load };
