package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.ui.screens.newId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-10 — شناسهٔ ردیف‌های تازهٔ برنامه همان ULIDِ وب و سرور است.
 *
 *  پیش‌تر `newId()` و `auditId()` شکلِ قدیمِ وب را می‌ساختند
 *  (`"id" + زمان + Math.random`): حدودِ سی بیتِ تصادفیِ ناامن، و دو
 *  ردیفِ یک میلی‌ثانیه از دو دستگاه می‌توانستند یک شناسه بگیرند — یعنی
 *  یکی روی دیگری در سرور می‌نشست. وب از `TohidSyncCore.ulid()` می‌سازد؛
 *  این‌جا همان `Ulid.kt` (SecureRandom).
 *
 *  ⛔ شناسه‌های ردیف‌های موجود دست نمی‌خورند — فقط ردیفِ تازه.
 */
class NewIdTest {

  private val crockford = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")

  private fun assertUlids(make: () -> String) {
    val before = System.currentTimeMillis()
    val made = (1..2_000).map { make() }
    val after = System.currentTimeMillis()

    for (id in made) assertTrue("ULID نیست: $id", crockford.matches(id))
    //  یکتا، حتی وقتی هزاران ردیف در یک میلی‌ثانیه ساخته می‌شوند
    assertEquals(made.size, made.toSet().size)
    //  مرتب بر اساسِ زمانِ ساخت — همان ترتیبی که op_id روی سرور دارد
    assertEquals(made, made.sorted())
    //  ده نویسهٔ اول، زمانِ ساخت است
    val ms = made.first().take(10).fold(0L) { acc, c -> acc * 32 + B32.indexOf(c) }
    assertTrue(ms in before..after)
  }

  @Test fun `شناسهٔ ردیفِ تازهٔ صفحه‌ها ULID است`() = assertUlids { newId() }

  @Test fun `شناسهٔ ردیفِ سابقه هم ULID است`() = assertUlids { auditId() }

  private companion object {
    const val B32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  }
}
