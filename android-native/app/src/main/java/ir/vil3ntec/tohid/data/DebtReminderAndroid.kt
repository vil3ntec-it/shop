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

/**
 *  CRM-06 — دکمه‌های «پیامک/واتس‌اپ» ِ حسابِ مشتری (`DebtorLedgerParts`) با روز و ساعتِ همین دستگاه: متن از قالبِ همین
 *  دستگاه ([DebtReminderPref])، و «فرستاده شد» فقط از راهِ `ShopStore.update` (`DebtReminder.sent`: یک رد در روز برای هر راه).
 */
object DebtReminderSend {
  fun message(context: Context, d: ShopData, debtorId: String, shopName: String): String =
    DebtReminder.forDebtor(d, debtorId, ir.vil3ntec.tohid.todayIso(), DebtReminderPref.template(context), shopName, System.currentTimeMillis())

  /** null ⇒ همان روز همان راه ثبت شده بود (هیچ نوشتن)؛ true نوشته شد؛ false قفلِ نرم یا دیسک */
  suspend fun sent(store: ShopStore, debtorId: String, channel: String): Boolean? =
    store.update { d -> DebtReminder.sent(d, debtorId, channel, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis()) }
}
