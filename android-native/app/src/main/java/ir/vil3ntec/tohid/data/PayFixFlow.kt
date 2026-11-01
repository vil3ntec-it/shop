package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayFix
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.PayResult

/**
 *  POS-40 — اصلاحِ روشِ پرداخت پس از فروش: دفترِ دکان ⇄ هستهٔ `core/pay/PayFix` (commonMain). خالص: نه Context،
 *  نه ساعت، نه دیسک؛ ثبت فقط با `PayFixPref.record` (دفترِ همین لحظهٔ دیسک، زیرِ قفلِ موتور).
 *
 *  ⛔ فاکتور دست نمی‌خورد (`payments`، `paymentMethod`، `paidAmount` همان سندِ ثبت‌شده‌اند؛ S89/`shop.doc_chain`).
 *  اصلاح یک ردیفِ تازهٔ سابقه است — `type = pay_fix`، `refId` = فاکتور، `amount` = مبلغ (افغانی، مثلِ بقیهٔ صندوق)،
 *  `fromMethod`/`toMethod`/`payRef`، `by`، `approvedBy`، `reason` — و مثلِ بقیهٔ سابقه همگام می‌شود (جدولِ فقط‌افزودنیِ
 *  `auditLog`؛ سرور و همگام‌سازی عوض نمی‌شوند).
 *
 *  ── اثر در صندوق ───────────────────────────────────────────────
 *  ردیف داده است، پس مثلِ ورود/خروجِ نقد همیشه خوانده می‌شود: `CashShift.saleCash` (صندوقِ ساده) و
 *  `PayFlow.saleRows` (صندوقِ شیفت، X/Z و پایانِ روز) ردیف‌های فاکتور را با [PayFix.apply] می‌گیرند — فقط
 *  اصلاحی که تا پایانِ همان پنجره ثبت شد. دفترِ بی `pay_fix` ⇒ همان حسابِ امروز، بایت‌به‌بایت. نوشتن فقط با
 *  تنظیمِ روشنِ همین دستگاه (`PayFixPref`، پیش‌فرض خاموش). ⚠️ نسخهٔ پیشینِ برنامه این ردیف را نمی‌شمارد.
 */
object PayFixFlow {

  /** ردیفِ سابقه ⇒ سندِ هسته؛ ردیفِ ناقص (بی فاکتور یا روش) ⇒ null */
  fun fixOf(a: AuditEntry): PayFix.Fix? {
    if (a.type != PayFix.TYPE) return null
    val sale = a.refId?.takeIf { it.isNotEmpty() } ?: return null
    val from = a.fromMethod ?: return null
    val to = a.toMethod ?: return null
    return PayFix.Fix(
      id = a.id, saleId = sale, from = from, to = to, amount = PayFlow.minor(a.amount), at = a.createdAt,
      by = a.by.orEmpty(), approvedBy = a.approvedBy.orEmpty(), reason = a.reason.orEmpty(), ref = a.payRef,
    )
  }

  /** اصلاح‌های هر فاکتور (از هر دستگاه)؛ دفترِ بی اصلاح ⇒ نقشهٔ خالی و هیچ حسابِ تازه‌ای */
  fun bySale(d: ShopData): Map<String, List<PayFix.Fix>> {
    if (d.auditLog.none { it.type == PayFix.TYPE }) return emptyMap()
    return d.auditLog.mapNotNull(::fixOf).groupBy { it.saleId }
  }

  /** ردیف‌های ثبت‌شدهٔ فاکتور، بی اصلاح — همان `PayFlow.saleRows` ِ امروز */
  fun baseRows(s: Sale): List<PayFix.Row> = PayFlow.saleRows(s).map { PayFix.Row(it.method, it.amount) }

  /** ردیف‌های همین لحظهٔ فاکتور، پس از همهٔ اصلاح‌ها */
  fun current(d: ShopData, s: Sale): PayFix.Applied = PayFix.apply(s.id, baseRows(s), bySale(d)[s.id].orEmpty())

  /** اصلاح‌های همین فاکتور، به ترتیبِ ثبت */
  fun history(d: ShopData, saleId: String): List<PayFix.Fix> =
    bySale(d)[saleId].orEmpty().sortedWith(compareBy<PayFix.Fix>({ it.at }, { it.id }))

  /** روزِ فاکتور بسته است؟ پنجره‌ها از `CashShift.list` و روزِ هر کدام همان `date` ِ ردیفِ باز کردن */
  fun closed(d: ShopData, s: Sale): Boolean =
    PayFix.closed(s.createdAt, s.date, CashShift.list(d).map { PayFix.Window(it.open.createdAt, it.close?.createdAt, it.open.date) })

  fun info(d: ShopData, s: Sale): PayFix.SaleInfo = PayFix.SaleInfo(
    id = s.id, rows = baseRows(s), cancelled = s.status == "cancelled", online = s.isOnlineOrder(),
    returned = d.saleReturns.any { it.saleId == s.id },
  )

  /** روش‌هایی که اصلاح به آن‌ها می‌برد: نقد و روش‌های روشنِ شمردنی (کیف پول، کارت، حواله) — نه حساب، اعتبار و کارتِ هدیه */
  fun targets(): List<PayMethod> = Pay.DEFAULT_METHODS.filter { it.enabled && it.kind in TARGET_KINDS }
  private val TARGET_KINDS = setOf("cash", "wallet", "card", "transfer", "other")

  /** خواستهٔ صفحه؛ `decision` همان `PermCore.evaluate(…, sales.pay_fix)` ِ همین کاربر */
  data class Request(
    val saleId: String,
    val from: String,
    val to: String,
    val amountMinor: Long,
    val by: String,
    val decision: String,
    val approvedBy: String?,
    val reason: String,
    val ref: String?,
  )

  sealed interface Built {
    data class Ok(val row: AuditEntry, val fix: PayFix.Fix) : Built
    data class Bad(val code: String, val method: String? = null) : Built
  }

  /**
   *  سنجشِ خواسته روی دفترِ [d] (دفترِ همین لحظه، زیرِ قفل) و ساختنِ ردیف با شناسهٔ [key]. فاکتورِ نبوده ⇒
   *  `SALE_REQUIRED`. [now] ساعتِ دستگاه و [today] روزِ کاریِ همان لحظه را مرز می‌دهد.
   */
  fun build(d: ShopData, key: String, r: Request, today: String, now: Long): Built {
    val sale = d.sales.find { it.id == r.saleId } ?: return Built.Bad("SALE_REQUIRED")
    val input = PayFix.Input(
      id = key, sale = info(d, sale), from = r.from, to = r.to, amount = r.amountMinor, at = now, by = r.by,
      decision = r.decision, approvedBy = r.approvedBy, reason = r.reason, ref = r.ref, closed = closed(d, sale),
      existing = d.auditLog.mapNotNull(::fixOf),
    )
    return when (val c = PayFix.check(input)) {
      is PayResult.Err -> Built.Bad(c.code, c.method)
      is PayResult.Ok -> Built.Ok(row(c.value, sale, today), c.value)
    }
  }

  /** ردیفِ سابقهٔ یک اصلاح؛ `notes` متنِ سابقه است (داده؛ همگام می‌شود و با زبانِ صفحه عوض نمی‌شود) */
  fun row(f: PayFix.Fix, sale: Sale, today: String): AuditEntry = AuditEntry(
    id = f.id, type = PayFix.TYPE, date = today, refId = f.saleId, amount = PayFlow.major(f.amount), createdAt = f.at,
    notes = "اصلاحِ روشِ پرداختِ فاکتور #${sale.invoiceLabel() ?: "0"}: ${PayFlow.methodName(f.from)} ⇐ ${PayFlow.methodName(f.to)}، " +
      "${ir.vil3ntec.tohid.money(PayFlow.major(f.amount))} افغانی — ${f.reason}" +
      (if (f.approvedBy != f.by) " — تأیید: ${f.approvedBy}" else ""),
    by = f.by, approvedBy = f.approvedBy, reason = f.reason, fromMethod = f.from, toMethod = f.to, payRef = f.ref,
  )
}
