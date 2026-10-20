/**
 * شکاف‌های شناخته — گامی که روی main **به دلیلِ نبودِ یک قابلیت** (نه یک باگ) قرمز است.
 *
 * ⛔ این فهرست جای «سبز کردن» نیست:
 *   • هر ردیف به یک گامِ مشخص می‌خورد و فقط وقتی که **همان** خطای ثبت‌شده (`match`)
 *     رخ دهد؛ هر خطای دیگری در همان گام قرمزِ واقعی است.
 *   • گامی که این‌جا هست ولی سبز شد «⚠️ xpass» چاپ می‌شود: یعنی قابلیت آمده و ردیف
 *     باید پاک شود (در جدولِ نهایی پیداست).
 *   • هر ردیف مرجع دارد: شناسهٔ سند، PR ِ بازِ سازنده یا blocker.
 *   • باگِ واقعی فقط با PR ِ اصلاحِ باز (شماره در `ref`) یا blocker ِ صریح (مثلاً پروندهٔ
 *     ممنوعِ مشترک با پمپ) این‌جا می‌آید؛ پس از مرجِ اصلاح «xpass» می‌شود و ردیف پاک.
 */
export const KNOWN_GAPS = [
  {
    scenario: 'return', step: 'S13.waste', match: /مقصدِ «ضایعات»/,
    ref: 'RET-07 (STATUS: ناقص) — SalesEngine.recordReturn مقصدِ برگشت (موجودی/ضایعات) ندارد؛ کالای «خراب» به موجودیِ فروختنی برمی‌گردد',
  },
  {
    scenario: 'stock', step: 'CHK-18.accept', match: /قبولِ سفارشِ بی‌موجودی پذیرفته شد/,
    ref: 'باگ — اصلاح در PR #308 (catalog.onOrderStatus رزروِ بی‌پشتوانه را بی‌مهلت می‌کرد)',
  },
  {
    scenario: 'stock', step: 'E2E-STK.pos-view', match: /فروشِ اینترنتی به دفترِ دستگاه برنمی‌گردد/,
    ref: '§۳۲.۲ بندهای ۱ و ۴ — MISSING: کسرِ سفارشِ اینترنتی فقط در دفترِ سرور (#263) است و به دفترِ برنامهٔ دکان (ShopStore.stock) برنمی‌گردد',
  },
  {
    scenario: 'security', step: 'E2E-SEC-13.op-children', match: /قلمِ بی‌فاکتور روی سرور ماند/,
    ref: 'باگ — BLOCKED: authorizeOp در server/src/lib/sync-v1.js (مشترک با پمپ، دست‌نزدنی) فقط سرِ فاکتور را رد می‌کند؛ تصمیمِ نشستِ اصلی لازم است',
  },
  {
    scenario: 'ops', step: 'E2E-OPS-14.replay', match: /فروشِ پس از پشتیبان روی سرورِ بازیابی‌شده نیست/,
    ref: '§۹.۵ — MISSING: پس از بازیابیِ سرور، برنامه cursor ِ جلوتر از سرِ دفترِ سرور را تشخیص نمی‌دهد و opهای «اعمال‌شده» را دوباره نمی‌فرستد',
  },
  {
    scenario: 'admin', step: 'E2E-ADM.counts', match: /Sync v1 شمرده نمی‌شود/,
    ref: 'باگ — اصلاح در PR #310 (GET /api/admin/shops/:id فقط راهِ قدیم را می‌شمرد)',
  },
];

/**
 * @returns {{status:'pass'|'fail'|'known-gap'|'xpass', ref?:string}}
 */
export function classify(scenario, stepId, error) {
  const gap = KNOWN_GAPS.find((g) => g.scenario === scenario && g.step === stepId);
  if (!error) return gap ? { status: 'xpass', ref: `در known-gaps هست ولی سبز شد — ردیف را پاک کنید (${gap.ref})` } : { status: 'pass' };
  if (gap && gap.match.test(String(error.message || error))) return { status: 'known-gap', ref: gap.ref };
  return { status: 'fail' };
}
