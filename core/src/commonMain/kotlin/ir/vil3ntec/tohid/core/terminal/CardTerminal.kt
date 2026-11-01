package ir.vil3ntec.tohid.core.terminal

/*
 *  کارت‌خوان (POS ِ بانکی) — رابطِ وصل شدنِ صندوق به دستگاهِ کارت (PAY-06، PAY-08).
 *
 *  خواستهٔ صاحب محصول: مبلغ از صندوقِ ما به کارت‌خوان برود، مشتری مبلغ را ببیند، کارت یا گوشی را بزند
 *  و تأیید کند. هیچ سندِ یکپارچه‌سازیِ کارت‌خوان یا بانکی در دست نیست؛ پس:
 *
 *  - [ManualCardTerminal] — تنها پیاده‌سازیِ امروز: صندوقدار مبلغ را دستی روی کارت‌خوان می‌زند و پس از
 *    تأیید، شمارهٔ تأیید/مرجعِ رسیدِ کارت‌خوان را در برنامه می‌نویسد. نتیجه همان ردیفِ «ثبتِ دستی»
 *    PAY-06 است (`pending`، `verification: manual`) — هرگز «تأییدِ دستگاه» (PAY-08).
 *  - [VendorAppTerminal] — جای خالیِ آگاهانه برای «برنامه به برنامه» (intent/AIDL/SDK ِ کارت‌خوان یا
 *    بانک یا حساب‌پی). تا سندِ واقعی نرسیده هیچ نامِ intent، فیلد یا پروتکلی حدس زده نمی‌شود؛ همیشه
 *    [TerminalResult.Unavailable] برمی‌گرداند و صفحه نشانش نمی‌دهد.
 *
 *  کاتلینِ خالص (commonMain)؛ پل به صفحه را پلتفرم می‌دهد.
 */

/** درخواستِ پرداخت به کارت‌خوان: مبلغ به «پول» (یک‌صدمِ افغانی) و شناسهٔ فروش */
data class TerminalRequest(val amountMinor: Long, val saleRef: String, val currency: String = "AFN")

/** نتیجهٔ کارت‌خوان */
sealed interface TerminalResult {
  /** تأیید شد — `terminalRef` شمارهٔ تأیید/مرجعِ کارت‌خوان؛ `maskedCard` فقط چهار رقمِ آخر، اگر داده شد */
  data class Approved(val terminalRef: String, val maskedCard: String? = null) : TerminalResult

  /** کارت رد شد (موجودی، رمز، …) — فروش با این روش بسته نمی‌شود */
  data class Declined(val reason: String) : TerminalResult

  /** صندوقدار یا مشتری انصراف داد */
  data object Cancelled : TerminalResult

  /** کارت‌خوان در دسترس نیست یا هنوز وصل نشده */
  data class Unavailable(val reason: String) : TerminalResult
}

/** کارت‌خوان، هر نوع که باشد */
interface CardTerminal {
  /** شناسهٔ ماشین‌خوان (`manual`، `vendor_app`) */
  val id: String

  /** مبلغ را به کارت‌خوان بده و تا پاسخ صبر کن */
  suspend fun requestPayment(amountMinor: Long, saleRef: String): TerminalResult
}

/**
 *  کارت‌خوانِ دستی: `ask` پنجرهٔ صندوقدار است (مبلغ را نشان می‌دهد و پاسخِ او را می‌گیرد). ورودی این‌جا
 *  سنجیده می‌شود: شمارهٔ مرجعِ نادرست هرگز «تأیید» نمی‌شود.
 */
class ManualCardTerminal(private val ask: suspend (TerminalRequest) -> Answer) : CardTerminal {

  /** پاسخِ صندوقدار */
  sealed interface Answer {
    data class Approved(val reference: String, val last4: String? = null) : Answer
    data class Declined(val reason: String = "") : Answer
    data object Cancelled : Answer
  }

  override val id: String = ID

  override suspend fun requestPayment(amountMinor: Long, saleRef: String): TerminalResult {
    if (amountMinor <= 0 || amountMinor > MAX_AMOUNT) return TerminalResult.Unavailable("bad_amount")
    return when (val a = ask(TerminalRequest(amountMinor, saleRef))) {
      is Answer.Approved -> {
        val ref = reference(a.reference) ?: return TerminalResult.Declined("bad_reference")
        val last4 = a.last4?.let { l -> digits(l).ifEmpty { null } }
        if (last4 != null && !(last4.length == 4 && last4.all { it in '0'..'9' })) return TerminalResult.Declined("bad_card")
        TerminalResult.Approved(ref, last4?.let { "•••• $it" })
      }
      is Answer.Declined -> TerminalResult.Declined(a.reason.trim())
      Answer.Cancelled -> TerminalResult.Cancelled
    }
  }

  companion object {
    const val ID = "manual"
    const val MAX_AMOUNT = 9_007_199_254_740_991L

    /**
     *  شمارهٔ تأیید/مرجعِ رسیدِ کارت‌خوان: رقم‌های فارسی/عربی ⇐ لاتین، فاصله‌ها حذف، فقط حرف و رقمِ لاتین و
     *  `-`، ۴ تا ۳۲ نویسه. نادرست ⇐ `null`.
     */
    fun reference(raw: String): String? {
      val s = digits(raw).filterNot { it.isWhitespace() }.uppercase()
      if (s.length !in 4..32) return null
      return s.takeIf { it.all { c -> c in 'A'..'Z' || c in '0'..'9' || c == '-' } }
    }

    /** رقم‌های ۰–۹ ِ فارسی و عربی ⇐ لاتین؛ بقیه دست نمی‌خورد */
    fun digits(s: String): String = buildString(s.length) {
      for (c in s.trim()) append(
        when (c) {
          in '۰'..'۹' -> '0' + (c - '۰')
          in '٠'..'٩' -> '0' + (c - '٠')
          else -> c
        },
      )
    }

    /** متنِ مرجعِ ردیفِ پرداخت (PAY-06): شمارهٔ تأیید و اگر داده شد چهار رقمِ آخرِ کارت */
    fun refText(a: TerminalResult.Approved): String = a.terminalRef + (a.maskedCard?.let { " ($it)" } ?: "")
  }
}

/**
 *  جای خالیِ «برنامه به برنامه» — کارت‌خوانِ هوشمندِ اندرویدی که برنامهٔ پرداختِ بانک/حساب‌پی رویش است.
 *
 *  برای ساختنش لازم است (از صاحب محصول): نام و مدلِ کارت‌خوان و نسخهٔ اندروید، بانکِ پذیرنده یا حساب‌پی،
 *  سندِ یکپارچه‌سازی (نامِ intent یا سرویسِ AIDL/SDK، فیلدهای درخواست و پاسخ، کدهای خطا، رفتارِ لغو و
 *  تکرار)، شناسهٔ پذیرنده/پایانه و دستگاه یا sandbox ِ آزمایشی. ⛔ تا آن روز هیچ intent یا فیلدی حدس زده
 *  نمی‌شود — همیشه «در دسترس نیست».
 */
object VendorAppTerminal : CardTerminal {
  const val ID = "vendor_app"
  override val id: String = ID

  override suspend fun requestPayment(amountMinor: Long, saleRef: String): TerminalResult =
    TerminalResult.Unavailable("vendor_integration_document_required")
}
