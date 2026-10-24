package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 *  بلیتِ شمارهٔ تأییدشدهٔ همین دستگاه (۲۳.۲) — یکی برای بازارچه و همهٔ ویترین‌ها.
 *
 *  بلیت خودش در سرور ذخیره نمی‌شود؛ «خروج از همه‌جا» یا پاک کردنِ حساب آن را
 *  در سرور باطل می‌کند و پاسخِ ۴۰۱ ِ بعدی همین‌جا پاکش می‌کند (`forget`).
 */
class ShopperTickets(
  private val storage: ShopperStorage,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  private val ticket = MutableStateFlow(storage.load(ShopperStorage.TICKET, PhoneTicket.serializer()))

  val state: StateFlow<PhoneTicket?> = ticket.asStateFlow()

  /** بلیتِ نامنقضی — یا null */
  fun current(): PhoneTicket? = ticket.value?.takeIf { it.validAt(now()) }

  /** آیا همین شماره تأیید شده است (شماره هر شکلی نوشته شده باشد) */
  fun covers(phone: String): Boolean {
    val t = current() ?: return false
    return LocaleFormat.normalizePhone(phone) == t.phone
  }

  fun save(value: PhoneTicket) {
    ticket.value = value
    storage.save(ShopperStorage.TICKET, PhoneTicket.serializer(), value)
  }

  fun forget() {
    ticket.value = null
    storage.write(ShopperStorage.TICKET, null)
  }
}

/** چرا تأیید نشد — صفحه هر کدام را با جملهٔ جدولِ زبان می‌گوید؛ `OTHER` پیامِ خودِ سرور. */
enum class VerifyProblem {
  INVALID_PHONE, CODE_REQUIRED, WRONG_CODE, CODE_EXPIRED, LOCKED, WAIT, DAILY_LIMIT, UNAVAILABLE, OFFLINE, OTHER;

  companion object {
    fun of(failure: ApiFailure): VerifyProblem = when {
      failure is ApiFailure.Offline || failure is ApiFailure.Unreachable || failure is ApiFailure.Timeout -> OFFLINE
      failure.code == "otp_wrong" -> WRONG_CODE
      failure.code in setOf("otp_expired", "otp_not_found", "otp_used") -> CODE_EXPIRED
      failure.code == "otp_locked" -> LOCKED
      failure.code == "otp_resend_wait" -> WAIT
      failure.code == "otp_daily_limit" || failure is ApiFailure.RateLimited -> DAILY_LIMIT
      failure.code == "verification_unavailable" -> UNAVAILABLE
      else -> OTHER
    }
  }
}

data class VerifyState(
  val step: Step = Step.PHONE,
  /** شمارهٔ E.164 ِ همین دور */
  val phone: String = "",
  val busy: Boolean = false,
  /** پیش از این لحظه «فرستادنِ دوباره» بسته است */
  val resendAt: Long = 0,
  val codeExpiresAt: Long = 0,
  val problem: VerifyProblem? = null,
  /** پیامِ سرور برای `OTHER` */
  val message: String = "",
) {
  enum class Step { PHONE, CODE, VERIFIED }

  fun canResend(now: Long): Boolean = step == Step.CODE && !busy && now >= resendAt
}

/**
 *  گام‌های تأییدِ شماره: شماره ⇐ کد ⇐ بلیت. یک نمونه برای هر برگهٔ تأیید.
 *
 *  ⛔ کدِ آزمایشیِ سرور (`devCode`) هرگز نشان داده نمی‌شود — خریدار باید
 *  کدی را بزند که به گوشی‌اش رسیده است.
 */
class PhoneVerification(
  private val api: ShopperApi,
  private val tickets: ShopperTickets,
  /** ویترینی که از آن تأیید می‌شود؛ null ⇒ از راهِ بازارچه */
  private val slug: String? = null,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  private val flow = MutableStateFlow(
    tickets.current()?.let { VerifyState(step = VerifyState.Step.VERIFIED, phone = it.phone) } ?: VerifyState(),
  )

  val state: StateFlow<VerifyState> = flow.asStateFlow()

  /** فرستادنِ کد (و «دوباره بفرست» با همان شماره) */
  suspend fun sendCode(rawPhone: String = flow.value.phone): Boolean {
    val phone = LocaleFormat.normalizePhone(rawPhone)
      ?: return fail(VerifyProblem.INVALID_PHONE)
    flow.value = flow.value.copy(phone = phone, busy = true, problem = null, message = "")
    return when (val r = api.startVerification(phone, slug)) {
      is ApiResult.Success -> {
        val at = now()
        flow.value = flow.value.copy(
          step = VerifyState.Step.CODE, busy = false,
          resendAt = at + r.value.resendSeconds.coerceAtLeast(0) * 1000L,
          codeExpiresAt = r.value.expiresAt,
        )
        true
      }
      is ApiResult.Failure -> fail(VerifyProblem.of(r.error), r.error.userMessage)
    }
  }

  suspend fun checkCode(rawCode: String): Boolean {
    val code = LocaleFormat.asciiDigits(rawCode).filter { it in '0'..'9' }
    if (code.isEmpty()) return fail(VerifyProblem.CODE_REQUIRED)
    val phone = flow.value.phone
    flow.value = flow.value.copy(busy = true, problem = null, message = "")
    return when (val r = api.checkVerification(phone, code, slug)) {
      is ApiResult.Success -> {
        tickets.save(r.value)
        flow.value = flow.value.copy(step = VerifyState.Step.VERIFIED, phone = r.value.phone, busy = false)
        true
      }
      is ApiResult.Failure -> fail(VerifyProblem.of(r.error), r.error.userMessage)
    }
  }

  /** شمارهٔ دیگر */
  fun restart() {
    flow.value = VerifyState(phone = flow.value.phone)
  }

  private fun fail(problem: VerifyProblem, message: String = ""): Boolean {
    flow.value = flow.value.copy(busy = false, problem = problem, message = message)
    return false
  }
}
