package ir.vil3ntec.tohid.core.perm

/**
 *  تأییدِ برگشت با «برگهٔ امضاشده» — RET-04، RET-10، STF-02 (S14).
 *
 *  همان قاعدهٔ سرور (`groupReturns` و `cumulativeNeed` در `server/src/lib/staff-approval.js`):
 *   • سندِ برگه `returns/<docId>` است و مبلغش جمعِ ردیف‌های **همین سند** به پول؛ برگه فقط همین
 *     سند را می‌پوشاند.
 *   • تصمیمِ «نیازمند تأیید» با سقفِ تجمعیِ فاکتور گرفته می‌شود: برگشت‌های پیشینِ همان فاکتور
 *     (از هر نقش) + همین سند. وگرنه یک برگشتِ بزرگ در چند تکهٔ زیرِ سقف می‌گذشت و برنامه
 *     «مجاز» می‌گفت در حالی که سرور سند را بی برگه رد می‌کرد.
 *   • برگه را فقط مدیر یا صاحب می‌دهد؛ بالای سقفِ نقش فقط صاحب (S14: «تأییدِ عادی بس نیست»).
 *
 *  فقط کمکی است: شکل و امضای برگه همان [ApprovalSlip] است و سدِ واقعی سرور.
 *  Kotlin خالص — بی android.*، بی java.*.
 */
object ReturnApprovals {

  const val PERM = "returns.create"

  /** نیازِ برگهٔ یک سندِ برگشت; [amountMinor] = جمعِ ردیف‌های همین سند به پول */
  fun need(docId: String, amountMinor: Long, at: Long? = null): ApprovalSlip.Need =
    ApprovalSlip.Need(PERM, "returns/$docId", null, amountMinor, at)

  /**
   *  مبلغِ تصمیمِ مجوز: برگشت‌های پیشینِ همان فاکتور + همین سند (به پول). مقدارِ منفی صفر
   *  حساب می‌شود و جمع از `Long.MAX_VALUE` نمی‌گذرد. برگشتِ بی رسید فاکتوری ندارد ⇒ `priorMinor = 0`.
   */
  fun cumulative(priorMinor: Long, thisMinor: Long): Long {
    val a = priorMinor.coerceAtLeast(0L)
    val b = thisMinor.coerceAtLeast(0L)
    return if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
  }

  /**
   *  سقفِ برگشتِ همین دستگاه (`return_limit`، برای مدیر و شاگرد؛ صاحب بیرون از آن): مثلِ سقفِ نقش
   *  تجمعیِ هر فاکتور است — برگشت‌های پیشینِ همان فاکتور + همین سند ([cumulative]). وگرنه برگشتِ بزرگ
   *  در چند سندِ زیرِ سقف زده می‌شد. [limitMinor] ≤ ۰ ⇒ بی سقف.
   */
  fun overDeviceLimit(priorMinor: Long, thisMinor: Long, limitMinor: Long): Boolean =
    limitMinor > 0 && cumulative(priorMinor, thisMinor) > limitMinor

  private val APPROVER_RANK = mapOf("manager" to 2, "owner" to 3)

  /**
   *  آیا تأییدکننده‌ای با نقشِ [approverRole] کاری را می‌پوشاند که دست‌کم [needed]
   *  (`manager` | `owner`) می‌خواهد؟ شاگرد، نقش‌های بی اختیارِ تأیید و نقشِ ناشناخته ⇒ نه؛
   *  [needed] ِ ناشناخته ⇒ فقط صاحب (بسته‌به‌خطا).
   */
  fun approverCovers(approverRole: String?, needed: String?): Boolean {
    val have = APPROVER_RANK[approverRole?.trim()?.lowercase()] ?: return false
    val want = APPROVER_RANK[needed?.trim()?.lowercase()] ?: APPROVER_RANK.getValue("owner")
    return have >= want
  }

  /**
   *  برگه همین سند را تا همین مبلغ می‌پوشاند؟ `null` یعنی بله، وگرنه کدِ خطای [ApprovalSlip.covers]
   *  (`approval_mismatch`، `approval_too_small`). زمان را سرور می‌سنجد.
   */
  fun covers(slip: ApprovalSlip.Slip, docId: String, amountMinor: Long): String? =
    ApprovalSlip.covers(slip, need(docId, amountMinor))
}
