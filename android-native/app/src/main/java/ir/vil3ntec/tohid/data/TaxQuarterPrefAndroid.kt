package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.report.QuarterTax

/**
 *  RPT-10 — «گزارشِ مالیاتِ سه‌ماهه» و یادآورِ سررسیدش روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ
 *  `tax_quarter`، کنارِ `DocGapsPref`). پیش‌فرض **خاموش**: نه بخشِ گزارش، نه کارتِ یادآور — برنامه همان امروز.
 *  نرخ، ماهِ آغازِ سالِ مالی و سررسید هم همین‌جا (`QuarterTax.Config`)؛ نرخ پیش‌فرض ندارد (D-11).
 */
object TaxQuarterPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "tax_quarter"
  private const val START = "tax_quarter_start_month"
  private const val RATE = "tax_quarter_rate_bp"
  private const val DUE_DAY = "tax_quarter_due_day"
  private const val DUE_MONTHS = "tax_quarter_due_months"
  private const val REMIND = "tax_quarter_remind_days"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  fun config(context: Context): QuarterTax.Config = runCatching {
    val p = prefs(context)
    val d = QuarterTax.Config()
    QuarterTax.Config(
      startMonth = p.getInt(START, d.startMonth),
      rateBp = p.getInt(RATE, d.rateBp),
      dueDay = p.getInt(DUE_DAY, d.dueDay),
      dueMonths = p.getInt(DUE_MONTHS, d.dueMonths),
      remindDays = p.getInt(REMIND, d.remindDays),
    ).clamped()
  }.getOrDefault(QuarterTax.Config())

  fun setConfig(context: Context, c: QuarterTax.Config) {
    val v = c.clamped()
    prefs(context).edit()
      .putInt(START, v.startMonth).putInt(RATE, v.rateBp).putInt(DUE_DAY, v.dueDay)
      .putInt(DUE_MONTHS, v.dueMonths).putInt(REMIND, v.remindDays)
      .apply()
  }
}
