package ir.vil3ntec.tohid.core.gateway

import ir.vil3ntec.tohid.core.perm.PinGuard

/*
 *  «حساب‌پیِ دکان» — پول مستقیم به حسابِ خودِ همان دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ PAY-21، PAY-06).
 *  کاتلینِ خالص؛ HTTP را پلتفرم می‌دهد ([GatewayTransport]). سرور: `server/src/lib/hesabpay-account.js`.
 *
 *  دو حالت:
 *  - «ساده» (simple): شماره یا QR ِ حساب‌پیِ خودِ دکان؛ صندوق همان را با مبلغ و کدِ یکتای فاکتور ([invoiceRef])
 *    نشان می‌دهد و فقط «پول رسید» ِ صندوقدار فاکتور را می‌بندد. اعلانِ برنامهٔ حساب‌پی (اختیاری، فقط اندروید)
 *    فقط **پیشنهاد** می‌دهد ([HesabPayHint]) — هرگز خودکار تأیید نمی‌کند؛ پیامک خوانده نمی‌شود.
 *  - «خودکار» (auto): کلید و رازِ پذیرندهٔ خودِ دکان (فقط‌نوشتنی) ⇐ همان قصدِ پرداختِ [HesabPayClient] و وب‌هوکِ
 *    امضاشده با رازِ همان دکان؛ تأییدِ خودکار فقط با مرجع و مبلغِ برابر از راهِ رسمی.
 *
 *  رمزِ حساب‌پی جدا از رمزِ برنامه است و سرور می‌سنجدش (قفل پس از ۵ خطا)؛ این‌جا فقط شکلش پیش از فرستادن.
 *
 *  نگهبانِ حسابِ گیرنده (سرور: `lib/hesabpay-guard.js`، مهاجرتِ ۲۰۵): QR ِ صندوق فقط از حسابِ ذخیره‌شده در سرور
 *  ([TillHesab.simple]) و کنارش «پول به حسابِ <نام> — <۴ رقم>» ([Payee])؛ حسابِ تازه ۲۴ ساعت در انتظار ([PendingPayee])
 *  با «این من نبودم — لغو کن» ([HesabPayAccountClient.cancelPending])؛ اثرِ انگشتِ گیرنده ([Payee.fp]) روی هر ردیفِ پرداخت.
 */

/**
 *  حسابِ گیرندهٔ پولِ دکان: نام و ۴ رقمِ آخر (برای «پول به حسابِ …») و اثرِ انگشتی که صندوق روی ردیفِ پرداخت می‌گذارد.
 *  `active` فقط در تاریخچهٔ حساب‌ها ([TillHesab.payees]) معنا دارد.
 */
data class Payee(val name: String, val last4: String, val fp: String, val active: Boolean = true) {
  companion object {
    fun fromMap(m: Map<*, *>?): Payee? {
      val last4 = (m?.get("last4") as? String).orEmpty()
      if (m == null || last4.isBlank()) return null
      return Payee((m["name"] as? String).orEmpty(), last4, (m["fp"] as? String).orEmpty(), m["active"] != false)
    }
  }
}

/** X/Z: جمعِ «پول رسید» ِ حساب‌پیِ یک حساب. `kind`: [ACTIVE] | [RETIRED] (حسابِ کهنهٔ همین دکان) | [UNKNOWN] | [NONE] (برنامهٔ کهنه) */
data class PayeeTotal(val kind: String, val name: String, val last4: String, val fp: String, val count: Int, val amountMinor: Long) {
  companion object {
    const val ACTIVE = "active"
    const val RETIRED = "retired"
    const val UNKNOWN = "unknown"
    const val NONE = "none"
  }
}

/** حسابِ تازه‌ای که هنوز فعال نیست (۲۴ ساعت) — فقط نام و ۴ رقم */
data class PendingPayee(val name: String, val last4: String, val until: Long)

/** شماره و QR ِ دکان برای صندوقِ حالتِ ساده */
data class SimpleAccount(val account: String, val qrPayload: String) {
  /** آنچه در QR ِ صندوق کشیده می‌شود: QR ِ خودِ دکان، وگرنه شماره */
  val qrText: String get() = qrPayload.ifBlank { account }
}

/**
 *  حالِ حساب‌پی برای صندوق (`GET /shop/payments/config`)؛ `payee` = گیرندهٔ فعال (سرورِ کهنه ⇐ null)؛ `payees` = حساب‌هایی
 *  که روزی فعال بودند (X/Z به تفکیکِ حساب)
 */
data class TillHesab(
  val gateway: Boolean,
  val mode: String,
  val simple: SimpleAccount?,
  val payee: Payee? = null,
  val payees: List<Payee> = emptyList(),
) {
  val any: Boolean get() = gateway || simple != null

  companion object {
    val OFF = TillHesab(false, HesabPayShop.OFF, null)

    fun fromMap(body: Map<String, Any?>?): TillHesab {
      val h = body?.get("hesabpay") as? Map<*, *> ?: return OFF
      val mode = (h["mode"] as? String)?.takeIf { it in HesabPayShop.MODES } ?: HesabPayShop.OFF
      val s = h["simple"] as? Map<*, *>
      val account = (s?.get("account") as? String).orEmpty()
      val qr = (s?.get("qrPayload") as? String).orEmpty()
      val simple = if (mode == HesabPayShop.SIMPLE && (account.isNotBlank() || qr.isNotBlank())) SimpleAccount(account, qr) else null
      val known = (h["payees"] as? List<*>).orEmpty().mapNotNull { Payee.fromMap(it as? Map<*, *>) }.filter { it.fp.isNotBlank() }
      return TillHesab(h["enabled"] == true, mode, simple, if (mode == HesabPayShop.OFF) null else Payee.fromMap(h["payee"] as? Map<*, *>), known)
    }
  }
}

/** QR ِ اسکن‌شدهٔ دکان: متنِ کامل (همان‌طور نگه داشته می‌شود) و شماره‌ای که در آن آمده (اگر آمده) */
data class ShopQr(val payload: String, val account: String)

object HesabPayShop {
  const val ROOT = "/shop/hesabpay"
  const val OFF = "off"
  const val SIMPLE = "simple"
  const val AUTO = "auto"
  val MODES = setOf(OFF, SIMPLE, AUTO)
  const val QR_MAX = 512

  /** null = خوب؛ "length" | "weak" — همان قاعدهٔ سرور و `PinGuard.newPinProblem` (۶ رقم، نه ساده) */
  fun pinProblem(pin: String): String? = PinGuard.newPinProblem(pin)

  /** رقم‌های فارسی/عربی ⇐ لاتین */
  fun latin(s: String): String = buildString(s.length) {
    for (ch in s) {
      append(
        when (ch) {
          in '۰'..'۹' -> '0' + (ch - '۰')
          in '٠'..'٩' -> '0' + (ch - '٠')
          else -> ch
        },
      )
    }
  }

  private val PHONE = Regex("""^(?:\+93|0093|93|0)?(7\d{8})$""")
  private val PHONE_IN = Regex("""(?:\+93|0093|93|0)?(7\d{8})(?!\d)""")

  /**
   *  شمارهٔ موبایلِ افغانستان ⇐ `07XXXXXXXX`؛ وگرنه null — همان `normAccount` ِ سرور.
   *  TODO(hesabpay-sandbox): اگر حساب‌پی شناسهٔ کیف‌پولِ غیرِ شماره هم دارد، هر دو سو با هم.
   */
  fun normalizeAccount(input: String): String? {
    val s = latin(input).filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' || it == ' ' }
    return PHONE.matchEntire(s)?.let { "0" + it.groupValues[1] }
  }

  /**
   *  متنِ QR ِ خودِ دکان (یک بار اسکن). بلندتر از ۵۱۲ یا با نویسهٔ کنترلی ⇐ null.
   *  TODO(hesabpay-sandbox): شکلِ QR ِ حساب‌پی تأیید نشده؛ اگر مبلغ/مرجع می‌پذیرد، [qrWithAmount] پرشان کند.
   */
  fun parseQr(text: String): ShopQr? {
    val s = text.trim()
    if (s.isEmpty() || s.length > QR_MAX || s.any { it.code < 0x20 || it.code == 0x7f }) return null
    val digits = latin(s).filterNot { it == ' ' || it == '-' }
    val account = PHONE_IN.find(digits)?.let { "0" + it.groupValues[1] }.orEmpty()
    return ShopQr(s, account)
  }

  /**
   *  QR ِ صندوق برای یک فاکتور. امروز همان QR ِ خودِ دکان (یا شماره) است و مبلغ و کد درشت کنارش نوشته می‌شوند.
   *  TODO(hesabpay-sandbox): اگر QR ِ حساب‌پی مبلغ و یادداشت می‌پذیرد، همین‌جا بیفزایید (و آزمونش).
   */
  @Suppress("UNUSED_PARAMETER")
  fun qrWithAmount(simple: SimpleAccount, amountMinor: Long, ref: String): String = simple.qrText

  private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

  /**
   *  کدِ یکتای فاکتور برای یادداشتِ پرداخت‌کننده — از شناسهٔ همان فروش، پس با بازکردنِ دوباره همان می‌ماند.
   *  «HP-XXXX-XXXX» با الفبای Crockford (بی I، L، O، U تا خواندن و نوشتنش آسان باشد).
   */
  fun invoiceRef(saleKey: String): String {
    var h = 0xcbf29ce484222325UL
    for (b in saleKey.encodeToByteArray()) {
      h = h xor b.toUByte().toULong()
      h *= 0x100000001b3UL
    }
    val out = StringBuilder(8)
    repeat(8) {
      out.append(CROCKFORD[(h and 31UL).toInt()])
      h = h shr 5
    }
    return "HP-" + out.substring(0, 4) + "-" + out.substring(4)
  }

  /**
   *  X/Z: «پول رسید» ‌های حساب‌پی (اثرِ انگشتِ گیرنده، مبلغ به پول) به تفکیکِ حساب — حسابِ فعال اول، سپس کهنه‌ها، سپس
   *  ⚠️ ناشناخته (اثرِ انگشتی که هیچ‌گاه حسابِ این دکان نبود) و «بی نشان» (برنامهٔ کهنه). نام و ۴ رقم فقط از [known] (سرور).
   */
  fun byPayee(rows: List<Pair<String?, Long>>, known: List<Payee>): List<PayeeTotal> {
    val groups = LinkedHashMap<String, MutableList<Long>>()
    rows.forEach { (fp, amount) -> groups.getOrPut(fp?.trim().orEmpty()) { ArrayList() }.add(amount) }
    val out = groups.map { (fp, amounts) ->
      val k = known.firstOrNull { it.fp == fp }
      val kind = when {
        fp.isEmpty() -> PayeeTotal.NONE
        k == null -> PayeeTotal.UNKNOWN
        k.active -> PayeeTotal.ACTIVE
        else -> PayeeTotal.RETIRED
      }
      PayeeTotal(kind, k?.name.orEmpty(), k?.last4.orEmpty(), fp, amounts.size, amounts.sum())
    }
    val order = listOf(PayeeTotal.ACTIVE, PayeeTotal.RETIRED, PayeeTotal.UNKNOWN, PayeeTotal.NONE)
    return out.sortedWith(compareBy({ order.indexOf(it.kind) }, { -it.amountMinor }))
  }

  /** کد برای مقایسه: فقط حرف و رقمِ بزرگ (یادداشتِ پرداخت‌کننده خط تیره یا فاصله را جا می‌اندازد) */
  fun refKey(text: String): String = latin(text).uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
}

/** حالِ «حساب‌پیِ دکان» برای صاحب؛ شماره/کلید فقط پس از رمز ([opened]) */
data class HesabAccountView(
  val pinSet: Boolean,
  val locked: Boolean,
  val lockedUntil: Long?,
  val mode: String,
  val connected: Boolean,
  val holdUntil: Long?,
  val autoAvailable: Boolean,
  val autoReason: String,
  val opened: Boolean = false,
  val account: String = "",
  val qrPayload: String = "",
  val keyLast4: String = "",
  val merchantSet: Boolean = false,
  val webhookUrl: String = "",
  /** گیرندهٔ فعال («پول به حسابِ <نام> — <۴ رقم>») */
  val payee: Payee? = null,
  /** حسابِ در انتظارِ فعال شدن ⇐ نوارِ «این من نبودم — لغو کن» */
  val pending: PendingPayee? = null,
  /** بخش پس از «این من نبودم» تا این لحظه بسته است */
  val sectionLockedUntil: Long? = null,
  /** همین دستگاه برای تغییر مطمئن است؟ (null = سرورِ کهنه) و از کی */
  val deviceTrusted: Boolean? = null,
  val trustedAfter: Long? = null,
  val trustDays: Int = 7,
) {
  companion object {
    private fun long(v: Any?): Long? = when (v) {
      is Number -> v.toLong()
      else -> null
    }

    fun fromMap(m: Map<String, Any?>?, opened: Boolean): HesabAccountView? {
      if (m == null || m["pinSet"] !is Boolean) return null
      val mode = (m["mode"] as? String)?.takeIf { it in HesabPayShop.MODES } ?: HesabPayShop.OFF
      val auto = m["auto"] as? Map<*, *>
      val p = m["pending"] as? Map<*, *>
      val pending = p?.let { x -> long(x["until"])?.let { PendingPayee((x["name"] as? String).orEmpty(), (x["last4"] as? String).orEmpty(), it) } }
      val device = m["device"] as? Map<*, *>
      return HesabAccountView(
        pinSet = m["pinSet"] == true,
        locked = m["locked"] == true,
        lockedUntil = long(m["lockedUntil"]),
        mode = mode,
        connected = m["connected"] == true,
        holdUntil = long(m["holdUntil"]),
        autoAvailable = m["autoAvailable"] == true,
        autoReason = (m["autoReason"] as? String).orEmpty(),
        opened = opened,
        account = (m["account"] as? String).orEmpty(),
        qrPayload = (m["qrPayload"] as? String).orEmpty(),
        keyLast4 = (auto?.get("keyLast4") as? String).orEmpty(),
        merchantSet = auto?.get("merchantSet") == true,
        webhookUrl = (auto?.get("webhookUrl") as? String).orEmpty(),
        payee = Payee.fromMap(m["payee"] as? Map<*, *>),
        pending = pending,
        sectionLockedUntil = long(m["sectionLockedUntil"]),
        deviceTrusted = device?.get("trusted") as? Boolean,
        trustedAfter = long(device?.get("trustedAfter")),
        trustDays = long(m["trustDays"])?.toInt() ?: 7,
      )
    }
  }
}

/** نتیجهٔ یک درخواستِ «حساب‌پیِ دکان» */
sealed interface AccountCall {
  data class Ok(val view: HesabAccountView) : AccountCall

  /** سرور رد کرد — `hesabpay_pin_wrong`، `hesabpay_pin_locked`، `owner_only`، `hesabpay_on_hold`… با پیامِ خودِ سرور */
  data class Refused(val status: Int, val code: String, val message: String) : AccountCall

  data class Offline(val message: String) : AccountCall
}

/**
 *  درهای «حساب‌پیِ دکان» (فقط صاحب). رمز همیشه به سرور می‌رود و همان‌جا سنجیده می‌شود؛ این‌جا فقط شکلِ رمزِ
 *  تازه پیش از فرستادن سنجیده می‌شود. کلید و راز فقط فرستاده می‌شوند و هرگز برنمی‌گردند.
 */
class HesabPayAccountClient(private val transport: GatewayTransport) {

  suspend fun status(): AccountCall = of(transport.get(HesabPayShop.ROOT), opened = false)

  suspend fun open(pin: String): AccountCall = of(transport.post("${HesabPayShop.ROOT}/open", mapOf("pin" to pin)), opened = true)

  /** رمزِ نخست (`current == null`) یا عوض کردنش با رمزِ فعلی */
  suspend fun setPin(current: String?, newPin: String): AccountCall {
    //  همان کدهای سرور: `hesabpay_pin_format` | `hesabpay_pin_weak`
    HesabPayShop.pinProblem(newPin)?.let { return AccountCall.Refused(400, if (it == "weak") "hesabpay_pin_weak" else "hesabpay_pin_format", "") }
    val body = LinkedHashMap<String, Any?>()
    if (current != null) body["pin"] = current
    body["newPin"] = HesabPayShop.latin(newPin)
    return of(transport.put("${HesabPayShop.ROOT}/pin", body), opened = false)
  }

  /** رمز فراموش شد: رمز و حساب پاک و ۲۴ ساعت بی حسابِ تازه (سرور) */
  suspend fun resetPin(): AccountCall = of(transport.post("${HesabPayShop.ROOT}/pin/reset", emptyMap()), opened = false)

  /** `name` = نامِ صاحبِ حساب (همان که صندوق کنارِ QR نشان می‌دهد)؛ سرور اجباری می‌خواهدش */
  suspend fun saveSimple(pin: String, name: String, account: String, qr: String): AccountCall {
    val body = linkedMapOf<String, Any?>("pin" to pin, "name" to name.trim(), "mode" to HesabPayShop.SIMPLE)
    if (account.isNotBlank()) body["account"] = account.trim()
    if (qr.isNotBlank()) body["qr"] = qr.trim()
    return of(transport.put("${HesabPayShop.ROOT}/account", body), opened = true)
  }

  /** «خودکار»: شناسهٔ پذیرنده اجباری است (سرور «پرداخت شد» را فقط با پذیرندهٔ همین دکان می‌زند) */
  suspend fun saveAuto(pin: String, name: String, apiKey: String, webhookSecret: String, merchantId: String): AccountCall {
    val body = linkedMapOf<String, Any?>(
      "pin" to pin, "name" to name.trim(), "mode" to HesabPayShop.AUTO, "apiKey" to apiKey.trim(),
      "webhookSecret" to webhookSecret.trim(), "merchantId" to merchantId.trim(),
    )
    return of(transport.put("${HesabPayShop.ROOT}/account", body), opened = true)
  }

  /** «این من نبودم — لغو کن»: حسابِ در انتظار پاک و بخش ۲۴ ساعت بسته (سرور تأییدِ دوبارهٔ ورود می‌خواهد، نه رمزِ حساب‌پی) */
  suspend fun cancelPending(): AccountCall = of(transport.post("${HesabPayShop.ROOT}/pending/cancel", emptyMap()), opened = false)

  suspend fun remove(pin: String): AccountCall = of(transport.post("${HesabPayShop.ROOT}/account/remove", mapOf("pin" to pin)), opened = true)

  private fun of(r: GatewayReply, opened: Boolean): AccountCall = when {
    r.status in 200..299 -> HesabAccountView.fromMap(r.body, opened)?.let { AccountCall.Ok(it) } ?: AccountCall.Offline(r.message)
    r.status == 0 || r.status == 429 || r.status >= 500 && r.code != "hesabpay_cred_key_missing" -> AccountCall.Offline(r.message)
    else -> AccountCall.Refused(r.status, r.code, r.message)
  }
}

/**
 *  پیشنهاد از اعلانِ برنامهٔ حساب‌پی (حالتِ ساده، اختیاری، فقط اندروید با اجازهٔ صاحب). ⛔ فقط پیشنهاد: هیچ تابعی
 *  این‌جا فاکتور را نمی‌بندد؛ صندوقدار «پول رسید» را می‌زند. «مبلغِ برابر» به‌تنهایی هرگز تأیید نیست (اعلانِ ساختگی،
 *  دو فاکتورِ هم‌مبلغ). متنِ اعلان نه ذخیره می‌شود، نه به سرور می‌رود.
 */
object HesabPayHint {
  /** اعلانِ تازه‌تر از این ⇐ پیشنهاد؛ کهنه‌تر نه */
  const val FRESH_MS = 10 * 60_000L

  /** اعلانی که پیش از باز شدنِ برگه آمده (با این فاصله) هم پذیرفته است */
  const val BEFORE_OPEN_MS = 60_000L

  data class Hint(val amountMinor: Long, val text: String, val at: Long)

  data class Suggestion(val amountMinor: Long, val refSeen: Boolean)

  //  TODO(hesabpay-sandbox): متنِ واقعیِ اعلانِ «پول رسید» ِ برنامهٔ حساب‌پی (نشانهٔ ورودی و ارز) تأیید نشده
  private val INCOMING = Regex("""(?i)(received|credited|deposit|دریافت|واریز|رسید|ترلاسه)""")
  private val CURRENCY = """(?:AFN|AFS|AF|افغانی|افغانۍ|؋)"""
  private val AMOUNT = Regex(
    """(?i)(?:$CURRENCY\s*(\d{1,3}(?:[,٬]\d{3})+|\d{1,12})(?:[.٫](\d{1,2}))?)|(?:(\d{1,3}(?:[,٬]\d{3})+|\d{1,12})(?:[.٫](\d{1,2}))?\s*$CURRENCY)""",
  )

  /** مبلغِ دریافتی (به پول) از متنِ اعلان؛ بی نشانهٔ «دریافت» یا بی ارز ⇐ null */
  fun amountOf(text: String): Long? {
    val s = HesabPayShop.latin(text)
    if (!INCOMING.containsMatchIn(s)) return null
    val m = AMOUNT.find(s) ?: return null
    val whole = (m.groups[1]?.value ?: m.groups[3]?.value ?: return null).filter { it.isDigit() }
    val frac = (m.groups[2]?.value ?: m.groups[4]?.value).orEmpty().padEnd(2, '0')
    if (whole.isEmpty() || whole.length > 12) return null
    return whole.toLong() * 100 + frac.toLong()
  }

  fun hintOf(text: String, at: Long): Hint? = amountOf(text)?.let { Hint(it, text, at) }

  /**
   *  پیشنهاد برای برگهٔ بازِ همین فاکتور: فقط اگر مبلغ دقیقاً برابر و اعلان تازه است. `refSeen` = کدِ همین فاکتور
   *  هم در اعلان آمده (پیشنهادِ قوی‌تر) — باز هم فقط پیشنهاد.
   */
  fun suggest(hint: Hint?, amountMinor: Long, ref: String, openedAt: Long, now: Long): Suggestion? {
    if (hint == null || amountMinor <= 0 || hint.amountMinor != amountMinor) return null
    if (hint.at < openedAt - BEFORE_OPEN_MS || now - hint.at > FRESH_MS || hint.at > now + BEFORE_OPEN_MS) return null
    val key = HesabPayShop.refKey(ref)
    return Suggestion(amountMinor, key.isNotEmpty() && HesabPayShop.refKey(hint.text).contains(key))
  }
}
