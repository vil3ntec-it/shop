package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSaleCaps
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.perm.SaleCaps
import ir.vil3ntec.tohid.core.sale.PriceList
import kotlin.math.roundToLong

/**
 *  STF-03 — پیش‌سنجیِ صندوق برای سقف‌هایی که سرور می‌سنجد: قرضِ فاکتور (`sales.credit`)، تغییرِ قیمتِ ردیف (`sales.price`)،
 *  قلمِ آزاد (`sales.free_line`، تصمیمِ ۱-۱۱ (۲)) و «تخفیفِ پنهان» (`sales.discount` با کاهشِ ردیف و کمبودِ جمعِ نهایی؛ قیمتِ
 *  عمده فقط برای مشتریِ لیستِ عمده فهرست است — ۱-۱۱ (۳)). قاعده **همان** `core/perm/SaleCaps` است که سرور
 *  (`server/src/lib/sale-caps.js`) با آن داوری می‌کند، روی **همان** فاکتور و قلم‌هایی که فرستاده می‌شوند (پس از تخفیفِ
 *  خودکار و امتیاز). پس صندوق هیچ فاکتوری را نمی‌فرستد که سرور بی خبرِ صندوق‌دار رد کند:
 *   • «ممنوع» ⇒ پیام و هیچ ثبت؛ • «نیازمندِ تأیید» ⇒ «پرسیدن از مدیر» (STF-02) یا PIN ِ مدیرِ همین دستگاه، و برگه
 *     همراهِ فاکتور (`Sale.approval` / `priceApproval` / `freeLineApproval` / `creditApproval`)؛ • «مجاز» ⇒ همان امروز.
 *
 *  ⛔ فقط برای کلیدی که سرور برای همین دکان اجرا می‌کند ([Rules]؛ `policy.editable` ⇐ پرچم‌های `shop.credit_cap` و
 *  `shop.price_cap`). پرچم‌های خاموش ⇒ [Rules.active] نادرست و هیچ چیز عوض نمی‌شود. صاحبِ دکان هرگز.
 *  ⛔ پرچمِ قیمت خاموش ⇒ تخفیف همان `DiscountGate` ِ امروز است و این‌جا شمرده نمی‌شود.
 *
 *  Kotlinِ خالص (بی android.*): گوشیِ نسخهٔ ۳ (`CheckoutRoute`) و کامپیوتر (`DeskSale`) هر دو همین را می‌خوانند.
 */
object SaleCapGate {

  /** کاربرِ صندوق و آنچه سرور اجرا می‌کند */
  data class Rules(val role: String, val layers: List<Any?>, val price: Boolean, val credit: Boolean) {
    val active: Boolean get() = (price || credit) && role != ShopRole.OWNER

    companion object {
      val OFF = Rules(ShopRole.OWNER, emptyList(), price = false, credit = false)
    }
  }

  /** یک نیاز با تصمیمِ همان کاربر و حالش با برگه‌های همین فاکتور */
  data class Ask(val need: ApprovalSlip.Need, val decision: PermCore.Decision, val state: SaleCaps.State) {
    val perm: String get() = need.perm
  }

  data class Check(val asks: List<Ask>) {
    /** نخستین نیازی که ثبت را نگه می‌دارد (همان ترتیبِ داوریِ سرور) */
    val blocked: Ask? get() = asks.firstOrNull { it.state == SaleCaps.State.DENIED || it.state == SaleCaps.State.NEEDS_APPROVAL }
    val ok: Boolean get() = blocked == null
  }

  private fun minor(v: Double): Long = if (v.isFinite()) (v * 100).roundToLong() else 0L
  private fun milli(v: Double): Long = if (v.isFinite()) (v * 1000).roundToLong() else 0L

  /** قیمت‌های فهرستِ کالا — همان `sale-caps.refOf` ِ سرور */
  fun ref(p: Product): SaleCaps.Ref = SaleCaps.Ref(
    saleMinor = minor(p.salePrice),
    wholesaleMinor = minor(p.wholesalePrice),
    windows = p.priceWindows.orEmpty().map { SaleCaps.Window(it.from, it.to, minor(it.price)) },
    packs = p.packs.orEmpty().map { SaleCaps.Pack(it.sizeMilli, it.priceMinor ?: 0L) },
  )

  /**
   *  فاکتور و قلم‌هایش ⇒ ورودیِ هسته (همان `saleOf`/`lineOf` ِ سرور)؛ امتیاز از ردیفِ `loyalty_spend` ِ همان ثبت؛ لیستِ عمده
   *  فقط وقتی مشتریِ فاکتور لیستِ پیش‌فرضِ عمده دارد (`Debtor.priceList`، همان `sale-caps.isWholesaleDebtor` ِ سرور)
   */
  fun sale(s: Sale, audit: List<AuditEntry> = emptyList(), debtors: List<Debtor> = emptyList()): SaleCaps.Sale = SaleCaps.Sale(
    id = s.id, totalMinor = minor(s.total), discountMinor = minor(s.discount), finalTotalMinor = minor(s.finalTotal),
    roundingMinor = s.roundingMinor ?: 0L, debtGivenMinor = minor(s.debtGiven), date = s.date,
    loyaltyMinor = audit.filter { it.type == LoyaltyPoints.SPEND && it.refId == s.id }.sumOf { maxOf(0L, minor(it.amount)) },
    wholesale = s.debtorId?.let { id -> debtors.firstOrNull { it.id == id } }?.priceList?.trim() == PriceList.WHOLESALE,
    //  POS-37: همان `saleOf({ taxOn })` ِ سرور — مالیاتِ ثبت‌شده فقط با پرچمِ `shop.sale_tax` در پایهٔ تخفیف
    taxMinor = if (SaleTaxWiring.serverFlag) maxOf(0L, s.taxMinor ?: 0L) else 0L,
  )

  fun line(i: SaleItem): SaleCaps.Line =
    SaleCaps.Line(i.productId, minor(i.unitPrice), milli(i.quantity), i.promo.sumOf { maxOf(0L, it.discountMinor) })

  /**
   *  @param data دفترِ پس از ثبتِ (آزمایشیِ) همین فاکتور — همان که فرستاده می‌شود
   *  @param slips برگه‌های همین فاکتور به کلیدِ کار (`sales.discount`، `sales.price`، `sales.free_line`، `sales.credit`)
   */
  fun check(rules: Rules, data: ShopData, saleId: String, slips: Map<String, ApprovalSlip.Slip>): Check {
    if (!rules.active) return Check(emptyList())
    val s = data.sales.find { it.id == saleId } ?: return Check(emptyList())
    val lines = data.saleItems.filter { it.saleId == saleId }.map(::line)
    val ids = lines.mapTo(HashSet()) { it.productId }
    val refs = data.products.filter { it.id in ids }.associate { it.id to ref(it) }
    val needs = SaleCaps.needs(sale(s, data.auditLog, data.debtors), lines, refs, rules.price, rules.credit)
      .filter { rules.price || it.perm != SaleCaps.DISCOUNT }
    return Check(
      needs.map { n ->
        val d = PermCore.evaluate(rules.role, rules.layers, n.perm, SaleCaps.req(n))
        Ask(n, d, SaleCaps.state(d.decision, n, slips[n.perm]))
      },
    )
  }

  /** نتیجهٔ ثبت ⇒ همان، یا `Failed` اگر سرور ردش می‌کرد (آخرین سد پیش از نوشتنِ دفتر؛ صفحه پیش‌تر پرسیده است) */
  fun guard(rules: Rules, r: SalesEngine.Result, slips: Map<String, ApprovalSlip.Slip>): SalesEngine.Result {
    val ok = r as? SalesEngine.Result.Ok ?: return r
    val b = check(rules, ok.data, ok.saleId, slips).blocked ?: return r
    return SalesEngine.Result.Failed(message(b))
  }

  /** برگه‌های پذیرفته ⇒ JSON ِ فاکتور (همان `DiscountGate.json`؛ ارجاعِ «پرسیدن از مدیر» ⇒ `{v:1, request}`) */
  fun json(slips: Map<String, ApprovalSlip.Slip>, perm: String): kotlinx.serialization.json.JsonObject? = slips[perm]?.let(DiscountGate::json)

  fun title(perm: String): String = when (perm) {
    SaleCaps.CREDIT -> StrSaleCaps.creditTitle
    SaleCaps.PRICE -> StrSaleCaps.priceTitle
    SaleCaps.FREE_LINE -> StrSaleCaps.freeLineTitle
    else -> StrSaleCaps.hiddenTitle
  }

  private fun pct(bp: Long?): String = ir.vil3ntec.tohid.qty((bp ?: 0L) / 100.0)

  fun summary(a: Ask): String = when (a.perm) {
    SaleCaps.CREDIT -> StrSaleCaps.creditSummary(
      ir.vil3ntec.tohid.money((a.need.amount ?: 0L) / 100.0), ir.vil3ntec.tohid.money((a.decision.limit ?: 0.0) / 100.0),
    )
    SaleCaps.PRICE -> StrSaleCaps.priceSummary(pct(a.need.pctBp), ir.vil3ntec.tohid.qty(a.decision.limit ?: 0.0))
    SaleCaps.FREE_LINE -> StrSaleCaps.freeLineSummary(
      ir.vil3ntec.tohid.money((a.need.amount ?: 0L) / 100.0), ir.vil3ntec.tohid.money((a.decision.limit ?: 0.0) / 100.0),
    )
    else -> StrSaleCaps.hiddenSummary(ir.vil3ntec.tohid.money((a.need.amount ?: 0L) / 100.0), pct(a.need.pctBp))
  }

  fun denied(perm: String): String = when (perm) {
    SaleCaps.CREDIT -> StrSaleCaps.creditDenied
    SaleCaps.PRICE -> StrSaleCaps.priceDenied
    SaleCaps.FREE_LINE -> StrSaleCaps.freeLineDenied
    else -> StrSaleCaps.hiddenDenied
  }

  /** پیامِ کوتاهِ همان نیاز (رد، یا «تأییدِ مدیر لازم است») */
  fun message(a: Ask): String = if (a.state == SaleCaps.State.DENIED) denied(a.perm) else StrSaleCaps.needsApproval(title(a.perm))
}
