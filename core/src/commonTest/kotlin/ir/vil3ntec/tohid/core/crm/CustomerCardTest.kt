package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.crm.CustomerCard.Check
import ir.vil3ntec.tohid.core.crm.CustomerCard.Clash
import ir.vil3ntec.tohid.core.crm.CustomerCard.Reason
import ir.vil3ntec.tohid.core.hw.Ean13
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CRM-16: کدِ کارتِ مشتری (رقمِ کنترل، یکتایی، کالا مقدم) و انتخابِ مشتری در صندوق. */
class CustomerCardTest {

  private fun ok(c: Check): String = assertIs<Check.Ok>(c).code
  private fun bad(c: Check): Reason = assertIs<Check.Bad>(c).reason

  @Test fun generatedCodesAreValidEan13WithTheCustomerPrefixAndStable() {
    val seen = HashSet<String>()
    for (i in 0 until 300) {
      val code = ok(CustomerCard.pick("01JCUSTOMER$i", null, null, { null }))
      assertEquals(13, code.length)
      assertTrue(code.startsWith(CustomerCard.PREFIX), code)
      assertTrue(Ean13.isValid(code), code)
      assertTrue(CustomerCard.isGenerated(code))
      //  همان مشتری روی هر دستگاه همان کد
      assertEquals(code, ok(CustomerCard.pick("01JCUSTOMER$i", "", null, { null })))
      seen += code
    }
    assertEquals(300, seen.size)
  }

  @Test fun takenCodesAreSkippedAndANewCardDiffersFromTheOldOne() {
    val first = ok(CustomerCard.pick("A", null, null, { null }))
    val second = ok(CustomerCard.pick("A", null, null, { if (it == first) Clash.PRODUCT else null }))
    assertNotEquals(first, second)
    //  «کارتِ تازه» برای کارتِ گم‌شده: کدِ فعلیِ خودش دوباره داده نمی‌شود
    assertNotEquals(first, ok(CustomerCard.pick("A", null, first, { null })))
    assertEquals(Reason.EXHAUSTED, bad(CustomerCard.pick("A", null, null, { Clash.CUSTOMER }, maxTries = 3)))
  }

  @Test fun manualCodesAreValidatedAndMustBeUniqueInTheShop() {
    assertEquals("12345678", ok(CustomerCard.validate(" 1234-5678 ")))
    assertEquals("12345678", ok(CustomerCard.validate("۱۲۳۴۵۶۷۸")))
    assertEquals(Reason.EMPTY, bad(CustomerCard.validate("  ")))
    assertEquals(Reason.NOT_DIGITS, bad(CustomerCard.validate("C1234567")))
    assertEquals(Reason.LENGTH, bad(CustomerCard.validate("1234567")))
    assertEquals(Reason.LENGTH, bad(CustomerCard.validate("1".repeat(21))))
    //  ۱۳ رقمی: رقمِ کنترل سنجیده می‌شود
    val good = "049123456789".let { it + Ean13.checkDigit(it) }
    assertEquals(good, ok(CustomerCard.validate(good)))
    val typo = good.dropLast(1) + ((good.last() - '0' + 1) % 10)
    assertEquals(Reason.CHECK_DIGIT, bad(CustomerCard.validate(typo)))
    //  یکتایی میانِ مشتریان و کالاها
    assertEquals(Reason.TAKEN_CUSTOMER, bad(CustomerCard.pick("A", good, null, { if (it == good) Clash.CUSTOMER else null })))
    assertEquals(Reason.TAKEN_PRODUCT, bad(CustomerCard.pick("A", good, null, { if (it == good) Clash.PRODUCT else null })))
    assertEquals(good, ok(CustomerCard.pick("A", good, null, { null })))
  }

  @Test fun tillPicksTheCustomerOnlyWhenNoProductReadsTheCode() {
    val card = "049123456789".let { it + Ean13.checkDigit(it) }
    val owners = mapOf(card to "debtor-1")
    val noProduct: (String) -> Boolean = { false }
    assertEquals("debtor-1", CustomerCard.route(true, card, noProduct, owners::get))
    //  رقمِ فارسی از بارکدخوانِ با چیدمانِ فارسی
    val persian = card.map { '۰' + (it - '0') }.joinToString("")
    assertEquals("debtor-1", CustomerCard.route(true, persian, noProduct, owners::get))
    //  همان کد بارکدِ کالا هم هست ⇒ کالا مقدم، مشتری نه
    assertNull(CustomerCard.route(true, card, { it == card }, owners::get))
    //  کدِ ناشناس، کوتاه یا با حرف ⇒ راهِ همیشگیِ کالا
    assertNull(CustomerCard.route(true, "0491111111111", noProduct, owners::get))
    assertNull(CustomerCard.route(true, "1234", noProduct, owners::get))
    assertNull(CustomerCard.route(true, "ABC12345678", noProduct, owners::get))
  }

  @Test fun settingOffLeavesTheTillUnchanged() {
    val card = "049123456789".let { it + Ean13.checkDigit(it) }
    var asked = 0
    assertNull(CustomerCard.route(false, card, { asked++; false }, { asked++; "debtor-1" }))
    assertEquals(0, asked)
  }
}
