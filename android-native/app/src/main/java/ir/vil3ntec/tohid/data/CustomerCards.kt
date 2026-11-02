package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.crm.CustomerCard
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.product.PackBarcodes

/**
 *  کارت یا بارکدِ مشتری (CRM-16) — سمتِ دفترِ دکان برای `core/crm/CustomerCard`.
 *
 *  • نوشتنِ `Debtor.cardCode` فقط این‌جا ([assign]، [remove])، هر بار با ردِ `customer_card_set` در
 *    دفترچهٔ ثبت؛ مشتریِ ادغام‌شده کارت نمی‌گیرد. کد با کارتِ همهٔ مشتریانِ دیگر (ادغام‌شده هم) و با هر
 *    کدی که کالا می‌خواند ([productClaims]: بارکدِ کامل یا نیمه با `BarcodeMatch`، بارکدِ بسته، سری) سنجیده
 *    می‌شود، پس در لحظهٔ دادن هیچ تداخلی نیست.
 *  • صندوق ([atTill]): فقط با `CustomerCardPref` (پیش‌فرض خاموش) و فقط وقتی هیچ کالایی کد را نمی‌خواند؛
 *    کارتِ مشتریِ ادغام‌شده مشتریِ ماندگار را انتخاب می‌کند (`CustomerCrm.resolve`).
 *  منطقِ خالص؛ آزمون: `CustomerCardsTest`.
 */
object CustomerCards {

  const val AUDIT_TYPE = "customer_card_set"

  /** کدِ تمیزِ کارت ⇒ شناسهٔ مشتری (همهٔ مشتریان، ادغام‌شده هم) */
  fun index(d: ShopData): Map<String, String> = buildMap {
    d.debtors.forEach { c -> c.cardCode?.takeIf { it.isNotBlank() }?.let { put(CustomerCard.normalize(it), c.id) } }
  }

  /** کالایی این کد را می‌خواند؟ — همان راه‌های صندوق: بارکدِ کالا (کامل یا نیمه)، بسته و سری */
  fun productClaims(d: ShopData, code: String, barcodes: Map<String, String> = ShopStore.barcodeIndex(d)): Boolean {
    val c = code.trim()
    if (c.isEmpty()) return false
    if (BarcodeMatch.find(c, barcodes) != BarcodeMatch.Hit.None) return true
    if (ShopStore.packIndex(d).lookup(c) { false } != PackBarcodes.Lookup.NotPack) return true
    return d.warehouseEntries.any { it.batch.isNotBlank() && it.batch.trim() == c }
  }

  /** مشتریِ فعالِ صاحبِ کارت (ادغام‌شده ⇒ ماندگار)، یا null */
  fun owner(d: ShopData, code: String): Debtor? {
    val id = index(d)[CustomerCard.normalize(code)] ?: return null
    val keep = CustomerCrm.resolve(d, id)
    return d.debtors.firstOrNull { it.id == keep && CustomerCrm.isActive(it) }
  }

  /** صندوق: کدِ خوانده‌شده ⇒ مشتری؛ null ⇒ راهِ همیشگیِ کالا (خاموش، کالا یا ناشناس) */
  fun atTill(enabled: Boolean, d: ShopData, code: String, barcodes: Map<String, String>? = null): Debtor? {
    if (!enabled) return null
    val index = barcodes ?: ShopStore.barcodeIndex(d)
    var found: Debtor? = null
    CustomerCard.route(true, code, { productClaims(d, it, index) }) { c -> owner(d, c)?.also { found = it }?.id }
    return found
  }

  /** چه کسی این کد را دارد (به‌جز خودِ همین مشتری) */
  fun clash(d: ShopData, selfId: String, code: String): CustomerCard.Clash? {
    val other = index(d)[CustomerCard.normalize(code)]
    if (other != null && other != selfId) return CustomerCard.Clash.CUSTOMER
    val digits = CustomerCard.normalize(code)
    return if (productClaims(d, code) || productClaims(d, digits)) CustomerCard.Clash.PRODUCT else null
  }

  fun reasonText(r: CustomerCard.Reason): String = when (r) {
    CustomerCard.Reason.EMPTY -> StrCustomerCard.reasonEmpty
    CustomerCard.Reason.NOT_DIGITS -> StrCustomerCard.reasonDigits
    CustomerCard.Reason.LENGTH -> StrCustomerCard.reasonLength
    CustomerCard.Reason.CHECK_DIGIT -> StrCustomerCard.reasonCheck
    CustomerCard.Reason.TAKEN_CUSTOMER -> StrCustomerCard.reasonCustomer
    CustomerCard.Reason.TAKEN_PRODUCT -> StrCustomerCard.reasonProduct
    CustomerCard.Reason.EXHAUSTED -> StrCustomerCard.reasonExhausted
  }

  /**
   *  کارتِ مشتری: `manual` ِ خالی ⇒ کدِ ساخته‌شده (و اگر کارت داشت، کدِ دیگری برای کارتِ گم‌شده)؛
   *  وگرنه همان کدِ کارتِ چاپ‌شده پس از سنجش. همه یا هیچ، با ردِ دفترچه.
   */
  fun assign(
    d: ShopData,
    id: String,
    manual: String?,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    if (!CustomerCrm.isActive(c)) return DebtorEngine.Result.Failed("این مشتری با مشتریِ دیگری ادغام شده است")
    val code = when (val p = CustomerCard.pick(id, manual, c.cardCode, { clash(d, id, it) })) {
      is CustomerCard.Check.Ok -> p.code
      is CustomerCard.Check.Bad -> return DebtorEngine.Result.Failed(reasonText(p.reason))
    }
    if (code == c.cardCode) return DebtorEngine.Result.Ok(d, id)
    return write(d, c, code, today, now, newId)
  }

  /** برداشتنِ کارت (گم‌شده یا پس‌گرفته) — با ردِ دفترچه؛ بی کارت ⇒ بی‌تغییر */
  fun remove(
    d: ShopData,
    id: String,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    if (c.cardCode == null) return DebtorEngine.Result.Ok(d, id)
    return write(d, c, null, today, now, newId)
  }

  private fun write(d: ShopData, c: Debtor, code: String?, today: String, now: Long, newId: () -> String): DebtorEngine.Result {
    val trace = AuditEntry(
      id = newId(), type = AUDIT_TYPE, date = today, refId = c.id, createdAt = now,
      notes = "کارتِ مشتریِ «${c.name}»: ${c.cardCode ?: "—"} ⇐ ${code ?: "—"}",
    )
    return DebtorEngine.Result.Ok(
      d.copy(
        debtors = d.debtors.map { if (it.id == c.id) it.copy(cardCode = code) else it },
        auditLog = d.auditLog + trace,
      ),
      id = c.id,
    )
  }
}
