package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  نقشِ این حساب در دکان — و آنچه از آن برمی‌آید.
 *
 *  ── چرا لازم شد ────────────────────────────────────────────────────
 *  سرور نقش را همیشه می‌دانست و اجازه‌ها را هم اجرا می‌کرد، ولی
 *  **برنامه** نمی‌دانست. یعنی شاگرد همان صفحه‌هایی را می‌دید که صاحب
 *  دکان: تنظیمات باز بود، کلیدِ حساب و کدِ شاگرد جلویش بود، و سود و
 *  زیانِ دکان را می‌خواند.
 *
 *  قرارِ صاحب دکان روشن است: شاگرد بفروشد، ببیند، کار کند — ولی
 *  تنظیمات را اصلاً باز نکند و عددهای مالی را نبیند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  این فقط برای **ظاهر** است. سدِ واقعی سمتِ سرور است و آنجا سرِ جایش
 *  هست؛ اینجا کاری می‌کند که شاگرد اصلاً به درِ بسته نخورد.
 */
object ShopRole {

  private const val PREFS = "tohid-ledger"   // کنارِ صاحبِ دفتر، تا با هم پاک شوند
  private const val KEY = "shop_role"

  /** نقش‌هایی که سرور می‌شناسد */
  const val OWNER = "owner"
  const val MANAGER = "manager"
  const val STAFF = "staff"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /**
   *  نقشِ ذخیره‌شده.
   *
   *  خالی یعنی هنوز از سرور نپرسیده‌ایم. آن‌وقت **صاحب** فرض می‌شود، نه
   *  شاگرد: کسی که آفلاین و بی‌حساب کار می‌کند دکانِ خودش را دارد و
   *  نباید تنظیماتِ خودش قفل باشد.
   */
  fun current(context: Context): String =
    prefs(context).getString(KEY, "").orEmpty().ifBlank { OWNER }

  fun remember(context: Context, role: String) {
    val clean = role.trim().lowercase()
    if (clean.isBlank()) return
    prefs(context).edit().putString(KEY, clean).apply()
  }

  fun forget(context: Context) {
    prefs(context).edit().remove(KEY).apply()
  }

  /** شاگرد است — نه صاحب، نه مدیر */
  fun isStaff(context: Context): Boolean = current(context) == STAFF

  /**
   *  حق دیدنِ عددهای مالی: درآمد، فایده، ضرر.
   *
   *  شاگرد می‌فروشد ولی نمی‌داند دکان چقدر سود می‌کند. این خواستهٔ
   *  صاحب دکان است، نه یک تصمیمِ فنی.
   */
  fun canSeeMoney(context: Context): Boolean = !isStaff(context)

  /** حق باز کردنِ تنظیمات */
  fun canOpenSettings(context: Context): Boolean = !isStaff(context)
}
