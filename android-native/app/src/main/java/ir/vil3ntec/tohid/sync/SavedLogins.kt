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

  @Serializable
  data class Entry(val identifier: String, val shop: String = "", val at: Long = 0L)

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
    val list = read(context).filterNot { it.identifier == id }
    write(context, listOf(Entry(id, shop, System.currentTimeMillis())) + list)
  }

  /** «این حساب را یادت نباشد» */
  fun forget(context: Context, identifier: String) {
    write(context, read(context).filterNot { it.identifier == identifier })
  }
}
