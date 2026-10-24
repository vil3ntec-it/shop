package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S2 — فیلدهای اختیاریِ تخفیفِ خودکار روی `Sale` و `SaleItem` (`EncodeDefault.NEVER`).
 *
 *  فاکتور و قلمِ قدیمی (و فاکتورِ کلیدِ خاموش) باید **بایت‌به‌بایت** همان JSONی را
 *  بدهند که پیش از این فیلدها می‌داد — وگرنه نخستین تفاضلِ Sync v1 پس از به‌روزرسانی
 *  برای هر فاکتورِ قدیمی op می‌ساخت. رشته‌های مرجع با `Models.kt`ِ main پیش از این
 *  تغییر (#307) ساخته شده‌اند، با همان تنظیمِ `ShopStore` و `LedgerDiff`.
 */
class SalePromoFieldsTest {

  private val ledger = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private val sale = Sale(
    id = "s1", invoiceNumber = 7, total = 773.0, discount = 0.0, finalTotal = 773.0, paidAmount = 773.0,
    createdAt = 1760000000000, date = "2026-10-19", seller = "ahmad",
  )
  private val item = SaleItem(id = "i1", saleId = "s1", productId = "tea", quantity = 2.0, unitPrice = 300.0, purchasePrice = 100.0, totalPrice = 600.0)

  private val oldSale =
    """{"id":"s1","invoiceNumber":7,"total":773.0,"discountType":"amount","discountValue":0.0,"discount":0.0,"finalTotal":773.0,""" +
      """"paymentMethod":"cash","debtorId":null,"paidAmount":773.0,"remaining":0.0,"status":"completed","debtGiven":0.0,""" +
      """"debtSettled":0.0,"createdAt":1760000000000,"date":"2026-10-19","syncStatus":"pending","cancelReason":"","seller":"ahmad"}"""
  private val oldItem =
    """{"id":"i1","saleId":"s1","productId":"tea","name":"","quantity":2.0,"unitPrice":300.0,"purchasePrice":100.0,"totalPrice":600.0,"returnedQty":0.0}"""

  @Test fun `فاکتور و قلمِ بی تخفیفِ خودکار همان JSONِ پیشین‌اند`() {
    assertEquals(oldSale, ledger.encodeToString(Sale.serializer(), sale))
    assertEquals(oldItem, ledger.encodeToString(SaleItem.serializer(), item))
    //  ردیفِ قدیمی ⇒ خواندن ⇒ نوشتن: همان رشته
    assertEquals(oldSale, ledger.encodeToString(Sale.serializer(), ledger.decodeFromString(Sale.serializer(), oldSale)))
    assertEquals(oldItem, ledger.encodeToString(SaleItem.serializer(), ledger.decodeFromString(SaleItem.serializer(), oldItem)))
    //  تنظیمِ پیش‌فرض (بی encodeDefaults) هم
    assertEquals(
      """{"id":"s1","invoiceNumber":7,"total":773.0,"finalTotal":773.0,"paidAmount":773.0,"createdAt":1760000000000,"date":"2026-10-19","seller":"ahmad"}""",
      Json.encodeToString(Sale.serializer(), sale),
    )
  }

  @Test fun `فیلدهای تازه فقط وقتی هستند نوشته می‌شوند و رفت‌وبرگشت دارند`() {
    val s = sale.copy(promoDiscountMinor = 9900, coupons = listOf("EID5"))
    val i = item.copy(listPriceMinor = 30000, promo = listOf(SalePromo("r1", 3, 6000), SalePromo("r2", 7, 2700)))
    val sj = ledger.encodeToString(Sale.serializer(), s)
    val ij = ledger.encodeToString(SaleItem.serializer(), i)
    assertEquals(oldSale.dropLast(1) + ""","promoDiscountMinor":9900,"coupons":["EID5"]}""", sj)
    assertEquals(oldItem.dropLast(1) + ""","listPriceMinor":30000,"promo":[{"id":"r1","rev":3,"discountMinor":6000},{"id":"r2","rev":7,"discountMinor":2700}]}""", ij)
    assertEquals(s, ledger.decodeFromString(Sale.serializer(), sj))
    assertEquals(i, ledger.decodeFromString(SaleItem.serializer(), ij))
    //  صفرِ صریح («سنجید و چیزی نشد») با null فرق دارد و نوشته می‌شود
    assertTrue(ledger.encodeToString(Sale.serializer(), sale.copy(promoDiscountMinor = 0)).contains("\"promoDiscountMinor\":0"))
    assertFalse(ledger.encodeToString(Sale.serializer(), sale.copy(coupons = emptyList())).contains("coupons"))
  }

  @Test fun `ساختِ پیشین فیلدهای تازه را نادیده می‌گیرد — عددها همان`() {
    //  همان `ignoreUnknownKeys` ِ برنامه: ردیفِ تازه برای مدلی که فیلد را ندارد فقط فیلدِ اضافه است
    val withNew = oldItem.dropLast(1) + ""","listPriceMinor":30000,"promo":[{"id":"r1","rev":3,"discountMinor":6000,"future":1}]}"""
    val back = ledger.decodeFromString(SaleItem.serializer(), withNew)
    assertEquals(item.copy(listPriceMinor = 30000, promo = listOf(SalePromo("r1", 3, 6000))), back)
  }
}
