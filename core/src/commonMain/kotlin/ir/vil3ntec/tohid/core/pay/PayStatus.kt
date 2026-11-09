package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.addExact

/**
 *  PAY-07 — وضعیتِ هر ردیفِ پرداختِ غیرنقدِ فاکتور: در انتظار، تأییدشده، ناموفق، لغو، نیازمند بررسی،
 *  بازپرداختِ کامل یا جزئی. وضعیت‌ها و گذارها همان [Pay.STATUSES] و [Pay.TRANSITIONS] اند (هم‌زادِ
 *  `license/pay-core.js`، بردارِ `constants.transitions` ِ `core/test-vectors/pay-v1.json`).
 *
 *  ⛔ فاکتور دست نمی‌خورد (`payments` در `SALE_FIXED`، `shop.doc_chain`). هر تغییرِ وضعیت یک **سندِ جدا**ست —
 *  ردیفِ فقط‌افزودنیِ سابقهٔ `pay_status` با فاکتور، شمارهٔ ردیف، روشِ همان ردیف، وضعیتِ پیشین و تازه، `by` —
 *  و مثلِ بقیهٔ سابقه همگام می‌شود. وضعیتِ همین لحظهٔ هر ردیف را [apply] از ردیفِ ثبت‌شده و همین سندها می‌سازد.
 *
 *  قاعده‌ها:
 *   • ⛔ صندوق ساده می‌ماند (صاحب‌کار): ردیفِ «ثبتِ دستی» (`pending` + `verification = manual` ِ ذخیره‌شده، PAY-08)
 *     **«تأییدشده»** خوانده و شمرده می‌شود ([initial]) — صندوقدار آن را زد چون پول رسید؛ هیچ گامِ اجباریِ تازه‌ای
 *     نیست و جمعِ X/Z همان امروز است. «در انتظار» فقط جایی است که جریان واقعاً منتظر است (حساب‌پیِ «ساده» پیش از
 *     «پول رسید»، حساب‌پیِ «خودکار» تا وب‌هوکِ سرور).
 *   • فقط ردیفِ دستی ([manual]): کیف پول، کارت، حواله، دیگر — نه نقد (همیشه تأییدشده)، نه «به حساب»،
 *     نه اعتبار و کارتِ هدیه (دفترِ خودشان)، نه ردیفِ تأییدِ سرور (`gateway`/`provider`: حساب‌پیِ خودکار؛ سرور
 *     مرجع است)، نه امانتِ پیک. فاکتورِ لغوشده و اینترنتی نه.
 *   • گذارها: جدولِ هسته محدود به [TARGETS]، به‌علاوهٔ [CORRECTIONS] (اصلاحِ ثبتِ «تأییدشده» به «نیازمند بررسی»،
 *     «ناموفق» یا «لغو»). بازپرداخت از راهِ برگشت می‌آید ([withRefunds])، نه از دکمه.
 *   • اجازه ([need]): «در انتظار» ⇒ «تأییدشده» یا «نیازمند بررسی» = صندوق (هر نقشی که فروش می‌نویسد، [SELL]);
 *     هر گذارِ دیگر (اصلاحِ تأییدشده، ناموفق، لغو، حلِ «نیازمند بررسی») فقط صاحب و مدیر ([MANAGE]). سرور همین را
 *     در `server/src/lib/doc-roles.js` (پرچمِ `shop.sync.doc_roles`) می‌سنجد.
 *   • سند `from` دارد (مقایسه و جایگزینی): دو دستگاهِ آفلاین ⇒ [apply] به ترتیبِ (زمان، شناسه)، و سندی که
 *     `from` اش دیگر وضعیتِ همان ردیف نیست کنار می‌رود (`skipped`) — بی وابستگی به ترتیبِ رسیدن.
 *   • پولِ دریافتی فقط [RECEIVED]؛ «در انتظار»، «ناموفق»، «لغو» و «نیازمند بررسی» هرگز در X/Z و گزارش پولِ
 *     گرفته‌شده نیستند ([unreceived]).
 *
 *  کاتلینِ خالص (commonMain)، بی هم‌زادِ JS (مثلِ `ShiftReview`): وب این ردیف را نمی‌سازد.
 */
object PayStatus {
  const val TYPE = "pay_status"
  const val NOTE_MAX = 120

  const val PENDING = "pending"
  const val CONFIRMED = "confirmed"
  const val FAILED = "failed"
  const val CANCELLED = "cancelled"
  const val NEEDS_REVIEW = "needs_review"
  const val REFUNDED = "refunded"
  const val PARTIAL = "partially_refunded"

  /** اجازهٔ لازم ([need]) — همان نام‌های `doc-roles.js` */
  const val SELL = "sell"
  const val MANAGE = "manage"

  /** پولِ گرفته‌شده (بازپرداخت جدا، از رویدادِ برگشت، کم می‌شود) */
  val RECEIVED: Set<String> = setOf(CONFIRMED, PARTIAL, REFUNDED)

  /** آنچه دستی گذاشته می‌شود */
  val TARGETS: Set<String> = setOf(CONFIRMED, FAILED, CANCELLED, NEEDS_REVIEW)

  /** نوع‌های «ثبتِ دستی» (PAY-08: `verification = manual`) */
  val MANUAL_KINDS: Set<String> = setOf("wallet", "card", "transfer", "other")

  /** تأییدِ سرور (درگاه/ارائه‌دهنده) — وضعیتش را فقط سرور عوض می‌کند */
  val SERVER_VERIFIED: Set<String> = setOf("gateway", "provider")

  /** سطحِ «ثبتِ دستی» ِ PAY-08 — صندوقدار پولِ رسیده را زد */
  const val MANUAL = "manual"

  /**
   *  اصلاح‌های دستی بیرون از جدولِ چرخهٔ درگاه (`Pay.TRANSITIONS`): ثبتِ «تأییدشده» را صاحب/مدیر بعداً به «نیازمند
   *  بررسی»، «ناموفق» یا «لغو» می‌برد (و از جمعِ دریافتی بیرون می‌رود).
   */
  val CORRECTIONS: Map<String, List<String>> = mapOf(CONFIRMED to listOf(NEEDS_REVIEW, FAILED, CANCELLED))

  /** یک ردیفِ پرداختِ فاکتور (به پول) — همان `SalePayment` ِ برنامه */
  data class Row(
    val method: String,
    val kind: String,
    val amount: Long,
    val status: String,
    val verification: String? = null,
    val custody: String? = null,
  )

  /** سندِ ثبت‌شدهٔ تغییرِ وضعیت؛ [index] جای ردیف در `payments` ِ فاکتور */
  data class Change(
    val id: String,
    val saleId: String,
    val index: Int,
    val method: String,
    val from: String,
    val to: String,
    val at: Long,
    val by: String,
    val note: String? = null,
  )

  data class Applied(val statuses: List<String>, val applied: List<String>, val skipped: List<String>)

  data class Input(
    val id: String,
    val saleId: String,
    val rows: List<Row>,
    val index: Int,
    val to: String,
    val at: Long,
    val by: String?,
    /** نقش فروش می‌نویسد (`canWrite(sales)`) */
    val sell: Boolean,
    /** صاحب یا مدیر */
    val manage: Boolean,
    val note: String? = null,
    val cancelled: Boolean = false,
    val online: Boolean = false,
    /** همهٔ سندهای ثبت‌شده (از هر دستگاه)؛ فقط همین فاکتور شمرده می‌شود */
    val existing: List<Change> = emptyList(),
  )

  /** وضعیتِ آغازینِ ردیف: ثبتِ دستیِ صندوقدار (`pending` + `manual`) ⇒ «تأییدشده»؛ بقیه همان ذخیره‌شده */
  fun initial(r: Row): String = if (r.status == PENDING && r.verification == MANUAL) CONFIRMED else r.status

  /** گذارِ دستیِ روا: مقصدِ دستی، و جدولِ هسته یا [CORRECTIONS] */
  fun canMove(from: String, to: String): Boolean =
    to in TARGETS && (Pay.canTransition(from, to) || CORRECTIONS[from]?.contains(to) == true)

  /** ردیفی که وضعیتش دستی عوض می‌شود */
  fun manual(r: Row): Boolean =
    r.kind in MANUAL_KINDS && r.verification !in SERVER_VERIFIED && r.custody == null && r.amount > 0

  /** اجازهٔ لازم برای گذارِ [from] ⇒ [to] */
  fun need(from: String, to: String): String =
    if (from == PENDING && (to == CONFIRMED || to == NEEDS_REVIEW)) SELL else MANAGE

  /** گذارهایی که این کاربر از [from] می‌تواند بگذارد */
  fun allowed(from: String, sell: Boolean, manage: Boolean): List<String> =
    (Pay.TRANSITIONS[from].orEmpty() + CORRECTIONS[from].orEmpty()).distinct()
      .filter { canMove(from, it) && if (need(from, it) == SELL) sell else manage }

  /**
   *  وضعیتِ همین لحظهٔ هر ردیف پس از سندهای ثبت‌شده تا [until]، به ترتیبِ (زمان، شناسه). سندِ نامعتبر — ردیفِ
   *  نبوده یا روشِ دیگر، ردیفِ نه-دستی، مقصدِ نادستی، `from` ِ کهنه یا گذارِ ناروا — کنار می‌رود.
   */
  fun apply(saleId: String, rows: List<Row>, changes: List<Change>, until: Long = Long.MAX_VALUE): Applied {
    val st = rows.map(::initial).toMutableList()
    val applied = ArrayList<String>()
    val skipped = ArrayList<String>()
    val seen = HashSet<String>()
    changes.filter { it.saleId == saleId && it.at <= until }
      .sortedWith(compareBy<Change>({ it.at }, { it.id }))
      .forEach { c ->
        if (!seen.add(c.id)) return@forEach
        val row = rows.getOrNull(c.index)
        val ok = row != null && row.method == c.method && manual(row) && st[c.index] == c.from && canMove(c.from, c.to)
        if (ok) { st[c.index] = c.to; applied += c.id } else skipped += c.id
      }
    return Applied(st, applied, skipped)
  }

  /**
   *  بازپرداختِ کامل یا جزئی از راهِ برگشت: [refunded] = آنچه به هر روش پس رفت (به پول). مثلِ `ReturnFlow`،
   *  پس‌داده به ترتیبِ ردیف‌ها میانِ ردیف‌های همان روش پخش می‌شود؛ فقط ردیفِ تأییدشده (یا نیمه‌پس‌داده) عوض می‌شود.
   */
  fun withRefunds(rows: List<Row>, statuses: List<String>, refunded: Map<String, Long>): List<String> {
    if (refunded.values.none { it > 0 }) return statuses
    val left = HashMap(refunded)
    return rows.mapIndexed { i, r ->
      val st = statuses[i]
      if (st != CONFIRMED && st != PARTIAL) return@mapIndexed st
      val back = minOf(left[r.method] ?: 0L, r.amount)
      if (back <= 0) return@mapIndexed st
      left[r.method] = (left[r.method] ?: 0L) - back
      if (back >= r.amount) REFUNDED else PARTIAL
    }
  }

  /**
   *  PAY-23 — وضعیتی که `Pay.refund` برای هر ردیف می‌بیند: پولِ رسیده «تأییدشده» (نیمه‌پس‌داده هم؛ باقی‌اش از
   *  `refunded` ِ ردیف) و هر چیزِ دیگر همان‌طور که هست، پس پس‌ندادنی. «رسیده» همان [unreceived] ِ X/Z است: امانتِ
   *  پیک (`custody`) پولِ دکان است؛ «در انتظار» ِ واقعی تا «پول رسید»، و «ناموفق»/«لغو»/«نیازمند بررسی» نه.
   *  [online] = فاکتورِ سفارشِ اینترنتی: بیرون از وضعیتِ دستی است و درگاه/سرور مرجع (ORD-15) ⇒ «در انتظار» همان امروز گرفته‌شده.
   */
  fun forRefund(rows: List<Row>, statuses: List<String>, online: Boolean = false): List<String> = rows.mapIndexed { i, r ->
    val st = statuses[i]
    if (st == CONFIRMED || st == PARTIAL || (st == PENDING && (online || r.custody != null))) CONFIRMED else st
  }

  /**
   *  PAY-23 — جای ماندهٔ بازپرداختِ فاکتور وقتی ردیفی نرسیده: پولِ رسیده (ردیفِ «تأییدشده» پس از [forRefund]) منهای همهٔ
   *  برگشت‌های پیشینِ همان فاکتور ([priorMinor]، به پول). ردیفِ نقد پس‌داده‌اش را جدا نمی‌شمارد و تا امروز سقفِ فاکتور بس
   *  بود چون همه رسیده بود؛ با ردیفِ نارسیده نقدِ دخل از نقدِ رسیده بیشتر بیرون می‌رفت. همه رسیده ⇒ null (سقفِ امروز).
   */
  fun refundRoom(rows: List<PaidRow>, priorMinor: Long): Long? {
    if (rows.all { it.status == CONFIRMED }) return null
    return maxOf(0L, rows.filter { it.status == CONFIRMED }.fold(0L) { a, r -> addExact(a, r.amount) } - priorMinor)
  }

  /** مبلغِ نارسیدهٔ هر روش — ردیفِ دارای مبلغ، بی امانت، با وضعیتِ بیرون از [RECEIVED] */
  fun unreceived(rows: List<Row>, statuses: List<String>): Map<String, Long> {
    val out = LinkedHashMap<String, Long>()
    rows.forEachIndexed { i, r ->
      if (r.amount > 0 && r.custody == null && statuses[i] !in RECEIVED) out[r.method] = addExact(out[r.method] ?: 0L, r.amount)
    }
    return out
  }

  /** جمعِ هر وضعیت (به پول) — فقط ردیف‌های غیرنقدِ کیف پول/کارت/حواله/دیگر */
  fun totals(rows: List<Row>, statuses: List<String>): Map<String, Long> {
    val out = LinkedHashMap<String, Long>()
    rows.forEachIndexed { i, r ->
      if (r.kind in MANUAL_KINDS && r.amount > 0 && r.custody == null) out[statuses[i]] = addExact(out[statuses[i]] ?: 0L, r.amount)
    }
    return out
  }

  private val WORST = listOf(NEEDS_REVIEW, FAILED, PENDING, CANCELLED, PARTIAL, REFUNDED, CONFIRMED)

  /** وضعیتِ فاکتور برای تراشه: بدترینِ ردیف‌های غیرنقد؛ بی ردیفِ غیرنقد ⇒ null */
  fun summary(rows: List<Row>, statuses: List<String>): String? =
    rows.indices.filter { rows[it].kind in MANUAL_KINDS && rows[it].amount > 0 && rows[it].custody == null }
      .map { statuses[it] }
      .minByOrNull { s -> WORST.indexOf(s).let { if (it < 0) WORST.size else it } }

  private fun oneLine(s: String) = '\n' !in s && '\r' !in s

  fun check(input: Input): PayResult<Change> {
    val id = input.id.trim()
    if (id.isEmpty()) return PayResult.Err("ID_REQUIRED")
    val saleId = input.saleId.trim()
    if (saleId.isEmpty()) return PayResult.Err("SALE_REQUIRED")
    val by = input.by?.trim().orEmpty()
    val note = input.note?.trim().orEmpty()
    input.existing.firstOrNull { it.id == id }?.let { old ->
      val same = old.saleId == saleId && old.index == input.index && old.to == input.to && old.by == by && (old.note ?: "") == note
      return if (same) PayResult.Ok(old, duplicate = true) else PayResult.Err("ID_CONFLICT")
    }
    if (input.cancelled) return PayResult.Err("SALE_CANCELLED")
    if (input.online) return PayResult.Err("SALE_ONLINE")
    val row = input.rows.getOrNull(input.index) ?: return PayResult.Err("BAD_INDEX")
    if (!manual(row)) return PayResult.Err("NOT_MANUAL")
    if (input.to !in Pay.STATUSES) return PayResult.Err("BAD_STATUS")
    if (input.to !in TARGETS) return PayResult.Err("BAD_TRANSITION")
    val from = apply(saleId, input.rows, input.existing).statuses[input.index]
    if (!canMove(from, input.to)) return PayResult.Err("BAD_TRANSITION")
    val may = if (need(from, input.to) == SELL) input.sell else input.manage
    if (!may) return PayResult.Err("PERM_DENIED")
    if (by.isEmpty()) return PayResult.Err("BY_REQUIRED")
    if (note.length > NOTE_MAX || !oneLine(note)) return PayResult.Err("BAD_NOTE")
    if (!posExact(input.at)) return PayResult.Err("INVALID_TIME")
    return PayResult.Ok(Change(id, saleId, input.index, row.method, from, input.to, input.at, by, note.ifEmpty { null }))
  }
}
