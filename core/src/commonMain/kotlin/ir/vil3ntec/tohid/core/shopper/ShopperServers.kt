package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.server.ServerInfo
import ir.vil3ntec.tohid.core.server.ServerRouter

/*
 *  برنامهٔ خریدِ مشتری با چند نشانیِ یک سرور (پرچمِ خاموشِ `tohid.multiServer` ِ زمانِ ساخت؛ `docs/SERVERS-fa.md` §۳).
 *
 *  پرچمِ خاموش ⇒ این پرونده اصلاً ساخته نمی‌شود و فرستنده همان `HttpAndroid`/`HttpIos` ِ امروز با همان نشانی است.
 *  روشن ⇒ [ShopperRoutedHttp]: همان فرستنده، با نشانیِ [ServerRouter] و جابه‌جاییِ خودکار فقط میانِ نشانی‌های همان سرور.
 */

/** فرستندهٔ هر نشانی: همان فرستندهٔ سکو، فقط نشانیِ پایه بیرون از آن */
fun interface ShopperSender {
  fun send(base: String, request: ShopperRequest): ShopperResponse
}

object ShopperServers {

  /** پاسخِ سرورِ ناشناس بیش از این خوانده نمی‌شود */
  private const val MAX_INFO_CHARS = 65_536

  /**
   *  پرسیدنِ هویتِ یک نشانی — همان `ServerSwitchAndroid.probe` ِ برنامهٔ دکان: `config/server`، و سرورِ قدیمی
   *  (۴۰۴/۴۰۱) ⇒ `/api/health`. هر خطا یا پاسخِ دیگری ⇒ `null`.
   */
  fun ask(sender: ShopperSender): (String, Int) -> ServerInfo? = { url, timeoutMs ->
    fun get(path: String): ShopperResponse =
      sender.send(url, ShopperRequest("GET", path, mapOf("Accept" to "application/json"), timeoutMs = timeoutMs))
    runCatching {
      val r = get("/api/v1/config/server")
      when {
        r.body.length > MAX_INFO_CHARS -> null
        r.status in 200..299 -> ServerInfo.parse(r.body)
        r.status == 404 || r.status == 401 -> get("/api/health").takeIf { it.status in 200..299 && it.body.length <= MAX_INFO_CHARS }
          ?.let { ServerInfo.fromHealth(it.body) }
        else -> null
      }
    }.getOrNull()
  }

  /**
   *  سیم‌کشیِ یک‌جا برای سکو (اندروید/آیفون): مخزنِ همان برنامه، فرستندهٔ همان برنامه. فقط با پرچمِ روشن ساخته شود.
   *  [notSent]: ردهٔ خطای «اصلاً نرفت» ِ همان سکو ([mayResend]).
   */
  fun routed(
    buildUrl: String,
    kv: ShopperKeyValue,
    manual: () -> String?,
    sender: ShopperSender,
    now: () -> Long,
    notSent: (Throwable) -> Boolean = { false },
  ): ShopperRoutedHttp {
    val store = object : ServerRouter.Store {
      override fun get(key: String): String? = kv.get(key)
      override fun put(key: String, value: String?) = kv.put(key, value)
    }
    return ShopperRoutedHttp(ServerRouter(buildUrl, store, ask(sender), now), manual, sender, notSent)
  }

  /**
   *  آیا همین درخواست پس از [error] روی نشانیِ دیگرِ همان سرور دوباره فرستاده شود.
   *  • GET/HEAD — بی اثرِ دوم؛ • کلیدِ یکتایی (`Idempotency-Key`، مثلِ ثبتِ سفارش) — سرور تکرار را همان پاسخ می‌دهد؛
   *  • [notSent] — فرستنده مطمئن است درخواست اصلاً نرفت (وصل نشد، نامِ میزبان پیدا نشد).
   *  ⛔ هر چیزِ دیگر (کد فرستادن، لغو، نظر…) شاید رسیده باشد ⇒ همان خطای شبکه به کاربر، نه کارِ دوباره.
   */
  fun mayResend(request: ShopperRequest, error: Throwable, notSent: (Throwable) -> Boolean): Boolean {
    val m = request.method.uppercase()
    if (m == "GET" || m == "HEAD") return true
    if (request.headers.keys.any { it.equals("Idempotency-Key", ignoreCase = true) }) return true
    return notSent(error)
  }
}

/**
 *  فرستنده با چند نشانیِ یک سرور. [manual]: نشانیِ دستیِ «تنظیمات» (فقط آزمون) — اگر هست، همان و بی جابه‌جایی،
 *  درست مثلِ امروز.
 */
class ShopperRoutedHttp(
  private val router: ServerRouter,
  private val manual: () -> String?,
  private val sender: ShopperSender,
  private val notSent: (Throwable) -> Boolean = { false },
) : ShopperHttp {

  /** نشانیِ همین حالا (عکس، تکهٔ نقشه…) — ⛔ بی شبکه */
  fun base(): String = manual()?.takeIf { it.isNotBlank() } ?: router.current()

  /** سنجشِ پس‌زمینه اگر وقتش رسیده (شروع و بازگشتِ برنامه؛ از رشتهٔ پس‌زمینه) — با نشانیِ دستی هیچ */
  fun refreshIfDue() {
    if (manual().isNullOrBlank()) router.refreshIfDue()
  }

  override fun send(request: ShopperRequest): ShopperResponse {
    manual()?.takeIf { it.isNotBlank() }?.let { return sender.send(it, request) }
    return router.send({ e -> ShopperServers.mayResend(request, e, notSent) }) { base -> sender.send(base, request) }
  }
}
