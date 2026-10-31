package ir.vil3ntec.tohid.core.gateway

/*
 *  قصدِ پرداختِ درگاهی (حساب‌پی) از نگاهِ برنامهٔ دکان — PAY-21، PAY-08.
 *
 *  قرارداد: `docs/PAYMENTS-HESABPAY-fa.md` (شاخهٔ سرور، PR #518) و بخشِ «حساب‌پی در صندوق» ِ
 *  `docs/RULES-fa.md`. این‌جا فقط خواندنِ پاسخِ سرور است: درختِ JSON (`Map`، همان شکلی که
 *  `PermPolicy.tree` ِ برنامه می‌سازد) ⇐ نوعِ کاتلین. فیلدِ ناشناخته نادیده؛ فیلدِ لازمِ
 *  نادرست ⇐ `null` (هرگز «پرداخت شد» از دادهٔ ناقص ساخته نمی‌شود).
 *
 *  ⛔ مبلغ همیشه عددِ صحیح به «پول» (یک‌صدمِ افغانی): ۱۴۵۰ افغانی ⇐ 145000.
 */

/** وضعیتِ قصد — همان واژه‌های سرور */
enum class IntentStatus(val wire: String) {
  CREATED("created"),
  PENDING("pending"),
  PAID("paid"),
  FAILED("failed"),
  EXPIRED("expired"),
  CANCELLED("cancelled"),
  REFUNDED("refunded"),

  /** واژه‌ای که این نسخه نمی‌شناسد — نه پایانی، نه «پرداخت شد» */
  UNKNOWN("");

  /** هنوز ممکن است پرداخت شود */
  val live: Boolean get() = this == CREATED || this == PENDING

  /** دیگر با خواندنِ بعدی عوض نمی‌شود؛ انتظار تمام است */
  val terminal: Boolean get() = this == PAID || this == FAILED || this == EXPIRED || this == CANCELLED || this == REFUNDED

  companion object {
    fun of(wire: Any?): IntentStatus = entries.firstOrNull { it != UNKNOWN && it.wire == wire } ?: UNKNOWN
  }
}

/**
 *  ردیفِ پرداختِ آماده‌ای که سرور برای فروشِ صندوقِ پرداخت‌شده می‌دهد (`payment` ِ پاسخ) —
 *  برنامه **همین** را به فروشی می‌افزاید که شناسه‌اش `refId` است، نه ردیفی که خودش بسازد.
 *  `verification = "gateway"` و `status = "confirmed"`: تأییدِ ارائه‌دهنده (PAY-08).
 */
data class GatewayPaymentRow(
  val method: String,
  val kind: String,
  val amountMinor: Long,
  val ref: String?,
  val status: String,
  val verification: String?,
  val intentId: String?,
) {
  companion object {
    fun fromMap(m: Map<*, *>?): GatewayPaymentRow? {
      if (m == null) return null
      val method = Wire.str(m["method"]).ifEmpty { return null }
      val kind = Wire.str(m["kind"]).ifEmpty { return null }
      val amount = Wire.long(m["amountMinor"]) ?: Wire.long(m["amount"]) ?: return null
      if (amount <= 0) return null
      val status = Wire.str(m["status"]).ifEmpty { return null }
      return GatewayPaymentRow(
        method = method,
        kind = kind,
        amountMinor = amount,
        ref = Wire.str(m["ref"]).ifEmpty { null },
        status = status,
        verification = Wire.str(m["verification"]).ifEmpty { null },
        intentId = Wire.str(m["intentId"]).ifEmpty { null },
      )
    }
  }
}

/** نمای قصد — پاسخِ ساختن و خواندن */
data class PaymentIntent(
  val id: String,
  val kind: String,
  /** شناسهٔ فروشی که این قصد می‌پردازد (صندوق: همان `SaleDraft.key`) */
  val refId: String,
  val status: IntentStatus,
  val amountMinor: Long,
  val currency: String,
  val checkoutUrl: String,
  /** همان `checkoutUrl` — همین QR می‌شود و همین رکوردِ NDEF ِ NFC */
  val qrPayload: String,
  /** میلی‌ثانیهٔ یونیکس، ساعتِ سرور؛ `null` = نامعلوم */
  val expiresAt: Long?,
  val paidAt: Long?,
  val providerRef: String,
  /** پرداختِ دیر یا مبلغِ ناجور — مدیر باید ببیند (S35) */
  val needsReview: Boolean,
  /** فقط برای `pos_sale` ِ پرداخت‌شده */
  val payment: GatewayPaymentRow?,
  /** زمانِ ساختن به ساعتِ سرور — برای تخمینِ اختلافِ ساعتِ دستگاه در شمارشِ معکوس */
  val createdAt: Long? = null,
) {
  companion object {
    /** درختِ JSON ⇐ قصد؛ بی شناسه، بی مبلغِ درست یا بی ارز ⇐ `null` */
    fun fromMap(m: Map<*, *>?): PaymentIntent? {
      if (m == null) return null
      val id = Wire.str(m["id"])
      if (!Wire.isToken(id)) return null
      val amount = Wire.long(m["amountMinor"]) ?: Wire.long(m["amount"]) ?: return null
      if (amount <= 0) return null
      val currency = Wire.str(m["currency"]).ifEmpty { return null }
      val checkout = Wire.str(m["checkoutUrl"])
      return PaymentIntent(
        id = id,
        kind = Wire.str(m["kind"]),
        refId = Wire.str(m["refId"]),
        status = IntentStatus.of(m["status"]),
        amountMinor = amount,
        currency = currency,
        checkoutUrl = checkout,
        qrPayload = Wire.str(m["qrPayload"]).ifEmpty { checkout },
        expiresAt = Wire.long(m["expiresAt"])?.takeIf { it > 0 },
        paidAt = Wire.long(m["paidAt"])?.takeIf { it > 0 },
        providerRef = Wire.str(m["providerRef"]),
        needsReview = m["needsReview"] == true,
        payment = GatewayPaymentRow.fromMap(m["payment"] as? Map<*, *>),
        createdAt = Wire.long(m["createdAt"])?.takeIf { it > 0 },
      )
    }
  }
}

/** خواندنِ مقدارهای درختِ JSON — عدد ممکن است `Double` (درختِ برنامه)، `Long` یا `Int` باشد */
internal object Wire {
  fun str(v: Any?): String = (v as? String)?.trim().orEmpty()

  /** فقط عددِ صحیحِ دقیق (۲^۵۳−۱)؛ اعشار، رشته و بی‌نهایت ⇐ `null` */
  fun long(v: Any?): Long? = when (v) {
    is Long -> v.takeIf { it in -MAX_SAFE..MAX_SAFE }
    is Int -> v.toLong()
    is Double -> if (v.isNaN() || v.isInfinite() || v % 1.0 != 0.0 || v > MAX_SAFE || v < -MAX_SAFE) null else v.toLong()
    is Float -> long(v.toDouble())
    else -> null
  }

  /** شناسه/کلید فقط با نویسه‌های امنِ نشانی — همان `[A-Za-z0-9_.:-]` ِ سرور */
  fun isToken(s: String, min: Int = 1, max: Int = 128): Boolean =
    s.length in min..max && s.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '.' || it == ':' || it == '-' }

  const val MAX_SAFE: Long = 9_007_199_254_740_991L
}
