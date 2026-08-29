package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  کلیدها باید دقیقاً همان شکلی باشند که نسخهٔ وب می‌سازد و می‌پذیرد؛
 *  اگر یک دسته کم یا زیاد شود، سرور کلیدِ گوشی را نمی‌شناسد.
 */
class AccountKeysTest {

  @Test
  fun `کلید حساب شکل TSH دارد`() {
    repeat(50) {
      val key = AccountKeys.newApiKey()
      assertTrue(key, AccountKeys.API_RE.matches(key))
    }
  }

  @Test
  fun `کد شاگرد شکل SHG دارد`() {
    repeat(50) {
      val code = AccountKeys.newStaffCode()
      assertTrue(code, AccountKeys.STAFF_RE.matches(code))
    }
  }

  @Test
  fun `کلیدها تکراری ساخته نمی‌شوند`() {
    val keys = (1..200).map { AccountKeys.newApiKey() }
    assertEquals(keys.size, keys.toSet().size)
  }

  @Test
  fun `حرف‌های گمراه‌کننده در کلید نمی‌آید`() {
    val body = AccountKeys.newApiKey().removePrefix("TSH-").replace("-", "")
    assertTrue(body.none { it in "IO01" })
  }
}
