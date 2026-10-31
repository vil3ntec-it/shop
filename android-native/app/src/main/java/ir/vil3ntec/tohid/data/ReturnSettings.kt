package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.returns.ReturnReasons

/**
 *  RET (v3 §۶.۸) — «برگشت و تعویضِ تازه»، انتخابِ خودِ دکان‌دار روی همین دستگاه.
 *
 *  پیش‌فرض **خاموش**: دکمهٔ «مرجوعی» همان پنجرهٔ امروز را باز می‌کند و همان
 *  `SalesEngine.recordReturn` را می‌زند؛ رسیدِ فروش هم بی QR می‌ماند. روشن ⇒
 *  `ReturnFlow` (جست‌وجو/اسکنِ رسید، دلیلِ اجباری، به‌موجودی/ضایعات، روشِ
 *  بازپرداخت، تعویض، بی‌رسید با تأیید، رسیدِ برگشت).
 *
 *  کنارِ `ShopPolicy` در همان پرونده (`tohid-policy`)، تا با هم پاک شوند. گوشی و
 *  کامپیوتر هر دو همین را می‌خوانند؛ وبِ مدیریت عوض نمی‌شود.
 */
object ReturnSettings {
  private const val PREFS = "tohid-policy"
  private const val ENABLED = "returns_v2"
  private const val LIMIT = "return_limit"
  private const val REASONS = "return_reasons"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun enabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED, false)

  fun setEnabled(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(ENABLED, on).apply()
  }

  /** سقفِ برگشت بی تأییدِ صاحبِ دکان برای مدیر و شاگرد (افغانی)؛ ۰ = بی سقف */
  fun limit(context: Context): Double =
    prefs(context).getFloat(LIMIT, 0f).toDouble().takeIf { it.isFinite() && it > 0 } ?: 0.0

  fun setLimit(context: Context, afghani: Double) {
    val v = if (afghani.isFinite() && afghani > 0) afghani.toFloat() else 0f
    prefs(context).edit().putFloat(LIMIT, v).apply()
  }

  /**
   *  RET-08 — فهرستِ دلیل‌های برگشتِ این دکان (هر خط یک دلیل)؛ نانوشته یا خالی ⇒ فهرستِ پیش‌فرض
   *  (`ReturnReasons.DEFAULT` = `ReturnFlow.REASONS`). پاک‌سازی همیشه با `ReturnReasons.normalize`.
   */
  fun reasons(context: Context): List<String> = ReturnReasons.parse(prefs(context).getString(REASONS, null).orEmpty())

  /** ذخیرهٔ متنِ کادرِ تنظیمات؛ فهرستی که همان پیش‌فرض است پاک می‌شود تا تغییرِ پیش‌فرض به آن برسد */
  fun setReasons(context: Context, text: String) {
    val list = ReturnReasons.parse(text)
    prefs(context).edit().apply {
      if (list == ReturnReasons.DEFAULT) remove(REASONS) else putString(REASONS, ReturnReasons.format(list))
    }.apply()
  }
}
