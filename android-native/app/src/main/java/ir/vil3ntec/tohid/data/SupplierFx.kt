package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.purchase.PurchaseFx

/**
 *  PUR-11 (تصمیمِ D-15 ب، ۱۴۰۵/۰۸/۲۶) — حسابِ ارزیِ تأمین‌کننده روی همان دو فهرستِ دفتر (`purchases`، `supplierPayments`).
 *
 *  • خریدِ ارزی همان ردیف‌های خرید است با فیلدهای افزودنیِ ارز (`Purchase.currency`/`fx…`)؛ `totalAmount`/`paidAmount` معادلِ
 *    افغانیِ نرخِ همان سند‌اند، پس انبار، بهای تمام‌شده، بدهیِ افغانیِ دفتر (`SupplierDebts`) و گزارش‌ها بی تغییرِ راه درست‌اند.
 *  • ماندهٔ هر ارز ([accounts]) = جمعِ ارزیِ خریدها − پرداختِ هنگامِ خرید − پرداخت‌های همان ارز؛ ارزشِ دفتری = همان جمع‌ها به
 *    افغانیِ ثبت‌شده. پرداخت ([pay]) به همان ارز یا به افغانی، با نرخِ خودش (`PurchaseFx.payInFx`/`payInAfn`)؛ رکوردِ پرداخت
 *    `amount` = ارزشِ دفتریِ برداشته‌شده و اختلافِ نرخ را ثابت نگه می‌دارد.
 *  • دخلِ افغانی ([drawerOut]، [drawerPaid]): پولی که به ارز داده شد از دخلِ افغانی نیست؛ پرداختِ حسابِ ارزی به افغانی همان
 *    پولِ واقعاً داده‌شده است. ردیفِ پیشین (بی ارز) بیت‌به‌بیت همان `amount`/`paidAmount`.
 *  خالص (نه Context، نه ساعت).
 */
object SupplierFx {

  /** چپ‌به‌راستِ جدا (LRI…PDI) — مبلغِ ارزی و خطِ نرخ در جملهٔ راست‌به‌چپ از هم نپاشند («۱۲٫۵۰ USD» نه «USD ۱۲٫۵۰») */
  fun ltr(s: String): String = "\u2066$s\u2069"

  /** مبلغِ ارزی برای صفحه (`SafeCurrencies.format`، جدا) */
  fun text(minor: Long, code: String): String = ltr(SafeCurrencies.format(minor, code))

  /** خطِ نرخ برای صفحه («1 USD = 70.25 AFN»، جدا) */
  fun rateText(r: PurchaseFx.Rate): String = ltr(PurchaseFx.rateLine(r))

  /** ارزِ ردیف: خالی یا افغانی ⇒ null (حسابِ افغانی، همهٔ ردیف‌های پیشین) */
  fun cur(c: String?): String? = c?.trim()?.takeIf { it.isNotEmpty() && it != PurchaseFx.AFN }

  fun rateOf(p: Purchase): PurchaseFx.Rate? = cur(p.currency)?.let { PurchaseFx.rate(it, p.fxRate, p.fxPer) }

  fun rateOf(p: SupplierPayment): PurchaseFx.Rate? = cur(p.currency)?.let { PurchaseFx.rate(it, p.fxRate, p.fxPer) }

  private fun minor(v: Double): Long = StockLedger.minor(v)

  /** حسابِ ارزِ [currency] ِ تأمین‌کنندهٔ [supplierId] (هیچ سند ⇒ صفر) */
  fun account(d: ShopData, supplierId: String, currency: String): PurchaseFx.Account {
    var fx = 0L
    var afn = 0L
    for (p in d.purchases) {
      if (p.supplierId != supplierId || cur(p.currency) != currency) continue
      fx += (p.fxTotalMinor ?: 0L) - (p.fxPaidMinor ?: 0L)
      afn += minor(p.totalAmount) - minor(p.paidAmount)
    }
    for (x in d.supplierPayments) {
      if (x.supplierId != supplierId || cur(x.currency) != currency) continue
      fx -= x.fxAmountMinor ?: 0L
      afn -= minor(x.amount)
    }
    return PurchaseFx.Account(currency, fx, afn)
  }

  /** همهٔ حساب‌های ارزیِ یک تأمین‌کننده (هر ارزی که سندی دارد)، به ترتیبِ کد */
  fun accounts(d: ShopData, supplierId: String): List<PurchaseFx.Account> {
    val codes = sortedSetOf<String>()
    d.purchases.forEach { if (it.supplierId == supplierId) cur(it.currency)?.let(codes::add) }
    d.supplierPayments.forEach { if (it.supplierId == supplierId) cur(it.currency)?.let(codes::add) }
    return codes.map { account(d, supplierId, it) }
  }

  /** پولِ نقدِ افغانی که این پرداخت از دخل برد: ردیفِ پیشین ⇒ `amount`؛ حسابِ ارزی به افغانی ⇒ همان پول؛ به ارز ⇒ صفر */
  fun drawerOut(p: SupplierPayment): Double = when {
    cur(p.currency) == null -> p.amount
    p.afnPaidMinor != null -> p.afnPaidMinor / 100.0
    else -> 0.0
  }

  /** پرداختِ هنگامِ خرید از دخلِ افغانی: سندِ ارزی به همان ارز پرداخت شد ⇒ صفر؛ پیشین ⇒ `paidAmount` */
  fun drawerPaid(p: Purchase): Double = if (cur(p.currency) != null) 0.0 else p.paidAmount

  /** جمعِ اختلافِ نرخِ پرداخت‌های بازهٔ [from]..[to] (`yyyy-MM-dd`، پول؛ مثبت = زیان، منفی = سود) */
  fun diff(d: ShopData, from: String, to: String, supplierId: String? = null): Long =
    d.supplierPayments.filter { (supplierId == null || it.supplierId == supplierId) && it.date in from..to }.sumOf { it.fxDiffMinor ?: 0L }

  /** ارزهایی که دفترِ خرید دارد (برای کادرِ انتخابِ ارز، کنارِ ارزهای گاوصندوق) */
  fun inLedger(d: ShopData): List<String> {
    val out = sortedSetOf<String>()
    d.purchases.forEach { cur(it.currency)?.let(out::add) }
    d.supplierPayments.forEach { cur(it.currency)?.let(out::add) }
    return out.toList()
  }

  /**
   *  ارزهای کادرِ فاکتورِ **تازه**: کلیدِ دکانِ «خرید به ارزِ دیگر» (`PurchaseFxPref`) خاموش ⇒ فقط افغانی (کادر پنهان، همان
   *  فرمِ پیشین). حساب، فاکتور و پرداختِ ارزیِ پیشین از این نمی‌گذرند و همیشه دیده و پرداخت می‌شوند.
   */
  fun offered(on: Boolean, all: List<String>): List<String> = if (on) all else listOf(PurchaseFx.AFN)

  /** آخرین نرخِ ثبت‌شدهٔ [currency] در خرید یا پرداخت (پیش‌پُرِ کادرِ نرخ)؛ نبود ⇒ null */
  fun lastRate(d: ShopData, currency: String): PurchaseFx.Rate? {
    val a = d.purchases.filter { cur(it.currency) == currency && rateOf(it) != null }.maxByOrNull { it.createdAt }
    val b = d.supplierPayments.filter { cur(it.currency) == currency && rateOf(it) != null }.maxByOrNull { it.createdAt }
    return when {
      a == null -> b?.let(::rateOf)
      b == null -> rateOf(a)
      b.createdAt > a.createdAt -> rateOf(b)
      else -> rateOf(a)
    }
  }

  /**
   *  نرخِ پیش‌پُرِ ارزِ [currency] در فاکتور و پرداختِ خرید: آخرین نرخِ ثبت‌شدهٔ دفتر ([lastRate])، وگرنه نرخِ همان ارز در جدولِ
   *  صندوقِ PAY-10 ([till]، `FxTillPref.rates`)؛ هیچ ⇒ null (کادرِ نرخ خالی).
   */
  fun defaultRate(d: ShopData, currency: String, till: List<ir.vil3ntec.tohid.core.pay.FxRate> = emptyList()): PurchaseFx.Rate? =
    lastRate(d, currency) ?: till.firstOrNull { it.currency == currency }?.let { PurchaseFx.rate(currency, it.rate, it.per) }

  /** نرخ و فیِ آخرین خریدِ ارزیِ [productId] از [supplierId] — پیش‌فرضِ برگشت به همان ارز؛ نبود ⇒ null */
  fun returnRate(d: ShopData, supplierId: String, productId: String): Purchase? =
    d.purchases.filter { it.supplierId == supplierId && it.productId == productId && it.quantity > 0 && rateOf(it) != null }
      .maxByOrNull { it.createdAt }

  /**
   *  پرداخت به حسابِ [currency] ِ تأمین‌کننده: [amountMinor] به واحدِ کوچکِ همان ارز، یا با [inAfn] به پول؛ نرخِ [rate] ِ
   *  **همین پرداخت**. اثر: یک `SupplierPayment` با `amount` = ارزشِ دفتریِ برداشته‌شده و فیلدهای ارز و اختلافِ نرخ، و سابقه.
   *  همان کلیدِ یکتای [LedgerEngine.paySupplier] (دو لمس یک پرداخت).
   */
  fun pay(
    d: ShopData,
    supplierId: String,
    currency: String,
    amountMinor: Long,
    inAfn: Boolean,
    rate: PurchaseFx.Rate,
    date: String,
    notes: String,
    today: String,
    now: Long,
    newId: () -> String,
    key: String? = null,
  ): LedgerEngine.Result {
    val k = key?.trim()?.ifEmpty { null }
    if (k != null && d.supplierPayments.any { it.id == k }) return LedgerEngine.Result.Failed(LedgerEngine.ALREADY_PAID)
    val supplier = d.suppliers.find { it.id == supplierId } ?: return LedgerEngine.Result.Failed(StrLedger.supplierNotFound)
    val code = cur(currency) ?: return LedgerEngine.Result.Failed(StrLedger.amountBad)
    if (rate.currency != code) return LedgerEngine.Result.Failed(StrLedger.amountBad)
    val acc = account(d, supplierId, code)
    val s = (if (inAfn) PurchaseFx.payInAfn(acc, amountMinor, rate) else PurchaseFx.payInFx(acc, amountMinor, rate))
      ?: return LedgerEngine.Result.Failed(StrLedger.amountBad)
    val id = k ?: newId()
    val when_ = date.ifBlank { today }
    val fxText = SafeCurrencies.format(s.fx, code)
    val diffText = if (s.diff == 0L) "" else "، اختلافِ نرخ ${ir.vil3ntec.tohid.money(s.diff / 100.0)} افغانی"  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    return LedgerEngine.Result.Ok(
      d.copy(
        supplierPayments = d.supplierPayments + SupplierPayment(
          id = id,
          supplierId = supplierId,
          amount = s.carried / 100.0,
          date = when_,
          notes = notes.trim(),
          createdAt = now,
          currency = code,
          fxAmountMinor = s.fx,
          fxRate = rate.rate,
          fxPer = rate.per,
          fxDiffMinor = s.diff.takeIf { it != 0L },
          afnPaidMinor = s.afnPaid,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = if (k != null) "$k-a" else newId(),
          type = "supplier_payment",
          date = when_,
          refId = id,
          notes = "پرداخت به «${supplier.name}» به مبلغ $fxText${s.afnPaid?.let { " (${ir.vil3ntec.tohid.money(it / 100.0)} افغانی)" }.orEmpty()} با نرخِ ${PurchaseFx.rateLine(rate)}$diffText",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          createdAt = now,
        ),
      ),
      id = id,
    )
  }
}
