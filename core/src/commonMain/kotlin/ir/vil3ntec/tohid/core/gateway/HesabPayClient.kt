package ir.vil3ntec.tohid.core.gateway

/*
 *  کارگزارِ قصدِ پرداختِ حساب‌پی — کاتلینِ خالص؛ HTTP را پلتفرم می‌دهد ([GatewayTransport]).
 *
 *  درها (پیشوندِ برنامهٔ دکان، `/api/v1` یا `/api`، را لایهٔ HTTP ِ برنامه می‌گذارد):
 *    GET  /shop/payments/config                 ⇐ {hesabpay:{enabled}}
 *    POST /shop/payments/intents                ⇐ ساختن (با کلیدِ یکتایی)
 *    GET  /shop/payments/intents/:id?wait=N     ⇐ وضعیت، با انتظارِ بلند
 *    POST /shop/payments/intents/:id/cancel     ⇐ لغو
 *
 *  ⛔ هر خطا، بی‌نتی یا نبودنِ در (۴۰۴، سرورِ کهنه) ⇐ «حساب‌پی نیست»؛ روش پنهان می‌ماند.
 */

/**
 *  پاسخِ HTTP از پلتفرم. `status = 0` یعنی نرسید (بی‌نت، مهلت، سرور در دسترس نیست).
 *  `body` درختِ JSON ِ پاسخِ ۲xx است؛ برای خطا `code` و `message` ِ سرور.
 */
data class GatewayReply(
  val status: Int,
  val body: Map<String, Any?>? = null,
  val code: String = "",
  val message: String = "",
)

/** لایهٔ HTTP ِ پلتفرم — با توکنِ کاربرِ دکان؛ خطا را به [GatewayReply] برمی‌گرداند، پرتاب نمی‌کند */
interface GatewayTransport {
  suspend fun get(path: String): GatewayReply

  /** `idempotencyKey` ⇐ سرآیندِ `Idempotency-Key` (همان مقدارِ بدنه) */
  suspend fun post(path: String, body: Map<String, Any?>, idempotencyKey: String? = null): GatewayReply
}

/** نتیجهٔ یک درخواست */
sealed interface GatewayCall<out T> {
  data class Ok<T>(val value: T) : GatewayCall<T>

  /** حساب‌پی برای این دکان خاموش است، یا سرور این در را ندارد */
  data object Disabled : GatewayCall<Nothing>

  /** نرسید یا سرور گذرا خطا داد (۴۲۹، ۵xx جز ۵۰۲) — تکرار ممکن است جواب بدهد */
  data class Offline(val message: String) : GatewayCall<Nothing>

  /** سرور رد کرد — کدِ ماشین‌خوان (`already_paid`، `intent_active`، `provider_unavailable`…) */
  data class Refused(val status: Int, val code: String, val message: String) : GatewayCall<Nothing>
}

class HesabPayClient(private val transport: GatewayTransport) {

  /** حساب‌پی برای این دکان روشن است؟ هر خطا ⇐ نه */
  suspend fun enabled(): Boolean {
    val r = transport.get("$ROOT/config")
    if (r.status !in 200..299) return false
    return ((r.body?.get("hesabpay") as? Map<*, *>)?.get("enabled")) == true
  }

  /**
   *  ساختنِ قصد برای فروشِ صندوق. `refId` همان شناسهٔ فروشی است که پس از پرداخت ثبت می‌شود؛
   *  `idempotencyKey` یک بار برای هر تلاشِ پرداخت ساخته و نگه داشته می‌شود (تکرار ⇐ همان قصد).
   */
  suspend fun create(refId: String, amountMinor: Long, idempotencyKey: String, description: String? = null): GatewayCall<PaymentIntent> {
    if (!Wire.isToken(refId)) return GatewayCall.Refused(400, "bad_ref", "")
    if (!Wire.isToken(idempotencyKey, min = 8)) return GatewayCall.Refused(400, "bad_idempotency_key", "")
    if (amountMinor <= 0 || amountMinor > Wire.MAX_SAFE) return GatewayCall.Refused(400, "bad_amount", "")
    val body = LinkedHashMap<String, Any?>()
    body["kind"] = "pos_sale"
    body["refId"] = refId
    body["amount"] = amountMinor
    body["currency"] = "AFN"
    body["idempotencyKey"] = idempotencyKey
    description?.trim()?.takeIf { it.isNotEmpty() }?.let { body["description"] = it.take(120) }
    return intentOf(transport.post("$ROOT/intents", body, idempotencyKey), create = true)
  }

  /** وضعیت؛ `waitSeconds > 0` ⇐ سرور تا تغییرِ وضعیت یا پایانِ مهلت نگه می‌دارد */
  suspend fun fetch(id: String, waitSeconds: Int = 0): GatewayCall<PaymentIntent> {
    if (!Wire.isToken(id)) return GatewayCall.Refused(404, "payment_not_found", "")
    val wait = waitSeconds.coerceIn(0, MAX_WAIT)
    return intentOf(transport.get("$ROOT/intents/$id" + if (wait > 0) "?wait=$wait" else ""), create = false)
  }

  /** لغو — فقط از created/pending؛ دیگر وضعیت‌ها ⇐ `Refused(409, not_cancellable)` */
  suspend fun cancel(id: String): GatewayCall<PaymentIntent> {
    if (!Wire.isToken(id)) return GatewayCall.Refused(404, "payment_not_found", "")
    return intentOf(transport.post("$ROOT/intents/$id/cancel", emptyMap()), create = false)
  }

  private fun intentOf(r: GatewayReply, create: Boolean): GatewayCall<PaymentIntent> = when {
    r.status in 200..299 -> PaymentIntent.fromMap(r.body)?.let { GatewayCall.Ok(it) }
      ?: GatewayCall.Offline(r.message)
    r.status == 0 || r.status == 429 || (r.status >= 500 && r.code != "provider_unavailable") ->
      GatewayCall.Offline(r.message)
    r.code == "hesabpay_disabled" -> GatewayCall.Disabled
    //  ۴۰۴ ِ بی کدِ قصد = سرور این در را ندارد (کهنه) ⇐ مثلِ خاموش
    r.status == 404 && r.code != "payment_not_found" && create -> GatewayCall.Disabled
    else -> GatewayCall.Refused(r.status, r.code, r.message)
  }

  companion object {
    const val ROOT = "/shop/payments"

    /** سقفِ انتظارِ سرور ۲۵ ثانیه است؛ برنامه کمتر می‌خواهد تا زیرِ مهلتِ خواندنِ ۳۰ ثانیه‌ای بماند */
    const val WAIT_SECONDS = 20
    const val MAX_WAIT = 25
  }
}
