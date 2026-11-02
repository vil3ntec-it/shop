package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.Loyalty
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  PRM-13 — کدام قاعدهٔ امتیاز در کار است: «قاعدهٔ یکسانِ دکان» (سرور، `GET /shop/loyalty`، پشتِ پرچمِ خاموشِ
 *  `shop.loyalty_rule`) یا قاعدهٔ همین دستگاه (`LoyaltyPref`). خالص: نه فایل، نه شبکه، نه Android.
 *
 *  ── برتری ([resolve]) ─────────────────────────────────────────────
 *   • دکان قاعدهٔ درستِ ذخیره‌شده دارد ⇒ همان (روشن/خاموش و قاعده، با انقضا) روی **همهٔ** دستگاه‌ها؛ تنظیمِ دستگاه کنار.
 *   • پرچمِ خاموش، سرورِ پیشین، هنوز ذخیره‌نشده یا هرگز گرفته‌نشده ⇒ قاعدهٔ همین دستگاه، همان امروز.
 *   • بی‌اینترنت ⇒ آخرین پاسخِ همین دکان که روی همین دستگاه مانده ([Shop]، `LoyaltyShop`)؛ هرگز گرفته نشده ⇒ دستگاه.
 *     فقط پاسخِ صریحِ `{enabled:false}` نسخهٔ نگه‌داشته را پاک می‌کند (پرچم خاموش شد) — خطای شبکه نه.
 *
 *  موتورهای خالص (برگشت و لغوِ `LoyaltyPoints`) به سکو دسترسی ندارند؛ مثلِ `DebtMinorFlag` از [current] می‌خوانند که
 *  نخستین `ShopStore` وصلش می‌کند (`LoyaltyShopAndroid.kt`). وصل‌نشده (آزمون، پیش از دفتر) ⇒ خاموش و بی انقضا.
 */
object LoyaltyRules {

  /** پاسخِ سرور برای همین دکان: [rule] `null` ⇒ پرچم روشن ولی هنوز ذخیره نشده */
  data class Shop(val on: Boolean, val rule: Loyalty.Rule?, val version: Long, val manage: Boolean)

  /** آنچه صندوق و دفتر به کار می‌برند؛ [shopWide] ⇒ از قاعدهٔ یکسانِ دکان */
  data class Config(val on: Boolean, val rule: Loyalty.Rule, val shopWide: Boolean)

  val OFF = Config(false, Loyalty.Rule(), false)

  fun resolve(localOn: Boolean, localRule: Loyalty.Rule, shop: Shop?): Config {
    val r = shop?.rule
    if (shop != null && r != null && r.valid) return Config(shop.on, r, true)
    return Config(localOn, localRule.takeIf { it.valid } ?: Loyalty.Rule(), false)
  }

  private fun num(o: JsonObject, k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  /** قاعده از JSON ِ سرور؛ هر عددِ نبوده/نادرست ⇒ `null` (نه قاعدهٔ نیمه‌کاره) */
  fun ruleOf(o: JsonObject?): Loyalty.Rule? {
    if (o == null) return null
    val every = num(o, "everyAfn") ?: return null
    val point = num(o, "pointAfn") ?: return null
    val share = num(o, "maxSharePct")?.takeIf { it in 0L..1000L } ?: return null
    val silver = num(o, "silverAfn") ?: return null
    val gold = num(o, "goldAfn") ?: return null
    val months = (num(o, "expiryMonths") ?: 0L).takeIf { it in 0L..1000L } ?: return null
    return Loyalty.Rule(every, point, share.toInt(), silver, gold, months.toInt()).takeIf { it.valid }
  }

  /** پاسخِ `GET /shop/loyalty`: `enabled:false` (پرچمِ خاموش) ⇒ `null` */
  fun parse(body: JsonObject): Shop? {
    if ((body["enabled"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val manage = ((body["permissions"] as? JsonObject)?.get("manage") as? JsonPrimitive)?.booleanOrNull == true
    return Shop(
      on = (body["on"] as? JsonPrimitive)?.booleanOrNull == true,
      rule = ruleOf(body["rule"] as? JsonObject),
      version = num(body, "version")?.coerceAtLeast(0) ?: 0,
      manage = manage,
    )
  }

  /** بدنهٔ `PUT /shop/loyalty` */
  fun body(on: Boolean, rule: Loyalty.Rule, expectedVersion: Long?): JsonObject = buildJsonObject {
    put("on", on)
    put("rule", buildJsonObject {
      put("everyAfn", rule.everyAfn); put("pointAfn", rule.pointAfn); put("maxSharePct", rule.maxSharePct)
      put("silverAfn", rule.silverAfn); put("goldAfn", rule.goldAfn); put("expiryMonths", rule.expiryMonths)
    })
    if (expectedVersion != null) put("expectedVersion", expectedVersion) else put("expectedVersion", JsonNull)
  }

  /** همان پاسخ برای نگه‌داشتن روی دستگاه (فقط آنچه [parse] می‌خواند) */
  fun serialize(s: Shop): JsonObject = buildJsonObject {
    put("enabled", true)
    put("on", s.on)
    put("version", s.version)
    put("permissions", buildJsonObject { put("manage", s.manage) })
    val r = s.rule
    if (r == null) put("rule", JsonNull) else put("rule", (body(s.on, r, null)["rule"] as JsonObject))
  }

  // ── خواننده برای موتورهای خالص ─────────────────────────────────────
  @Volatile private var reader: (() -> Config)? = null

  /** فقط آزمون: جای قاعدهٔ وصل‌شده را می‌گیرد */
  @Volatile var override: Config? = null

  /** فقط یک بار (`LoyaltyRules.bind` ِ `LoyaltyShopAndroid.kt`) */
  fun bindOnce(r: () -> Config) { if (reader == null) reader = r }

  fun current(): Config = override ?: runCatching { reader?.invoke() }.getOrNull() ?: OFF
}
