package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.backup.WipeAll.Decision
import ir.vil3ntec.tohid.core.backup.WipeAll.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OPS-02 — «پاک‌سازیِ کاملِ اطلاعات»: فقط صاحب، نامِ دقیقِ دکان، «پاک شد» فقط پس از نوشتنِ موفق — شرح سرِ [WipeAll] */
class WipeAllTest {

  private val roles = listOf("owner", "manager", "staff", "cashier", "storekeeper", "picker", "accountant", "courier", "", "OWNER")

  @Test fun onlyTheOwnerSeesTheEraseRow() {
    for (r in roles) assertEquals(r == "owner", WipeAll.visible(r), r)
  }

  @Test fun theRightNameWithHarmlessWritingDifferencesMatches() {
    assertTrue(WipeAll.matches("دکان احمد", "دکان احمد"))
    assertTrue(WipeAll.matches("  دکان احمد ", "دکان احمد"), "فاصلهٔ دو سر")
    assertTrue(WipeAll.matches("سوپر مارکت", "سوپر‌مارکت"), "نیم‌فاصله و فاصله")
    assertTrue(WipeAll.matches("كتاب علي", "کتاب علی"), "ی و ک ِ عربی")
    assertTrue(WipeAll.matches("دکان احمد", "دکانِ احمد"), "کسرهٔ اضافه")
    assertTrue(WipeAll.matches("vill3n shop", "VILL3N Shop"), "حروفِ لاتین")
  }

  @Test fun aWrongPartialOrEmptyNameNeverMatches() {
    assertFalse(WipeAll.matches("", "دکان احمد"))
    assertFalse(WipeAll.matches("دکان", "دکان احمد"))
    assertFalse(WipeAll.matches("دکان احمد و پسران", "دکان احمد"))
    assertFalse(WipeAll.matches("دکان محمود", "دکان احمد"))
    assertFalse(WipeAll.matches("", ""), "نامِ خالیِ دکان هیچ‌وقت با خالی برابر نیست")
    assertFalse(WipeAll.matches("   ", " ‌ "))
  }

  @Test fun aShopWithoutANameAsksForTheButtonWord() {
    assertEquals("دکان احمد", WipeAll.expected("  دکان احمد ", "پاک کن"))
    assertEquals("پاک کن", WipeAll.expected("", "پاک کن"))
    assertEquals("پاک کن", WipeAll.expected("   ", "پاک کن"))
    assertTrue(WipeAll.matches("پاک کن", WipeAll.expected("", "پاک کن")))
  }

  @Test fun rightBeforeWritingTheRoleThenTheNameAreCheckedAgain() {
    assertEquals(Decision.ERASE, WipeAll.decide("owner", "دکان احمد", "دکان احمد"))
    assertEquals(Decision.WRONG_NAME, WipeAll.decide("owner", "دکان", "دکان احمد"))
    assertEquals(Decision.WRONG_NAME, WipeAll.decide("owner", "", "دکان احمد"))
    for (r in roles - "owner") {
      assertEquals(Decision.NOT_OWNER, WipeAll.decide(r, "دکان احمد", "دکان احمد"), "نقش در این فاصله عوض شد: $r")
    }
  }

  @Test fun erasedIsSaidOnlyAfterASuccessfulWrite() {
    assertEquals(Outcome.WIPED, WipeAll.outcome(true))
    assertEquals(Outcome.FAILED, WipeAll.outcome(false))
  }
}
