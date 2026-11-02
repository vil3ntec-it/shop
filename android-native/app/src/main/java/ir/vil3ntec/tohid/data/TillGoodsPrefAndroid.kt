package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  «ثبتِ سریع و واحدِ بزرگ در صندوق» روی **همین دستگاه** (پروندهٔ `tohid-policy`). هر سه پیش‌فرض **خاموش** ⇒
 *  صندوق بایت‌به‌بایت همان امروز.
 *  - `quick_register` (POS-17، S03): بارکدِ ناشناس ⇒ پنجرهٔ «ثبتِ سریعِ کالا» در خودِ صندوق برای نقشی که کالا
 *    می‌نویسد (`QuickRegister.allowed`، همان `PermCore.canWrite` ِ سرور) و کالای ثبت‌شده به فاکتور می‌رود؛
 *    صندوق‌دارِ بی‌مجوز فقط «قلمِ آزاد» (اگر بسته نباشد).
 *  - `unit_sale` (POS-09، PRD-07، S05): Ctrl+U و واحدهای کالا (واحدِ دوم و بسته‌ها با قیمتِ لیست) در برگهٔ ردیف.
 *  - `whole_packs`: فروشِ بخشی از بسته مجاز نیست (۱٫۵ کارتن رد)؛ فقط با `unit_sale`.
 */
object TillGoodsPref {

  private const val QUICK = "quick_register"
  private const val UNITS = "unit_sale"
  private const val WHOLE = "whole_packs"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)
  private fun get(context: Context, key: String) = runCatching { prefs(context).getBoolean(key, false) }.getOrDefault(false)
  private fun put(context: Context, key: String, on: Boolean) { prefs(context).edit().putBoolean(key, on).apply() }

  fun quickRegister(context: Context): Boolean = get(context, QUICK)
  fun setQuickRegister(context: Context, on: Boolean) = put(context, QUICK, on)

  fun units(context: Context): Boolean = get(context, UNITS)
  fun setUnits(context: Context, on: Boolean) = put(context, UNITS, on)

  fun wholePacks(context: Context): Boolean = get(context, WHOLE)
  fun setWholePacks(context: Context, on: Boolean) = put(context, WHOLE, on)

  /** برنامهٔ واحدهای ردیفِ [p] برای برگهٔ ردیف؛ خاموش، قلمِ آزاد یا کالای تک‌واحد ⇒ `null` (همان برگهٔ پیشین) */
  fun plan(context: Context, p: Product?, line: SalesEngine.CartLine, next: Boolean = false): UnitLine.Plan? =
    if (p == null || line.free || !units(context)) null else UnitLine.plan(p, wholePacks(context), line.quantity, line.unitPrice, next)
}
