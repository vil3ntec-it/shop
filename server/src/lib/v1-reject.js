'use strict';
/**
 * ردِ بخشی از یک push ِ Sync v1 ِ دکان، پیش از `sync-v1.js` (مشترک با پمپ، دست‌نخورده).
 *
 * میان‌افزارِ فقط‌دکان (`staff-approval.v1Guard`، `safe-ledger.v1SafeGuard`) opهای ردشده را از
 * بدنه برمی‌دارد تا v1 فقط بقیه را بنشاند، و پاسخِ ردشده‌ها را سرِ جای خودشان در `results`
 * می‌گذارد — برنامه پاسخِ هر op را به ترتیبِ فرستادن می‌خواند. چند میان‌افزار پشتِ سرِ هم
 * درست کنارِ هم می‌نشینند: هر کدام نسبت به همان فهرستی که خودش دید جا می‌دهد.
 *
 * @param {Array} ops              همان `req.body.ops` که این میان‌افزار دید
 * @param {Map<number, object>} rejected  جایگاه ⇐ `{op_id, status: 'rejected', reason}`
 */
function rejectOps(req, res, ops, rejected) {
  if (!rejected.size) return;
  //  ردی که به `req.body.ops` نرسد یعنی op ِ ردشده نشانده می‌شود ⇒ فهرستِ دیگر خطای برنامه‌نویس است و push می‌شکند (بسته، نه باز)
  if (!req.body || req.body.ops !== ops) throw new Error('v1-reject: ops باید همان req.body.ops باشد');
  //  ⛔ فهرستِ همان درخواست **در جا** کوتاه می‌شود، نه `req.body.ops = …`: نگهبانی که پس از `await` می‌نویسد (POS-40 ِ
  //  `till-guard`، پیش‌تر `product-unit-rules`) آن نوشتن را CodeQL تا `applyOp` ِ `sync-v1.js` (پمپ، دست‌نخورده) دنبال
  //  می‌کرد (remote-property-injection ِ دسته‌های ۴۰ و ۴۶). نسخهٔ `original` برای جاگذاریِ پاسخ‌هاست: میان‌افزارِ بعدی همین
  //  آرایه را باز کوتاه می‌کند و هر کدام نسبت به فهرستی که خودش دید جا می‌دهد.
  const original = ops.slice();
  let w = 0;
  for (let i = 0; i < original.length; i++) if (!rejected.has(i)) ops[w++] = original[i];
  ops.length = w;
  const send = res.json.bind(res);
  res.json = (body) => {
    if (body && body.ok && Array.isArray(body.results)) {
      const rest = body.results.slice();
      const merged = [];
      for (let i = 0; i < original.length; i++) merged.push(rejected.has(i) ? rejected.get(i) : rest.shift());
      //  اگر شمارِ پاسخ‌ها نخواند، چیزی گم نشود
      body.results = merged.filter(Boolean).concat(rest);
    }
    return send(body);
  };
}

module.exports = { rejectOps };
