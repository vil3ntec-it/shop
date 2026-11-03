package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.gateway.GatewayCall
import ir.vil3ntec.tohid.core.gateway.HesabPayAttempts
import ir.vil3ntec.tohid.core.gateway.HesabTill
import ir.vil3ntec.tohid.core.gateway.IntentStatus
import ir.vil3ntec.tohid.core.gateway.PaymentIntent
import ir.vil3ntec.tohid.core.gateway.PendingPayment
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv

/**
 *  گام‌های قصدِ پرداختِ حساب‌پی، بی Compose: آغاز (ازسرگیری، جایگزینی یا ساختن)، دیدنِ هر وضعیت، لغو و تسویه — مشترکِ
 *  برگهٔ کامپیوتر (`HesabPaySheet`) و صندوقِ گوشیِ نسخهٔ ۳ (`HesabTillRoute`). جابه‌جا از `HesabPaySheet` بی تغییرِ رفتار.
 *  هر رکورد پیش از درخواست ذخیره می‌شود؛ کلیدِ یکتایی تا تلاشِ تازه همان می‌ماند.
 */
class HesabPayIntentFlow(
  private val env: HesabPayEnv,
  private val saleKey: String,
  private val amountMinor: Long,
  private val total: Double,
  private val draft: PayFlow.Draft?,
  private val precheck: () -> String?,
) {
  sealed interface Step {
    data class Show(val intent: PaymentIntent) : Step
    /** پایانِ این دور: پیامِ کاربر و «دوباره» شدنی است یا نه */
    data class Stop(val message: String, val retry: Boolean) : Step
  }

  private var rec: PendingPayment? = null
  val skewMs: Long get() = rec?.skewMs ?: 0

  suspend fun start(): Step = when (val plan = HesabPayAttempts.plan(env.pending.of(saleKey), saleKey, amountMinor, env.now())) {
    is HesabPayAttempts.Plan.Resume -> resume(plan.pending)
    is HesabPayAttempts.Plan.Replace -> replace(plan.old, plan.fresh)
    is HesabPayAttempts.Plan.Fresh -> create(plan.pending)
  }

  /** همان قصد: بی شناسه (پاسخِ ساختن گم شد) ⇐ همان کلید دوباره؛ با شناسه ⇐ خواندن */
  private suspend fun resume(p: PendingPayment): Step {
    rec = p
    val id = p.intentId ?: return create(p)
    return when (val r = env.client.fetch(id, 0)) {
      is GatewayCall.Ok -> show(r.value)
      is GatewayCall.Refused -> if (r.code == "payment_not_found") { forget(); start() } else stop(r.message.ifEmpty { StrPay.unreachable }, true)
      is GatewayCall.Offline -> stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> stop(StrPay.unreachable, true)
    }
  }

  /** سبد پس از QR عوض شد ⇐ اول لغوِ قصدِ قبلی؛ اگر همان دم پرداخت شده بود، فروش بسته نمی‌شود */
  private suspend fun replace(old: PendingPayment, fresh: PendingPayment): Step {
    val oldId = old.intentId ?: return create(fresh)
    val now: PaymentIntent? = when (val gone = env.client.cancel(oldId)) {
      is GatewayCall.Ok -> gone.value
      is GatewayCall.Refused ->
        if (gone.code == "payment_not_found") null
        else (env.client.fetch(oldId, 0) as? GatewayCall.Ok<PaymentIntent>)?.value ?: return stop(StrPay.unreachable, true)
      is GatewayCall.Offline -> return stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> return stop(StrPay.unreachable, true)
    }
    if (now != null && now.status == IntentStatus.PAID) {
      env.pending.save(HesabPayAttempts.withIntent(old, now))
      return stop(StrPay.amountMismatch, false)
    }
    return create(fresh)
  }

  private suspend fun create(p: PendingPayment): Step {
    //  پیش از گرفتنِ پول: فاکتوری که ثبت‌شدنی نیست (موجودی، تکرار) قصد نمی‌گیرد
    precheck()?.let { return stop(it, true) }
    rec = p
    env.pending.save(p)
    return when (val r = env.client.create(saleKey, amountMinor, p.idempotencyKey)) {
      is GatewayCall.Ok -> show(r.value, fresh = true)
      is GatewayCall.Refused -> when (r.code) {
        "provider_unavailable" -> { env.pending.save(p.copy(status = IntentStatus.FAILED.wire)); stop(StrPay.providerDown, true) }
        "already_paid" -> stop(StrPay.alreadyPaid, false)
        "intent_active" -> stop(StrPay.otherOpen, true)
        "hesabpay_disabled" -> stop(StrPay.disabled, false)
        "idempotency_key_reused" -> { forget(); stop(StrPay.unreachable, true) }
        else -> stop(r.message.ifEmpty { StrPay.unreachable }, true)
      }
      is GatewayCall.Offline -> stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> stop(StrPay.disabled, false)
    }
  }

  private fun show(i: PaymentIntent, fresh: Boolean = false): Step {
    //  هرگز قصدِ فاکتورِ دیگر یا مبلغِ دیگر روی این فاکتور
    if ((i.refId.isNotEmpty() && i.refId != saleKey) || i.amountMinor != amountMinor) return stop(StrPay.amountMismatch, false)
    seen(i, if (fresh) env.now() else null)
    return Step.Show(i)
  }

  fun seen(i: PaymentIntent, localNow: Long? = null) {
    val r = rec ?: return
    val next = HesabPayAttempts.withIntent(r, i, localNow)
    rec = next
    env.pending.save(next)
  }

  fun forget() {
    rec = null
    env.pending.forget(saleKey)
  }

  suspend fun cancel(i: PaymentIntent): Step = when (val r = env.client.cancel(i.id)) {
    is GatewayCall.Ok -> { seen(r.value); Step.Show(r.value) }
    is GatewayCall.Refused -> when (r.code) {
      //  دیگر لغوشدنی نیست ⇐ شاید همین الان پرداخت شد
      "not_cancellable" -> when (val f = env.client.fetch(i.id, 0)) {
        is GatewayCall.Ok -> { seen(f.value); Step.Show(f.value) }
        else -> stop(StrPay.unknown, true)
      }
      "payment_not_found" -> { forget(); stop(StrPay.cancelled, true) }
      else -> stop(r.message.ifEmpty { StrPay.unknown }, true)
    }
    is GatewayCall.Offline, GatewayCall.Disabled -> stop(StrPay.unknown, true)
  }

  fun settle(i: PaymentIntent): PayFlow.Outcome<Settlement> = HesabPaySettle.settle(total, draft, saleKey, i)

  private fun stop(message: String, retry: Boolean) = Step.Stop(message, retry)
}

/** پرداختی رسید ولی خودکار بسته نشد (`HesabTill.verdict`) — پیامِ کاربر با دلیل */
object HesabHold {
  fun text(h: HesabTill.Hold): String = StrTillPay.review(
    when (h) {
      HesabTill.Hold.NOT_PAID -> StrTillPay.whyNotPaid
      HesabTill.Hold.OTHER_SALE -> StrTillPay.whyOtherSale
      HesabTill.Hold.AMOUNT -> StrTillPay.whyAmount
      HesabTill.Hold.PAYEE -> StrTillPay.whyPayee
      HesabTill.Hold.PAYEE_UNKNOWN -> StrTillPay.whyPayeeUnknown
      HesabTill.Hold.FLAGGED -> StrTillPay.whyFlagged
    },
  )
}
