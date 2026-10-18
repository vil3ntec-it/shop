package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.PermCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 *  `policy`ِ سرور (`GET /api/shop`) ⇐ ورودیِ `PermCore` — STF-01..03.
 *
 *  سرور لایه‌ها را می‌فرستد (`policy.layers`: نقش در این دکان، سپس همین
 *  کاربر) و برنامه با **همان هسته‌ای** که سرور و وب دارند تصمیم می‌گیرد،
 *  پس دکمه و سرور هیچ‌وقت دو صدا نمی‌شوند. نبودن یا خراب بودنِ policy
 *  یعنی «بی لایه» = همان رفتارِ امروزِ نقش.
 *
 *  این فقط برای **ظاهر** است؛ سدِ واقعی سمتِ سرور است.
 */
object PermPolicy {

  /** درختِ JSON ⇐ Map / List / String / Double / Boolean / null (همان شکلِ نسخهٔ JS) */
  fun tree(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, v) -> m[k] = tree(v) } }
    is JsonArray -> e.map { tree(it) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.booleanOrNull != null -> e.booleanOrNull
      else -> e.doubleOrNull ?: e.content
    }
  }

  fun layers(policyJson: String?): List<Any?> {
    if (policyJson.isNullOrBlank()) return emptyList()
    return runCatching {
      (tree(Json.parseToJsonElement(policyJson).jsonObject["layers"]) as? List<*>)?.toList()
    }.getOrNull() ?: emptyList()
  }

  fun allows(role: String, policyJson: String?, key: String): Boolean =
    PermCore.allows(role, layers(policyJson), key)
}
