package ir.vil3ntec.tohid.core.credit

/**
 *  CRM-03 — «سقفِ اعتبار و سیاستِ تأیید؛ بیش از سقف، تأییدِ مدیر لازم است» در صندوق (سناریوی S07).
 *
 *  کاتلینِ خالص، همه به پول (یک‌صدمِ افغانی، `Long`) و بی گردکردن: ماندهٔ فعلیِ مشتری + بردِ همین
 *  فاکتور با سقف سنجیده می‌شود. ماندهٔ دفتر و سقفِ مشتری را برنامه می‌دهد (`DebtBook.creditCheck`)؛
 *  این‌جا فقط حساب و «آیا این تأیید همین فروش را می‌پوشاند».
 *
 *  ⛔ بسته‌به‌خطا: عددِ بیرون از ±(2^53−1) یا سقفِ منفی «بیش از سقف» است، نه «آزاد».
 */
object CreditGate {

  /** بزرگ‌ترین عددِ صحیحِ امن (همان قاعدهٔ پولِ §۲۸.۴) */
  const val MAX_SAFE: Long = 9_007_199_254_740_991L

  /** نقش‌هایی که فروشِ بیش از سقف را تأیید می‌کنند (همان `ApproverVault.APPROVER_ROLES`) */
  val APPROVER_ROLES: List<String> = listOf("owner", "manager")

  /** فروشِ نسیه‌ای که از سقف می‌گذرد */
  data class Over(val limitMinor: Long, val balanceMinor: Long, val addMinor: Long, val afterMinor: Long) {
    /** چقدر از سقف بیرون می‌زند */
    val excessMinor: Long get() = afterMinor - limitMinor
  }

  /**
   *  تأییدِ بیش از سقف — چه کسی، با چه نقشی، برای کدام مشتری، تا چه بردی و چرا. به همان مشتری و
   *  تا همان مبلغ بسته است: سبدِ بزرگ‌تر یا مشتریِ دیگر دوباره تأیید می‌خواهد.
   */
  data class Approval(val by: String, val role: String, val debtorId: String, val addMinor: Long, val reason: String)

  enum class State {
    /** بی سقف، بی بردِ تازه، یا هنوز زیرِ سقف */
    FREE,
    /** بیش از سقف و هنوز تأییدی که بپوشاند نیست ⇒ هیچ چیز ثبت نمی‌شود */
    NEEDS_APPROVAL,
    /** تأییدِ معتبرِ مدیر/صاحب همین مشتری و همین مبلغ را می‌پوشاند */
    APPROVED,
  }

  private fun safe(v: Long) = v in -MAX_SAFE..MAX_SAFE

  /**
   *  @param limitMinor سقفِ مشتری؛ `null` ⇒ بی سقف (همان رفتارِ همیشه)
   *  @param balanceMinor ماندهٔ فعلیِ حساب (مثبت = بدهکار، منفی = طلبکار)
   *  @param addMinor بردِ تازهٔ همین فاکتور
   *  @return `null` اگر از سقف نمی‌گذرد، وگرنه [Over]
   */
  fun check(limitMinor: Long?, balanceMinor: Long, addMinor: Long): Over? {
    if (limitMinor == null || addMinor <= 0) return null
    if (!safe(limitMinor) || limitMinor < 0 || !safe(balanceMinor) || !safe(addMinor)) {
      return Over(limitMinor, balanceMinor, addMinor, Long.MAX_VALUE)
    }
    //  هر دو در ±(2^53−1)‌اند، پس جمع در Long سرریز نمی‌کند
    val after = balanceMinor + addMinor
    return if (after > limitMinor) Over(limitMinor, balanceMinor, addMinor, after) else null
  }

  /** خالی ⇒ صاحبِ دکان (همان قاعدهٔ `ShopRole.current`)؛ نقشِ ناشناخته ⇒ نه */
  fun canApprove(role: String?): Boolean {
    val r = role?.trim()?.lowercase().orEmpty()
    return r.isEmpty() || r in APPROVER_ROLES
  }

  fun state(over: Over?, debtorId: String, approval: Approval?): State {
    if (over == null) return State.FREE
    val a = approval ?: return State.NEEDS_APPROVAL
    val covers = a.by.isNotBlank() && a.reason.isNotBlank() && canApprove(a.role) &&
      a.debtorId == debtorId && a.addMinor >= over.addMinor
    return if (covers) State.APPROVED else State.NEEDS_APPROVAL
  }
}
