package ir.vil3ntec.tohid.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 *  تبدیلِ پاسخِ خام به مدل — یک جا، با یک قاعده.
 *
 *  `ignoreUnknownKeys` یعنی سرور می‌تواند میدانِ تازه اضافه کند بدونِ
 *  اینکه نسخه‌های نصب‌شده بشکنند. `explicitNulls = false` یعنی `null` در
 *  پاسخ همان پیش‌فرضِ مدل می‌شود، نه استثنا.
 */
object ApiJson {

  val format: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
  }

  /**
   *  پاسخ را به مدل تبدیل می‌کند و اگر نشد، خطای «پاسخ نامعتبر» می‌دهد.
   *
   *  استثنای خامِ کتابخانه هیچ‌وقت بالا نمی‌رود: کاربر نباید متنِ انگلیسیِ
   *  یک کتابخانه را ببیند.
   */
  inline fun <reified T> decode(element: JsonElement): T =
    runCatching { format.decodeFromJsonElement<T>(element) }
      .getOrElse { throw ApiFailure.InvalidResponse() }

  /**
   *  مدلی که داخلِ یک کلیدِ پوششی نشسته: `{ "shop": { … } }`.
   *  اگر کلید نبود، `null` — نه خطا، چون «نداری» هم یک پاسخ است.
   */
  inline fun <reified T> decodeAt(body: JsonObject, key: String): T? {
    val child = body[key] ?: return null
    if (child is JsonPrimitive && child.content == "null") return null
    return decode<T>(child)
  }

  /** یک رشتهٔ ساده از پاسخ — بی‌آنکه روی شیء `jsonPrimitive` صدا زده شود */
  fun text(body: JsonObject, key: String): String =
    (body[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()

  fun long(body: JsonObject, key: String, fallback: Long = 0): Long =
    (body[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: fallback

  fun int(body: JsonObject, key: String, fallback: Int = 0): Int =
    (body[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback

  fun bool(body: JsonObject, key: String, fallback: Boolean = false): Boolean =
    (body[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback
}
