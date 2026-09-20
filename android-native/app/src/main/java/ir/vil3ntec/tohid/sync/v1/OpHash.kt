package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 *  اثرِ انگشتِ یک op — **باید مو‌به‌مو با سرور یکی باشد**.
 *
 *  سرور در `server/src/lib/sync-v1.js → hashOf` همین را می‌سازد:
 *
 *      sha256("<table>|<row_id>|<type>|<canonical(fields)>")
 *
 *  و اگر برنامه `hash` بفرستد و نخواند، op را `hash_mismatch` رد می‌کند.
 *  پس این دو تابع یک قرارداد مشترک‌اند، نه یک جزئیاتِ داخلی.
 *
 *  ⚠️ `canonical` یعنی JSON با کلیدهای **مرتب** و بی هیچ فاصله. هر
 *  تفاوتی — حتی یک فاصله — اثرِ انگشت را عوض می‌کند.
 *  ⚠️ رشته‌ها باید مثلِ `JSON.stringify` فرار داده شوند؛
 *  `JsonPrimitive.toString()` در kotlinx همین کار را می‌کند.
 */
object OpHash {

  fun canonical(value: JsonElement): String = when (value) {
    is JsonNull -> "null"
    is JsonPrimitive -> value.toString()
    is JsonArray -> value.joinToString(",", "[", "]") { canonical(it) }
    is JsonObject -> value.keys.sorted().joinToString(",", "{", "}") { key ->
      JsonPrimitive(key).toString() + ":" + canonical(value.getValue(key))
    }
  }

  fun sha256Hex(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
      val v = b.toInt() and 0xFF
      out.append("0123456789abcdef"[v ushr 4])
      out.append("0123456789abcdef"[v and 0x0F])
    }
    return out.toString()
  }

  fun of(table: String, rowId: String, type: String, fields: JsonObject?): String =
    sha256Hex("$table|$rowId|$type|" + canonical(fields ?: JsonObject(emptyMap())))
}
