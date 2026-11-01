package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.core.staff.TimeClock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 *  ورود و خروجِ کارمند و نرخ‌های پورسانت — `/shop/staff-time` (`server/src/routes/staff-time.js`، STF-07، STF-08).
 *  ⛔ پیش‌فرض خاموش: بی `shop.staff_time` فقط `/config` جواب می‌دهد و `enabled` نه است.
 *  ⛔ سرور مرجع است: همان قاعدهٔ `TimeClock.check` را آن‌جا هم می‌سنجد؛ شناسهٔ زدن ساختهٔ دستگاه است
 *     و فرستادنِ دوباره‌اش (صفِ آفلاین) ردیفِ دوم نمی‌سازد.
 */
class StaffTimeRepository(private val api: ApiClient) {

  data class Config(val enabled: Boolean, val memberId: String, val role: String)

  /** زدن با نامِ عضو (برای گزارشِ مدیر) */
  data class Named(val punch: TimeClock.Punch, val name: String)

  data class PlanDoc(val plan: Commission.Plan, val version: Int)

  suspend fun config(): ApiResult<Config> = result {
    val o = api.get("$ROOT/config")
    Config(
      enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull == true,
      memberId = (o["memberId"] as? JsonPrimitive)?.content.orEmpty(),
      role = (o["role"] as? JsonPrimitive)?.content.orEmpty(),
    )
  }

  suspend fun punch(p: TimeClock.Punch, forMember: String? = null): ApiResult<Unit> = result {
    api.post("$ROOT/punches", buildJsonObject {
      put("id", p.id); put("kind", p.kind.code); put("at", p.atMs); put("deviceUid", p.deviceId)
      if (forMember != null) put("memberId", forMember)
    })
    Unit
  }

  suspend fun punches(fromMs: Long, toMs: Long): ApiResult<List<Named>> = result {
    (api.get("$ROOT/punches?from=$fromMs&to=$toMs")["punches"] as? JsonArray).orEmpty().mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
      val kind = TimeClock.Kind.entries.firstOrNull { it.code == s("kind") } ?: return@mapNotNull null
      val at = (o["at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
      Named(TimeClock.Punch(s("id"), s("memberId"), kind, at, s("deviceUid")), s("name"))
    }
  }

  suspend fun plan(): ApiResult<PlanDoc> = result {
    val o = api.get("$ROOT/commission-plan")
    fun rates(k: String): Map<String, Int> = (o[k] as? JsonObject).orEmpty()
      .mapNotNull { (key, v) -> (v as? JsonPrimitive)?.intOrNull?.let { key to it } }.toMap()
    PlanDoc(
      Commission.Plan((o["defaultBp"] as? JsonPrimitive)?.intOrNull ?: 0, rates("sellerBp"), rates("categoryBp")),
      (o["version"] as? JsonPrimitive)?.intOrNull ?: 0,
    )
  }

  suspend fun savePlan(plan: Commission.Plan, version: Int): ApiResult<PlanDoc> = result {
    api.put("$ROOT/commission-plan", buildJsonObject {
      put("version", version); put("defaultBp", plan.defaultBp)
      putJsonObject("sellerBp") { plan.sellerBp.forEach { (k, v) -> put(k, v) } }
      putJsonObject("categoryBp") { plan.categoryBp.forEach { (k, v) -> put(k, v) } }
    })
    PlanDoc(plan, version + 1)
  }

  private companion object {
    const val ROOT = "/shop/staff-time"
  }
}
