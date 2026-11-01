package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  CRM-06 — سمتِ پلتفرمِ «متنِ یادآوریِ بدهی» (`DebtReminder`): قالبِ **همین دستگاه** کنارِ سیاست‌های
 *  دیگرِ دکان (`ShopPolicy`، پروندهٔ `tohid-policy` ‹`debt_reminder_template`›).
 *
 *  نبودِ کلید (پیش‌فرض) یا قالبِ نادرست ⇒ `DebtReminder.DEFAULT_TEMPLATE`، یعنی همان جملهٔ امروز، بیت‌به‌بیت.
 *  فقط قالبِ درست ذخیره می‌شود؛ قالبِ برابر با پیش‌فرض کلید را برمی‌دارد. ویرایش فقط در بخشِ «گزارش‌های
 *  مشتری» ِ تنظیماتِ کامپیوتر است (همان `ShopPolicy.crmReports`، پیش‌فرض خاموش).
 */
object DebtReminderPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "debt_reminder_template"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun template(context: Context): String =
    runCatching { prefs(context).getString(KEY, null) }.getOrNull()
      ?.takeIf { DebtReminder.check(it) == null }
      ?.trim()
      ?: DebtReminder.DEFAULT_TEMPLATE

  /** `null` ⇒ ذخیره شد؛ وگرنه همان خطا و چیزی عوض نمی‌شود */
  fun set(context: Context, template: String): DebtReminder.Problem? {
    DebtReminder.check(template)?.let { return it }
    val t = template.trim()
    val e = prefs(context).edit()
    if (t == DebtReminder.DEFAULT_TEMPLATE) e.remove(KEY) else e.putString(KEY, t)
    e.apply()
    return null
  }

  /** برگشت به همان جملهٔ امروز */
  fun reset(context: Context) {
    prefs(context).edit().remove(KEY).apply()
  }
}
