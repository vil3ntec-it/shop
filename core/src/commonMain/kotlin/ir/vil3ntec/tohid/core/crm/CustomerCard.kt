package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.hw.Ean13
import ir.vil3ntec.tohid.core.platform.sha256

/**
 *  کارت یا بارکدِ مشتری برای شناختنِ سریع در صندوق (CRM-16، §۶.۷) — منطقِ خالص (commonMain).
 *
 *  • فقط رقم: بارکدخوانی که با چیدمانِ فارسی/پشتو تایپ می‌کند حرفِ لاتین را حرفِ دیگری می‌فرستد،
 *    ولی رقمِ فارسی را `normalize` لاتین می‌کند (همان F0-06 ِ بارکدِ کالا).
 *  • کدِ ساخته‌شده EAN-13 با پیشوندِ «049» (بازهٔ داخلیِ GS1 ِ ۰۴۰–۰۴۹، نه کشوری، نه ۲۰/۲۹ ِ
 *    بارکدِ داخلیِ کالا و نه ۲x ِ ترازو) و رقمِ کنترل؛ نه رقم از SHA-256 ِ شناسهٔ ثابتِ مشتری، پس
 *    یک مشتری روی هر دستگاه همان کد را می‌گیرد. کدِ گرفته‌شده ⇒ تلاشِ بعدی.
 *  • کارتِ ازپیش‌چاپ‌شده هم پذیرفته است: ۸ تا ۲۰ رقم؛ ۱۳ رقمی باید رقمِ کنترلِ EAN-13 ِ درست داشته
 *    باشد (غلطِ تایپی گرفته می‌شود).
 *  • یکتایی: نه کارتِ مشتریِ دیگر، نه هر کدی که کالا آن را می‌خواند (بارکدِ کامل یا نیمه، بسته، سری)؛
 *    سنجشِ «گرفته» با فراخواننده است ([pick]).
 *  • صندوق ([route]): خاموش ⇒ هیچ؛ بارکدِ کالا همیشه مقدم است؛ فقط کدی که هیچ کالایی نمی‌خواند
 *    می‌تواند مشتری را انتخاب کند.
 *
 *  آزمون: `CustomerCardTest` (commonTest).
 */
object CustomerCard {

  const val PREFIX = "049"
  const val MIN_LENGTH = 8
  const val MAX_LENGTH = 20

  /** فاصله، خط‌تیره و رقمِ فارسی/عربی ⇒ رشتهٔ رقمِ لاتین (حرف همان می‌ماند تا `validate` ردش کند) */
  fun normalize(raw: String): String = buildString {
    for (ch in raw.trim()) when (ch) {
      ' ', '-', '‌', ' ' -> Unit
      in '۰'..'۹' -> append('0' + (ch - '۰'))
      in '٠'..'٩' -> append('0' + (ch - '٠'))
      else -> append(ch)
    }
  }

  enum class Reason { EMPTY, NOT_DIGITS, LENGTH, CHECK_DIGIT, TAKEN_CUSTOMER, TAKEN_PRODUCT, EXHAUSTED }

  sealed interface Check {
    data class Ok(val code: String) : Check
    data class Bad(val reason: Reason) : Check
  }

  /** کدِ واردشده (دستی یا از کارتِ چاپی) ⇒ کدِ تمیز یا دلیلِ رد */
  fun validate(raw: String): Check {
    val code = normalize(raw)
    if (code.isEmpty()) return Check.Bad(Reason.EMPTY)
    if (code.any { it !in '0'..'9' }) return Check.Bad(Reason.NOT_DIGITS)
    if (code.length !in MIN_LENGTH..MAX_LENGTH) return Check.Bad(Reason.LENGTH)
    if (code.length == 13 && !Ean13.isValid(code)) return Check.Bad(Reason.CHECK_DIGIT)
    return Check.Ok(code)
  }

  /** همین قاعده‌ها این کد را ساخته‌اند؟ */
  fun isGenerated(code: String): Boolean = code.length == 13 && code.startsWith(PREFIX) && Ean13.isValid(code)

  /** نامزدِ شمارهٔ `attempt` برای `seed`: «049» + نه رقم از ۴۰ بیتِ نخستِ SHA-256 + رقمِ کنترل */
  internal fun candidate(seed: String, attempt: Int): String {
    val h = sha256("tohid-customer-card:$seed:$attempt".encodeToByteArray())
    var v = 0L
    for (i in 0 until 5) v = (v shl 8) or (h[i].toLong() and 0xFF)
    val first12 = PREFIX + (v % 1_000_000_000L).toString().padStart(9, '0')
    return first12 + Ean13.checkDigit(first12)
  }

  /** چه کسی کد را گرفته — `null` یعنی آزاد */
  enum class Clash { CUSTOMER, PRODUCT }

  /**
   *  کدِ کارتِ یک مشتری: `manual` ِ خالی/null ⇒ ساختن (نخستین نامزدِ آزاد که با کارتِ فعلیِ خودش هم
   *  فرق دارد، تا «کارتِ تازه» برای کارتِ گم‌شده کدِ دیگری بدهد)؛ وگرنه همان کدِ واردشده پس از سنجش.
   *  [clash] برای هر کد می‌گوید مشتریِ دیگری یا کالایی آن را دارد.
   */
  fun pick(seed: String, manual: String?, current: String?, clash: (String) -> Clash?, maxTries: Int = 64): Check {
    if (manual.isNullOrBlank()) {
      for (attempt in 0 until maxTries) {
        val code = candidate(seed, attempt)
        if (code != current && clash(code) == null) return Check.Ok(code)
      }
      return Check.Bad(Reason.EXHAUSTED)
    }
    val ok = validate(manual) as? Check.Ok ?: return validate(manual)
    return when (clash(ok.code)) {
      Clash.CUSTOMER -> Check.Bad(Reason.TAKEN_CUSTOMER)
      Clash.PRODUCT -> Check.Bad(Reason.TAKEN_PRODUCT)
      null -> ok
    }
  }

  /**
   *  صندوق: کدِ خوانده‌شده ⇒ شناسهٔ مشتری، یا null یعنی «راهِ همیشگیِ کالا». خاموش ⇒ null بی هیچ
   *  سنجشی؛ کدی که کالا آن را می‌خواند ([isProductCode]) ⇒ null (کالا مقدم است)؛ وگرنه صاحبِ کارت.
   */
  fun route(enabled: Boolean, raw: String, isProductCode: (String) -> Boolean, ownerOf: (String) -> String?): String? {
    if (!enabled) return null
    val code = normalize(raw)
    if (code.length !in MIN_LENGTH..MAX_LENGTH || code.any { it !in '0'..'9' }) return null
    if (isProductCode(raw.trim()) || isProductCode(code)) return null
    return ownerOf(code)
  }
}
