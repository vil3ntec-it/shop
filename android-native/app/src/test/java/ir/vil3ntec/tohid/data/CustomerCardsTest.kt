package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.crm.CustomerCard
import ir.vil3ntec.tohid.core.hw.Ean13
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CRM-16: دادنِ کارت (یکتا، بی تداخل با کالا، با ردِ دفترچه) و انتخابِ مشتری با کارت در صندوق. */
class CustomerCardsTest {

  private fun ean(first12: String) = first12 + Ean13.checkDigit(first12)
  private val productCode = ean("626000000001")
  private val data = ShopData(
    debtors = listOf(Debtor(id = "c1", name = "احمد"), Debtor(id = "c2", name = "محمود")),
    products = listOf(Product(id = "p1", name = "چای", barcodes = listOf(productCode))),
  )
  private var n = 0
  private fun id() = "a${n++}"

  private fun ok(r: DebtorEngine.Result): ShopData = (r as DebtorEngine.Result.Ok).data
  private fun failed(r: DebtorEngine.Result): String = (r as DebtorEngine.Result.Failed).message
  private fun card(d: ShopData, id: String) = d.debtors.first { it.id == id }.cardCode

  @Test fun `کدِ ساخته‌شده EAN-13 ِ درست است و ردِ دفترچه می‌گذارد`() {
    val d = ok(CustomerCards.assign(data, "c1", null, "2026-11-01", 1L, ::id))
    val code = card(d, "c1")!!
    assertTrue(CustomerCard.isGenerated(code))
    assertEquals(1, d.auditLog.count { it.type == CustomerCards.AUDIT_TYPE && it.refId == "c1" })
    //  کارتِ تازه برای کارتِ گم‌شده کدِ دیگری است
    val again = ok(CustomerCards.assign(d, "c1", "", "2026-11-01", 2L, ::id))
    assertNotEquals(code, card(again, "c1"))
  }

  @Test fun `کدِ کارت در دکان یکتاست و بارکدِ کالا را نمی‌گیرد`() {
    val code = ean("049000000123")
    val d = ok(CustomerCards.assign(data, "c1", code, "2026-11-01", 1L, ::id))
    assertEquals(code, card(d, "c1"))
    assertTrue(failed(CustomerCards.assign(d, "c2", code, "2026-11-01", 1L, ::id)).isNotBlank())
    assertTrue(failed(CustomerCards.assign(d, "c2", productCode, "2026-11-01", 1L, ::id)).isNotBlank())
    //  نیمهٔ بارکدِ کالا هم تداخل است
    assertEquals(CustomerCard.Clash.PRODUCT, CustomerCards.clash(d, "c2", "99" + productCode))
    //  رقمِ کنترلِ غلط
    val typo = code.dropLast(1) + ((code.last() - '0' + 1) % 10)
    assertTrue(failed(CustomerCards.assign(d, "c2", typo, "2026-11-01", 1L, ::id)).isNotBlank())
    //  همان کدِ خودش دوباره ⇒ بی‌تغییر، بی رد
    assertEquals(d, ok(CustomerCards.assign(d, "c1", code, "2026-11-01", 1L, ::id)))
  }

  @Test fun `در صندوق کارت مشتری را انتخاب می‌کند و بارکدِ کالا مقدم است`() {
    val code = ean("049000000123")
    val d = ok(CustomerCards.assign(data, "c1", code, "2026-11-01", 1L, ::id))
    assertEquals("c1", CustomerCards.atTill(true, d, code)?.id)
    //  کالایی که بعداً همین بارکد را گرفت ⇒ کالا، نه مشتری
    val clash = d.copy(products = d.products + Product(id = "p2", name = "قند", barcodes = listOf(code)))
    assertNull(CustomerCards.atTill(true, clash, code))
    assertNull(CustomerCards.atTill(true, d, productCode))
    assertNull(CustomerCards.atTill(true, d, ean("049000000999")))
  }

  @Test fun `کارتِ مشتریِ ادغام‌شده مشتریِ ماندگار را انتخاب می‌کند`() {
    val code = ean("049000000123")
    val d = ok(CustomerCards.assign(data, "c1", code, "2026-11-01", 1L, ::id))
    val merged = d.copy(debtors = d.debtors.map { if (it.id == "c1") it.copy(mergedInto = "c2") else it })
    assertEquals("c2", CustomerCards.atTill(true, merged, code)?.id)
    assertTrue(failed(CustomerCards.assign(merged, "c1", null, "2026-11-01", 1L, ::id)).isNotBlank())
  }

  @Test fun `برداشتنِ کارت با رد است و مشتریِ بی کارت دست نمی‌خورد`() {
    val d = ok(CustomerCards.assign(data, "c1", null, "2026-11-01", 1L, ::id))
    val removed = ok(CustomerCards.remove(d, "c1", "2026-11-01", 2L, ::id))
    assertNull(card(removed, "c1"))
    assertEquals(2, removed.auditLog.count { it.type == CustomerCards.AUDIT_TYPE })
    assertEquals(data, ok(CustomerCards.remove(data, "c2", "2026-11-01", 2L, ::id)))
  }

  @Test fun `تنظیمِ خاموش یعنی صندوق و دادهٔ امروز بی‌تغییر`() {
    val code = ean("049000000123")
    val d = ok(CustomerCards.assign(data, "c1", code, "2026-11-01", 1L, ::id))
    assertNull(CustomerCards.atTill(false, d, code))
    //  مشتریِ بی کارت کلیدِ تازه‌ای در JSON نمی‌گیرد (EncodeDefault NEVER)
    val json = Json { encodeDefaults = true }
    assertFalse(json.encodeToString(Debtor.serializer(), Debtor(id = "c9", name = "x")).contains("cardCode"))
    assertTrue(json.encodeToString(Debtor.serializer(), card(d, "c1")!!.let { Debtor(id = "c1", cardCode = it) }).contains("cardCode"))
  }
}
