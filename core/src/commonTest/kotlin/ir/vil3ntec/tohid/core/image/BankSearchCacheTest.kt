package ir.vil3ntec.tohid.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** IMG-BANK-11 (بی اینترنت ⇒ فقط کشِ قدیمی با برچسب) و IMG-BANK-15 (تغییرِ بانک ⇒ کش باطل). */
class BankSearchCacheTest {

  private val minute = 60_000L

  @Test fun img_bank_11_offlineAnswerIsAlwaysStaleWithItsAge() {
    val c = BankSearchCache()
    val k = c.keyOf("چای سبز", hasImage = true, cursor = null)
    c.put(k, """{"items":[1]}""", nowMs = 1_000_000, bankRev = 7)
    val s = assertNotNull(c.offline(k, nowMs = 1_000_000 + 5 * minute))
    assertFalse(s.live)
    assertEquals("""{"items":[1]}""", s.payload)
    assertEquals(1_000_000, s.savedAtMs)
    assertEquals(5 * minute, s.ageMs)
    assertEquals(7, s.bankRev)
  }

  @Test fun unknownQueryOfflineHasNoAnswerRatherThanAGuess() {
    val c = BankSearchCache()
    c.put(c.keyOf("چای", true, null), "a", 0, 1)
    assertNull(c.offline(c.keyOf("قند", true, null), 10))
    //  همان پرس‌وجو با فیلتر یا صفحهٔ دیگر کلیدِ دیگر است
    assertNull(c.offline(c.keyOf("چای", false, null), 10))
    assertNull(c.offline(c.keyOf("چای", true, "c2"), 10))
    //  فاصله‌های اضافه همان کلید
    assertNotNull(c.offline(c.keyOf("  چای ", true, null), 10))
  }

  @Test fun img_bank_15_bankChangeInvalidatesEveryOlderPage() {
    val c = BankSearchCache()
    val a = c.keyOf("پنیر", true, null)
    val b = c.keyOf("شیر", true, null)
    c.put(a, "old-a", 0, bankRev = 3)
    c.put(b, "old-b", 1, bankRev = 3)
    //  مدیر عکسی را بایگانی کرد ⇒ پاسخِ بعدی نسخهٔ ۴ است
    c.put(c.keyOf("ماست", true, null), "new", 2, bankRev = 4)
    assertNull(c.offline(a, 3))
    assertNull(c.offline(b, 3))
    assertEquals(1, c.size)
    assertEquals(4, c.bankRev)
    //  بازگردانیِ دیتابیس (نسخهٔ پایین‌تر) هم یعنی «عوض شد»
    c.observe(2)
    assertEquals(0, c.size)
  }

  @Test fun tooOldPagesAreNeverShown() {
    val c = BankSearchCache(maxAgeMs = 10 * minute)
    val k = c.keyOf("برنج", true, null)
    c.put(k, "p", 0, 1)
    assertNull(c.offline(k, 10 * minute + 1))
    assertEquals(0, c.size)
  }

  @Test fun clockGoingBackwardsNeverGivesNegativeAge() {
    val c = BankSearchCache()
    val k = c.keyOf("روغن", true, null)
    c.put(k, "p", 5 * minute, 1)
    assertEquals(0, assertNotNull(c.offline(k, 0)).ageMs)
  }

  @Test fun boundedByEntriesAndCharactersLeastRecentlyUsedFirst() {
    val c = BankSearchCache(maxEntries = 2, maxChars = 10)
    val a = c.keyOf("a", true, null); val b = c.keyOf("b", true, null); val d = c.keyOf("d", true, null)
    c.put(a, "1111", 0, 1)
    c.put(b, "2222", 1, 1)
    assertNotNull(c.offline(a, 2))                  //  a تازه‌ترین شد
    c.put(d, "3333", 3, 1)                          //  ⇒ b بیرون می‌رود، نه a
    assertNotNull(c.offline(a, 4))
    assertNull(c.offline(b, 4))
    assertNotNull(c.offline(d, 4))
    c.put(c.keyOf("big", true, null), "12345678901", 5, 1)   //  یک صفحه بیش از سقف ⇒ نمی‌ماند
    assertEquals(2, c.size)
    c.put(a, "123456789", 6, 1)                     //  ۹ + ۴ > ۱۰ ⇒ قدیمی‌تر بیرون
    assertEquals(listOf(a), c.entries().map { it.key })
  }

  @Test fun restoreKeepsOnlyFreshPagesOfTheNewestBankVersion() {
    val day = 24 * 60 * minute
    val c = BankSearchCache(maxAgeMs = 2 * day)
    val saved = listOf(
      BankSearchCache.Entry("x", "old-rev", savedAtMs = 1 * day, bankRev = 3),
      BankSearchCache.Entry("y", "fresh", savedAtMs = 2 * day, bankRev = 4),
      BankSearchCache.Entry("z", "too-old", savedAtMs = 0, bankRev = 4),
    )
    c.restore(saved, nowMs = 2 * day + minute)
    assertEquals(4, c.bankRev)
    assertEquals(listOf("y"), c.entries().map { it.key })
  }
}
