package ir.vil3ntec.tohid.core.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** IMG-PRO-13 — کشِ عکس‌های کوچکِ «انتخاب از بانک»: نسخهٔ تازه هرگز عکسِ کهنه را از کش نمی‌گیرد، و کش کران‌دار است */
class BankThumbCacheTest {

  private val v1 = "/api/image-bank/file/img-7/1/thumb"
  private val v2 = "/api/image-bank/file/img-7/2/thumb"

  @Test
  fun aNewApprovedVersionMissesTheOldCachedImage() {
    val c = BankThumbCache<String>()
    val k1 = BankThumbCache.key(v1, 96)!!
    c.put(k1, "عکسِ نسخهٔ ۱")
    assertEquals("عکسِ نسخهٔ ۱", c.get(k1))
    //  سرور پس از تأییدِ عکسِ تازه نشانیِ نسخهٔ ۲ را می‌دهد ⇒ کلیدِ دیگر ⇒ کش خالی، پس از سرور خوانده می‌شود
    val k2 = BankThumbCache.key(v2, 96)!!
    assertNotEquals(k1, k2)
    assertNull(c.get(k2))
    c.put(k2, "عکسِ نسخهٔ ۲")
    assertEquals("عکسِ نسخهٔ ۲", c.get(k2))
  }

  @Test
  fun keyCarriesThePixelSizeAndRejectsEmptyUrls() {
    assertNotEquals(BankThumbCache.key(v1, 96), BankThumbCache.key(v1, 192))
    assertEquals(BankThumbCache.key(v1, 1), BankThumbCache.key(v1, 0))
    assertEquals(BankThumbCache.key(v1, 1), BankThumbCache.key(v1, -5))
    assertNull(BankThumbCache.key(null, 96))
    assertNull(BankThumbCache.key("", 96))
    assertNull(BankThumbCache.key("   ", 96))
  }

  @Test
  fun leastRecentlyUsedLeavesFirstAndSizeStaysBounded() {
    val c = BankThumbCache<Int>(max = 3)
    c.put("a", 1); c.put("b", 2); c.put("c", 3)
    assertEquals(1, c.get("a"))   //  «a» تازه شد ⇒ «b» کم‌کاربردترین است
    c.put("d", 4)
    assertEquals(3, c.size)
    assertNull(c.get("b"))
    assertEquals(1, c.get("a"))
    assertEquals(3, c.get("c"))
    assertEquals(4, c.get("d"))
    //  گذاشتنِ دوبارهٔ همان کلید جای تازه نمی‌گیرد
    c.put("d", 5)
    assertEquals(3, c.size)
    assertEquals(5, c.get("d"))
    repeat(100) { c.put("k$it", it) }
    assertEquals(3, c.size)
    assertFailsWith<IllegalArgumentException> { BankThumbCache<Int>(max = 0) }
  }
}
