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

  /** «فاکتور #۱۲» در شرحِ ردیف ⇒ ۱۲ */
  private val INVOICE = Regex("""فاکتور\s*#\s*(\d+)""")

  fun invoiceNumberOf(notes: String): Int? = INVOICE.find(notes)?.groupValues?.get(1)?.toIntOrNull()

  /** صورت‌حساب: ردیف به ردیف به ترتیبِ تاریخ، با ماندهٔ پس از هر ردیف و فاکتورِ پشتش */
  fun ledger(d: ShopData, debtorId: String): List<LedgerLine> {
    val byNumber = d.sales.filter { it.debtorId == debtorId && it.invoiceNumber != null }
      .associateBy { it.invoiceNumber!! }
    var run = 0.0
    return d.transactions.filter { it.debtorId == debtorId }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .mapIndexed { i, tx ->
        val give = tx.type == "give"
        run += if (give) tx.amount else -tx.amount
        val n = invoiceNumberOf(tx.notes)
        LedgerLine(
          tx = tx, row = i + 1,
          give = if (give) tx.amount else 0.0,
          receive = if (give) 0.0 else tx.amount,
          balance = run,
          saleId = n?.let { byNumber[it]?.id },
          invoiceNumber = n,
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
          saleId = s.id, invoiceNumber = s.invoiceNumber, date = s.date,
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
        l.invoiceNumber?.toString().orEmpty(),
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
        s.invoiceNumber?.toString().orEmpty(), s.date, items[s.id].orEmpty().size.toString(),
        r(s.total), r(s.discount), r(s.finalTotal), r(s.paidAmount), r(s.remaining),
        if (s.paymentMethod == "credit") "نسیه" else "نقد",
        if (s.status == "cancelled") "لغوشده" else "ثبت‌شده",
      )
    }
    return ReportCsv.Sheet("فاکتورهای-${safe(debtor.name)}-$today", rows)
  }

  fun itemsSheet(d: ShopData, debtor: Debtor, today: String): ReportCsv.Sheet {
    val rows = mutableListOf(listOf("تاریخ", "فاکتور", "کالا", "تعداد", "فی", "جمع", "برگشتی"))
    items(d, debtor.id).forEach { l ->
      rows += listOf(l.date, l.invoiceNumber?.toString().orEmpty(), l.name, q(l.quantity), r(l.unitPrice), r(l.total), if (l.returned > 0) q(l.returned) else "")
    }
    return ReportCsv.Sheet("اقلامِ-${safe(debtor.name)}-$today", rows)
  }
}
