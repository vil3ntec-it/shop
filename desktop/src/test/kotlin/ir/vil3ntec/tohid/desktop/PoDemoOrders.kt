package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoBillLineDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoPostedDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo

/**
 *  سفارش‌های ثابتِ آزمون‌های صفحهٔ سفارشِ خرید (PUR-02، PUR-14) روی دفترِ `MoneyDemo` (تأمین‌کنندهٔ «شرکت پخش البرز» با
 *  برنج و روغن): یک پیش‌نویس، یک فرستاده (تأمین‌کنندهٔ بیرون از دفترِ این دستگاه، تا ثبتِ رسید در دفتر چیزی ننویسد)، یک
 *  بخشی‌رسیده با فاکتورِ بیش از رسیده (اختلاف) و یک کامل با فاکتورِ هم‌خوان.
 */
internal object PoDemoOrders {
  const val SUPPLIER_NAME = "شرکت پخش البرز"
  private val now = System.currentTimeMillis() - 86_400_000L

  val lines = listOf(
    PoLineDto("l1", "p-rice", "برنج سیلا ۵ کیلویی", "بسته", 10_000, 95_000),
    PoLineDto("l2", "p-oil", "روغن آفتاب‌گردان", "بوتل", 12_000, 31_000),
  )

  val draft = PurchaseOrderDto(id = "po-d", no = 1043, supplierId = MoneyDemo.SUPPLIER, supplierName = SUPPLIER_NAME, state = "draft", lines = lines, version = 1, createdAt = now)

  val sent = PurchaseOrderDto(
    id = "po-s", no = 1044, supplierId = "s-far", supplierName = "بازرگانی دور", state = "sent", lines = lines, version = 2,
    createdAt = now + 1, sentAt = now + 2,
  )

  private fun posted(line: String, qty: Long, price: Long) = PoPostedDto(line, qty, qty, 0, qty, 0, 0, price, qty * price / 1000)

  /** ۴ بسته برنج رسید؛ فاکتورِ تأمین‌کننده ۶ بسته نوشته ⇒ اختلاف */
  val partial = PurchaseOrderDto(
    id = "po-p", no = 1045, supplierId = MoneyDemo.SUPPLIER, supplierName = SUPPLIER_NAME, state = "sent", lines = lines, version = 2,
    createdAt = now + 3, sentAt = now + 4, receiptCount = 1, firstReceiptAt = now + 5,
    receipts = listOf(PoReceiptDto("r1", "po-p", listOf(PoReceiptLineDto("l1", 4_000, 0, 95_000)), listOf(posted("l1", 4_000, 95_000)), 380_000, createdAt = now + 5)),
    bills = listOf(PoBillDto("b1", "po-p", "INV-500", null, listOf(PoBillLineDto("l1", "p-rice", 6_000, 570_000)), 570_000, createdAt = now + 6)),
  )

  /** همه رسید و فاکتور همان رسیده ⇒ هم‌خوان */
  val complete = PurchaseOrderDto(
    id = "po-c", no = 1046, supplierId = MoneyDemo.SUPPLIER, supplierName = SUPPLIER_NAME, state = "sent", lines = lines, version = 2,
    createdAt = now + 7, sentAt = now + 8, receiptCount = 1, firstReceiptAt = now + 9,
    receipts = listOf(
      PoReceiptDto(
        "r2", "po-c", listOf(PoReceiptLineDto("l1", 10_000, 0, 95_000), PoReceiptLineDto("l2", 12_000, 0, 31_000)),
        listOf(posted("l1", 10_000, 95_000), posted("l2", 12_000, 31_000)), 950_000 + 372_000, createdAt = now + 9,
      ),
    ),
    bills = listOf(
      PoBillDto("b2", "po-c", "INV-600", null, listOf(PoBillLineDto("l1", "p-rice", 10_000, 950_000), PoBillLineDto("l2", "p-oil", 12_000, 372_000)), 1_322_000, createdAt = now + 10),
    ),
  )

  val all = listOf(draft, sent, partial, complete)
}
