package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.sale.SaleTax

/**
 *  POS-37 — تنظیمِ «مالیاتِ فاکتور» ِ دکان در `tohid-policy` (کلیدهای دکان در `ShopSettingsKeys`: به همهٔ دستگاه‌ها می‌رود):
 *  `sale_tax` حالت («invoice» / «line»؛ نبود ⇒ خاموش)، `sale_tax_bp` نرخِ پایه به صدمِ درصد (بی پیش‌فرض، D-11)،
 *  `sale_tax_rates` نرخِ کالاها در حالتِ ردیف («شناسه=نرخ»، حداکثر `SaleTax.MAX_RATES`). نوشتن فقط از صفحهٔ تنظیماتِ صاحب و مدیر.
 */
object SaleTaxPref {

  private const val PREFS = "tohid-policy"
  private const val MODE = "sale_tax"
  private const val RATE = "sale_tax_bp"
  private const val RATES = "sale_tax_rates"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** تنظیمِ ذخیره‌شده (بی سنجشِ پرچمِ سرور)؛ خاموش یا نادرست ⇒ `null` */
  fun config(context: Context): SaleTax.Config? = runCatching {
    val p = prefs(context)
    val mode = SaleTax.Mode.of(p.getString(MODE, null)) ?: return@runCatching null
    val rates = p.getStringSet(RATES, null).orEmpty().mapNotNull { item ->
      val eq = item.lastIndexOf('=')
      if (eq <= 0) null else item.substring(eq + 1).toIntOrNull()?.let { item.substring(0, eq) to it }
    }.toMap()
    SaleTax.clean(SaleTax.Config(mode, p.getInt(RATE, 0), rates))
  }.getOrNull()

  /** `null` ⇒ خاموش (هر سه کلید پاک) */
  fun set(context: Context, c: SaleTax.Config?) {
    val v = SaleTax.clean(c)
    val e = prefs(context).edit()
    if (v == null) e.remove(MODE).remove(RATE).remove(RATES)
    else {
      e.putString(MODE, v.mode.wire).putInt(RATE, v.rateBp)
      if (v.rates.isEmpty()) e.remove(RATES) else e.putStringSet(RATES, v.rates.entries.map { "${it.key}=${it.value}" }.toSet())
    }
    e.apply()
  }

  /** همان که صندوق به `SalesEngine.Checkout.tax` می‌دهد: پرچمِ سرور و همین تنظیم */
  fun active(context: Context): SaleTax.Config? = SaleTaxWiring.active(config(context))
}
