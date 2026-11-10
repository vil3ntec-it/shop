package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  PIN ِ شخصیِ صندوق در سرور — `/shop/till-pin` (`server/src/routes/shop-till-pin.js`، STF-04، S46).
 *  ⛔ پیش‌فرض خاموش: بی `shop.till_pin` فقط `/config` جواب می‌دهد و `enabled` نه است ⇒ برنامه هیچ چیزِ دیگری نمی‌فرستد.
 *  ⛔ سرور مرجع است: PIN را خودش می‌سنجد (PBKDF2، ۵ خطا ⇒ قفلِ زمان‌دار) و نوبتِ «چه کسی پشتِ صندوق است» را روی
 *     دستگاهِ همین توکن باز می‌کند. PIN فقط در بدنهٔ همین درخواست‌ها می‌رود؛ هیچ‌جا نوشته یا ثبت نمی‌شود.
 *  ⛔ گذاشتنِ PIN ِ دیگری تأییدِ دوبارهٔ ورود می‌خواهد (`reauth_required`؛ پنجره‌اش را `ApiClient` خودش باز می‌کند).
 */
class TillPinRepository(private val api: ApiClient) {

  suspend fun enabled(): Boolean =
    (result { api.get("$ROOT/config") }.valueOrNull()?.get("enabled") as? JsonPrimitive)?.booleanOrNull == true

  /** PIN ِ عضوِ [memberId] (شناسهٔ عضویت، `MemberDto.id`) */
  suspend fun enroll(memberId: String, pin: String): ApiResult<Unit> = result {
    api.put("$ROOT/members/$memberId", buildJsonObject { put("pin", pin) })
    Unit
  }

  /** سنجشِ PIN ِ کاربرِ [userId] روی همین دستگاه ⇒ شناسهٔ نوبتِ سرور، یا `null` (خودِ حسابِ دستگاه) */
  suspend fun verify(userId: String, pin: String): ApiResult<String?> = result {
    val session = api.post("$ROOT/verify", buildJsonObject { put("userId", userId); put("pin", pin) })["session"] as? JsonObject
    (session?.get("id") as? JsonPrimitive)?.content
  }

  /** قفلِ صندوق (Ctrl+L): نوبتِ بازِ همین دستگاه بسته می‌شود */
  suspend fun endCurrent(): ApiResult<Unit> = result {
    api.post("$ROOT/current/end", buildJsonObject { put("reason", "lock") })
    Unit
  }

  private companion object {
    const val ROOT = "/shop/till-pin"
  }
}
