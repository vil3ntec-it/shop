package ir.vil3ntec.tohid.data

/**
 *  پروندهٔ مشتری — همهٔ آنچه یک مشتری با دکان داشته، از همان دفترِ فروش و قرض.
 *
 *  خواستهٔ صاحب مخزن: «حساب‌هاشون… داخلش کادری یا شبیه اکسل ستونی و
 *  بردگی تاریخ… و فاکتور یارو هم باشه… هر چیزی که برمی‌داره دیده بشه…
 *  فاکتور هم جدا دیده بشه… کسایی که خرید می‌کنن هم باید ثبت بشه و
 *  فاکتورشونو نشون داده بشه.»
 *
 *  هیچ عددی جدا ذخیره نمی‌شود: مانده از `transactions`، خرید و اقلام از
 *  `sales`/`saleItems` با `Sale.debtorId` — که برای فروشِ **نقدی** با مشتری
 *  هم پر است. پس «مشتریِ خریدارِ بی‌قرض» هم پرونده دارد.
 */
object CustomerEngine {

  data class Summary(
    val id: String,
    val name: String,
    val phone: String,
    /** مانده — مثبت یعنی بدهکار به دکان */
    val balance: Double,
    /** جمعِ خریدِ فاکتورهای ثبت‌شده (لغوشده نه) */
    val bought: Double,
    val invoices: Int,
    /** تاریخِ آخرین خرید (yyyy-MM-dd) یا null */
    val lastPurchase: String?,
    /** چند روز است بدهکار است — null یعنی بدهی ندارد */
    val debtDays: Long?,
    /** آخرین پرداخت (yyyy-MM-dd) یا null */
    val lastPaid: String?,
  )

  data class LedgerLine(
    val tx: DebtTransaction,
    val row: Int,
    val give: Double,
    val receive: Double,
    val balance: Double,
    /** فاکتورِ پشتِ این ردیف (فروشِ نسیه، لغو، مرجوعی) — اگر هست */
    val saleId: String?,
    val invoiceNumber: Int?,
    /** شمارهٔ نمایشی، با سریِ دستگاه اگر دارد (`B1-12`) — F0-03 */
    val invoiceLabel: String? = invoiceNumber?.toString(),
  )

  data class ItemLine(
    val saleId: String,
    val invoiceNumber: Int?,
    val date: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val total: Double,
    val returned: Double,
    /** شمارهٔ نمایشی، با سریِ دستگاه اگر دارد (`B1-12`) — F0-03 */
    val invoiceLabel: String? = invoiceNumber?.toString(),
  )

  fun summary(d: ShopData, debtor: Debtor, now: Long = System.currentTimeMillis()): Summary {
    val sales = d.sales.filter { it.debtorId == debtor.id && it.status != "cancelled" }
    val receives = d.transactions.filter { it.debtorId == debtor.id && it.type != "give" }
    return Summary(
      id = debtor.id,
      name = debtor.name,
      phone = debtor.phone,
      balance = ShopStore.debt(d, debtor.id),
      bought = sales.sumOf { it.finalTotal },
      invoices = sales.size,
      lastPurchase = sales.maxOfOrNull { it.date }?.takeIf { it.isNotBlank() },
      debtDays = DebtorEngine.debtDays(d, debtor.id, now),
      lastPaid = receives.maxOfOrNull { it.date }?.takeIf { it.isNotBlank() },
    )
  }

  fun summaries(d: ShopData, now: Long = System.currentTimeMillis()): List<Summary> =
    d.debtors.map { summary(d, it, now) }

  /**
   *  «فاکتور #۱۲» یا «فاکتور #B1-12» در شرحِ ردیف.
   *  سری (F0-03) اختیاری است: شرح‌های پیش از سری همان عدد را دارند.
   */
  private val INVOICE = Regex("""فاکتور\s*#\s*(?:([A-Z][1-9][0-9]{0,3})-)?(\d+)""")

  fun invoiceNumberOf(notes: String): Int? = INVOICE.find(notes)?.groupValues?.get(2)?.toIntOrNull()

  /** شمارهٔ نمایشیِ شرح: `B1-12` یا `12`؛ `null` اگر شرح فاکتوری نام نبرده. */
  fun invoiceLabelOf(notes: String): String? = INVOICE.find(notes)?.let { m ->
    val n = m.groupValues[2].toIntOrNull() ?: return@let null
    val series = m.groupValues[1]
    if (series.isEmpty()) n.toString() else "$series-$n"
  }

  /** صورت‌حساب: ردیف به ردیف به ترتیبِ تاریخ، با ماندهٔ پس از هر ردیف و فاکتورِ پشتش */
  fun ledger(d: ShopData, debtorId: String): List<LedgerLine> {
    //  کلید همان شمارهٔ نمایشی است: `A1-5` و `B1-5` دو فاکتورِ جدااند (F0-03)
    val byLabel = d.sales.filter { it.debtorId == debtorId && it.invoiceNumber != null }
      .associateBy { it.invoiceLabel()!! }
    var run = 0.0
    return d.transactions.filter { it.debtorId == debtorId }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .mapIndexed { i, tx ->
        val give = tx.type == "give"
        run += if (give) tx.amount else -tx.amount
        val n = invoiceNumberOf(tx.notes)
        val label = invoiceLabelOf(tx.notes)
        LedgerLine(
          tx = tx, row = i + 1,
          give = if (give) tx.amount else 0.0,
          receive = if (give) 0.0 else tx.amount,
          balance = run,
          saleId = label?.let { byLabel[it]?.id },
          invoiceNumber = n,
          invoiceLabel = label,
        )
      }
  }

  /** فاکتورهای مشتری، تازه‌ترین بالا — لغوشده هم، با همان وضعیت */
  fun invoices(d: ShopData, debtorId: String): List<Sale> =
    d.sales.filter { it.debtorId == debtorId }
      .sortedWith(compareByDescending<Sale> { it.date }.thenByDescending { it.createdAt })

  /** هر قلمی که برداشته — از همهٔ فاکتورهای ثبت‌شده، تازه‌ترین بالا */
  fun items(d: ShopData, debtorId: String): List<ItemLine> {
    val sales = invoices(d, debtorId).filter { it.status != "cancelled" }
    val bySale = d.saleItems.groupBy { it.saleId }
    return sales.flatMap { s ->
      bySale[s.id].orEmpty().map { it ->
        ItemLine(
          saleId = s.id, invoiceNumber = s.invoiceNumber, invoiceLabel = s.invoiceLabel(), date = s.date,
          name = SalesEngine.itemName(d, it), quantity = it.quantity,
          unitPrice = it.unitPrice, total = it.totalPrice, returned = it.returnedQty,
        )
      }
    }
  }

  /* ── برگه‌های اکسل و چاپ ─────────────────────────────────────────── */

  private fun r(v: Double) = Math.round(v).toString()
  private fun q(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
  private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "مشتری" }

  fun ledgerSheet(d: ShopData, debtor: Debtor, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("ردیف", "تاریخ", "شرح", "فاکتور", "برد (بدهکار)", "رسید (بستانکار)", "مانده"))
    ledger(d, debtor.id).forEach { l ->
      rows += listOf(
        l.row.toString(), l.tx.date, l.tx.notes.ifBlank { if (l.give > 0) "برد" else "رسید" },
        l.invoiceLabel.orEmpty(),
        if (l.give > 0) r(l.give) else "", if (l.receive > 0) r(l.receive) else "", r(l.balance),
      )
    }
    return ReportCsv.Sheet("صورت‌حساب-${safe(debtor.name)}-$today", rows)
  }

  fun invoicesSheet(d: ShopData, debtor: Debtor, today: String): ReportCsv.Sheet {
    val items = d.saleItems.groupBy { it.saleId }
    val rows = mutableListOf(listOf("فاکتور", "تاریخ", "اقلام", "جمع", "تخفیف", "قابلِ پرداخت", "پرداخت‌شده", "باقی", "نوع", "وضعیت"))
    invoices(d, debtor.id).forEach { s ->
      rows += listOf(
        s.invoiceLabel().orEmpty(), s.date, items[s.id].orEmpty().size.toString(),
        r(s.total), r(s.discount), r(s.finalTotal), r(s.paidAmount), r(s.remaining),
        PayFlow.payLabel(s),
        if (s.status == "cancelled") "لغوشده" else "ثبت‌شده",
      )
    }
    return ReportCsv.Sheet("فاکتورهای-${safe(debtor.name)}-$today", rows)
  }

  fun itemsSheet(d: ShopData, debtor: Debtor, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("تاریخ", "فاکتور", "کالا", "تعداد", "فی", "جمع", "برگشتی"))
    items(d, debtor.id).forEach { l ->
      rows += listOf(l.date, l.invoiceLabel.orEmpty(), l.name, q(l.quantity), r(l.unitPrice), r(l.total), if (l.returned > 0) q(l.returned) else "")
    }
    return ReportCsv.Sheet("اقلامِ-${safe(debtor.name)}-$today", rows)
  }
}
