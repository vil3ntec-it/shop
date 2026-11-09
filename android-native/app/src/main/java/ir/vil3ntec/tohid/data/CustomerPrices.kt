package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrCustomerPrices
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.sale.CustomerPrice
import ir.vil3ntec.tohid.core.sale.PriceList
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 *  «قیمتِ خاصِ این مشتری» (POS-18) — نوشتنِ ردیف‌های `Debtor.specialPrices` با سابقه، خالص و آزمون‌پذیر
 *  (`CustomerPricesTest`). قاعدهٔ درستی، بازه و مجوز در هستهٔ `core/sale/CustomerPrice`؛ این‌جا فقط دفتر.
 *
 *  ⛔ هر افزودن، ویرایش یا حذف یک ردیفِ سابقه (`customer_price_set` / `customer_price_removed`) با نامِ کاربر (`by`)،
 *  کالا، قیمتِ پیشین و تازه دارد. بی تغییر ⇒ همان دفتر (همان شیء) و هیچ سابقه. مشتریِ ادغام‌شده نه.
 *  مجوز را صفحه پیش از باز کردن و این‌جا پیش از نوشتن می‌پرسد ([save]/[remove] ‹allowed›).
 */
object CustomerPrices {

  const val AUDIT_SET = "customer_price_set"
  const val AUDIT_REMOVE = "customer_price_removed"

  fun entries(debtor: Debtor?): List<CustomerPrice.Entry> =
    debtor?.specialPrices.orEmpty().map { CustomerPrice.Entry(it.productId, it.priceMinor, it.from, it.to) }

  fun minor(afn: Double): Long = (afn * 100.0).roundToLong()

  private fun refusal(code: String): String = when (code) {
    "product" -> StrCustomerPrices.pickProduct
    "price" -> StrCustomerPrices.badPrice
    "date" -> StrCustomerPrices.badDate
    "range" -> StrCustomerPrices.badRange
    "duplicate" -> StrCustomerPrices.duplicate
    "full" -> StrCustomerPrices.full(CustomerPrice.MAX_ENTRIES)
    else -> StrCustomerPrices.notSaved
  }

  private fun afn(minor: Long?): String = minor?.let { ir.vil3ntec.tohid.money(it / 100.0) } ?: "—"

  /**
   *  افزودن یا ویرایش: [replacing] = کالای ردیفی که ویرایش می‌شود (`null` ⇒ ردیفِ تازه). کالای تازهٔ ردیف می‌تواند
   *  همان یا کالای دیگری باشد؛ هر کالا یک ردیف.
   */
  fun save(
    d: ShopData,
    debtorId: String,
    replacing: String?,
    entry: SpecialPrice,
    allowed: Boolean,
    by: String?,
    today: String,
    now: Long,
    newId: () -> String,
  ): DebtorEngine.Result {
    if (!allowed) return DebtorEngine.Result.Failed(StrCustomerPrices.notAllowed)
    val c = d.debtors.find { it.id == debtorId } ?: return DebtorEngine.Result.Failed(StrLedger.customerNotFound)
    if (!CustomerCrm.isActive(c)) return DebtorEngine.Result.Failed(StrLedger.customerMerged)
    val clean = entry.copy(productId = entry.productId.trim(), from = entry.from?.trim()?.ifEmpty { null }, to = entry.to?.trim()?.ifEmpty { null })
    val product = d.products.find { it.id == clean.productId }
      ?: return DebtorEngine.Result.Failed(StrCustomerPrices.pickProduct)
    val list = c.specialPrices.orEmpty()
    val old = replacing?.let { r -> list.find { it.productId == r } }
    if (replacing != null && old == null) return DebtorEngine.Result.Failed(StrCustomerPrices.notSaved)
    val others = list.filter { it !== old }
    CustomerPrice.check(CustomerPrice.Entry(clean.productId, clean.priceMinor, clean.from, clean.to), entries(c.copy(specialPrices = others)))
      ?.let { return DebtorEngine.Result.Failed(refusal(it)) }
    if (old == clean) return DebtorEngine.Result.Ok(d, debtorId)
    val next = if (old == null) list + clean else list.map { if (it === old) clean else it }
    val trace = AuditEntry(
      id = newId(), type = AUDIT_SET, date = today, refId = debtorId, createdAt = now, by = by?.trim()?.ifEmpty { null },
      amount = clean.priceMinor / 100.0,
      notes = "قیمتِ خاصِ «${c.name}» برای «${product.name}»: ${afn(old?.priceMinor)} ⇐ ${afn(clean.priceMinor)}" +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        (if (clean.from != null || clean.to != null) " (${clean.from ?: "…"} تا ${clean.to ?: "…"})" else ""),  // داده: همان یادداشت
    )
    return DebtorEngine.Result.Ok(
      d.copy(debtors = d.debtors.map { if (it.id == debtorId) c.copy(specialPrices = next) else it }, auditLog = d.auditLog + trace),
      debtorId,
    )
  }

  fun remove(
    d: ShopData,
    debtorId: String,
    productId: String,
    allowed: Boolean,
    by: String?,
    today: String,
    now: Long,
    newId: () -> String,
  ): DebtorEngine.Result {
    if (!allowed) return DebtorEngine.Result.Failed(StrCustomerPrices.notAllowed)
    val c = d.debtors.find { it.id == debtorId } ?: return DebtorEngine.Result.Failed(StrLedger.customerNotFound)
    val list = c.specialPrices.orEmpty()
    val old = list.find { it.productId == productId } ?: return DebtorEngine.Result.Ok(d, debtorId)
    val name = d.products.find { it.id == productId }?.name ?: productId
    val trace = AuditEntry(
      id = newId(), type = AUDIT_REMOVE, date = today, refId = debtorId, createdAt = now, by = by?.trim()?.ifEmpty { null },
      amount = old.priceMinor / 100.0,
      notes = "قیمتِ خاصِ «${c.name}» برای «$name» برداشته شد (${afn(old.priceMinor)})",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    )
    //  فهرستِ خالی ⇒ `null` (همان قرض‌دارِ بی قیمتِ خاص)
    val next = (list - old).ifEmpty { null }
    return DebtorEngine.Result.Ok(
      d.copy(debtors = d.debtors.map { if (it.id == debtorId) c.copy(specialPrices = next) else it }, auditLog = d.auditLog + trace),
      debtorId,
    )
  }
}

/**
 *  سبدِ صندوق ⇐ `CustomerPrice.reprice` (POS-18، فقط با «قیمتِ خاصِ مشتری» ِ روشن؛ خاموش ⇒ همان `repriceCart` ِ لیست).
 *  قلمِ آزاد و کالای ناشناخته دست نمی‌خورند؛ ردیفی که دقیقاً به قیمتِ لیستِ بستهٔ کامل است (`PackScan.packListed`) «ثابت»
 *  است. بی تغییر ⇒ همان شیءِ سبد.
 */
object CustomerPriceCart {

  data class Out(val cart: List<SalesEngine.CartLine>, val changed: Int, val keptManual: Int, val special: Int)

  private fun same(a: Double?, b: Double?): Boolean = a != null && b != null && abs(a - b) < 1e-6

  fun reprice(
    d: ShopData,
    cart: List<SalesEngine.CartLine>,
    invoice: PriceList.State,
    debtorId: String?,
    date: String,
    manualAllowed: Boolean,
  ): Out {
    val byId = d.products.associateBy { it.id }
    val debtor = debtorId?.let { id -> d.debtors.find { it.id == id } }
    val customerList = debtorId?.let { debtor?.priceList.orEmpty() }
    val core = ArrayList<CustomerPrice.Line>(cart.size)
    val at = ArrayList<Int>(cart.size)
    cart.forEachIndexed { i, l ->
      val p = byId[l.productId]
      if (l.free || p == null) return@forEachIndexed
      val fromSpecial = same(l.unitPrice, l.specialPrice)
      core += CustomerPrice.Line(
        key = i.toString(),
        productId = p.id,
        prices = PriceList.Prices(CustomerPrices.minor(p.salePrice), CustomerPrices.minor(p.wholesalePrice)),
        unitMinor = l.unitPrice?.let(CustomerPrices::minor),
        specialMinor = if (fromSpecial) l.unitPrice?.let(CustomerPrices::minor) else null,
        fixed = !fromSpecial && l.unitPrice != null && PackScan.packListed(p, l.unitPrice, l.quantity),
      )
      at += i
    }
    val r = CustomerPrice.reprice(core, CustomerPrices.entries(debtor), customerList, invoice, date, manualAllowed)
    var touched = false
    val out = cart.toMutableList()
    r.lines.forEachIndexed { k, line ->
      val i = at[k]
      val l = cart[i]
      val unit = line.unitMinor?.let { it / 100.0 }
      val special = line.specialMinor?.let { it / 100.0 }
      //  همان فی (ممیزِ شناور) ⇒ همان ردیف
      val unitSame = (unit == null && l.unitPrice == null) || same(unit, l.unitPrice)
      val specialSame = (special == null && l.specialPrice == null) || same(special, l.specialPrice)
      if (!unitSame || !specialSame) { out[i] = l.copy(unitPrice = unit, specialPrice = special); touched = true }
    }
    return Out(if (touched) out else cart, r.changed, r.keptManual, r.special)
  }

  /** شمارِ ردیف‌های کالادارِ [after] که فیشان با همان کالا در [before] فرق دارد — پیامِ «قیمتِ n ردیف عوض شد» */
  fun changedSince(before: List<SalesEngine.CartLine>, after: List<SalesEngine.CartLine>): Int {
    val was = before.filter { !it.free }.associateBy { it.productId }
    return after.count { l -> !l.free && was[l.productId]?.let { w -> !((w.unitPrice == null && l.unitPrice == null) || same(w.unitPrice, l.unitPrice)) } == true }
  }

  /**
   *  نشانِ «قیمتِ خاص» ِ ردیفی از سندِ ثابت‌قیمت (پیش‌فاکتور، اصلاحِ فاکتور) هنگامِ رفتن به سبد: فقط اگر [unit] امروز
   *  ([date]) هنوز همان قیمتِ خاصِ فعالِ همین مشتری برای همین کالاست؛ وگرنه `null` ⇒ ردیف «دستی» با همان فی می‌ماند
   *  (`CustomerPrice.reprice`). بی این شرط، نشان فیِ سند را به قیمتِ خاصِ تازه (یا پس از پایانِ بازه به لیست) می‌بُرد.
   */
  fun stillSpecial(d: ShopData, debtorId: String?, productId: String, unit: Double?, date: String): Double? {
    if (unit == null || debtorId == null) return null
    val e = CustomerPrice.find(CustomerPrices.entries(d.debtors.find { it.id == debtorId }), productId, date) ?: return null
    return unit.takeIf { e.priceMinor == CustomerPrices.minor(it) }
  }

  /** برچسبِ منبعِ قیمتِ قلمِ ثبت‌شده (رسید و سابقه): «قیمتِ خاص» یا `null` */
  fun label(item: SaleItem): String? = if (item.priceSource == CustomerPrice.SPECIAL) StrCustomerPrices.sourceLabel else null

  /** یادداشتِ ردیفِ رسید با برچسبِ منبع: بی منبع ⇒ همان یادداشت (همان رسیدِ پیشین) */
  fun noteWith(item: SaleItem, note: String? = item.note): String? {
    val tag = label(item) ?: return note
    return listOfNotNull(tag, note?.takeIf { it.isNotBlank() }).joinToString(" · ")
  }
}
