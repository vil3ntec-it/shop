package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.SimpleAsk

/**
 *  RPT-16 — پاسخِ «پرسشِ ساده» از **همان** موتورهای «گزارش‌ها» (`ReportEngine`، `ShopStore`)، با همان بازه‌های
 *  `ReportEngine.rangeOf`؛ پس هر عدد همان عددِ زبانهٔ هم‌نامِ گزارش است و این‌جا هیچ فرمولِ تازه‌ای نیست.
 *  شکل و اجازهٔ پرسش در `core/report/SimpleAsk` است. فقط خواندن: هیچ داده‌ای نوشته یا فرستاده نمی‌شود.
 */
object SimpleAskEngine {

  enum class Kind { MONEY, COUNT, QTY }

  enum class Key(val kind: Kind) {
    NET(Kind.MONEY), INVOICES(Kind.COUNT), DISCOUNT(Kind.MONEY), RETURNS(Kind.MONEY),
    COGS(Kind.MONEY), GROSS_PROFIT(Kind.MONEY), EXPENSES(Kind.MONEY), NET_PROFIT(Kind.MONEY),
    PURCHASE_TOTAL(Kind.MONEY), PURCHASE_PAID(Kind.MONEY), PURCHASE_DEBT(Kind.MONEY), PURCHASES(Kind.COUNT),
    CATEGORY_NET(Kind.MONEY), CATEGORY_PRODUCTS(Kind.COUNT), PRODUCT_NET(Kind.MONEY),
    ON_HAND(Kind.QTY), OUT(Kind.COUNT), LOW(Kind.COUNT), SOLD(Kind.QTY),
    DEBT_REMAINING(Kind.MONEY), DEBT_GIVEN(Kind.MONEY), DEBT_RECEIVED(Kind.MONEY), DEBT_TOTAL(Kind.MONEY), DEBTORS(Kind.COUNT),
  }

  /** `name`: ردیفِ فهرست (پرفروش‌ها)؛ بی آن عددِ کلِ پرسش */
  data class Value(val key: Key, val amount: Double, val name: String? = null)

  /**
   *  `from`/`to` فقط برای پرسشِ بازه‌دار. `status` برای موجودیِ یک کالا همان `ShopStore.stockStatus` (‹out›/‹low›/‹ok›).
   *  `names` کالاهای «تمام‌شده» و سپس «کم» به ترتیبِ «گزارش‌ها».
   */
  data class Answer(
    val query: SimpleAsk.Query,
    val from: String? = null,
    val to: String? = null,
    val values: List<Value>,
    val status: String? = null,
    val names: List<String> = emptyList(),
  ) {
    fun amount(k: Key): Double? = values.firstOrNull { it.key == k && it.name == null }?.amount
  }

  sealed interface Outcome {
    data class Ok(val answer: Answer) : Outcome
    data class Rejected(val issues: List<SimpleAsk.Issue>) : Outcome
  }

  /** نام‌هایی که پرسش می‌تواند بگیرد: کالا، دسته (فهرستِ دکان و دستهٔ خودِ کالاها)، مشتری و فروشندهٔ فاکتورها */
  fun catalog(d: ShopData): SimpleAsk.Catalog = SimpleAsk.Catalog(
    products = d.products.filter { it.name.isNotBlank() }.map { SimpleAsk.Named(it.id, it.name) },
    categories = (d.productCategories + d.products.map { it.category }).map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    customers = d.debtors.filter { it.name.isNotBlank() }.map { SimpleAsk.Named(it.id, it.name) },
    //  همان گروه‌بندیِ «فروشِ هر کاربر» (خالی ⇒ «نامعلوم»)، از همهٔ تاریخ‌ها
    cashiers = ReportEngine.bySeller(d, ALL_FROM, ALL_TO).map { it.seller },
  )

  /** همان بازهٔ زبانه‌های «گزارش‌ها» برای هر انتخاب — ماه/سالِ خورشیدی با `ReportEngine.jalaliMonth` */
  fun range(p: SimpleAsk.Period, today: String): Pair<String, String> {
    val r = when (p.span) {
      SimpleAsk.Span.TODAY -> ReportEngine.Range.TODAY
      SimpleAsk.Span.WEEK -> ReportEngine.Range.WEEK
      SimpleAsk.Span.MONTH -> ReportEngine.Range.MONTH
      SimpleAsk.Span.JMONTH -> ReportEngine.Range.JMONTH
      SimpleAsk.Span.JYEAR -> ReportEngine.Range.JYEAR
      SimpleAsk.Span.ALL -> ReportEngine.Range.ALL
    }
    return ReportEngine.rangeOf(r, today, p.jy, p.jm)
  }

  /** پرسشِ نادرست یا ممنوع ⇒ `Rejected` با همان دلیل‌های `SimpleAsk.validate`؛ هیچ عددی از آن ساخته نمی‌شود */
  fun answer(d: ShopData, q: SimpleAsk.Query, today: String, canSeeMoney: Boolean): Outcome {
    val issues = SimpleAsk.validate(q, canSeeMoney, catalog(d))
    if (issues.isNotEmpty()) return Outcome.Rejected(issues)
    val (from, to) = q.period?.let { range(it, today) } ?: (null to null)
    return Outcome.Ok(if (from != null && to != null) ranged(d, q, from, to) else now(d, q))
  }

  private fun ranged(d: ShopData, q: SimpleAsk.Query, from: String, to: String): Answer {
    fun of(vararg v: Value) = Answer(q, from, to, v.toList())
    return when (q.template) {
      SimpleAsk.Template.SALES -> ReportEngine.sales(d, from, to).let {
        of(Value(Key.NET, it.net), Value(Key.INVOICES, it.count.toDouble()), Value(Key.DISCOUNT, it.discount), Value(Key.RETURNS, it.returnAmount))
      }
      SimpleAsk.Template.PROFIT -> ReportEngine.sales(d, from, to).let {
        of(Value(Key.GROSS_PROFIT, it.grossProfit), Value(Key.COGS, it.cogs), Value(Key.EXPENSES, it.expenses), Value(Key.NET_PROFIT, it.netProfit))
      }
      SimpleAsk.Template.EXPENSES -> of(Value(Key.EXPENSES, ReportEngine.sales(d, from, to).expenses))
      SimpleAsk.Template.PURCHASES -> ReportEngine.purchases(d, from, to).let {
        of(Value(Key.PURCHASE_TOTAL, it.total), Value(Key.PURCHASE_PAID, it.paid), Value(Key.PURCHASE_DEBT, it.debt), Value(Key.PURCHASES, it.count.toDouble()))
      }
      SimpleAsk.Template.CASHIER_SALES -> ReportEngine.bySeller(d, from, to).firstOrNull { it.seller == q.cashier }.let {
        of(Value(Key.NET, it?.net ?: 0.0), Value(Key.INVOICES, (it?.count ?: 0).toDouble()), Value(Key.DISCOUNT, it?.discount ?: 0.0))
      }
      SimpleAsk.Template.CATEGORY_SALES -> ReportEngine.stockInsight(d, from, to).rows.filter { it.product.category.trim() == q.category }.let { rows ->
        of(Value(Key.CATEGORY_NET, rows.sumOf { it.sales }), Value(Key.CATEGORY_PRODUCTS, rows.count { it.sales > 0.0 }.toDouble()))
      }
      SimpleAsk.Template.PRODUCT_SALES ->
        of(Value(Key.PRODUCT_NET, ReportEngine.stockInsight(d, from, to).rows.firstOrNull { it.product.id == q.product }?.sales ?: 0.0))
      else -> now(d, q)
    }
  }

  private fun now(d: ShopData, q: SimpleAsk.Query): Answer = when (q.template) {
    SimpleAsk.Template.PRODUCT_STOCK -> {
      val p = d.products.first { it.id == q.product }
      Answer(q, values = listOf(Value(Key.ON_HAND, ShopStore.stock(d, p.id))), status = ShopStore.stockStatus(d, p))
    }
    SimpleAsk.Template.LOW_STOCK -> ReportEngine.products(d).let { r ->
      Answer(q, values = listOf(Value(Key.OUT, r.out.size.toDouble()), Value(Key.LOW, r.low.size.toDouble())), names = (r.out + r.low).map { it.name })
    }
    SimpleAsk.Template.TOP_SELLING -> Answer(q, values = ReportEngine.products(d).topSelling.map { Value(Key.SOLD, it.quantity, it.product.name) })
    SimpleAsk.Template.SLOW_SELLING -> Answer(q, values = ReportEngine.products(d).slowest.map { Value(Key.SOLD, it.quantity, it.product.name) })
    SimpleAsk.Template.CUSTOMER_DEBT -> {
      val row = ReportEngine.debtors(d).first { it.debtor.id == q.customer }
      Answer(q, values = listOf(Value(Key.DEBT_REMAINING, row.remaining), Value(Key.DEBT_GIVEN, row.given), Value(Key.DEBT_RECEIVED, row.received)))
    }
    SimpleAsk.Template.DEBT_TOTAL -> ReportEngine.debtors(d).let { rows ->
      //  همان کاشیِ «جمع طلب از مشتریان» (مانده‌های مثبت) و شمارِ بدهکارِ «گزارش‌ها»ی کامپیوتر (بیش از نیم افغانی)
      Answer(q, values = listOf(Value(Key.DEBT_TOTAL, rows.sumOf { it.remaining.coerceAtLeast(0.0) }), Value(Key.DEBTORS, rows.count { it.remaining > 0.5 }.toDouble())))
    }
    else -> error("پرسشِ بازه‌دار بی بازه: ${q.template}")
  }

  private const val ALL_FROM = "0000-01-01"
  private const val ALL_TO = "9999-12-31"
}
