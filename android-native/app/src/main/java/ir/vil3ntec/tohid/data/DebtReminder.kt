package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money

/**
 *  یادآوریِ بدهی با قالب و زمان‌بندی (CRM-06).
 *
 *  ── قالب ─────────────────────────────────────────────────────────────
 *  متنِ امروزِ دکمه‌های «پیامک/واتس‌اپ» (`Debtors.kt` › `ReminderRow`) قالبِ پیش‌فرض
 *  است و `render` با آن **همان** جمله را می‌سازد (آزمونِ مرجعِ `DebtReminderTest`).
 *  جای‌نگهدارها: {نام} {مبلغ} {دکان} {سررسید} {روز}. جای‌نگهدارِ ناشناخته دست نمی‌خورد.
 *
 *  ── زمان‌بندی ─────────────────────────────────────────────────────────
 *  «امروز به چه کسی یادآوری شود»: مانده ≥ `minBalance` و (بدهیِ کهنه‌تر از `minDebtDays`
 *  یا بردی با سررسیدِ گذشته) و از آخرین یادآوری دست‌کم `everyDays` روز گذشته. «آخرین
 *  یادآوری» از دفترچهٔ ثبت (`debt_reminder`) خوانده می‌شود، پس فیلدِ تازه‌ای لازم نیست و
 *  با همگام‌سازی به دستگاه‌های دیگر هم می‌رسد.
 *
 *  ⛔ ارسالِ خودکار نیست: پیامک/واتس‌اپِ بیرونی به حساب و بودجه (D-09) بسته است.
 *  این‌جا فقط فهرست و متن ساخته می‌شود؛ فرستادن همان دکمهٔ دستیِ کاربر است.
 */
object DebtReminder {

  const val DEFAULT_TEMPLATE = "سلام {نام} عزیز، یادآوری می‌کنم که {مبلغ} افغانی از حساب شما باقی مانده. ممنون."
  /** همان متنِ امروز وقتی مانده‌ای نیست */
  const val NO_BALANCE_TEMPLATE = "سلام {نام} عزیز،"
  const val MAX_TEMPLATE_LENGTH = 600

  val PLACEHOLDERS: List<String> = listOf("نام", "مبلغ", "دکان", "سررسید", "روز")
  val CHANNELS: Map<String, String> = linkedMapOf("sms" to "پیامک", "whatsapp" to "واتس‌اپ", "call" to "زنگ")

  private val SLOT = Regex("""\{([^{}\s]{1,20})\}""")

  data class Vars(
    val name: String,
    val balance: Double,
    val store: String = "",
    /** yyyy-MM-dd؛ خالی یعنی سررسید ندارد */
    val dueDate: String = "",
    val debtDays: Long? = null,
  )

  /** متنِ نهایی. مبلغ با همان `money()` ِ برنامه؛ سررسید به خورشیدی */
  fun render(template: String, v: Vars): String = SLOT.replace(template) { m ->
    when (m.groupValues[1]) {
      "نام" -> v.name
      "مبلغ" -> money(v.balance)
      "دکان" -> v.store
      "سررسید" -> if (v.dueDate.isBlank()) "" else formatDate(v.dueDate)
      "روز" -> v.debtDays?.toString().orEmpty()
      else -> m.value
    }
  }

  /** متنِ امروزِ دکمه‌ها: مانده > ۰ ⇒ قالب؛ وگرنه فقط سلام */
  fun message(v: Vars, template: String = DEFAULT_TEMPLATE): String =
    render(if (v.balance > 0) template.ifBlank { DEFAULT_TEMPLATE } else NO_BALANCE_TEMPLATE, v)

  /** خطای قالب برای فرم — `null` یعنی درست */
  fun validate(template: String): String? {
    val t = template.trim()
    if (t.isEmpty()) return "متنِ یادآوری خالی است"
    if (t.length > MAX_TEMPLATE_LENGTH) return "متنِ یادآوری حداکثر $MAX_TEMPLATE_LENGTH حرف"
    val unknown = SLOT.findAll(t).map { it.groupValues[1] }.filter { it !in PLACEHOLDERS }.distinct().toList()
    if (unknown.isNotEmpty()) return "جای‌نگهدارِ ناشناخته: " + unknown.joinToString("، ") { "{$it}" }
    return null
  }

  /* ------------------------------ زمان‌بندی ------------------------------ */

  data class Rule(
    val minBalance: Double = 1.0,
    val minDebtDays: Long = 30,
    val everyDays: Int = 7,
  )

  data class Due(
    val debtor: Debtor,
    val balance: Double,
    val debtDays: Long?,
    /** کهنه‌ترین سررسیدِ گذشتهٔ بردهای باز — خالی اگر نیست */
    val overdueSince: String,
    val lastReminded: String?,
    val message: String,
  )

  /** تاریخِ آخرین یادآوریِ ثبت‌شده (yyyy-MM-dd) — یادآوریِ مشتریِ ادغام‌شده در همین یکی هم حساب است */
  fun lastReminded(d: ShopData, debtorId: String): String? {
    val ids = d.debtors.filter { it.mergedInto == debtorId }.mapTo(hashSetOf(debtorId)) { it.id }
    return d.auditLog.filter { it.type == TYPE && it.refId in ids && it.date.isNotBlank() }.maxOfOrNull { it.date }
  }

  private fun daysBetween(from: String, to: String): Long? {
    val a = ReportDates.epochDay(from) ?: return null
    val b = ReportDates.epochDay(to) ?: return null
    return b - a
  }

  /**
   *  فهرستِ امروز: سررسیدگذشته‌ها اول، سپس بدهکارترین. مشتریِ ادغام‌شده و مشتریِ بی شماره
   *  بیرون‌اند (یادآوری بی شماره فرستادنی نیست).
   */
  fun due(
    d: ShopData,
    today: String,
    rule: Rule = Rule(),
    template: String = DEFAULT_TEMPLATE,
    store: String = "",
    now: Long = System.currentTimeMillis(),
  ): List<Due> = CustomerCrm.active(d).mapNotNull { c ->
    if (CustomerCrm.phones(c).none { CustomerCrm.phoneKey(it) != null }) return@mapNotNull null
    val balance = ShopStore.debt(d, c.id)
    if (balance < rule.minBalance || balance <= 0) return@mapNotNull null
    val days = DebtorEngine.debtDays(d, c.id, now)
    val book = DebtBook.book(d, c.id, today)
    val overdue = book?.overdue?.mapNotNull { it.dueDate }?.minOrNull().orEmpty()
    if (overdue.isEmpty() && (days == null || days < rule.minDebtDays)) return@mapNotNull null
    val last = lastReminded(d, c.id)
    if (last != null) {
      val since = daysBetween(last, today)
      if (since != null && since < rule.everyDays) return@mapNotNull null
    }
    val due = overdue.ifEmpty { book?.nextDue?.dueDate.orEmpty() }
    Due(c, balance, days, overdue, last, message(Vars(c.name, balance, store, due, days), template))
  }.sortedWith(
    compareBy<Due> { it.overdueSince.isEmpty() }.thenByDescending { it.balance }.thenBy { it.debtor.id },
  )

  const val TYPE = "debt_reminder"

  /** «یادآوری فرستاده شد» — ردی در دفترچهٔ ثبت؛ همان که `due` برای فاصله می‌خواند */
  fun record(
    d: ShopData,
    debtorId: String,
    channel: String,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == debtorId } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    val via = CHANNELS[channel] ?: return DebtorEngine.Result.Failed("راهِ یادآوریِ ناشناخته")
    val trace = AuditEntry(
      id = newId(), type = TYPE, date = today, refId = debtorId, createdAt = now,
      notes = "یادآوریِ بدهی به «${c.name}» با $via — مانده ${money(ShopStore.debt(d, debtorId))} افغانی",
    )
    return DebtorEngine.Result.Ok(d.copy(auditLog = d.auditLog + trace), debtorId)
  }

  fun dueSheet(d: ShopData, today: String, rule: Rule = Rule(), template: String = DEFAULT_TEMPLATE, store: String = ""): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("نام", "شماره تماس", "مانده", "روزهای بدهی", "سررسیدِ گذشته", "آخرین یادآوری", "متنِ پیام"))
    due(d, today, rule, template, store).forEach { x ->
      rows += listOf(
        x.debtor.name, x.debtor.phone.ifBlank { x.debtor.phone2.orEmpty() }, Math.round(x.balance).toString(),
        x.debtDays?.toString().orEmpty(), x.overdueSince, x.lastReminded.orEmpty(), x.message,
      )
    }
    return ReportCsv.Sheet("یادآوری-بدهی-$today", rows)
  }
}
