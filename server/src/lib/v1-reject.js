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
  req.body.ops = ops.filter((_, i) => !rejected.has(i));
  const send = res.json.bind(res);
  res.json = (body) => {
    if (body && body.ok && Array.isArray(body.results)) {
      const rest = body.results.slice();
      const merged = [];
      for (let i = 0; i < ops.length; i++) merged.push(rejected.has(i) ? rejected.get(i) : rest.shift());
      //  اگر شمارِ پاسخ‌ها نخواند، چیزی گم نشود
      body.results = merged.filter(Boolean).concat(rest);
    }
    return send(body);
  };
}

module.exports = { rejectOps };
