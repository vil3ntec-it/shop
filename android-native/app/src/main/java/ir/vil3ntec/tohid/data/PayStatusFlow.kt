package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.core.pay.ShiftRow

/**
 *  PAY-07 — وضعیتِ پرداختِ ردیف‌های غیرنقدِ فاکتور: دفترِ دکان ⇄ هستهٔ `core/pay/PayStatus` (commonMain). خالص:
 *  نه Context، نه ساعت؛ ثبت فقط با [record] (دفترِ همین لحظهٔ دیسک، زیرِ قفلِ موتور — همان راهِ `PayFixPref.record`).
 *
 *  ⛔ فاکتور دست نمی‌خورد (`payments` سندِ ثبت‌شده است؛ S89/`shop.doc_chain`). تغییرِ وضعیت ردیفِ تازهٔ سابقه است —
 *  `type = pay_status`، `refId` = فاکتور، `payRow`/`payMethod`، `fromStatus`/`toStatus`، `amount` = مبلغِ ردیف
 *  (افغانی، مثلِ بقیهٔ سابقه)، `by` و `note` ِ اختیاری — و مثلِ بقیهٔ سابقه همگام می‌شود (جدولِ فقط‌افزودنیِ
 *  `auditLog`)؛ سرور نقش را در `doc-roles.js` می‌سنجد (`shop.sync.doc_roles`).
 *
 *  ── منبعِ هر وضعیت ───────────────────────────────────────────
 *   • نقد، «به حساب»، اعتبار و کارتِ هدیه: همیشه تأییدشده (ردیفِ ثبت‌شده).
 *   • حساب‌پیِ «خودکار» (`verification = gateway`): سرور مرجع است؛ برنامه فقط نشان می‌دهد.
 *   • حساب‌پیِ «ساده»: همان «پول رسید» ِ صندوقدار پیش از بستنِ فاکتور تأییدش کرده (`verification = cashier`).
 *   • ⛔ کارت/کیف پول/حوالهٔ ثبتِ دستی (`pending` + `manual` ِ ذخیره‌شده): **«تأییدشده»** (`PayStatus.initial`) —
 *     صندوقدار آن را زد چون پول رسید؛ هیچ گامِ اجباری نیست و جمعِ امروز همان است. فقط صاحب و مدیر بعداً آن را به
 *     «نیازمند بررسی»، «ناموفق» یا «لغو» می‌برند (و حلِ بررسی).
 *   • «در انتظار» فقط ردیفی که واقعاً منتظرِ تأیید است؛ «پول رسید» ِ صندوقدار آن را تأیید می‌کند.
 *   • بازپرداختِ کامل یا جزئی: از برگشت‌های همین فاکتور (`SaleReturn.toMethods`، همان راهِ `Pay.refund`).
 *
 *  ── پول ──────────────────────────────────────────────────────
 *  X/Z و پایانِ روز (`PayFlow.ledgerEvents` ⇐ [received]) فقط پولِ رسیده را می‌شمارند: ردیفی که «در انتظار»،
 *  «ناموفق»، «لغو» یا «نیازمند بررسی» است — تا [until] ِ همان پنجره — کنار می‌رود. دفترِ بی سندِ `pay_status` ⇒ همان
 *  جمعِ امروز، بایت‌به‌بایت. نقدِ دخل (`CashShift`) فقط ردیفِ نقد است و دست نمی‌خورد.
 */
object PayStatusFlow {

  /**
   *  PAY-07 (د۱۸): آینهٔ وضعیتِ بعدیِ حساب‌پیِ خودکار (فاکتور ⇐ روش ⇐ وضعیت؛ `core/pay/PayMirror`) — فقط نمایش ([view]،
   *  [chip]، [review])؛ X/Z و جمع‌ها ([totals]، [unreceived]) دست نمی‌خورند. پرچمِ خاموشِ `shop.pay_status_mirror` ⇒ خالی ⇒
   *  همان امروز. می‌نویسد فقط `PayMirrorAndroid` (پس از هر دورِ همگام‌سازی).
   */
  @Volatile var mirror: Map<String, Map<String, String>> = emptyMap()

  /** ردیفِ سابقه ⇒ سندِ هسته؛ ردیفِ ناقص ⇒ null */
  fun changeOf(a: AuditEntry): PayStatus.Change? {
    if (a.type != PayStatus.TYPE) return null
    val sale = a.refId?.takeIf { it.isNotEmpty() } ?: return null
    return PayStatus.Change(
      id = a.id, saleId = sale, index = a.payRow ?: return null, method = a.payMethod ?: return null,
      from = a.fromStatus ?: return null, to = a.toStatus ?: return null, at = a.createdAt, by = a.by.orEmpty(), note = a.note,
    )
  }

  /** سندهای هر فاکتور (از هر دستگاه)؛ دفترِ بی سند ⇒ نقشهٔ خالی */
  fun bySale(d: ShopData): Map<String, List<PayStatus.Change>> {
    if (d.auditLog.none { it.type == PayStatus.TYPE }) return emptyMap()
    return d.auditLog.mapNotNull(::changeOf).groupBy { it.saleId }
  }

  /** ردیف‌های ثبت‌شدهٔ فاکتور (به همان ترتیبِ `payments`) */
  fun rows(s: Sale): List<PayStatus.Row> =
    s.payments.orEmpty().map { PayStatus.Row(it.method, it.kind, it.amountMinor, it.status, it.verification, it.custody) }

  /** آنچه با برگشت‌های همین فاکتور به هر روش پس رفت (به پول) */
  fun refunded(d: ShopData, saleId: String): Map<String, Long> {
    val out = HashMap<String, Long>()
    d.saleReturns.forEach { r ->
      if (r.saleId == saleId) r.toMethods?.forEach { (m, a) -> out[m] = (out[m] ?: 0L) + PayFlow.minor(a) }
    }
    return out
  }

  /** وضعیتِ همین لحظهٔ هر ردیف — سندها، سپس بازپرداختِ برگشت‌ها */
  fun current(d: ShopData, s: Sale, changes: List<PayStatus.Change> = bySale(d)[s.id].orEmpty()): List<String> {
    val rows = rows(s)
    if (rows.isEmpty()) return emptyList()
    val st = PayStatus.withRefunds(rows, PayStatus.apply(s.id, rows, changes).statuses, refunded(d, s.id))
    return ir.vil3ntec.tohid.core.pay.PayMirror.overlay(rows, st, mirror[s.id].orEmpty())
  }

  /**
   *  مبلغِ نارسیدهٔ هر روشِ این فاکتور تا [until] — بیرون از پولِ دریافتیِ X/Z. فاکتورِ بی `payments` (صندوقِ امروز)
   *  هیچ ردیفِ غیرنقدی ندارد ⇒ نقشهٔ خالی.
   */
  fun unreceived(s: Sale, changes: List<PayStatus.Change>, until: Long = Long.MAX_VALUE): Map<String, Long> {
    val rows = rows(s)
    if (rows.isEmpty()) return emptyMap()
    return PayStatus.unreceived(rows, PayStatus.apply(s.id, rows, changes, until).statuses)
  }

  /** ردیف‌های پولِ دریافتیِ فاکتور: [rows] (پس از اصلاحِ روش) منهای نارسیدهٔ هر روش، از نخستین ردیفِ همان روش */
  fun received(rows: List<ShiftRow>, out: Map<String, Long>): List<ShiftRow> {
    if (out.isEmpty()) return rows
    val left = HashMap(out)
    return rows.mapNotNull { r ->
      val cut = minOf(left[r.method] ?: 0L, r.amount)
      if (cut > 0) left[r.method] = (left[r.method] ?: 0L) - cut
      (r.amount - cut).takeIf { it > 0 }?.let { ShiftRow(r.method, it) }
    }
  }

  /** یک ردیفِ غیرنقدِ فاکتور برای صفحه */
  data class RowView(val index: Int, val method: String, val amount: Long, val status: String, val manual: Boolean)

  /** ردیف‌های کیف پول/کارت/حواله/دیگرِ فاکتور با وضعیتِ همین لحظه؛ فاکتورِ اینترنتی ⇒ هیچ (پولش نزدِ درگاه/پیک است) */
  fun view(d: ShopData, s: Sale, changes: List<PayStatus.Change> = bySale(d)[s.id].orEmpty()): List<RowView> {
    if (s.isOnlineOrder()) return emptyList()
    val rows = rows(s)
    val st = current(d, s, changes)
    return rows.indices.filter { rows[it].kind in PayStatus.MANUAL_KINDS && rows[it].amount > 0 && rows[it].custody == null }
      .map { RowView(it, rows[it].method, rows[it].amount, st[it], PayStatus.manual(rows[it]) && s.status != "cancelled") }
  }

  /** وضعیتِ فاکتور برای تراشهٔ فهرست — فقط وقتی «تأییدشده» نیست (فهرستِ آرام)؛ بی ردیفِ غیرنقد ⇒ null */
  fun chip(d: ShopData, s: Sale, changes: List<PayStatus.Change> = bySale(d)[s.id].orEmpty()): String? {
    if (s.isOnlineOrder() || s.status == "cancelled" || s.payments.isNullOrEmpty()) return null
    return PayStatus.summary(rows(s), current(d, s, changes))?.takeIf { it != PayStatus.CONFIRMED }
  }

  /**
   *  PAY-07 (کامپیوتر، `DeskInvoices`): ردیف‌های غیرنقدِ فاکتور با گذارهایی که همین کاربر دارد — همان [view] و
   *  `PayStatus.allowed` ِ برگهٔ گوشی؛ ردیفِ درگاه، فاکتورِ لغوشده یا اینترنتی ⇒ بی گذار. ثبتِ دستی «تأییدشده» است و
   *  فقط صاحب و مدیر آن را به «نیازمند بررسی»/«ناموفق»/«لغو» می‌برند؛ ثبت همان [record].
   */
  data class DeskRow(val view: RowView, val targets: List<String>)

  fun desk(d: ShopData, s: Sale, sell: Boolean, manage: Boolean, changes: List<PayStatus.Change> = bySale(d)[s.id].orEmpty()): List<DeskRow> =
    view(d, s, changes).map { DeskRow(it, if (it.manual) PayStatus.allowed(it.status, sell, manage) else emptyList()) }

  /** «نیازمند بررسی»: هر ردیفِ زنده با همین وضعیت، تازه‌ترین فاکتور اول */
  data class ReviewItem(val sale: Sale, val row: RowView)

  fun review(d: ShopData): List<ReviewItem> {
    val all = bySale(d)
    val mirrored = mirror
    if (all.isEmpty() && mirrored.isEmpty()) return emptyList()
    return d.sales.filter { (it.id in all || it.id in mirrored) && it.status != "cancelled" }
      .sortedByDescending { it.createdAt }
      .flatMap { s -> view(d, s, all[s.id].orEmpty()).filter { it.status == PayStatus.NEEDS_REVIEW }.map { ReviewItem(s, it) } }
  }

  /** جمعِ هر وضعیت (به پول) در پنجرهٔ [from]..[to] ِ ثبتِ فاکتور — «در انتظار»/«نیازمند بررسی» ِ صندوق */
  fun totals(d: ShopData, from: Long, to: Long): Map<String, Long> {
    val all = bySale(d)
    val out = LinkedHashMap<String, Long>()
    d.sales.forEach { s ->
      if (s.status == "cancelled" || s.isOnlineOrder() || s.createdAt !in from..to || s.payments.isNullOrEmpty()) return@forEach
      val rows = rows(s)
      PayStatus.totals(rows, PayStatus.apply(s.id, rows, all[s.id].orEmpty(), to).statuses)
        .forEach { (k, v) -> out[k] = (out[k] ?: 0L) + v }
    }
    return out
  }

  /** خواستهٔ صفحه؛ [sell] = نقش فروش می‌نویسد، [manage] = صاحب یا مدیر */
  data class Request(
    val saleId: String,
    val index: Int,
    val to: String,
    val by: String,
    val sell: Boolean,
    val manage: Boolean,
    val note: String? = null,
  )

  sealed interface Built {
    data class Ok(val row: AuditEntry, val change: PayStatus.Change) : Built
    data class Bad(val code: String) : Built
  }

  /** سنجشِ خواسته روی دفترِ [d] (دفترِ همین لحظه، زیرِ قفل) و ساختنِ ردیف با شناسهٔ [key] */
  fun build(d: ShopData, key: String, r: Request, today: String, now: Long): Built {
    val sale = d.sales.find { it.id == r.saleId } ?: return Built.Bad("SALE_REQUIRED")
    val input = PayStatus.Input(
      id = key, saleId = sale.id, rows = rows(sale), index = r.index, to = r.to, at = now, by = r.by,
      sell = r.sell, manage = r.manage, note = r.note, cancelled = sale.status == "cancelled", online = sale.isOnlineOrder(),
      existing = d.auditLog.mapNotNull(::changeOf),
    )
    return when (val c = PayStatus.check(input)) {
      is PayResult.Err -> Built.Bad(c.code)
      is PayResult.Ok -> Built.Ok(row(c.value, sale, today), c.value)
    }
  }

  /** نامِ فارسیِ وضعیت در متنِ سابقه (داده؛ همگام می‌شود و با زبانِ صفحه عوض نمی‌شود) */
  private val LOG_NAMES = mapOf(
    PayStatus.PENDING to "در انتظار", PayStatus.CONFIRMED to "تأییدشده", PayStatus.FAILED to "ناموفق",  // داده: نامِ حالت درونِ یادداشتِ سابقه
    PayStatus.CANCELLED to "لغو", PayStatus.NEEDS_REVIEW to "نیازمند بررسی",  // داده: نامِ حالت درونِ یادداشتِ سابقه
  )

  /** ردیفِ سابقهٔ یک تغییر */
  fun row(c: PayStatus.Change, sale: Sale, today: String): AuditEntry {
    val amount = sale.payments?.getOrNull(c.index)?.amountMinor ?: 0L
    return AuditEntry(
      id = c.id, type = PayStatus.TYPE, date = today, refId = c.saleId, amount = PayFlow.major(amount), createdAt = c.at,
      notes = "وضعیتِ پرداختِ فاکتور #${sale.invoiceLabel() ?: "0"}: ${PayFlow.methodName(c.method)} " +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        "${ir.vil3ntec.tohid.money(PayFlow.major(amount))} افغانی، ${LOG_NAMES[c.from] ?: c.from} ⇐ ${LOG_NAMES[c.to] ?: c.to}" +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        (c.note?.let { " — $it" } ?: ""),
      by = c.by, note = c.note, payRow = c.index, payMethod = c.method, fromStatus = c.from, toStatus = c.to,
    )
  }

  sealed interface Saved {
    /** نوشته شد؛ [again] ⇒ همین کلید پیش‌تر نشسته بود */
    data class Ok(val again: Boolean) : Saved
    /** ردِ هسته روی دفترِ همین لحظه (کدِ `PayStatus.check`) */
    data class Bad(val code: String) : Saved
    /** قفلِ نرم یا دیسک */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`) — نه حافظهٔ صفحه که پس از
   *  Pull ِ کارگر کهنه است؛ سنجش روی همان دفتر (سندِ دستگاهِ دیگر که همین حالا رسید). کلید = شناسهٔ ردیف ⇒ دو ضربه
   *  یک ردیف. ساعت از دستگاه.
   */
  suspend fun record(store: ShopStore, key: String, r: Request, today: String, now: Long): Saved {
    var bad: Built.Bad? = null
    val out = AppendOnce.append<ShopData>(
      key,
      { d, k -> d.auditLog.any { it.id == k } },
      { cur, k ->
        when (val b = build(cur, k, r, today, now)) {
          is Built.Ok -> cur.copy(auditLog = cur.auditLog + b.row)
          is Built.Bad -> { bad = b; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true)
      AppendOnce.Outcome.Skipped -> bad?.let { Saved.Bad(it.code) } ?: Saved.NotSaved
      AppendOnce.Outcome.Unread -> Saved.Bad("UNREAD")
      AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
