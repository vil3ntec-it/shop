package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PUR-02/03 (`claude/pur-2`): «در راه» ِ پیشنهادِ سفارش از **همهٔ** سفارش‌های باز (نه فقط صفحهٔ نخستِ ۵۰ فرستادهٔ تازه‌تر که پر
 *  از سفارشِ کامل است) و به **واحدِ پایهٔ کالا** (ردیفِ به واحدِ خرید × ضریب)، و پالایهٔ وضعیتِ نمایشیِ سرور (`status`) با
 *  پالایشِ دومِ برنامه برای سرورِ کهنه.
 */
class PurchaseOrderPlanningTest {

  private fun order(id: String, status: String, vararg left: Pair<String, Long>, lines: List<PoLineDto> = emptyList()) = PurchaseOrderDto(
    id = id, supplierId = "s1", status = status,
    state = if (status == "draft" || status == "cancelled") status else "sent",
    lines = lines,
    progress = left.mapIndexed { i, (pid, m) -> PoProgressDto(lineId = "l${i + 1}", productId = pid, remainingMilli = m) },
  )

  @Test fun `در راه به واحدِ پایه — دو کارتنِ ۲۴تایی ۴۸ دانه است نه ۲`() {
    val carton = order(
      "o1", "partial", "pA" to 2_000L, "pB" to 3_000L,
      lines = listOf(
        PoLineDto("l1", "pA", "کالای A", "کارتن", 5_000, 240_000, factorMilli = 24_000),
        PoLineDto("l2", "pB", "کالای B", "عدد", 3_000, 10_000),
      ),
    )
    val loose = order("o2", "sent", "pA" to 7_000L, lines = listOf(PoLineDto("l1", "pA", "کالای A", "عدد", 7_000, 10_000)))
    //  کامل و پیش‌نویس «در راه» نیستند، با ضریب هم
    val done = order("o3", "complete", "pA" to 0L, lines = listOf(PoLineDto("l1", "pA", "کالای A", "کارتن", 1_000, 1, factorMilli = 24_000)))
    val draft = order("o4", "draft", "pA" to 9_000L, lines = listOf(PoLineDto("l1", "pA", "کالای A", "کارتن", 9_000, 1, factorMilli = 24_000)))
    assertEquals(mapOf("pA" to 48_000L + 7_000L, "pB" to 3_000L), PurchaseOrderBook.onOrderMilli(listOf(carton, loose, done, draft)))
    //  ضریبِ نیم (بستهٔ نیم‌کیلویی) هم دقیق به هزارم
    val half = order("o5", "sent", "pD" to 3_000L, lines = listOf(PoLineDto("l1", "pD", "کالای D", "بسته", 3_000, 1, factorMilli = 500)))
    assertEquals(mapOf("pD" to 1_500L), PurchaseOrderBook.onOrderMilli(listOf(half)))
  }

  @Test fun `سفارش‌های پیشنهاد — صفحهٔ نخستِ فرستاده‌ها و همهٔ صفحه‌های باز، بی تکرار`() = runBlocking {
    val calls = mutableListOf<Pair<String?, Boolean>>()
    val c1 = order("c1", "complete")
    val o1old = order("o1", "sent", "pA" to 1_000L)
    val o1 = order("o1", "partial", "pA" to 500L)
    val o2 = order("o2", "sent", "pB" to 2_000L)
    val o3 = order("o3", "sent", "pC" to 3_000L)
    val got = PurchaseOrderBook.planningOrders { cursor, open ->
      calls += cursor to open
      when {
        !open -> PoPageDto(listOf(c1, o1old), next = "recent-next")
        cursor == null -> PoPageDto(listOf(o1, o2), next = "n1")
        cursor == "n1" -> PoPageDto(emptyList(), next = "n2")
        cursor == "n2" -> PoPageDto(listOf(o3), next = null)
        else -> error("نشانگرِ ناشناس $cursor")
      }
    }
    assertEquals(listOf("c1", "o1", "o2", "o3"), got.map { it.id })
    assertEquals("partial", got.first { it.id == "o1" }.status)
    //  صفحهٔ «فرستاده» فقط یک بار (زمانِ تأمین)، صفحهٔ تهی با نشانگر پایانِ «باز» نیست
    assertEquals(listOf<Pair<String?, Boolean>>(null to false, null to true, "n1" to true, "n2" to true), calls)
    assertEquals(mapOf("pA" to 500L, "pB" to 2_000L, "pC" to 3_000L), PurchaseOrderBook.onOrderMilli(got))
  }

  @Test fun `سفارش‌های پیشنهاد — شکستِ یک صفحه آنچه رسید را می‌دهد و صفحه‌ها سقف دارند`() = runBlocking {
    val o1 = order("o1", "sent", "pA" to 1_000L)
    val broken = PurchaseOrderBook.planningOrders { cursor, open ->
      if (!open) null else if (cursor == null) PoPageDto(listOf(o1), next = "n1") else null
    }
    assertEquals(listOf("o1"), broken.map { it.id })
    var pages = 0
    val endless = PurchaseOrderBook.planningOrders { _, open ->
      if (open) pages++
      PoPageDto(emptyList(), next = "again")
    }
    assertTrue(endless.isEmpty())
    assertEquals(PurchaseOrderBook.PLANNING_PAGES, pages)
  }

  @Test fun `پالایهٔ وضعیتِ نمایشیِ سرور و پالایشِ دوم برای سرورِ کهنه`() {
    assertEquals(null, PoForms.serverStatus(PoForms.Filter.ALL))
    assertEquals(null, PoForms.serverStatus(PoForms.Filter.DRAFT))
    assertEquals(null, PoForms.serverStatus(PoForms.Filter.CANCELLED))
    assertEquals("sent", PoForms.serverStatus(PoForms.Filter.SENT))
    assertEquals("partial", PoForms.serverStatus(PoForms.Filter.PARTIAL))
    assertEquals("complete", PoForms.serverStatus(PoForms.Filter.COMPLETE))
    //  همان وضعیتِ ذخیره‌شدهٔ پیشین کنارش (سرورِ کهنه فقط این را می‌پالاید)
    PoForms.Filter.entries.filter { PoForms.serverStatus(it) != null }.forEach {
      assertEquals(PurchaseOrderCore.State.SENT, PoForms.serverState(it))
    }
    val mixed = listOf(order("a", "sent"), order("b", "partial"), order("c", "complete"), order("d", "cancelled"), order("e", "draft"))
    assertEquals(listOf("a", "b"), PoForms.openOnly(mixed).map { it.id })
    assertEquals(listOf("b"), PoForms.shown(mixed, PoForms.Filter.PARTIAL).map { it.id })
    assertEquals("open", PurchaseOrderBook.OPEN_STATUS)
  }
}
