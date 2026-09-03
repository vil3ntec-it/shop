package ir.vil3ntec.tohid.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 *  حساب‌هایی که قبلاً از این گوشی وارد شده‌اند.
 *
 *  فروشنده هر روز صبح دکان را باز می‌کند؛ نباید هر بار شمارهٔ کاملش را
 *  تایپ کند. یک لمس روی نامش، کادر را پر می‌کند و فقط رمز می‌ماند.
 *
 *  **رمز اینجا ذخیره نمی‌شود** — نه رمز، نه توکن. فقط شناسه (شماره یا
 *  ایمیل) و نام دکان، همان دو چیزی که نسخهٔ وب هم نگه می‌دارد. اگر گوشی
 *  دست کسی بیفتد، از این فهرست چیزی جز نام به دست نمی‌آورد؛ توکنِ حساب
 *  جای دیگری است و با «خروج از حساب» پاک می‌شود.
 *
 *  چهار تا آخر نگه داشته می‌شود، مثل وب — بیشتر از این، فهرست خودش
 *  می‌شود یک صفحهٔ دیگر برای گشتن.
 */
object SavedLogins {

  private const val PREFS = "tohid"

  // همان کلیدِ نسخهٔ وب، تا پشتیبان و همگام‌سازی یک زبان داشته باشند
  private const val KEY = "tohid-saved-logins-v1"

  const val MAX = 4

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /**
   *  @param refresh توکنِ تازه‌سازیِ همان حساب، اگر داشته باشیم.
   *
   *  ── چرا اینجا می‌نشیند ──────────────────────────────────────────
   *  گزارش شد: «از حساب خارج می‌شوم، اسمِ حساب را نشان می‌دهد، رویش
   *  می‌زنم ولی مرا داخل نمی‌برد و رمز می‌خواهد». درست بود: ردیفِ
   *  «ورودِ سریع» فقط کادرِ ایمیل را پر می‌کرد. بی توکن هم راهی نبود —
   *  ورود بدونِ هیچ اعتبارنامه‌ای معنا ندارد.
   *
   *  پس همان توکن نگه داشته می‌شود. `SyncStore.signOut` توکن را روی
   *  سرور باطل نمی‌کند (فقط از گوشی پاکش می‌کند)، پس همان توکن هنوز
   *  معتبر است و یک زدن، نشست را برمی‌گرداند.
   *
   *  کجا می‌نشیند: همان `SharedPreferences`ِ خصوصیِ برنامه که توکنِ
   *  فعال هم در آن است — پس دستهٔ تازه‌ای از خطر باز نمی‌شود. و
   *  «فراموش کن» روی ردیف، پاکش می‌کند.
   */
  @Serializable
  data class Entry(
    val identifier: String,
    val shop: String = "",
    val at: Long = 0L,
    val refresh: String = "",
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
  fun remember(context: Context, identifier: String, shop: String = "", refresh: String = "") {
    val id = identifier.trim()
    if (id.isEmpty()) return
    val list = read(context).filterNot { it.identifier == id }
    //  توکنِ قبلی را با رشتهٔ خالی خراب نکن: ورودِ تازه گاهی توکن ندارد
    val old = read(context).firstOrNull { it.identifier == id }
    //  مقدارِ قبلی را با رشتهٔ خالی خراب نکن: ورودِ سریع نامِ دکان را
    //  دوباره نمی‌فرستد و توکن هم همیشه همراهش نیست
    val keep = refresh.ifBlank { old?.refresh.orEmpty() }
    val label = shop.ifBlank { old?.shop.orEmpty() }
    write(context, listOf(Entry(id, label, System.currentTimeMillis(), keep)) + list)
  }

  /** توکنِ تازه‌سازیِ همان حساب را کنارش می‌گذارد — هنگام خروج */
  fun keepToken(context: Context, identifier: String, refresh: String) {
    val id = identifier.trim()
    if (id.isEmpty() || refresh.isBlank()) return
    val list = read(context)
    val old = list.firstOrNull { it.identifier == id }
    val entry = Entry(id, old?.shop.orEmpty(), old?.at ?: System.currentTimeMillis(), refresh)
    write(context, listOf(entry) + list.filterNot { it.identifier == id })
  }

  /** «این حساب را یادت نباشد» */
  fun forget(context: Context, identifier: String) {
    write(context, read(context).filterNot { it.identifier == identifier })
  }
}
