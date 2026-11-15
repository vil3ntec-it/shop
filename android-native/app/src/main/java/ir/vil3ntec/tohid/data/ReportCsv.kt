package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSheets

/**
 *  خروجی CSV گزارش‌ها — ستون‌به‌ستون همان چیزی که نسخهٔ وب می‌سازد.
 *
 *  دو نکته که اگر رعایت نشود فایل در اکسل خراب باز می‌شود:
 *
 *   ۱) BOM در ابتدای فایل. بدون آن، اکسل فارسی را با کدگذاری محلی
 *      می‌خواند و متن به هم می‌ریزد — همان کاری که وب هم می‌کند.
 *   ۲) هر خانه در گیومه، و گیومهٔ داخلِ متن دوبار. اسم کالایی که ویرگول
 *      دارد وگرنه ستون‌ها را جابه‌جا می‌کند.
 *
 *  عددها لاتین می‌مانند، نه فارسی: این فایل برای حساب‌وکتاب است و رقمِ
 *  فارسی را اکسل عدد نمی‌شناسد.
 */
object ReportCsv {

  data class Sheet(val name: String, val rows: List<List<String>>) {
    val isEmpty: Boolean get() = rows.size <= 1
  }

  /**
   *  @param reminderTemplate متنِ یادآوریِ همین دستگاه (`DebtReminderPref`، CRM-06)؛ پیش‌فرض همان جملهٔ امروز
   *  @param lineSeller POS-23 — «فروشندهٔ هر ردیف» (`LineSellerPref`): برگهٔ `sellers` به فروشندهٔ ردیف و با ستونِ برگشتی؛
   *    `false` (پیش‌فرض) ⇒ همان برگهٔ امروز
   *  @param phone CRM-15 — شمارهٔ مشتری در برگهٔ قرض‌داران (`customerPhoneExport`)؛ پیش‌فرض همان کامل
   */
  fun of(
    section: String, d: ShopData, from: String, to: String, today: String, reminderTemplate: String = DebtReminder.DEFAULT_TEMPLATE,
    lineSeller: Boolean = false, phone: (String) -> String = { it },
    //  لحظهٔ «اکنون» ِ برگه‌هایی که روزِ بدهی را از زمانِ ثبت می‌شمارند («یادآوری»)؛ آزمون آن را ثابت می‌کند
    now: Long = System.currentTimeMillis(),
  ): Sheet =
    when (section) {
      "sales" -> sales(d, from, to)
      "products" -> products(d, today)
      "debtors" -> debtors(d, today, phone)
      "purchases" -> purchases(d, from, to)
      "expiry" -> expiry(d, today)
      "profit" -> profit(d, from, to)
      "expenses" -> expenses(d, from, to)
      "sellers" -> sellers(d, from, to, lineSeller)
      //  گزارش‌های مشتری (CRM-04، ۱۰، ۰۶، ۱۴) — فقط وقتی `ShopPolicy.crmReports` روشن است دیده می‌شوند
      "aging" -> CustomerInsight.agingSheet(d, today)
      "rfm" -> CustomerInsight.rfmSheet(d, today)
      "duplicates" -> CustomerMerge.duplicatesSheet(d, today)
      "reminders" -> DebtReminder.dueSheet(d, today, template = reminderTemplate, now = now)
      //  شماره‌های جاافتادهٔ اسناد (RPT-17) — فقط وقتی `DocGapsPref` روشن است دیده می‌شود
      "gaps" -> DocGapsSheet.sheet(d, today)
      //  INV-17 — فقط وقتی `ShrinkReportPref` روشن است دیده می‌شود
      "shrink" -> ShrinkLoss.sheet(d, from, to)
      //  «تحلیلِ موجودی» (INV-15، INV-16) — فقط وقتی `ShopPolicy.stockInsight` روشن است دیده می‌شود
      "insight" -> insight(d, from, to)
      //  «نیاز به سفارش» (INV-12) — فقط وقتی `ShopPolicy.stockLevels` روشن است دیده می‌شود
      "reorder" -> reorder(d, today)
      else -> stock(d, today)
    }

  /**
   *  POS-23 — برگهٔ [sheet] ِ صفحه با «فروشندهٔ هر ردیف» ([on] = `LineSellerPref.on`): فقط برگهٔ `sellers` عوض می‌شود (به
   *  فروشندهٔ ردیف و با ستونِ برگشتی)؛ خاموش یا برگهٔ دیگر ⇒ همان [sheet]، بی هیچ کارِ دیگری.
   */
  fun lineSellers(sheet: Sheet, section: String, d: ShopData, from: String, to: String, on: Boolean): Sheet =
    if (on && section == "sellers") sellers(d, from, to, true) else sheet

  /**
   *  RPT-13 — ستونی که «مقایسه با دورهٔ قبل» در خودِ جدول کنارش «دورهٔ قبل» و «تغییر» می‌گذارد؛ فقط برگه‌هایی که ستونِ
   *  اولشان کلیدِ پایدار است (شرحِ سود و زیان، فروشنده). برگهٔ فهرستیِ سند (فاکتور، خرید، مصرف) ⇒ `null` (آن‌ها: [compareGroup]).
   */
  fun compareColumn(section: String): Int? = when (section) {
    "profit" -> 1
    "sellers" -> 2
    else -> null
  }

  /** برگهٔ [cur] با دو ستونِ دورهٔ قبل از برگهٔ [prev] (`core/report/ReportCompare`)؛ جفت‌نشدنی ⇒ همان [cur] */
  fun compared(section: String, cur: Sheet, prev: Sheet, prevTitle: String, changeTitle: String): Sheet {
    val col = compareColumn(section) ?: return cur
    return ir.vil3ntec.tohid.core.report.ReportCompare.column(cur.rows, prev.rows, col, prevTitle, changeTitle)?.let { cur.copy(rows = it) } ?: cur
  }

  /**
   *  RPT-13 — برگهٔ فهرستیِ سند در «مقایسه»: (ستونِ گروه، ستونِ جمع). فاکتور ⇐ «روش پرداخت» و «مبلغ نهایی» (بی فاکتورِ
   *  لغوشده)، خرید ⇐ «تأمین‌کننده» و «جمع»، مصرف ⇐ «دسته» و «مبلغ». برگه‌های دیگر ⇒ `null`.
   */
  fun compareGroup(section: String): Pair<Int, Int>? = when (section) {
    "sales" -> 7 to 4
    "purchases" -> 1 to 6
    "expenses" -> 2 to 3
    else -> null
  }

  /**
   *  برگهٔ فهرستیِ [cur] گروه‌شده با [compareGroup] و دو ستونِ دورهٔ قبل از [prev] (`ReportCompare.grouped`): شمار و جمعِ هر
   *  گروه، «دورهٔ قبل» و «تغییر»؛ گروهِ بی نام «—». برگهٔ دیگر یا جفت‌نشدنی ⇒ همان [cur].
   */
  fun comparedList(section: String, cur: Sheet, prev: Sheet, countTitle: String, prevTitle: String, changeTitle: String): Sheet {
    val (key, value) = compareGroup(section) ?: return cur
    val isSales = section == "sales"
    val counts: (List<String>) -> Boolean = { r -> !isSales || r.getOrNull(SALE_STATUS) != CANCELLED }
    return ir.vil3ntec.tohid.core.report.ReportCompare.grouped(cur.rows, prev.rows, key, value, countTitle, "—", prevTitle, changeTitle, counts)
      ?.let { cur.copy(rows = it) } ?: cur
  }

  /** ستونِ «وضعیت» ِ برگهٔ فروش و خانهٔ فاکتورِ لغوشده‌اش (همان [sales]) */
  private const val SALE_STATUS = 8
  private val CANCELLED: String get() = StrSheets.cancelledZ

  /** سود و زیانِ بازه — همان عددهای `ReportEngine.sales`، سطر به سطر */
  private fun profit(d: ShopData, from: String, to: String): Sheet {
    val r = ReportEngine.sales(d, from, to)
    val rows = mutableListOf(listOf(StrSheets.description, StrSheets.amount))
    rows += listOf(StrSheets.invoiceCount, r.count.toString())
    rows += listOf(StrSheets.grossSales, round(r.gross))
    rows += listOf(StrSheets.discount, round(r.discount))
    rows += listOf(StrSheets.netSales, round(r.net))
    rows += listOf(StrSheets.returns, round(r.returnAmount))
    rows += listOf(StrSheets.cogs, round(r.cogs))
    rows += listOf(StrSheets.grossProfit, round(r.grossProfit))
    rows += listOf(StrSheets.expenses, round(r.expenses))
    rows += listOf(StrSheets.netProfit, round(r.netProfit))
    return Sheet(StrSheets.nameProfit(from, to), rows)
  }

  private fun expenses(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(listOf(StrSheets.date, StrSheets.title, StrSheets.category, StrSheets.amount, StrSheets.note))
    d.expenses.filter { it.date in from..to }.sortedBy { it.date }.forEach { e ->
      rows += listOf(e.date, e.title, e.category, round(e.amount), e.notes)
    }
    return Sheet(StrSheets.nameExpenses(from, to), rows)
  }

  private fun sellers(d: ShopData, from: String, to: String, lineSeller: Boolean = false): Sheet {
    val rows = mutableListOf(listOf(StrSheets.seller, StrSheets.invoiceCount, StrSheets.netSales, StrSheets.discount))
    //  POS-23: فروشندهٔ هر ردیف ⇒ همان برگه به فروشندهٔ ردیف، با ستونِ برگشتیِ ردیف‌های همان فروشنده (پایهٔ پورسانت)
    if (lineSeller) rows[0] = rows[0] + StrSheets.returned
    ReportEngine.bySeller(d, from, to, lineSeller).forEach { s ->
      rows += listOf(s.seller, s.count.toString(), round(s.net), round(s.discount)) + (if (lineSeller) listOf(round(s.returns)) else emptyList())
    }
    return Sheet(StrSheets.nameSellers(from, to), rows)
  }

  private fun purchases(d: ShopData, from: String, to: String): Sheet {
    val names = d.suppliers.associate { it.id to it.name }
    val products = d.products.associate { it.id to it.name }
    val rows = mutableListOf(listOf(StrSheets.date, StrSheets.supplier, StrSheets.product, StrSheets.quantity, StrSheets.unit, StrSheets.purchasePrice, StrSheets.total, StrSheets.paid, StrSheets.debt))
    ReportEngine.purchases(d, from, to).rows.forEach { p ->
      rows += listOf(
        p.date, names[p.supplierId].orEmpty(), products[p.productId].orEmpty(),
        ir.vil3ntec.tohid.qty(p.quantity), p.unit, round(p.purchasePrice),
        round(p.totalAmount), round(p.paidAmount), round(p.debt),
      )
    }
    return Sheet(StrSheets.namePurchases(from, to), rows)
  }

  private fun expiry(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(listOf(StrSheets.product, StrSheets.batch, StrSheets.productionDate, StrSheets.expiryDate, StrSheets.daysLeft, StrSheets.quantityLeft, StrSheets.unit, StrSheets.status))
    ExpiryEngine.lots(d, today).forEach { l ->
      rows += listOf(
        l.product.name, l.entry.batch, l.entry.productionDate, l.entry.expiryDate, l.daysLeft.toString(),
        ir.vil3ntec.tohid.qty(l.remaining), l.product.unit,
        when { l.expired -> StrSheets.expired; l.daysLeft <= ExpiryEngine.nearDays -> StrSheets.nearExpiry; else -> StrSheets.healthy },
      )
    }
    return Sheet(StrSheets.nameExpiry(today), rows)
  }

  private fun sales(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(
      listOf(
        StrSheets.invoiceNo, StrSheets.date, StrSheets.grossTotal, StrSheets.discount, StrSheets.finalTotal,
        StrSheets.paid, StrSheets.remaining, StrSheets.payMethod, StrSheets.status, StrSheets.cancelReason, StrSheets.seller,
      )
    )
    d.sales
      .filter { it.date >= from && it.date <= to }
      .sortedBy { it.createdAt }
      .forEach { s ->
        rows += listOf(
          s.invoiceLabel() ?: "",
          s.date,
          round(s.total), round(s.discount), round(s.finalTotal),
          round(s.paidAmount), round(s.remaining),
          ir.vil3ntec.tohid.print.Receipts.payName(PayFlow.payLabel(s, cash = StrSheets.cashPay)),  // نامِ روش به زبانِ برنامه (فارسی ⇒ همان)
          if (s.status == "cancelled") CANCELLED else StrSheets.recorded,
          s.cancelReason,
          s.seller,
        )
      }
    return Sheet(StrSheets.nameSales(from, to), rows)
  }

  /**
   *  تحلیلِ موجودی: ردهٔ ABC بر پایهٔ فروشِ خالصِ `from..to` و ارزشِ موجودیِ همین حالا به قیمتِ خرید
   *  و فروش — همان عددهای `ReportEngine.stockInsight` (حساب در `core`)؛ جمعِ ستون‌های ارزش همان جمعِ کاشی‌هاست.
   *  کالای بی موجودیِ مثبت ستونِ ارزش ندارد. سهم‌ها با «٪» نوشته می‌شوند تا جدول جمعشان نزند.
   */
  private fun insight(d: ShopData, from: String, to: String): Sheet {
    val s = ReportEngine.stockInsight(d, from, to)
    val values = s.valuation.lines.associateBy { it.id }
    val rows = mutableListOf(
      listOf(
        StrSheets.row, StrSheets.productName, StrSheets.category, StrSheets.abcClass, StrSheets.rangeNetSales, StrSheets.salesShare, StrSheets.cumulativeShare,
        StrSheets.stock, StrSheets.costValue, StrSheets.retailValue,
      )
    )
    s.rows.forEachIndexed { i, r ->
      val v = values[r.product.id]
      rows += listOf(
        (i + 1).toString(), r.product.name, r.product.category, r.abc.name, round(r.sales),
        pct(r.shareBp), pct(r.cumulativeBp), trim(r.onHand),
        v?.let { round(it.costMinor / 100.0) }.orEmpty(), v?.let { round(it.retailMinor / 100.0) }.orEmpty(),
      )
    }
    return Sheet(StrSheets.nameInsight(from, to), rows)
  }

  /** کالاهایی که به نقطهٔ سفارش، حد کم بودن یا صفر رسیده‌اند، با پیشنهادِ «تا بیشینه» — `WarehouseEngine.reorderList` */
  private fun reorder(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf(StrSheets.productName, StrSheets.category, StrSheets.unit, StrSheets.stock, StrSheets.lowLimit, StrSheets.reorderPoint, StrSheets.maximum, StrSheets.status, StrSheets.suggestedQuantity)
    )
    WarehouseEngine.reorderList(d).forEach { r ->
      val p = r.product
      rows += listOf(
        p.name, p.category, p.unit, trim(r.onHand),
        if (p.minStock > 0) trim(p.minStock) else "", p.reorderPoint?.let { trim(it) }.orEmpty(), p.maxStock?.let { trim(it) }.orEmpty(),
        when (r.state) {
          ir.vil3ntec.tohid.core.stock.StockLevelRules.State.OUT -> StrSheets.outOfStock
          ir.vil3ntec.tohid.core.stock.StockLevelRules.State.BELOW_MIN -> StrSheets.belowLow
          else -> StrSheets.timeToOrder
        },
        r.suggest?.let { trim(it) } ?: StrSheets.noMaximum,
      )
    }
    return Sheet(StrSheets.nameReorder(today), rows)
  }

  /** بیسیس‌پوینت ⇒ «۲۶.۸٪» */
  private fun pct(bp: Long): String = "${bp / 100}.${(bp % 100) / 10}٪"

  private fun products(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf(
        StrSheets.productName, StrSheets.category, StrSheets.unit, StrSheets.purchasePrice, StrSheets.salePrice,
        StrSheets.stock, StrSheets.minStock, StrSheets.status, StrSheets.quantitySold, StrSheets.profit,
      )
    )
    d.products.forEach { p ->
      val (sold, profit) = ReportEngine.productStat(d, p.id)
      rows += listOf(
        p.name, p.category, p.unit,
        round(p.purchasePrice), round(p.salePrice),
        trim(ShopStore.stock(d, p.id)), trim(p.minStock),
        when (ShopStore.stockStatus(d, p)) {
          "out" -> StrSheets.outOfStock
          "low" -> StrSheets.lowStock
          else -> StrSheets.enoughStock
        },
        trim(sold), round(profit),
      )
    }
    return Sheet(StrSheets.nameProducts(today), rows)
  }

  /**
   *  صورت‌حسابِ یک مشتری — هر برد و رسید به ترتیبِ زمان، با ماندهٔ پس از
   *  هر ردیف. ماندهٔ آخر همان «الباقی»ِ صفحهٔ حساب است (`ShopStore.debt`)،
   *  پس کاغذی که به مشتری داده می‌شود با برنامه یکی است.
   */
  fun statement(d: ShopData, debtorId: String, today: String): Sheet {
    val debtor = d.debtors.find { it.id == debtorId }
    val rows = mutableListOf(listOf(StrSheets.date, StrSheets.description, StrSheets.given, StrSheets.receivedCol, StrSheets.balance))
    var balance = 0.0
    val own = CustomerAlias.of(d, debtorId)  // CRM-10: با سندهای ادغام‌شده‌های پیوندی
    d.transactions.filter { it.debtorId in own }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .forEach { tx ->
        val give = tx.type == "give"
        balance += if (give) tx.amount else -tx.amount
        rows += listOf(
          tx.date,
          tx.notes.ifBlank { if (give) StrSheets.given else StrSheets.receivedCol },
          if (give) round(tx.amount) else "",
          if (give) "" else round(tx.amount),
          round(balance),
        )
      }
    val name = debtor?.name?.replace(Regex("[\\\\/:*?\"<>|]"), " ")?.trim().orEmpty()
    return Sheet(StrSheets.nameStatement(name.ifBlank { StrSheets.customerFallback }, today), rows)
  }

  /** گزارشِ اختلافِ انبارگردانی — پیش از ثبت، برای بایگانی یا بازبینی */
  fun stockCount(lines: List<WarehouseEngine.CountLine>, today: String): Sheet {
    val rows = mutableListOf(listOf(StrSheets.productNameShort, StrSheets.unit, StrSheets.appStock, StrSheets.counted, StrSheets.difference, StrSheets.differenceValue))
    lines.forEach { l ->
      rows += listOf(l.product.name, l.product.unit, trim(l.system), trim(l.counted), trim(l.diff), round(l.value))
    }
    return Sheet(StrSheets.nameStockCount(today), rows)
  }

  private fun debtors(d: ShopData, today: String, phone: (String) -> String = { it }): Sheet {
    val rows = mutableListOf(
      listOf(StrSheets.debtorName, StrSheets.phone, StrSheets.debt, StrSheets.lastTxDate, StrSheets.note)
    )
    d.debtors.forEach { debtor ->
      val last = DebtRows.of(d)[debtor.id]?.maxByOrNull { it.createdAt }
      rows += listOf(
        debtor.name, phone(debtor.phone),
        round(ShopStore.debt(d, debtor.id)),
        last?.date ?: "",
        debtor.notes,
      )
    }
    return Sheet(StrSheets.nameDebtors(today), rows)
  }

  private fun stock(d: ShopData, today: String): Sheet = moves(d, d.stockMovements, StrSheets.nameStockMoves(today))

  /**
   *  INV-02 — گردشِ موجودی با کاربر و دلیلِ هر حرکت (`StockMovement.userId/reason`؛ نداشته ⇒ خالی).
   *  صفحهٔ «حرکاتِ انبار» ِ یک کالا همین را با حرکت‌های صافی‌شدهٔ همان صفحه می‌سازد.
   */
  fun moves(d: ShopData, moves: List<StockMovement>, name: String): Sheet {
    val rows = mutableListOf(listOf(StrSheets.date, StrSheets.productCol, StrSheets.moveType, StrSheets.count, StrSheets.details, StrSheets.user, StrSheets.reason))
    val names = d.products.associate { it.id to it.name }
    moves.sortedBy { it.createdAt }.forEach { m ->
      rows += listOf(
        m.date,
        names[m.productId] ?: StrSheets.deletedMark,
        movementLabel(m.type),
        trim(m.qty),
        m.notes,
        m.userId?.trim().orEmpty(),
        m.reason?.trim().orEmpty(),
      )
    }
    return Sheet(name, rows)
  }

  /** متنِ نهایی فایل، آمادهٔ نوشتن */
  fun text(sheet: Sheet): String = buildString {
    append('﻿')
    sheet.rows.forEach { row ->
      append(row.joinToString(",") { cell -> "\"" + cell.replace("\"", "\"\"") + "\"" })
      append("\r\n")
    }
  }

  private fun round(value: Double): String = Math.round(value).toString()

  private fun trim(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)

  /** همان برچسب‌هایی که صفحهٔ انبار نشان می‌دهد */
  private fun movementLabel(type: String): String = when (type) {
    "purchase_in" -> StrSheets.movePurchaseIn
    "sale" -> StrSheets.moveSale
    "customer_return" -> StrSheets.moveCustomerReturn
    "supplier_return" -> StrSheets.moveSupplierReturn
    "adjustment" -> StrSheets.moveAdjustment
    "count_adjust" -> StrSheets.moveCountAdjust
    "damage" -> StrSheets.waste
    //  انتقال بین محل‌ها (موتورِ تازهٔ موجودی): دو ردیف، خروجیِ منفی و ورودیِ مثبت
    "transfer_out" -> StrSheets.moveTransferOut
    "transfer_in" -> StrSheets.moveTransferIn
    "transfer" -> StrSheets.moveTransfer
    else -> type
  }
}
