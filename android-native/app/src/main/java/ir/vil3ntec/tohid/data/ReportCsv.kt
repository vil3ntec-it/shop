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
      else -> stock(d, today)
    }

  private fun sales(d: ShopData, from: String, to: String): Sheet {
    val rows = mutableListOf(
      listOf(
        "شماره فاکتور", "تاریخ", "مبلغ کل", "تخفیف", "مبلغ نهایی",
        "پرداختی", "باقی‌مانده", "روش پرداخت", "وضعیت",
      )
    )
    d.sales
      .filter { it.date >= from && it.date <= to }
      .sortedBy { it.createdAt }
      .forEach { s ->
        rows += listOf(
          s.invoiceNumber?.toString() ?: "",
          s.date,
          round(s.total), round(s.discount), round(s.finalTotal),
          round(s.paidAmount), round(s.remaining),
          if (s.paymentMethod == "credit") "نسیه" else "نقدی",
          if (s.status == "cancelled") "لغو‌شده" else "ثبت‌شده",
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
    else -> type
  }
}
