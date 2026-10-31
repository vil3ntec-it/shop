package ir.vil3ntec.tohid.core.stock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** INV-06 (هر اسکن یک واحد)، INV-08 (شمارشِ کور)، INV-07 (ادغامِ برگه‌های چند دستگاه). */
class CountToolsTest {

  private val owners = mapOf("6260000000017" to "tea", "111" to "rice", "222" to "old")
  private val counts = HashMap<String, Long>()

  private fun scan(code: String) = CountScan.scan(
    code, owners::get, counts::get, weighed = { it == "rice" }, active = { it != "old" },
  ).also { if (it is CountScan.Result.Counted) counts[it.productId] = it.countedMilli }

  @Test fun everyScanAddsOneUnit() {
    assertEquals(CountScan.Result.Counted("tea", 1000), scan("6260000000017"))
    assertEquals(CountScan.Result.Counted("tea", 2000), scan(" ۶۲۶۰۰۰۰۰۰۰۰۱۷ "))
    //  شمارشِ دستیِ ۲٫۵ و بعد اسکن ⇒ ۳٫۵
    counts["tea"] = 2500
    assertEquals(CountScan.Result.Counted("tea", 3500), scan("6260000000017"))
  }

  @Test fun unknownWeighedAndInactiveChangeNothing() {
    assertEquals(CountScan.Result.Unknown("999"), scan("999"))
    assertEquals(CountScan.Result.Weighed("rice"), scan("111"))
    assertEquals(CountScan.Result.Inactive("old"), scan("222"))
    assertEquals(emptyMap(), counts)
  }

  @Test fun blindCountHidesExpectedAndDifference() {
    assertEquals(CountBlind.Hint(null, null), CountBlind.hint(5000, 4000, blind = true))
    assertEquals(CountBlind.Hint(null, null), CountBlind.hint(5000, null, blind = true))
    assertEquals(CountBlind.Hint(5000, -1000), CountBlind.hint(5000, 4000, blind = false))
    assertEquals(CountBlind.Hint(5000, null), CountBlind.hint(5000, null, blind = false))
  }

  @Test fun sheetsFromSeveralDevicesAddUpAndOverlapIsShown() {
    val a = CountMerge.Sheet("s-a", "گوشیِ ۱", 10, mapOf("tea" to 3000, "soap" to 2000))
    val b = CountMerge.Sheet("s-b", "گوشیِ ۲", 11, mapOf("tea" to 1000, "milk" to 500))
    val r = CountMerge.merge(listOf(a, b))
    assertEquals(mapOf("tea" to 4000L, "soap" to 2000L, "milk" to 500L), r.counts)
    assertEquals(setOf("tea"), r.shared)
    assertEquals(2, r.sheets.size)
  }

  @Test fun theSameSheetTwiceIsNotCountedTwice() {
    val a1 = CountMerge.Sheet("s-a", "گوشیِ ۱", 10, mapOf("tea" to 3000))
    val a2 = a1.copy(at = 20, counts = mapOf("tea" to 5000))
    assertEquals(mapOf("tea" to 5000L), CountMerge.merge(listOf(a1, a2)).counts)
    //  نسخهٔ کهنه‌تر پس از تازه‌تر جای آن را نمی‌گیرد
    assertEquals(mapOf("tea" to 5000L), CountMerge.merge(listOf(a2, a1)).counts)
    assertEquals(emptySet(), CountMerge.merge(listOf(a1, a2)).shared)
  }

  @Test fun encodeDecodeRoundTripsAndRejectsBadInput() {
    val s = CountMerge.Sheet("01JSHEET", "گوشیِ\tانبار\n۲", 1_700_000_000_000, linkedMapOf("tea" to 3000L, "rice" to 1250L))
    val back = assertNotNull(CountMerge.decode(CountMerge.encode(s)))
    assertEquals(s.copy(device = "گوشیِ انبار ۲"), back)
    //  BOM و پایانِ خطِ ویندوز
    assertEquals(back, CountMerge.decode("﻿" + CountMerge.encode(s).replace("\n", "\r\n")))
    val ok = CountMerge.encode(s)
    assertNull(CountMerge.decode(""))
    assertNull(CountMerge.decode(ok.replace(CountMerge.HEADER, "tohid-count v2")))
    assertNull(CountMerge.decode(ok.replace("\t3000", "\t-1")))
    assertNull(CountMerge.decode(ok.replace("\t3000", "\tسه")))
    assertNull(CountMerge.decode(ok + "tea\t1\n"))
    assertNull(CountMerge.decode(ok.replace("at 1700000000000", "at x")))
    assertNull(CountMerge.decode(ok + "بی‌تب\n"))
    //  برگهٔ بی ردیف درست است (دستگاهی که هنوز چیزی نشمرده)
    assertEquals(emptyMap(), CountMerge.decode(CountMerge.encode(s.copy(counts = emptyMap())))?.counts)
  }
}
