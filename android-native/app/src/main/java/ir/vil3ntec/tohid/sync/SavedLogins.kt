package ir.vil3ntec.tohid.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 *  حساب‌هایی که قبلاً از این گوشی وارد شده‌اند.
 *
 *  فروشنده هر روز صبح دکان را باز می‌کند؛ نباید هر بار نشانیِ کاملش را
 *  تایپ کند. یک لمس روی نامش، کادر را پر می‌کند و فقط رمز می‌ماند.
 *
 *  **هیچ اعتبارنامه‌ای اینجا نمی‌ماند** — نه رمز، نه توکن. فقط شناسه
 *  (ایمیل یا شماره) و نام دکان، همان دو چیزی که نسخهٔ وب هم نگه می‌دارد.
 *
 *  ── توکنی که اینجا بود و برداشته شد ────────────────────────────────
 *  تا دیروز کنارِ هر ردیف، **توکنِ تازه‌سازیِ همان حساب** هم نوشته
 *  می‌شد تا «ورودِ سریع» بدونِ رمز کار کند. سه چیز با هم غلط بود:
 *
 *    ۱) آن توکن نود روز عمر دارد و هنگام خروج روی سرور **باطل
 *       نمی‌شد**. یعنی «خروج از حساب» در واقع خروج نبود: یک کلیدِ
 *       زندهٔ سه‌ماهه روی گوشی جا می‌ماند.
 *    ۲) اینجا `SharedPreferences`ِ معمولیِ برنامه است، نه آن یکیِ
 *       رمزشده. توکنِ فعال در `TokenStore` با کلیدِ Keystore رمز
 *       می‌شود؛ این یکی کنارِ نامِ دکان، **رمزنشده** می‌نشست.
 *    ۳) پس دو نسخه از یک کلید روی گوشی بود و «خروج» فقط یکی‌شان را
 *       پاک می‌کرد.
 *
 *  حالا ردیف همان کارِ همیشگی‌اش را می‌کند — یک لمس، کادر پر — ولی
 *  رمز یا کد لازم است. دقیقاً همان رفتاری که نسخهٔ وب از روزِ اول
 *  داشت. `purgeTokens` هم توکن‌هایی را که از نسخه‌های قبلی روی گوشی
 *  مانده‌اند پاک می‌کند، وگرنه وصله فقط جلوی نوشتنِ تازه را می‌گرفت و
 *  آنچه از قبل نوشته شده بود تا ابد می‌ماند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  چهار تا آخر نگه داشته می‌شود، مثل وب — بیشتر از این، فهرست خودش
 *  می‌شود یک صفحهٔ دیگر برای گشتن.
 */
object SavedLogins {

  private const val PREFS = "tohid"

  // همان کلیدِ نسخهٔ وب، تا پشتیبان و همگام‌سازی یک زبان داشته باشند
  private const val KEY = "tohid-saved-logins-v1"

  /** یک بار پاک‌سازیِ توکن‌های جامانده از نسخه‌های قبلی */
  private const val PURGED = "tohid-saved-logins-purged-v1"

  const val MAX = 4

  //  `ignoreUnknownKeys` اینجا فقط ادبِ کد نیست، لازم است: ردیف‌هایی که
  //  نسخهٔ قبلی نوشته یک کلیدِ `refresh` هم دارند و بدونِ این، خواندنشان
  //  استثنا می‌شد و کلِ فهرست خالی برمی‌گشت.
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  @Serializable
  data class Entry(
    val identifier: String,
    val shop: String = "",
    val at: Long = 0L,
  )

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun read(context: Context): List<Entry> {
    val raw = prefs(context).getString(KEY, null) ?: return emptyList()
    return runCatching { json.decodeFromString<List<Entry>>(raw) }.getOrDefault(emptyList())
  }

  private fun write(context: Context, list: List<Entry>) {
    prefs(context).edit().putString(KEY, json.encodeToString(list.take(MAX))).apply()
  }

  /** بعد از ورودِ موفق صدا زده می‌شود؛ تازه‌ترین حساب همیشه اول فهرست است */
  fun remember(context: Context, identifier: String, shop: String = "") {
    val id = identifier.trim()
    if (id.isEmpty()) return
    val list = read(context)
    val old = list.firstOrNull { it.identifier == id }
    //  مقدارِ قبلی را با رشتهٔ خالی خراب نکن: ورودِ سریع نامِ دکان را
    //  دوباره نمی‌فرستد
    val label = shop.ifBlank { old?.shop.orEmpty() }
    write(context, listOf(Entry(id, label, System.currentTimeMillis())) + list.filterNot { it.identifier == id })
  }

  /**
   *  پاک کردنِ توکن‌هایی که نسخه‌های قبلی اینجا نوشته‌اند.
   *
   *  خواندن و دوباره نوشتنِ فهرست کافی است: `Entry` دیگر کلیدِ `refresh`
   *  ندارد، پس آنچه نوشته می‌شود بدونِ آن است و متنِ قبلی — با توکنِ
   *  داخلش — رویش نوشته می‌شود.
   *
   *  یک بار اجرا می‌شود و بس؛ ولی حتی اگر چند بار صدا زده شود، بی‌ضرر
   *  است. هنگامِ خروج هم بی‌قید‌وشرط اجرا می‌شود، چون آنجا همان لحظه‌ای
   *  است که کاربر انتظار دارد چیزی باقی نماند.
   */
  fun purgeTokens(context: Context, force: Boolean = false) {
    val p = prefs(context)
    if (!force && p.getBoolean(PURGED, false)) return
    runCatching {
      val raw = p.getString(KEY, null)
      if (raw != null && raw.contains("\"refresh\"")) write(context, read(context))
    }
    p.edit().putBoolean(PURGED, true).apply()
  }

  /** «این حساب را یادت نباشد» */
  fun forget(context: Context, identifier: String) {
    write(context, read(context).filterNot { it.identifier == identifier })
  }
}
