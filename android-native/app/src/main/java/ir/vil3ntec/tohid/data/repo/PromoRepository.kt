package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.PermPolicy
import ir.vil3ntec.tohid.data.PromoJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 *  تخفیف‌های دکان — `/api/shop/promos` (`server/src/routes/shop-promos.js`).
 *  خواندن برای هر عضو؛ نوشتن با `settings.write`؛ «زیرِ بهای خرید» فقط صاحب.
 */
class PromoRepository(private val api: ApiClient) {

  /** پاسخِ خام — `PromoJson.snapshotOf` می‌خواندش و همان روی دستگاه نگه داشته می‌شود */
  suspend fun state(): ApiResult<JsonObject> = result { api.get(ROOT) }

  suspend fun setEnabled(on: Boolean): ApiResult<Boolean> = result {
    api.put("$ROOT/settings", buildJsonObject { put("enabled", on) })
    on
  }

  /** قاعدهٔ ذخیره‌شده، همان‌طور که سرور نرمالش کرده (کدِ بزرگ، `uses`ِ سرور)، با `rev`ِ تازه‌اش */
  suspend fun save(rule: PromoCore.Rule): ApiResult<Pair<PromoCore.Rule, Long>> = result {
    val out = api.put("$ROOT/rules/${enc(rule.id)}", buildJsonObject { put("rule", PromoJson.toJson(rule)) })
    PromoCore.ruleOf(PermPolicy.tree(out["rule"])) to ((out["rule"] as? JsonObject)?.let(PromoJson::rev) ?: 0)
  }

  suspend fun delete(id: String): ApiResult<Unit> = result { api.delete("$ROOT/rules/${enc(id)}"); Unit }

  private companion object {
    const val ROOT = "/shop/promos"
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
  }
}
