package ir.vil3ntec.tohid.core.account

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 *  O-09 / SUB-09 — سقفِ دستگاهِ اشتراکِ دکان، سمتِ برنامه.
 *
 *  قراردادِ سرور (`server/src/routes/license.js`، پرچمِ `shop.device_cap`):
 *    • صاحبِ دکان هرگز از دستگاهِ ثبت‌شدهٔ خودش بیرون نمی‌ماند: بیرون از سقف هم `license` می‌گیرد، با
 *      `warning = {code: "device_over_cap", devices, maxDevices, manage: "devices", message}`؛
 *    • کارمندِ بیرون از سقف ⇒ `license: null`، `reason: "device_limit"` (قفلِ `serverDenied`).
 *
 *  این‌جا فقط دو تصمیم است، بی شبکه و بی رابط: هشدارِ صاحب از پاسخ، و کارِ بعدیِ صفحهٔ قفل — که برای
 *  `device_limit` «دستگاه‌ها» است، نه «قیمت‌ها» (دکانی که اشتراک دارد را به خریدِ اشتراک فرستادن بن‌بست است).
 */
object DeviceCap {

  /** دکان `devices` دستگاه دارد و اشتراک برای `maxDevices` است (همیشه `devices > maxDevices > 0`) */
  data class Over(val devices: Int, val maxDevices: Int)

  /** کارِ دکمهٔ صفحهٔ قفل */
  enum class Next { SIGN_UP, PLANS, DEVICES }

  const val WARNING_CODE = "device_over_cap"
  const val DENIED_DEVICE_LIMIT = "device_limit"

  private fun valid(devices: Int?, max: Int?): Over? =
    if (devices != null && max != null && max > 0 && devices > max) Over(devices, max) else null

  /** هشدارِ صاحب از پاسخِ `POST /api/license/sync` — فقط کنارِ مجوزِ واقعی؛ شکلِ خراب ⇒ هیچ */
  fun overOf(body: JsonObject): Over? {
    val license = (body["license"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (license.isNullOrBlank()) return null
    val w = body["warning"] as? JsonObject ?: return null
    if ((w["code"] as? JsonPrimitive)?.content != WARNING_CODE) return null
    val devices = (w["devices"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    val max = (w["maxDevices"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    return valid(devices, max)
  }

  /** شکلِ نگه‌داری (`SyncStore`): «دستگاه/سقف» */
  fun encode(o: Over?): String? = o?.let { "${it.devices}/${it.maxDevices}" }

  fun decode(s: String?): Over? {
    val parts = s?.split('/') ?: return null
    if (parts.size != 2) return null
    return valid(parts[0].toIntOrNull(), parts[1].toIntOrNull())
  }

  /** صفحهٔ قفلِ بخشِ پولی: بی حساب ⇒ ساختِ حساب؛ دستگاهِ بیرون از سقف ⇒ «دستگاه‌ها»؛ بقیه ⇒ «قیمت‌ها» */
  fun lockedNext(signedIn: Boolean, serverDenied: String?): Next = when {
    !signedIn -> Next.SIGN_UP
    serverDenied == DENIED_DEVICE_LIMIT -> Next.DEVICES
    else -> Next.PLANS
  }
}
