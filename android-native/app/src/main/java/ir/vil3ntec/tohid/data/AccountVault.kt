package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  چیزهای کوچکی که مالِ حساب‌اند، نه مالِ گوشی.
 *
 *  ── چرا لازم شد ────────────────────────────────────────────────────
 *  دفترِ دکان به حساب بسته شد، ولی چند چیزِ کوچک بیرون از دفتر مانده
 *  بودند و در حافظه‌ی مشترکِ برنامه می‌نشستند. مهم‌ترینشان **نامِ دکان**
 *  بود: روی داشبورد، در تنظیمات، و — بدتر از همه — بالای **فاکتورِ
 *  چاپ‌شده**. یعنی محمود که روی گوشیِ احمد وارد می‌شد، فاکتورهایش با
 *  نامِ دکانِ احمد چاپ می‌شد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  اینجا هر کلید زیرِ نامِ صاحبش بایگانی می‌شود و کلیدِ حسابِ تازه سرِ
 *  جایش می‌نشیند. صفحه‌ها همان کلیدِ همیشگی را می‌خوانند و چیزی در آن‌ها
 *  عوض نشده؛ فقط مقدارش دیگر مالِ نفرِ قبلی نیست.
 *
 *  چیزهایی که عمداً اینجا نیستند، چون به **گوشی** بسته‌اند نه به حساب:
 *  نشانیِ چاپگر و پهنای کاغذش، تمِ روشن/تاریک، رمزِ ورودِ برنامه، و
 *  نشانیِ سرور.
 */
object AccountVault {

  private const val PREFS = "tohid"

  /** کلیدهایی که با عوض شدنِ حساب باید جابه‌جا شوند */
  private val KEYS = listOf(
    "store_name",      // نامِ دکان — روی فاکتور چاپ می‌شود
    "last_backup_at",  // آخرین پشتیبان — هشدارش مالِ همان حساب است
  )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun vaultKey(key: String, owner: String) = "$key@${safe(owner)}"

  private fun safe(owner: String): String =
    owner.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(48).ifBlank { "anon" }

  /**
   *  بایگانیِ مقدارهای حسابِ قبلی و باز کردنِ مقدارهای حسابِ تازه.
   *
   *  در یک `edit` انجام می‌شود تا اگر وسطِ کار چیزی پیش بیاید، نصفه‌نیمه
   *  نماند: یا همه‌ی کلیدها جابه‌جا شده‌اند یا هیچ‌کدام.
   */
  fun switch(context: Context, from: String, to: String) {
    val p = prefs(context)
    val edit = p.edit()
    for (key in KEYS) {
      //  مقدارِ فعلی می‌رود زیرِ نامِ صاحبش
      val current = p.all[key]
      if (current != null) put(edit, vaultKey(key, from), current) else edit.remove(vaultKey(key, from))

      //  و مقدارِ حسابِ تازه — اگر روی این گوشی سابقه‌ای داشته باشد
      val mine = p.all[vaultKey(key, to)]
      if (mine != null) put(edit, key, mine) else edit.remove(key)
    }
    edit.apply()
  }

  private fun put(edit: android.content.SharedPreferences.Editor, key: String, value: Any) {
    when (value) {
      is String -> edit.putString(key, value)
      is Boolean -> edit.putBoolean(key, value)
      is Int -> edit.putInt(key, value)
      is Long -> edit.putLong(key, value)
      is Float -> edit.putFloat(key, value)
      //  نوعِ ناشناخته را جابه‌جا نمی‌کنیم؛ ولی مقدارِ نفرِ قبلی هم
      //  نباید بماند
      else -> edit.remove(key)
    }
  }
}
