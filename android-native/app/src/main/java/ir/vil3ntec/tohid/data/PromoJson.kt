package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  قالبِ JSONِ تخفیف‌ها — همان شکلِ `GET /api/shop/promos` و `ruleOf`ِ هسته
 *  (`license/promo-core.js` = `server/src/lib/promo-core.js`).
 *  خواندن از درختِ JSON و `PromoCore.ruleOf` می‌گذرد تا هر نوعِ نادرست همان
 *  پیش‌فرضی را بگیرد که سرور و وب می‌گیرند.
 */
object PromoJson {

  /** پاسخِ سرور (یا نسخهٔ ذخیره‌شدهٔ همان) ⇒ عکسِ حال */
  fun snapshotOf(o: JsonObject, at: Long): PromoEngine.Snapshot {
    val perms = o["permissions"] as? JsonObject
    val raw = (o["rules"] as? JsonArray).orEmpty()
    val rules = raw.map { PromoCore.ruleOf(PermPolicy.tree(it)) }
    return PromoEngine.Snapshot(
      enabled = bool(o["enabled"]),
      rules = rules,
      manage = bool(perms?.get("manage")),
      belowCost = bool(perms?.get("belowCost")),
      at = at,
      revs = rules.zip(raw).mapNotNull { (r, e) -> (e as? JsonObject)?.let { rev(it) }?.takeIf { it > 0 }?.let { r.id to it } }.toMap(),
    )
  }

  private fun bool(e: JsonElement?): Boolean = (e as? JsonPrimitive)?.booleanOrNull == true

  /** عکسِ حال ⇒ همان شکلِ پاسخِ سرور، برای نگه‌داشتن روی دستگاه */
  fun toJson(s: PromoEngine.Snapshot): JsonObject = buildJsonObject {
    put("enabled", s.enabled)
    //  `rev` فقط برای همین نسخهٔ دستگاه — `PUT` ِ قاعده آن را نمی‌فرستد
    put("rules", JsonArray(s.rules.map { r -> s.revs[r.id]?.let { JsonObject(toJson(r) + ("rev" to JsonPrimitive(it))) } ?: toJson(r) }))
    put("permissions", buildJsonObject { put("manage", s.manage); put("belowCost", s.belowCost) })
  }

  private fun strs(xs: List<String>) = JsonArray(xs.map { JsonPrimitive(it) })

  /** قاعده ⇒ JSONی که `PUT /api/shop/promos/rules/:id` می‌پذیرد */
  fun toJson(r: PromoCore.Rule): JsonObject = buildJsonObject {
    put("id", r.id)
    put("name", r.name)
    put("kind", r.kind)
    put("active", r.active)
    put("stack", r.stack)
    put("priority", r.priority)
    put("code", r.code)
    put("maxUses", r.maxUses)
    put("uses", r.uses)
    put("allowBelowCost", r.allowBelowCost)
    put("scope", buildJsonObject {
      put("products", strs(r.scope.products)); put("categories", strs(r.scope.categories))
      put("suppliers", strs(r.scope.suppliers)); put("brands", strs(r.scope.brands))
      put("exProducts", strs(r.scope.exProducts)); put("exCategories", strs(r.scope.exCategories))
      put("exSuppliers", strs(r.scope.exSuppliers)); put("exBrands", strs(r.scope.exBrands))
    })
    put("groups", strs(r.groups))
    put("channels", strs(r.channels))
    put("window", buildJsonObject {
      put("from", r.window.from); put("to", r.window.to)
      put("days", buildJsonArray { r.window.days.forEach { add(JsonPrimitive(it)) } })
      put("start", r.window.start); put("end", r.window.end)
      put("except", strs(r.window.except))
    })
    put("bp", r.bp)
    put("amountMinor", r.amountMinor)
    put("prices", JsonArray(r.prices.map { p -> buildJsonObject { put("productId", p.productId); put("unitMinor", p.unitMinor) } }))
    put("tiers", JsonArray(r.tiers.map { t -> buildJsonObject { put("min", t.min); put("bp", t.bp); put("amountMinor", t.amountMinor) } }))
    put("buy", r.buy)
    put("get", r.get)
    put("qty", r.qty)
    put("priceMinor", r.priceMinor)
  }

  /** شمارهٔ نسخهٔ قاعده که سرور برمی‌گرداند (`rev`) — نمایش، و `SalePromo.rev` روی قلمِ فاکتور */
  fun rev(o: JsonObject): Long = (o["rev"] as? JsonPrimitive)?.longOrNull ?: 0
}
