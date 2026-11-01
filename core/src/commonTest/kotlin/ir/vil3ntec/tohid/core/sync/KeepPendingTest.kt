package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.KeepPending.Drop
import ir.vil3ntec.tohid.core.sync.KeepPending.Keep
import ir.vil3ntec.tohid.core.sync.KeepPending.Op
import ir.vil3ntec.tohid.core.sync.KeepPending.Trim
import kotlin.test.Test
import kotlin.test.assertEquals

/** قاعدهٔ `KeepPending` روی هر هدف؛ همهٔ شکل‌ها با بردارِ مشترکِ وب: `jvmTest/…/KeepPendingVectorsTest.kt` */
class KeepPendingTest {

  private fun set(row: String, vararg f: String) = Op("products", row, delete = false, set = f.toSet())
  private fun del(row: String) = Op("products", row, delete = true)

  /** ویرایشِ وسطِ pull: قیمتِ در صف بر قیمتِ رسیده می‌ماند، نامِ رسیده می‌نشیند */
  @Test
  fun editDuringPullSurvives() {
    val out = KeepPending.verdicts(listOf(set("p1", "price", "name")), listOf(set("p1", "price")))
    assertEquals(listOf(Trim(setOf("price"), emptyMap())), out)
  }

  /** صفِ خالی ⇒ همه همان، بی هیچ کار */
  @Test
  fun emptyQueueKeepsAll() {
    assertEquals(listOf(Keep, Keep), KeepPending.verdicts(listOf(set("p1", "price"), del("p2")), emptyList()))
  }

  /** حذفِ در صف و ویرایشِ رسیده ⇒ شبح نه؛ ویرایشِ در صف و حذفِ رسیده ⇒ ردیف نمی‌رود */
  @Test
  fun deletesFollowTheQueue() {
    assertEquals(listOf(Drop), KeepPending.verdicts(listOf(set("p1", "name")), listOf(del("p1"))))
    assertEquals(listOf(Drop), KeepPending.verdicts(listOf(del("p1")), listOf(set("p1", "price"))))
    assertEquals(listOf(Keep), KeepPending.verdicts(listOf(del("p1")), listOf(del("p1"))))
  }

  /** پولِ رسیده: `$inc` روی `$inc` همان؛ مقدارِ ساده روی `$inc` ِ صف ⇒ با دلتای صف */
  @Test
  fun countersStayExact() {
    val paid = { d: Double -> Op("sales", "s1", delete = false, inc = mapOf("paidAmount" to d)) }
    val plain = Op("sales", "s1", delete = false, set = setOf("paidAmount"))
    assertEquals(listOf(Keep, Trim(emptySet(), mapOf("paidAmount" to 70.0))),
      KeepPending.verdicts(listOf(paid(30.0), plain), listOf(paid(50.0), paid(20.0))))
  }
}
