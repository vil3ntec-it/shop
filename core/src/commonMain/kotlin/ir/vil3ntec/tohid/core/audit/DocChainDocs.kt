package ir.vil3ntec.tohid.core.audit

/**
 *  SEC-02 — اسنادِ مالیِ **بی‌شمارهٔ** دفتر در همان زنجیرهٔ هشِ سریِ دستگاه ([DocChain]): برگشتِ فروش، قرض/رسید،
 *  خرید و پرداخت به تأمین‌کننده. فاکتور شماره دارد و «تا کجا» یش `lastNo` است؛ این‌ها شماره ندارند ⇒ نشانِ جدا.
 *
 *  ── «تا کجا» (نشانِ سندِ بی‌شماره، در [DocChainState]) ──────────────────────────────────────
 *  - [DocChainState.docFrom]: زنجیره از این لحظه (ساعتِ دستگاه هنگامِ آغاز). سندِ پیش از آن لنگر نمی‌خورد — همان
 *    قاعدهٔ `startNo` ِ فاکتور («گذشتهٔ لنگرنخورده چیزی را ثابت نمی‌کند»). ‎-1‎ ⇐ زنجیرهٔ ساختهٔ نسخهٔ پیشین: در نخستین
 *    دور همان لحظه آغاز می‌شود (تاریخچه دوباره ساخته نمی‌شود).
 *  - [DocChainState.docAt]: تازه‌ترین زمانِ سندِ زنجیره‌شده — **هرگز جلوتر از ساعتِ همین دستگاه** (سندِ دستگاهی که
 *    ساعتش جلو است نشان را جلو نمی‌برد و سندِ خودِ این دستگاه را بیرون نمی‌اندازد).
 *  - [DocChainState.docIds]: کلیدِ اسنادِ زنجیره‌شدهٔ پنجرهٔ [SETTLE_MS] پیش از [DocChainState.docAt]. سندی که دیر
 *    رسید (همگام‌سازیِ دستگاهِ دیگر، ساعتِ کمی عقب) ولی در پنجره است هنوز **یک بار** زنجیره می‌شود؛ سندِ پیش از پنجره نه
 *    (صفحهٔ «بررسیِ یکپارچگی» آن را «بی لنگر» می‌شمارد، [DocChainAudit]).
 *
 *  ── سند ───────────────────────────────────────────────────────────────────────────────
 *  همان سرآیندِ سندِ فاکتور (`chain`، `seq`، `kind = doc`، `type`، `v = 1`، `id`، `status = posted`، `postedAt`،
 *  `by`) و فقط بخش‌های **تغییرناپذیر و بی دادهٔ شخصی** — شناسه‌ها، مقدار (هزارم) و مبلغ (پول). نه نام، نه یادداشت،
 *  نه دلیلِ آزاد، نه سررسید (ویرایش‌پذیر)، نه شمارهٔ فاکتورِ خرید (پس از ثبت داده می‌شود)، نه هزینهٔ جانبی (نمایش).
 *  هر ردیفِ دفتر یک سند است (برگشتِ چندقلمی چند سند با همان `docId`): ردیفی که دیرتر همگام شد جا نمی‌ماند.
 *  هر دستگاه رونوشتِ دفترِ **خودش** را لنگر می‌زند: سندی که از دستگاهِ دیگر رسید در زنجیرهٔ هر دو می‌نشیند (سرور نوع
 *  را محدود نمی‌کند و هر زنجیره جداست).
 *
 *  Kotlin خالص (commonMain). بردارِ مرجع: `core/test-vectors/doc-chain-docs-v1.json` (`server/scripts/doc-chain-docs-vectors.js`).
 */
sealed class PlainFacts {
  abstract val type: String
  abstract val id: String
  abstract val postedAt: Long
  abstract val currency: String
  internal abstract fun fields(into: MutableMap<String, Any?>)
}

/** یک ردیفِ برگشتِ فروش (`SaleReturn`) */
data class ReturnFacts(
  override val id: String,
  override val postedAt: Long,
  override val currency: String,
  val saleId: String,
  val saleItemId: String,
  val productId: String,
  val qty: Long,
  val amount: Long,
  val docId: String? = null,
  val disposition: String? = null,
  val refund: String? = null,
  val toAccount: Long? = null,
  val toCredit: Long? = null,
  val creditTo: String? = null,
  val unitPrice: Long? = null,
) : PlainFacts() {
  override val type: String get() = DocChainDocs.RETURN
  override fun fields(into: MutableMap<String, Any?>) {
    into["qtyScale"] = DocChain.QTY_SCALE
    into["saleId"] = saleId
    into["saleItemId"] = saleItemId
    into["productId"] = productId
    into["qty"] = qty
    into["amount"] = amount
    docId?.takeIf { it.isNotBlank() }?.let { into["docId"] = it }
    disposition?.takeIf { it.isNotBlank() }?.let { into["disposition"] = it }
    refund?.takeIf { it.isNotBlank() }?.let { into["refund"] = it }
    toAccount?.let { into["toAccount"] = it }
    toCredit?.let { into["toCredit"] = it }
    creditTo?.takeIf { it.isNotBlank() }?.let { into["creditTo"] = it }
    unitPrice?.let { into["unitPrice"] = it }
  }
}

/** یک ردیفِ قرض یا رسید (`DebtTransaction`)؛ [direction] = `give` | `receive` */
data class DebtFacts(
  override val id: String,
  override val postedAt: Long,
  override val currency: String,
  val customerId: String,
  val direction: String,
  val amount: Long,
  val refId: String? = null,
  val kind: String? = null,
) : PlainFacts() {
  override val type: String get() = DocChainDocs.DEBT
  override fun fields(into: MutableMap<String, Any?>) {
    into["customerId"] = customerId
    into["direction"] = direction
    into["amount"] = amount
    refId?.takeIf { it.isNotBlank() }?.let { into["refId"] = it }
    kind?.takeIf { it.isNotBlank() }?.let { into["debtKind"] = it }
  }
}

/** یک ردیفِ خرید (`Purchase`)؛ برگشت به تأمین‌کننده همان ردیف با مقدار و مبلغِ منفی است */
data class PurchaseFacts(
  override val id: String,
  override val postedAt: Long,
  override val currency: String,
  val supplierId: String,
  val productId: String,
  val qty: Long,
  val unitPrice: Long,
  val total: Long,
  val paid: Long,
  val debt: Long,
) : PlainFacts() {
  override val type: String get() = DocChainDocs.PURCHASE
  override fun fields(into: MutableMap<String, Any?>) {
    into["qtyScale"] = DocChain.QTY_SCALE
    into["supplierId"] = supplierId
    into["productId"] = productId
    into["qty"] = qty
    into["unitPrice"] = unitPrice
    into["total"] = total
    into["paid"] = paid
    into["debt"] = debt
  }
}

/** یک پرداخت به تأمین‌کننده (`SupplierPayment`) */
data class SupplierPayFacts(
  override val id: String,
  override val postedAt: Long,
  override val currency: String,
  val supplierId: String,
  val amount: Long,
) : PlainFacts() {
  override val type: String get() = DocChainDocs.SUPPLIER_PAY
  override fun fields(into: MutableMap<String, Any?>) {
    into["supplierId"] = supplierId
    into["amount"] = amount
  }
}

object DocChainDocs {
  const val RETURN = "return"
  const val DEBT = "debt"
  const val PURCHASE = "purchase"
  const val SUPPLIER_PAY = "supplier_pay"
  val TYPES: Set<String> = setOf(RETURN, DEBT, PURCHASE, SUPPLIER_PAY)

  /** پنجرهٔ «دیر رسید ولی هنوز زنجیره می‌شود» پیش از [DocChainState.docAt] — سه روز */
  const val SETTLE_MS = 3L * 24 * 60 * 60_000L

  /** کلیدِ سند در [DocChainState.docIds] */
  fun key(f: PlainFacts): String = "${f.type}:${f.id}"

  /** شناسه‌ای که در کلید و خطِ فایل جا می‌شود (بی جداکننده‌ها) */
  fun validId(id: String): Boolean = id.isNotEmpty() && id.length <= 128 && id.none { it == ',' || it == '\t' || it == '\n' || it == '\r' }

  /** سندِ v1 ِ [f] در حلقهٔ [seq] ِ [chain]؛ [by] کاربرِ واردشده (خالی ⇐ نوشته نمی‌شود) */
  fun doc(chain: String, seq: Long, by: String, f: PlainFacts): Map<String, Any?> {
    val doc = LinkedHashMap<String, Any?>()
    doc["chain"] = chain
    doc["seq"] = seq
    doc["kind"] = "doc"
    doc["type"] = f.type
    doc["v"] = 1L
    doc["id"] = f.id
    doc["status"] = "posted"
    doc["postedAt"] = f.postedAt
    if (by.isNotBlank()) doc["by"] = by
    doc["currency"] = f.currency
    f.fields(doc)
    return doc
  }

  /** مرزِ پایینِ پنجره: نه پیش از آغاز، نه پیش از [SETTLE_MS] پیش از «تا کجا» */
  fun floor(state: DocChainState): Long = maxOf(state.docFrom, state.docAt - SETTLE_MS)

  /** اسنادِ تازه برای زنجیره، به ترتیبِ زمان، نوع و شناسه؛ هر کلید یک بار */
  fun pending(state: DocChainState, docs: List<PlainFacts>): List<PlainFacts> {
    if (state.docFrom < 0) return emptyList()
    val from = floor(state)
    return docs.asSequence()
      .filter { it.type in TYPES && validId(it.id) && it.postedAt >= from && key(it) !in state.docIds }
      .sortedWith(compareBy<PlainFacts>({ it.postedAt }, { it.type }, { it.id }))
      .distinctBy(::key)
      .toList()
  }

  /**
   *  نشان پس از نشستنِ [chained]: «تا کجا» جلو (نه جلوتر از [now])، و کلیدها فقط برای اسنادِ همین دفتر که هنوز در
   *  پنجره‌اند (رفته‌ها و پیش از پنجره کنار).
   */
  fun advance(state: DocChainState, chained: List<PlainFacts>, docs: List<PlainFacts>, now: Long): DocChainState {
    val newest = chained.maxOfOrNull { it.postedAt } ?: return state
    val at = maxOf(state.docAt, minOf(newest, now))
    val next = state.copy(docAt = at)
    val from = floor(next)
    val live = HashSet<String>()
    for (d in docs) if (d.postedAt >= from) live += key(d)
    val ids = (state.docIds + chained.map(::key)).filterTo(HashSet()) { it in live }
    return next.copy(docIds = ids)
  }
}
