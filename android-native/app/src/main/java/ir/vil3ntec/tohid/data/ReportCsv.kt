package ir.vil3ntec.tohid.data

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
   */
  fun of(
    section: String, d: ShopData, from: String, to: String, today: String, reminderTemplate: String = DebtReminder.DEFAULT_TEMPLATE,
    lineSeller: Boolean = false,
  ): Sheet =
    when (section) {
      "sales" -> sales(d, from, to)
      "products" -> products(d, today)
      "debtors" -> debtors(d, today)
      "purchases" -> purchases(d, from, to)
      "expiry" -> expiry(d, today)
      "profit" -> profit(d, from, to)
      "expenses" -> expenses(d, from, to)
      "sellers" -> sellers(d, from, to, lineSeller)
      //  گزارش‌های مشتری (CRM-04، ۱۰، ۰۶، ۱۴) — فقط وقتی `ShopPolicy.crmReports` روشن است دیده می‌شوند
      "aging" -> CustomerInsight.agingSheet(d, today)
      "rfm" -> CustomerInsight.rfmSheet(d, today)
      "duplicates" -> CustomerMerge.duplicatesSheet(d, today)
      "reminders" -> DebtReminder.dueSheet(d, today, template = reminderTemplate)
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
  private const val CANCELLED = "لغو‌شده"  // داده: خانهٔ «وضعیت» ِ برگهٔ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)

  /** سود و زیانِ بازه — همان عددهای `ReportEngine.sales`، سطر به سطر */
  private fun profit(d: ShopData, from: String, to: String): Sheet {
    val r = ReportEngine.sales(d, from, to)
    val rows = mutableListOf(listOf("شرح", "مبلغ"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("تعدادِ فاکتور", r.count.toString())  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("فروشِ ناخالص", round(r.gross))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("تخفیف", round(r.discount))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("فروشِ خالص", round(r.net))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("مرجوعی", round(r.returnAmount))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("بهای تمام‌شدهٔ کالا", round(r.cogs))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("سودِ ناخالص", round(r.grossProfit))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("مصارف", round(r.expenses))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    rows += listOf("سودِ خالص", round(r.netProfit))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    return Sheet("سود-و-زیان-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun expenses(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(listOf("تاریخ", "عنوان", "دسته", "مبلغ", "یادداشت"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    d.expenses.filter { it.date in from..to }.sortedBy { it.date }.forEach { e ->
      rows += listOf(e.date, e.title, e.category, round(e.amount), e.notes)
    }
    return Sheet("مصارف-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun sellers(d: ShopData, from: String, to: String, lineSeller: Boolean = false): Sheet {
    val rows = mutableListOf(listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص", "تخفیف"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    //  POS-23: فروشندهٔ هر ردیف ⇒ همان برگه به فروشندهٔ ردیف، با ستونِ برگشتیِ ردیف‌های همان فروشنده (پایهٔ پورسانت)
    if (lineSeller) rows[0] = rows[0] + "برگشتی"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    ReportEngine.bySeller(d, from, to, lineSeller).forEach { s ->
      rows += listOf(s.seller, s.count.toString(), round(s.net), round(s.discount)) + (if (lineSeller) listOf(round(s.returns)) else emptyList())
    }
    return Sheet("فروشِ-هر-کاربر-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun purchases(d: ShopData, from: String, to: String): Sheet {
    val names = d.suppliers.associate { it.id to it.name }
    val products = d.products.associate { it.id to it.name }
    val rows = mutableListOf(listOf("تاریخ", "تأمین‌کننده", "کالا", "مقدار", "واحد", "قیمت خرید", "جمع", "پرداختی", "بدهی"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    ReportEngine.purchases(d, from, to).rows.forEach { p ->
      rows += listOf(
        p.date, names[p.supplierId].orEmpty(), products[p.productId].orEmpty(),
        ir.vil3ntec.tohid.qty(p.quantity), p.unit, round(p.purchasePrice),
        round(p.totalAmount), round(p.paidAmount), round(p.debt),
      )
    }
    return Sheet("گزارش-خرید-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun expiry(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(listOf("کالا", "سری ساخت", "تاریخ تولید", "تاریخ انقضا", "روز مانده", "مقدارِ مانده", "واحد", "وضعیت"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    ExpiryEngine.lots(d, today).forEach { l ->
      rows += listOf(
        l.product.name, l.entry.batch, l.entry.productionDate, l.entry.expiryDate, l.daysLeft.toString(),
        ir.vil3ntec.tohid.qty(l.remaining), l.product.unit,
        when { l.expired -> "منقضی"; l.daysLeft <= ExpiryEngine.nearDays -> "نزدیک به انقضا"; else -> "سالم" },  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
      )
    }
    return Sheet("گزارش-انقضا-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun sales(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(
      listOf(
        "شماره فاکتور", "تاریخ", "مبلغ کل", "تخفیف", "مبلغ نهایی",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
        "پرداختی", "باقی‌مانده", "روش پرداخت", "وضعیت", "دلیلِ لغو", "فروشنده",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
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
          PayFlow.payLabel(s, cash = "نقدی"),  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          if (s.status == "cancelled") CANCELLED else "ثبت‌شده",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          s.cancelReason,
          s.seller,
        )
      }
    return Sheet("گزارش-فروش-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
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
        "ردیف", "نام محصول", "دسته", "ردهٔ ABC", "فروشِ خالصِ بازه", "سهم از فروش", "سهمِ انباشته",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
        "موجودی", "ارزشِ خرید", "ارزشِ فروش",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
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
    return Sheet("تحلیل-موجودی-$from-تا-$to", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  /** کالاهایی که به نقطهٔ سفارش، حد کم بودن یا صفر رسیده‌اند، با پیشنهادِ «تا بیشینه» — `WarehouseEngine.reorderList` */
  private fun reorder(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf("نام محصول", "دسته", "واحد", "موجودی", "حد کم بودن", "نقطهٔ سفارش", "بیشینه", "وضعیت", "مقدارِ پیشنهادی")  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    )
    WarehouseEngine.reorderList(d).forEach { r ->
      val p = r.product
      rows += listOf(
        p.name, p.category, p.unit, trim(r.onHand),
        if (p.minStock > 0) trim(p.minStock) else "", p.reorderPoint?.let { trim(it) }.orEmpty(), p.maxStock?.let { trim(it) }.orEmpty(),
        when (r.state) {
          ir.vil3ntec.tohid.core.stock.StockLevelRules.State.OUT -> "تمام‌شده"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          ir.vil3ntec.tohid.core.stock.StockLevelRules.State.BELOW_MIN -> "زیرِ حد کم بودن"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          else -> "وقتِ سفارش"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
        },
        r.suggest?.let { trim(it) } ?: "بیشینه تعریف نشده",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
      )
    }
    return Sheet("نیاز-به-سفارش-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  /** بیسیس‌پوینت ⇒ «۲۶.۸٪» */
  private fun pct(bp: Long): String = "${bp / 100}.${(bp % 100) / 10}٪"

  private fun products(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf(
        "نام محصول", "دسته", "واحد", "قیمت خرید", "قیمت فروش",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
        "موجودی", "حداقل موجودی", "وضعیت", "تعداد فروخته‌شده", "سود",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
      )
    )
    d.products.forEach { p ->
      val (sold, profit) = ReportEngine.productStat(d, p.id)
      rows += listOf(
        p.name, p.category, p.unit,
        round(p.purchasePrice), round(p.salePrice),
        trim(ShopStore.stock(d, p.id)), trim(p.minStock),
        when (ShopStore.stockStatus(d, p)) {
          "out" -> "تمام‌شده"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          "low" -> "موجودی کم"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          else -> "موجودی کافی"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
        },
        trim(sold), round(profit),
      )
    }
    return Sheet("گزارش-محصولات-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  /**
   *  صورت‌حسابِ یک مشتری — هر برد و رسید به ترتیبِ زمان، با ماندهٔ پس از
   *  هر ردیف. ماندهٔ آخر همان «الباقی»ِ صفحهٔ حساب است (`ShopStore.debt`)،
   *  پس کاغذی که به مشتری داده می‌شود با برنامه یکی است.
   */
  fun statement(d: ShopData, debtorId: String, today: String): Sheet {
    val debtor = d.debtors.find { it.id == debtorId }
    val rows = mutableListOf(listOf("تاریخ", "شرح", "برد", "رسید", "مانده"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    var balance = 0.0
    d.transactions.filter { it.debtorId == debtorId }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .forEach { tx ->
        val give = tx.type == "give"
        balance += if (give) tx.amount else -tx.amount
        rows += listOf(
          tx.date,
          tx.notes.ifBlank { if (give) "برد" else "رسید" },  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
          if (give) round(tx.amount) else "",
          if (give) "" else round(tx.amount),
          round(balance),
        )
      }
    val name = debtor?.name?.replace(Regex("[\\\\/:*?\"<>|]"), " ")?.trim().orEmpty()
    return Sheet("صورت‌حساب-${name.ifBlank { "مشتری" }}-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  /** گزارشِ اختلافِ انبارگردانی — پیش از ثبت، برای بایگانی یا بازبینی */
  fun stockCount(lines: List<WarehouseEngine.CountLine>, today: String): Sheet {
    val rows = mutableListOf(listOf("نام کالا", "واحد", "موجودی برنامه", "شمرده‌شده", "اختلاف", "ارزش اختلاف"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    lines.forEach { l ->
      rows += listOf(l.product.name, l.product.unit, trim(l.system), trim(l.counted), trim(l.diff), round(l.value))
    }
    return Sheet("انبارگردانی-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun debtors(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf("نام قرض‌دار", "شماره تماس", "بدهی", "تاریخ آخرین تراکنش", "یادداشت")  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    )
    d.debtors.forEach { debtor ->
      val last = d.transactions.filter { it.debtorId == debtor.id }.maxByOrNull { it.createdAt }
      rows += listOf(
        debtor.name, debtor.phone,
        round(ShopStore.debt(d, debtor.id)),
        last?.date ?: "",
        debtor.notes,
      )
    }
    return Sheet("گزارش-قرض‌داران-$today", rows)  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
  }

  private fun stock(d: ShopData, today: String): Sheet = moves(d, d.stockMovements, "گردش-موجودی-$today")  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)

  /**
   *  INV-02 — گردشِ موجودی با کاربر و دلیلِ هر حرکت (`StockMovement.userId/reason`؛ نداشته ⇒ خالی).
   *  صفحهٔ «حرکاتِ انبار» ِ یک کالا همین را با حرکت‌های صافی‌شدهٔ همان صفحه می‌سازد.
   */
  fun moves(d: ShopData, moves: List<StockMovement>, name: String): Sheet {
    val rows = mutableListOf(listOf("تاریخ", "محصول", "نوع حرکت", "تعداد", "توضیحات", "کاربر", "دلیل"))  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    val names = d.products.associate { it.id to it.name }
    moves.sortedBy { it.createdAt }.forEach { m ->
      rows += listOf(
        m.date,
        names[m.productId] ?: "(حذف‌شده)",  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
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
    "purchase_in" -> "ورود خرید"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "sale" -> "فروش"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "customer_return" -> "مرجوعی مشتری"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "supplier_return" -> "برگشت به تأمین‌کننده"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "adjustment" -> "اصلاح موجودی"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "count_adjust" -> "اصلاحِ شمارش"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "damage" -> "ضایعات"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    //  انتقال بین محل‌ها (موتورِ تازهٔ موجودی): دو ردیف، خروجیِ منفی و ورودیِ مثبت
    "transfer_out" -> "انتقالِ خروجی"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "transfer_in" -> "انتقالِ ورودی"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    "transfer" -> "انتقال"  // داده: برگه و ستونِ خروجیِ اکسل (منتظرِ تصمیمِ صاحب‌کار)
    else -> type
  }
}
