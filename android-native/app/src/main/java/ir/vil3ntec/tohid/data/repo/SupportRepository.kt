package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.SupportSendDto
import ir.vil3ntec.tohid.core.model.SupportViewDto
import ir.vil3ntec.tohid.core.model.VipRedeemDto
import ir.vil3ntec.tohid.core.model.VisitDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  پشتیبانی، کد اشتراک و تپشِ بازدید.
 *
 *  ── چرا این سه با هم ───────────────────────────────────────────────
 *  هر سه یک ویژگی مشترک دارند که بقیهٔ مخزن‌ها ندارند: **به حساب بند
 *  نیستند**. کسی که هنوز ثبت‌نام نکرده باید بتواند بپرسد، دیده شود، و
 *  اگر کدی گرفته خرجش کند. شناسهٔ دستگاه کافی است.
 *
 *  ── چرا توکن با این حال فرستاده می‌شود ─────────────────────────────
 *  اگر حساب باشد، گفت‌وگو به همان حساب بسته می‌شود نه به این گوشی — پس
 *  کسی که گوشی‌اش را عوض می‌کند، همان گفت‌وگو را دارد. سرور خودش تصمیم
 *  می‌گیرد؛ اینجا فقط هر دو را می‌فرستیم.
 */
class SupportRepository(private val api: ApiClient, private val signedIn: () -> Boolean) {

  /**
   *  یک درخواست که با توکن می‌رود اگر حسابی باشد، وگرنه بی‌توکن.
   *
   *  بدونِ این، کاربرِ بی‌حساب روی `authorized` می‌افتاد و آنجا تلاشِ
   *  تازه‌سازیِ توکنِ ناموجود، درخواست را می‌شکست.
   */
  private suspend fun send(method: String, path: String, body: JsonObject?): JsonObject =
    when {
      body == null && signedIn() -> api.get(path)
      body == null -> api.getPublic(path)
      signedIn() -> if (method == "POST") api.post(path, body) else api.put(path, body)
      else -> api.postPublic(path, body)
    }

  /* ------------------------------ پشتیبانی ------------------------------ */

  /**
   *  گفت‌وگوی من، با پیام‌هایش.
   *
   *  @param after فقط پیام‌های بعد از این زمان — تا برنامه لازم نباشد
   *  هر چند ثانیه کلِ گفت‌وگو را از سرور بکشد.
   */
  suspend fun thread(deviceUid: String, after: Long = 0): ApiResult<SupportViewDto> = result {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Support.THREAD,
      mapOf("deviceUid" to deviceUid, "after" to after.takeIf { it > 0 }, "app" to "shop"),
    )
    ApiJson.decode<SupportViewDto>(send("GET", path, null))
  }

  suspend fun sendMessage(deviceUid: String, name: String, body: String): ApiResult<SupportSendDto> = result {
    ApiJson.decode<SupportSendDto>(
      send(
        "POST",
        ApiEndpoints.Support.MESSAGES,
        buildJsonObject {
          put("deviceUid", JsonPrimitive(deviceUid))
          put("app", JsonPrimitive("shop"))
          put("body", JsonPrimitive(body.trim()))
          if (name.isNotBlank()) put("name", JsonPrimitive(name.trim()))
        },
      )
    )
  }

  /** «خواندم» — نقطهٔ قرمز را پاک می‌کند */
  suspend fun markRead(deviceUid: String): ApiResult<Unit> = result {
    send(
      "POST",
      ApiEndpoints.Support.READ,
      buildJsonObject { put("deviceUid", JsonPrimitive(deviceUid)) },
    )
    Unit
  }

  /* ------------------------------ کد اشتراک ------------------------------ */

  /**
   *  خرج کردنِ کدِ شش‌رقمی.
   *
   *  این یکی **حساب لازم دارد** و از `api.post` می‌رود: اشتراک روی دکان
   *  می‌نشیند و دکان بدونِ حساب وجود ندارد. سرور هم همین را می‌گوید.
   */
  suspend fun redeemVip(code: String): ApiResult<VipRedeemDto> = result {
    ApiJson.decode<VipRedeemDto>(
      api.post(
        ApiEndpoints.Vip.REDEEM,
        buildJsonObject { put("code", JsonPrimitive(code.filter { it.isDigit() })) },
      )
    )
  }

  /* ------------------------------ تپشِ بازدید ------------------------------ */

  /**
   *  «من آمدم».
   *
   *  هیچ خطایی به بیرون نمی‌رود؛ صدا زننده‌اش هم نتیجه را لازم ندارد جز
   *  عددِ پیام‌های خوانده‌نشده.
   */
  suspend fun visit(
    deviceUid: String,
    platform: String,
    version: String,
    location: JsonObject? = null,
  ): ApiResult<VisitDto> = result {
    ApiJson.decode<VisitDto>(
      send(
        "POST",
        ApiEndpoints.VISIT,
        buildJsonObject {
          put("deviceUid", JsonPrimitive(deviceUid))
          put("app", JsonPrimitive("shop"))
          put("platform", JsonPrimitive(platform))
          put("version", JsonPrimitive(version))
          put("language", JsonPrimitive("fa"))
          if (location != null) put("location", location)
        },
      )
    )
  }
}
