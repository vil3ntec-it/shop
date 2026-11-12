package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.promo.PromoHeld
import ir.vil3ntec.tohid.data.PermPolicy
import ir.vil3ntec.tohid.data.PromoJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

  /**
   *  PRM-02 — کدهای سقف‌دارِ همین فاکتور پیش از ثبت (`POST /redeem`). همان `saleId` دوباره
   *  مصرفِ دوم نمی‌سازد؛ همه یا هیچ. خطا ⇒ `ApiFailure` با `status` و `code` ِ سرور (`PromoRedeem.decide`).
   */
  suspend fun redeem(saleId: String, codes: List<String>, debtorId: String?): ApiResult<Unit> = result {
    api.post("$ROOT/redeem", redeemBody(saleId, codes, debtorId))
    Unit
  }

  /**
   *  PRM-02 — آزاد کردنِ همهٔ کدهای سقف‌دارِ یک فاکتورِ لغوشده (`POST /redeem/release`، بی `codes` ⇒ همه).
   *  همان فاکتور دوباره ⇒ `released: 0`، بی اثرِ دوم. خطا ⇒ `ApiFailure` (`PromoRedeem.releaseDecision`).
   */
  suspend fun release(saleId: String): ApiResult<Unit> = result {
    api.post("$ROOT/redeem/release", buildJsonObject { put("saleId", saleId) })
    Unit
  }

  /**
   *  PRM-02 — کدهای نگه‌داشته برای آزادِ دستیِ صاحب/مدیر (`GET /redemptions`؛ فقط دارندهٔ `sales.cancel`).
   *  خطا ⇒ `ApiFailure` با `status` و `code` ِ سرور (`PromoHeld.access`).
   */
  suspend fun held(): ApiResult<PromoHeld.Page> = result { PromoHeld.parse(api.get(PromoHeld.PATH)) }

  /** PRM-02 — آزادِ دستیِ یک فاکتورِ لغوشده: همان `POST /redeem/release`، با شمارِ آزادشده (`PromoHeld.releaseOutcome`) */
  suspend fun releaseHeld(saleId: String): ApiResult<Int> = result {
    PromoHeld.releasedOf(api.post(PromoHeld.RELEASE_PATH, PromoHeld.releaseBody(saleId)))
  }

  companion object {
    private const val ROOT = "/shop/promos"
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     *  بدنهٔ `POST /redeem`. PRM-11: کلیدِ `debtorId` همیشه هست (بی مشتری ⇒ `null` ِ صریح) — سرور کدِ قاعدهٔ
     *  گروه‌دار را فقط با گروهِ قرض‌دارِ دفترِ خودش می‌گیرد؛ نبودنِ کلید یعنی برنامهٔ پیشین.
     */
    fun redeemBody(saleId: String, codes: List<String>, debtorId: String?): JsonObject = buildJsonObject {
      put("saleId", saleId)
      put("codes", JsonArray(codes.map { JsonPrimitive(it) }))
      put("debtorId", debtorId?.let { JsonPrimitive(it) } ?: JsonNull)
    }
  }
}
