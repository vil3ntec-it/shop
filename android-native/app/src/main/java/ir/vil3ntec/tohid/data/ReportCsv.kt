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

  fun of(section: String, d: ShopData, from: String, to: String, today: String): Sheet =
    when (section) {
      "sales" -> sales(d, from, to)
      "products" -> products(d, today)
      "debtors" -> debtors(d, today)
      "purchases" -> purchases(d, from, to)
      "expiry" -> expiry(d, today)
      "profit" -> profit(d, from, to)
      "expenses" -> expenses(d, from, to)
      "sellers" -> sellers(d, from, to)
      else -> stock(d, today)
    }

  /** سود و زیانِ بازه — همان عددهای `ReportEngine.sales`، سطر به سطر */
  private fun profit(d: ShopData, from: String, to: String): Sheet {
    val r = ReportEngine.sales(d, from, to)
    val rows = mutableListOf(listOf("شرح", "مبلغ"))
    rows += listOf("تعدادِ فاکتور", r.count.toString())
    rows += listOf("فروشِ ناخالص", round(r.gross))
    rows += listOf("تخفیف", round(r.discount))
    rows += listOf("فروشِ خالص", round(r.net))
    rows += listOf("مرجوعی", round(r.returnAmount))
    rows += listOf("بهای تمام‌شدهٔ کالا", round(r.cogs))
    rows += listOf("سودِ ناخالص", round(r.grossProfit))
    rows += listOf("مصارف", round(r.expenses))
    rows += listOf("سودِ خالص", round(r.netProfit))
    return Sheet("سود-و-زیان-$from-تا-$to", rows)
  }

  private fun expenses(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(listOf("تاریخ", "عنوان", "دسته", "مبلغ", "یادداشت"))
    d.expenses.filter { it.date in from..to }.sortedBy { it.date }.forEach { e ->
      rows += listOf(e.date, e.title, e.category, round(e.amount), e.notes)
    }
    return Sheet("مصارف-$from-تا-$to", rows)
  }

  private fun sellers(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(listOf("فروشنده", "تعدادِ فاکتور", "فروشِ خالص", "تخفیف"))
    ReportEngine.bySeller(d, from, to).forEach { s -> rows += listOf(s.seller, s.count.toString(), round(s.net), round(s.discount)) }
    return Sheet("فروشِ-هر-کاربر-$from-تا-$to", rows)
  }

  private fun purchases(d: ShopData, from: String, to: String): Sheet {
    val names = d.suppliers.associate { it.id to it.name }
    val products = d.products.associate { it.id to it.name }
    val rows = mutableListOf(listOf("تاریخ", "تأمین‌کننده", "کالا", "مقدار", "واحد", "قیمت خرید", "جمع", "پرداختی", "بدهی"))
    ReportEngine.purchases(d, from, to).rows.forEach { p ->
      rows += listOf(
        p.date, names[p.supplierId].orEmpty(), products[p.productId].orEmpty(),
        ir.vil3ntec.tohid.qty(p.quantity), p.unit, round(p.purchasePrice),
        round(p.totalAmount), round(p.paidAmount), round(p.debt),
      )
    }
    return Sheet("گزارش-خرید-$from-تا-$to", rows)
  }

  private fun expiry(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(listOf("کالا", "سری ساخت", "تاریخ تولید", "تاریخ انقضا", "روز مانده", "مقدارِ مانده", "واحد", "وضعیت"))
    ExpiryEngine.lots(d, today).forEach { l ->
      rows += listOf(
        l.product.name, l.entry.batch, l.entry.productionDate, l.entry.expiryDate, l.daysLeft.toString(),
        ir.vil3ntec.tohid.qty(l.remaining), l.product.unit,
        when { l.expired -> "منقضی"; l.daysLeft <= ExpiryEngine.NEAR_DAYS -> "نزدیک به انقضا"; else -> "سالم" },
      )
    }
    return Sheet("گزارش-انقضا-$today", rows)
  }

  private fun sales(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(
      listOf(
        "شماره فاکتور", "تاریخ", "مبلغ کل", "تخفیف", "مبلغ نهایی",
        "پرداختی", "باقی‌مانده", "روش پرداخت", "وضعیت", "دلیلِ لغو", "فروشنده",
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
          PayFlow.payLabel(s, cash = "نقدی"),
          if (s.status == "cancelled") "لغو‌شده" else "ثبت‌شده",
          s.cancelReason,
          s.seller,
        )
      }
    return Sheet("گزارش-فروش-$from-تا-$to", rows)
  }

  private fun products(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf(
        "نام محصول", "دسته", "واحد", "قیمت خرید", "قیمت فروش",
        "موجودی", "حداقل موجودی", "وضعیت", "تعداد فروخته‌شده", "سود",
      )
    )
    d.products.forEach { p ->
      val (sold, profit) = ReportEngine.productStat(d, p.id)
      rows += listOf(
        p.name, p.category, p.unit,
        round(p.purchasePrice), round(p.salePrice),
        trim(ShopStore.stock(d, p.id)), trim(p.minStock),
        when (ShopStore.stockStatus(d, p)) {
          "out" -> "تمام‌شده"
          "low" -> "موجودی کم"
          else -> "موجودی کافی"
        },
        trim(sold), round(profit),
      )
    }
    return Sheet("گزارش-محصولات-$today", rows)
  }

  /**
   *  صورت‌حسابِ یک مشتری — هر برد و رسید به ترتیبِ زمان، با ماندهٔ پس از
   *  هر ردیف. ماندهٔ آخر همان «الباقی»ِ صفحهٔ حساب است (`ShopStore.debt`)،
   *  پس کاغذی که به مشتری داده می‌شود با برنامه یکی است.
   */
  fun statement(d: ShopData, debtorId: String, today: String): Sheet {
    val debtor = d.debtors.find { it.id == debtorId }
    val rows = mutableListOf(listOf("تاریخ", "شرح", "برد", "رسید", "مانده"))
    var balance = 0.0
    d.transactions.filter { it.debtorId == debtorId }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .forEach { tx ->
        val give = tx.type == "give"
        balance += if (give) tx.amount else -tx.amount
        rows += listOf(
          tx.date,
          tx.notes.ifBlank { if (give) "برد" else "رسید" },
          if (give) round(tx.amount) else "",
          if (give) "" else round(tx.amount),
          round(balance),
        )
      }
    val name = debtor?.name?.replace(Regex("[\\\\/:*?\"<>|]"), " ")?.trim().orEmpty()
    return Sheet("صورت‌حساب-${name.ifBlank { "مشتری" }}-$today", rows)
  }

  /** گزارشِ اختلافِ انبارگردانی — پیش از ثبت، برای بایگانی یا بازبینی */
  fun stockCount(lines: List<WarehouseEngine.CountLine>, today: String): Sheet {
    val rows = mutableListOf(listOf("نام کالا", "واحد", "موجودی برنامه", "شمرده‌شده", "اختلاف", "ارزش اختلاف"))
    lines.forEach { l ->
      rows += listOf(l.product.name, l.product.unit, trim(l.system), trim(l.counted), trim(l.diff), round(l.value))
    }
    return Sheet("انبارگردانی-$today", rows)
  }

  private fun debtors(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(
      listOf("نام قرض‌دار", "شماره تماس", "بدهی", "تاریخ آخرین تراکنش", "یادداشت")
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
    return Sheet("گزارش-قرض‌داران-$today", rows)
  }

  private fun stock(d: ShopData, today: String): Sheet {
    val rows = mutableListOf(listOf("تاریخ", "محصول", "نوع حرکت", "تعداد", "توضیحات"))
    d.stockMovements.sortedBy { it.createdAt }.forEach { m ->
      rows += listOf(
        m.date,
        d.products.find { it.id == m.productId }?.name ?: "(حذف‌شده)",
        movementLabel(m.type),
        trim(m.qty),
        m.notes,
      )
    }
    return Sheet("گردش-موجودی-$today", rows)
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
    "purchase_in" -> "ورود خرید"
    "sale" -> "فروش"
    "customer_return" -> "مرجوعی مشتری"
    "supplier_return" -> "برگشت به تأمین‌کننده"
    "adjustment" -> "اصلاح موجودی"
    "damage" -> "ضایعات"
    else -> type
  }
}
