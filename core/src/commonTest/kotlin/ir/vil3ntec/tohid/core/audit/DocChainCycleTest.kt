package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  SEC-02 — یک دورِ همگام‌سازی: پرچمِ خاموش ⇒ فقط پرسش (حداکثر روزی یک بار) و هیچ حلقه؛ روشن ⇒
 *  از همین لحظه، دورهٔ «آخرینِ سرور + ۱»، گزارش و تأیید؛ مشکلِ سرور ⇒ ایستادن و دورهٔ تازه؛ سریِ دیگر ⇒
 *  ایستادن بی دورهٔ تازه؛ بی شبکه ⇒ حلقه روی دیسک ساخته می‌شود و دورِ بعد می‌رود.
 */
class DocChainCycleTest {

  private val off = DocChainCycle.Gate(false, 0)
  private val history = listOf(sale("s1", 1), sale("s2", 2), sale("a5", 5, series = "A1"))

  private fun tick(gate: DocChainCycle.Gate, now: Long, disk: MemoryDocDisk, server: FakeChainServer, sales: List<SaleFacts>) =
    runSync { DocChainCycle.tick(gate, now, DocChainBook(disk), server, "shp_t", "B1", "usr_1", sales) }

  @Test
  fun flagOffAsksAtMostOncePerDayAndBuildsNothing() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer().apply { status = DocChainStatus(enabled = false) }
    val (g1, o1) = tick(off, 1_000_000, disk, server, history)
    assertIs<DocChainCycle.Outcome.Disabled>(o1)
    assertEquals(DocChainCycle.Gate(false, 1_000_000), g1)
    val (g2, o2) = tick(g1, 1_000_000 + DocChainCycle.PROBE_MS - 1, disk, server, history)
    assertIs<DocChainCycle.Outcome.Skipped>(o2)
    assertEquals(g1, g2)
    assertEquals(1, server.statusCalls.size)
    tick(g2, 1_000_000 + DocChainCycle.PROBE_MS, disk, server, history)
    assertEquals(2, server.statusCalls.size)
    assertTrue(server.reports.isEmpty())
    assertNull(disk.state)
    assertTrue(disk.files.isEmpty())
  }

  /**
   *  ⛔ پرچمِ خاموش = همان همگام‌سازیِ main: یک روزِ کامل دورِ پنج‌دقیقه‌ای (۲۸۸ دور) ⇒ فقط یک پرسش.
   *  سرورِ کهنه (۴۰۴) یا بی‌شبکه (`status()` ⇐ null) هم همین: پرسش در هر دور تکرار نمی‌شود.
   */
  @Test
  fun flagOffOr404DoesNotAskAgainForADay() {
    assertEquals(24 * 60 * 60_000L, DocChainCycle.PROBE_MS)
    for (answer in listOf<DocChainStatus?>(DocChainStatus(enabled = false), null)) {
      val disk = MemoryDocDisk()
      val server = FakeChainServer().apply { status = answer }
      val start = 1_000_000L
      var g = off
      for (i in 0 until 288) g = tick(g, start + i * 5 * 60_000L, disk, server, history).first
      assertEquals(1, server.statusCalls.size, "پاسخ: $answer")
      assertTrue(server.reports.isEmpty())
      assertTrue(disk.files.isEmpty())
      //  پس از یک روز ⇒ پرسشِ دوم
      tick(g, start + DocChainCycle.PROBE_MS, disk, server, history)
      assertEquals(2, server.statusCalls.size, "پاسخ: $answer")
    }
  }

  @Test
  fun turningOnStartsFromNowInTheNextEpochAndAcks() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer().apply { status = DocChainStatus(enabled = true, lastEpoch = 2) }
    val (g, o) = tick(off, 5, disk, server, history)
    assertEquals(true, g.enabled)
    val st = assertIs<DocChainCycle.Outcome.Reported>(o).state
    assertEquals(3, st.epoch)
    assertEquals(3, st.startNo, "فاکتورهای ۱ و ۲ ِ پیش از روشن شدن در زنجیره نمی‌آیند")
    assertEquals(0, st.seq)
    assertEquals(1, server.reports.size)

    //  فروشِ تازه ⇐ حلقه، گزارش و تأیید
    val (_, o2) = tick(g, 6, disk, server, history + sale("s3", 3) + sale("s4", 4))
    val st2 = assertIs<DocChainCycle.Outcome.Reported>(o2).state
    assertEquals(2, st2.seq)
    assertEquals(listOf(1L, 2L), server.reports.last().links.map { it.seq })
    assertEquals(2, st2.ackSeq)
    assertEquals(st2.head, st2.ackHash)
    //  روشن ⇒ دیگر پرچم پرسیده نمی‌شود
    assertEquals(1, server.statusCalls.size)
  }

  @Test
  fun offlineStillChainsLocallyAndSendsLater() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer()
    val (g, _) = tick(off, 5, disk, server, history)
    server.reply = null
    val (g2, o) = tick(g, 6, disk, server, history + sale("s3", 3))
    assertIs<DocChainCycle.Outcome.Offline>(o)
    assertEquals(true, g2.enabled)
    assertEquals(1, disk.lines(1).size)
    server.reply = { r -> DocChainReply(true, listOf(DocChainAck(r.series, r.epoch, r.seq, r.head))) }
    tick(g2, 7, disk, server, history + sale("s3", 3))
    assertEquals(listOf(1L), server.reports.last().links.map { it.seq })
  }

  @Test
  fun serverProblemHaltsThenNextCycleStartsANewEpoch() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer()
    val (g, _) = tick(off, 5, disk, server, history)
    server.reply = { r -> DocChainReply(true, problems = listOf(DocChainProblem(r.series, r.epoch, "fork", 1))) }
    val (g2, o) = tick(g, 6, disk, server, history + sale("s3", 3))
    assertEquals("fork", assertIs<DocChainCycle.Outcome.Reported>(o).state.halted)
    server.reply = { r -> DocChainReply(true, listOf(DocChainAck(r.series, r.epoch, r.seq, r.head))) }
    server.status = DocChainStatus(enabled = true, lastEpoch = 1)
    val (_, o3) = tick(g2, 7, disk, server, history + sale("s3", 3) + sale("s4", 4))
    val st = assertIs<DocChainCycle.Outcome.Reported>(o3).state
    assertEquals(2, st.epoch)
    assertEquals(5, st.startNo)
    assertEquals("", st.halted)
    assertEquals(1, disk.lines(1).size, "دورهٔ کهنه شاهد می‌ماند")
  }

  @Test
  fun foreignSeriesHaltsWithoutNewEpochAndSendsOnlyTheHead() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer()
    val (g, _) = tick(off, 5, disk, server, history)
    server.reply = { r -> DocChainReply(true, problems = listOf(DocChainProblem(r.series, r.epoch, DocChain.SERIES_OWNER, 0))) }
    val (g2, _) = tick(g, 6, disk, server, history + sale("s3", 3))
    val (_, o) = tick(g2, 7, disk, server, history + sale("s3", 3) + sale("s4", 4))
    val st = assertIs<DocChainCycle.Outcome.Reported>(o).state
    assertEquals(DocChain.SERIES_OWNER, st.halted)
    assertEquals(1, st.epoch)
    assertTrue(server.reports.last().links.isEmpty())
    assertNull(server.reports.last().broken)
    assertEquals(1, server.statusCalls.size)
  }

  @Test
  fun serverTurningOffStopsTheChain() {
    val disk = MemoryDocDisk()
    val server = FakeChainServer()
    val (g, _) = tick(off, 5, disk, server, history)
    server.reply = { DocChainReply(enabled = false) }
    val (g2, o) = tick(g, 6, disk, server, history)
    assertIs<DocChainCycle.Outcome.Disabled>(o)
    assertEquals(DocChainCycle.Gate(false, 6), g2)
    //  خاموش ⇒ فروشِ تازه حلقه نمی‌شود
    val (_, o3) = tick(g2, 7, disk, server, history + sale("s3", 3))
    assertIs<DocChainCycle.Outcome.Skipped>(o3)
    assertTrue(disk.lines(1).isEmpty())
  }

  @Test
  fun invalidSeriesOrShopDoesNothing() {
    val server = FakeChainServer()
    val (_, o) = runSync { DocChainCycle.tick(off, 5, DocChainBook(MemoryDocDisk()), server, "shp_t", "", "u", history) }
    assertIs<DocChainCycle.Outcome.Skipped>(o)
    val (_, o2) = runSync { DocChainCycle.tick(off, 5, DocChainBook(MemoryDocDisk()), server, "bad/shop", "B1", "u", history) }
    assertIs<DocChainCycle.Outcome.Skipped>(o2)
    assertTrue(server.statusCalls.isEmpty())
  }
}
