package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  CRM-09/CHK-08 — «عکسِ دروازه» و «یادداشتِ صوتی» ِ نشانیِ سفارشِ ویترین برای برنامهٔ دکان: نمای همان پاسخِ
 *  `GET /shop/orders/:id/address-media` ِ سرور (`routes/order-address-media.js`) و نشانیِ بایت‌ها.
 *
 *  ⛔ پرچمِ خاموش (`{enabled:false}`)، پاسخِ کهنه/ناجور یا نبودنِ هر دو ⇒ [visible] = false (هیچ بخشی). عکس فقط با نوعِ
 *  `image/…` و صدا فقط با `audio/…` پذیرفته است (جابه‌جا ⇒ «نیست»). نشانی فقط برای شناسهٔ درستِ سفارش ([OrderEvidence.validOrderId]).
 */
object OrderAddressMedia {

  enum class Kind(val wire: String) { GATE_PHOTO("gate-photo"), VOICE_NOTE("voice-note") }

  data class Meta(val mime: String, val size: Long, val createdAt: Long)

  data class View(val enabled: Boolean, val gatePhoto: Meta? = null, val voiceNote: Meta? = null)

  private fun meta(o: Any?, prefix: String): Meta? {
    val x = o as? JsonObject ?: return null
    val mime = (x["mime"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
    if (!mime.startsWith(prefix)) return null
    val size = (x["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null
    return Meta(mime, size, (x["createdAt"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L)
  }

  fun parse(o: JsonObject?): View {
    val enabled = (o?.get("enabled") as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
    if (o == null || !enabled) return View(false)
    return View(true, meta(o["gatePhoto"], "image/"), meta(o["voiceNote"], "audio/"))
  }

  fun visible(v: View): Boolean = v.enabled && (v.gatePhoto != null || v.voiceNote != null)

  fun path(orderId: String): String? =
    if (OrderEvidence.validOrderId(orderId)) "/shop/orders/$orderId/address-media" else null

  fun bytesPath(orderId: String, kind: Kind): String? = path(orderId)?.let { "$it/${kind.wire}" }
}
