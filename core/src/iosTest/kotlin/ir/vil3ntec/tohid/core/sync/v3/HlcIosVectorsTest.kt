package ir.vil3ntec.tohid.core.sync.v3

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 *  آزمایشِ iOS (spike): همان سطرهای `core/test-vectors/hlc-v1.json` (بخش‌های
 *  parse/format/canonical/compare/fromTs) روی Kotlin/Native — به‌ویژه Regex
 *  (`\n`ِ پایانی، رقم‌های عربی). فقط با `-Pios=true` ساخته می‌شود؛ جای همیشگی‌اش
 *  `commonTest` است (قدمِ ۱ِ مسیرِ انتقال در `core/build.gradle.kts`).
 */
class HlcIosVectorsTest {
  private fun t(wall: Long, counter: Long, node: String) = HlcTime(wall, counter, node)

  @Test fun parse() {
    assertEquals(t(1760000000000L, 3L, "dev-a"), Hlc.parse("1760000000000:3:dev-a"), "1760000000000:3:dev-a")
    assertEquals(null, Hlc.parse("000001760000000000:000003:dev-a"), "000001760000000000:000003:dev-a")
    assertEquals(t(0L, 0L, "x"), Hlc.parse("0:0:x"), "0:0:x")
    assertEquals(t(999999999999999L, 999999L, "n"), Hlc.parse("999999999999999:999999:n"), "999999999999999:999999:n")
    assertEquals(t(1760000000000L, 3L, "a.b_c-D9"), Hlc.parse("1760000000000:3:a.b_c-D9"), "1760000000000:3:a.b_c-D9")
    assertEquals(t(1760000000000L, 3L, "nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn"), Hlc.parse("1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn"), "1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn")
    assertEquals(null, Hlc.parse("1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn"), "1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn")
    assertEquals(null, Hlc.parse("1234567890123456:0:x"), "1234567890123456:0:x")
    assertEquals(null, Hlc.parse("1:1234567:x"), "1:1234567:x")
    assertEquals(null, Hlc.parse("1:2:"), "1:2:")
    assertEquals(null, Hlc.parse(":2:x"), ":2:x")
    assertEquals(null, Hlc.parse("1::x"), "1::x")
    assertEquals(null, Hlc.parse("-1:2:x"), "-1:2:x")
    assertEquals(null, Hlc.parse("1.5:2:x"), "1.5:2:x")
    assertEquals(null, Hlc.parse("1:2:dev a"), "1:2:dev a")
    assertEquals(null, Hlc.parse("1:2:dev/a"), "1:2:dev/a")
    assertEquals(null, Hlc.parse("1:2:a\n"), "1:2:a\n")
    assertEquals(null, Hlc.parse(" 1:2:a"), " 1:2:a")
    assertEquals(null, Hlc.parse("1:2:a:b"), "1:2:a:b")
    assertEquals(null, Hlc.parse(""), "")
    assertEquals(null, Hlc.parse("abc"), "abc")
    assertEquals(null, Hlc.parse("١:٢:a"), "١:٢:a")
  }

  @Test fun format() {
    assertEquals("000000000000000:000000:x", Hlc.format(0L, 0L, "x"))
    assertEquals("001760000000000:000003:dev-a", Hlc.format(1760000000000L, 3L, "dev-a"))
    assertEquals("999999999999999:999999:n", Hlc.format(999999999999999L, 999999L, "n"))
    assertEquals("000000000000001:1234567:x", Hlc.format(1L, 1234567L, "x"))
    assertEquals("1234567890123456:000000:x", Hlc.format(1234567890123456L, 0L, "x"))
    assertEquals("000000000000000:000000:neg", Hlc.format(-5L, -2L, "neg"))
  }

  @Test fun canonical() {
    assertEquals("001760000000000:000003:dev-a", Hlc.canonical("1760000000000:3:dev-a"), "1760000000000:3:dev-a")
    assertEquals("", Hlc.canonical("000001760000000000:000003:dev-a"), "000001760000000000:000003:dev-a")
    assertEquals("000000000000000:000000:x", Hlc.canonical("0:0:x"), "0:0:x")
    assertEquals("999999999999999:999999:n", Hlc.canonical("999999999999999:999999:n"), "999999999999999:999999:n")
    assertEquals("001760000000000:000003:a.b_c-D9", Hlc.canonical("1760000000000:3:a.b_c-D9"), "1760000000000:3:a.b_c-D9")
    assertEquals("001760000000000:000003:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn", Hlc.canonical("1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn"), "1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn")
    assertEquals("", Hlc.canonical("1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn"), "1760000000000:3:nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn")
    assertEquals("", Hlc.canonical("1234567890123456:0:x"), "1234567890123456:0:x")
    assertEquals("", Hlc.canonical("1:1234567:x"), "1:1234567:x")
    assertEquals("", Hlc.canonical("1:2:"), "1:2:")
    assertEquals("", Hlc.canonical(":2:x"), ":2:x")
    assertEquals("", Hlc.canonical("1::x"), "1::x")
    assertEquals("", Hlc.canonical("-1:2:x"), "-1:2:x")
    assertEquals("", Hlc.canonical("1.5:2:x"), "1.5:2:x")
    assertEquals("", Hlc.canonical("1:2:dev a"), "1:2:dev a")
    assertEquals("", Hlc.canonical("1:2:dev/a"), "1:2:dev/a")
    assertEquals("", Hlc.canonical("1:2:a\n"), "1:2:a\n")
    assertEquals("", Hlc.canonical(" 1:2:a"), " 1:2:a")
    assertEquals("", Hlc.canonical("1:2:a:b"), "1:2:a:b")
    assertEquals("", Hlc.canonical(""), "")
    assertEquals("", Hlc.canonical("abc"), "abc")
    assertEquals("", Hlc.canonical("١:٢:a"), "١:٢:a")
  }

  @Test fun compare() {
    assertEquals(0, Hlc.compare("1:0:a", "1:0:a"))
    assertEquals(-1, Hlc.compare("1:0:a", "1:0:b"))
    assertEquals(1, Hlc.compare("1:0:b", "1:0:a"))
    assertEquals(1, Hlc.compare("1:1:a", "1:0:z"))
    assertEquals(1, Hlc.compare("2:0:a", "1:999999:z"))
    assertEquals(0, Hlc.compare("000000000000001:000000:a", "1:0:a"))
    assertEquals(1, Hlc.compare("1760000000000:3:dev-a", "1760000000000:3:dev-B"))
    assertEquals(-1, Hlc.compare("1760000000000:3:Z", "1760000000000:3:a"))
    assertEquals(1, Hlc.compare("1760000000000:3:v1.000000000000012", "1760000000000:3:server"))
    assertEquals(1, Hlc.compare("999999999999999:999999:a", "1:0:a"))
  }

  @Test fun fromTs() {
    assertEquals("001760000000000:000000:dev", Hlc.fromTs(1760000000000L, 0, "dev"))
    assertEquals("001760000000000:000007:dev", Hlc.fromTs(1760000000000L, 7, "dev"))
    assertEquals("000000000000000:000000:dev", Hlc.fromTs(0L, 0, "dev"))
    assertEquals("000000000000000:000002:dev", Hlc.fromTs(-1L, 2, "dev"))
    assertEquals("999999999999999:000000:dev", Hlc.fromTs(10000000000000000L, 0, "dev"))
    assertEquals("999999999999999:999999:dev", Hlc.fromTs(999999999999999L, 999999, "dev"))
    assertEquals("000000000000005:999999:dev", Hlc.fromTs(5L, 1234567, "dev"))
    assertEquals("000000000000005:000000:dev", Hlc.fromTs(5L, -3, "dev"))
  }
}
