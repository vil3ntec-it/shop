package ir.vil3ntec.tohid.ui.components.common

/**
 *  نمایشِ «کارتی» یا «جدولی» برای فهرست‌های اصلی — VISUAL-DIRECTION §۱.۵.
 *
 *  هر فهرستِ اصلی دو نما دارد: کارت (مثلِ تصویرِ مرجعِ ۱) و جدول (مثلِ
 *  تصویرِ مرجعِ ۲). هر دو همان داده، فیلتر، جست‌وجو و کلیدها را دارند و
 *  عوض‌کردنِ نما فیلتر و انتخاب را نگه می‌دارد (این کارِ صفحه است؛ این‌جا
 *  فقط «کدام نما» تصمیم گرفته می‌شود).
 *
 *  انتخابِ کاربر **ترجیحِ نمایشیِ محلی** است: برای هر کاربر، هر صفحه، روی
 *  همین دستگاه. همگام نمی‌شود و در پشتیبان نمی‌رود — در `tohid-ui` می‌ماند،
 *  کنارِ حالتِ منوی کناری (`desk_rail`).
 *
 *  پیش‌فرض‌ها: صفحه‌های مدیریت = جدول؛ فروش = کارت؛ گوشی **همیشه** کارت
 *  (جدولِ عریض روی گوشی جا نمی‌شود، §۴ همان سند).
 */
enum class ViewMode(val id: String) {
  CARD("card"),
  TABLE("table");

  companion object {
    /** مقدارِ ذخیره‌شده ⇐ نما؛ هر چیزِ ناشناخته `null` (یعنی پیش‌فرض) */
    fun parse(raw: String?): ViewMode? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
  }
}

/** نوعِ صفحه — فقط برای پیش‌فرض */
enum class ScreenKind { MANAGEMENT, SALE }

/** اندازهٔ صفحه از نگاهِ این تصمیم — گوشی یا بزرگ‌تر */
enum class FormFactor { PHONE, LARGE }

object ViewModePref {
  /** همان پروندهٔ ترجیحاتِ نمایشی (`AppRoot`: `desk_rail`) */
  const val PREFS = "tohid-ui"
  private const val PREFIX = "view_mode"
  /** کاربرِ بی‌حساب (آفلاین، پیش از ورود) */
  const val LOCAL_USER = "local"

  /** پیش‌فرضِ هر صفحه */
  fun default(kind: ScreenKind, form: FormFactor): ViewMode = when {
    form == FormFactor.PHONE -> ViewMode.CARD
    kind == ScreenKind.SALE -> ViewMode.CARD
    else -> ViewMode.TABLE
  }

  /**
   *  نمای مؤثر. گوشی همیشه کارت است، حتی اگر همین کاربر روی تبلت یا
   *  کامپیوترِ همین دستگاه (پنجرهٔ پهن) «جدول» را انتخاب کرده باشد —
   *  انتخاب پاک نمی‌شود، فقط روی گوشی به کار نمی‌رود.
   */
  fun resolve(stored: String?, kind: ScreenKind, form: FormFactor): ViewMode =
    if (form == FormFactor.PHONE) ViewMode.CARD
    else ViewMode.parse(stored) ?: default(kind, form)

  /** آیا کلیدِ عوض‌کردنِ نما نشان داده شود؟ روی گوشی فقط یک نما هست */
  fun toggleVisible(form: FormFactor): Boolean = form != FormFactor.PHONE

  /** نمای دیگر — برای دکمهٔ تک‌کلیدیِ عوض‌کردن */
  fun other(mode: ViewMode): ViewMode = if (mode == ViewMode.CARD) ViewMode.TABLE else ViewMode.CARD

  /**
   *  کلیدِ ذخیره: `view_mode:<کاربر>:<صفحه>`.
   *
   *  شناسه‌ها پاک‌سازی می‌شوند تا «:» یا فاصلهٔ درونِ شناسه دو کلید را
   *  یکی نکند. کاربرِ خالی = `local`.
   */
  fun key(userId: String?, screen: String): String {
    require(screen.isNotBlank()) { "screen id is required" }
    val u = clean(userId).ifEmpty { LOCAL_USER }
    return "$PREFIX:$u:${clean(screen)}"
  }

  private fun clean(s: String?): String =
    s.orEmpty().trim().map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '@') it else '_' }.joinToString("")

  /**
   *  انبارِ کوچکِ کلید/مقدار — تا منطق بی اندروید آزموده شود. `Store.of`
   *  همان `SharedPreferences`ِ برنامه را می‌پوشاند (اندروید و شیمِ کامپیوتر).
   */
  interface Store {
    fun get(key: String): String?
    fun put(key: String, value: String)

    companion object {
      fun of(prefs: android.content.SharedPreferences): Store = object : Store {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
      }
    }
  }

  fun load(store: Store, userId: String?, screen: String, kind: ScreenKind, form: FormFactor): ViewMode =
    resolve(runCatching { store.get(key(userId, screen)) }.getOrNull(), kind, form)

  /**
   *  ذخیرهٔ انتخاب. روی گوشی چیزی نوشته نمی‌شود (نما همیشه کارت است و
   *  نوشتنِ «کارت» انتخابِ جدولِ همین کاربر روی پنجرهٔ پهن را پاک می‌کرد).
   */
  fun save(store: Store, userId: String?, screen: String, mode: ViewMode, form: FormFactor) {
    if (form == FormFactor.PHONE) return
    runCatching { store.put(key(userId, screen), mode.id) }
  }
}
