package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.returns.ReturnPolicy

/**
 *  RET-09 — سیاستِ برگشتِ همین دستگاه (`tohid-policy`: `return_window_days`، `return_blocked`)؛ فایلِ
 *  مرزیِ پلتفرم. قاعده در هسته (`core/…/returns/ReturnPolicy.kt`).
 *
 *  پیش‌فرض **خاموش**: نبودنِ هر دو کلید ⇒ `ReturnPolicy.Policy()` (`on = false`) و برگشت همان امروز.
 *  فقط در جریانِ تازهٔ برگشت (`ReturnSettings.enabled`) به کار می‌رود.
 */
object ReturnPolicyPrefs {
  private const val PREFS = "tohid-policy"
  private const val DAYS = "return_window_days"
  private const val BLOCKED = "return_blocked"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun of(context: Context): ReturnPolicy.Policy {
    val p = prefs(context)
    return ReturnPolicy.Policy(
      days = p.getInt(DAYS, 0).coerceIn(0, ReturnPolicy.MAX_DAYS),
      blocked = ReturnPolicy.parseRules(p.getString(BLOCKED, null)),
    )
  }

  /** نوشتهٔ کادرِ مهلت؛ خالی یا ۰ ⇒ کلید پاک (بی مهلت) */
  fun setDays(context: Context, text: String) {
    val n = ReturnPolicy.days(text)
    prefs(context).edit().apply { if (n > 0) putInt(DAYS, n) else remove(DAYS) }.apply()
  }

  /** متنِ کادرِ «غیرقابلِ برگشت»؛ فهرستِ خالی ⇒ کلید پاک */
  fun setBlocked(context: Context, text: String) {
    val list = ReturnPolicy.parseRules(text)
    prefs(context).edit().apply { if (list.isNotEmpty()) putString(BLOCKED, ReturnPolicy.formatRules(list)) else remove(BLOCKED) }.apply()
  }
}
