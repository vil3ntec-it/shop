package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  اسکن در پس‌زمینه: دوامِ صف (سطرِ نیمه‌نوشته، تکرار، فشرده‌سازی)، بستهٔ حافظهٔ بارکدخوان، دنبالهٔ کارتِ فرمان و
 *  قاعدهٔ «هیچ فروشِ بی‌صدا».
 */
class ScanQueueTest {

  private val dev = "bt:AA:BB:CC:DD:EE:01"
  private fun s(id: String, code: String, at: Long, src: ScanSource = ScanSource.LIVE, qty: Int = 1, time: String? = null) =
    QueuedScan(id, dev, at, code, src, qty, time)

  private fun journal(vararg lines: String) = lines.joinToString("\n", postfix = "\n")

  @Test fun `صف پس از بازخوانی همان می‌ماند و برداشته‌ها برنمی‌گردند`() {
    val text = journal(
      ScanJournal.scan(s("1", "4006381333931", 1_000)),
      ScanJournal.scan(s("2", "4006381333931", 2_000)),
      ScanJournal.scan(s("3", "123456", 3_000, ScanSource.MEMORY, qty = 3, time = "2026-11-02T10:00:00")),
      ScanJournal.taken("1"),
    )
    val st = ScanJournal.replay(text)
    assertEquals(listOf("2", "3"), st.pending.map { it.id })
    assertEquals(3, st.pending[1].qty)
    assertEquals(ScanSource.MEMORY, st.pending[1].source)
    assertEquals("2026-11-02T10:00:00", st.pending[1].deviceTime)
    assertEquals(0, st.broken)
  }

  @Test fun `سطرِ نیمه‌نوشته یا دست‌خورده کنار می‌رود و بقیه سالم است`() {
    val good = ScanJournal.scan(s("1", "111111", 1_000))
    val torn = ScanJournal.scan(s("2", "222222", 2_000)).dropLast(3)
    val edited = ScanJournal.scan(s("3", "333333", 3_000)).replace("333333", "999999")
    val st = ScanJournal.replay(good + "\n" + edited + "\n" + torn)
    assertEquals(listOf("1"), st.pending.map { it.id })
    assertEquals(2, st.broken)
    assertTrue(ScanJournal.replay("").pending.isEmpty())
    assertTrue(ScanJournal.replay(null).pending.isEmpty())
  }

  @Test fun `تکرارِ دستگاه و زمان و کد یک بار صف می‌شود حتی پس از برداشتن و فشرده‌سازی`() {
    var st = ScanJournal.State()
    val a = s("1", "777777", 5_000, ScanSource.MEMORY, time = "2026-11-01T09:00:00")
    st = ScanJournal.add(st, a)
    //  همان اسکنِ حافظه با شناسه و زمانِ رسیدنِ دیگر
    st = ScanJournal.add(st, a.copy(id = "2", atMs = 9_000))
    assertEquals(1, st.pending.size)
    //  همان کد در زمانِ دیگر اسکنِ دیگری است
    st = ScanJournal.add(st, a.copy(id = "3", deviceTime = "2026-11-01T09:00:05"))
    assertEquals(2, st.pending.size)
    st = ScanJournal.take(st, setOf("1"))
    val again = ScanJournal.replay(ScanJournal.compact(st))
    assertEquals(listOf("3"), again.pending.map { it.id })
    assertFalse(ScanJournal.accepts(again, a.copy(id = "9")))
    //  کدِ خالی یا فقط نویسهٔ کنترل پذیرفته نیست
    assertFalse(ScanJournal.accepts(again, s("x", "\u0002\r", 1)))
  }

  @Test fun `فشرده‌سازی فقط وقتی سطرهای مرده زیادند`() {
    var st = ScanJournal.State()
    (1..300).forEach { st = ScanJournal.add(st, s("$it", "C$it", it.toLong())) }
    assertFalse(ScanJournal.shouldCompact(st))
    st = ScanJournal.take(st, (1..299).map { "$it" }.toSet())
    assertTrue(ScanJournal.shouldCompact(st))
    val back = ScanJournal.replay(ScanJournal.compact(st))
    assertEquals(listOf("300"), back.pending.map { it.id })
    assertEquals(300, back.keys.size)
  }

  @Test fun `جمعِ در انتظار و گروه‌بندی - همان کد N بار یک ردیف با N`() {
    val list = listOf(s("1", "A", 10), s("2", "B", 20), s("3", "A", 30), s("4", "C", 40, ScanSource.MEMORY, qty = 4))
    assertEquals(listOf(CodeCount("A", 2, 10), CodeCount("B", 1, 20), CodeCount("C", 4, 40)), PendingScans.group(list))
    val sum = PendingScans.summary(list)
    assertEquals(4, sum.scans); assertEquals(7, sum.units); assertEquals(3, sum.codes); assertEquals(1, sum.devices); assertEquals(1, sum.fromMemory)
    assertEquals(10L, sum.firstAt)
    val (known, unknown) = PendingScans.split(PendingScans.group(list)) { it != "B" }
    assertEquals(listOf("A", "C"), known.map { it.code }); assertEquals(listOf("B"), unknown.map { it.code })
  }

  @Test fun `بستهٔ حافظه از اسکنِ دست جدا می‌شود`() {
    val g = BurstGate.Gate()
    //  دو اسکنِ دست با یک ثانیه فاصله
    assertTrue(g.offer("A", 0).isEmpty())
    assertEquals(listOf<BurstGate.Out>(BurstGate.Out.Live("A", 0)), g.offer("B", 1_000))
    assertEquals(listOf<BurstGate.Out>(BurstGate.Out.Live("B", 1_000)), g.tick(1_200))
    //  بستهٔ حافظه: سطرها چند میلی‌ثانیه پشتِ هم
    listOf("X", "Y", "Z").forEachIndexed { i, c -> assertTrue(g.offer(c, 5_000L + i * 8).isEmpty()) }
    assertTrue(g.tick(5_100).isEmpty())
    assertEquals(listOf<BurstGate.Out>(BurstGate.Out.Burst(listOf("X", "Y", "Z"), 5_000)), g.tick(5_300))
    assertFalse(g.waiting)
    g.offer("Q", 9_000)
    assertEquals(listOf<BurstGate.Out>(BurstGate.Out.Live("Q", 9_000)), g.flush())
  }

  @Test fun `سطرِ حافظه - کد، شمار و تاریخ به شکل‌های رایج`() {
    assertEquals(MemoryItem("4006381333931"), MemoryLine.parse("4006381333931\r"))
    assertEquals(MemoryItem("4006381333931", 3), MemoryLine.parse("4006381333931,3"))
    assertEquals(MemoryItem("1234", 2), MemoryLine.parse("1234;2"))
    assertEquals(MemoryItem("4006381333931", 1, "2026-11-02T09:05:03"), MemoryLine.parse("2026/11/02 9:05:03,4006381333931"))
    assertEquals(MemoryItem("ABC123", 5, "2026-11-02T10:22:00"), MemoryLine.parse("ABC123\t5\t2026-11-02 10:22"))
    //  کدی که ویرگول دارد شکسته نمی‌شود
    assertEquals(MemoryItem("AB,CD"), MemoryLine.parse("AB,CD"))
    //  شمارِ بزرگ یا صفر شمار نیست
    assertEquals(MemoryItem("X1,0"), MemoryLine.parse("X1,0"))
    assertNull(MemoryLine.parse("  \r\n"))
    assertNull(MemoryLine.time("2026-13-02 10:00"))
  }

  @Test fun `بستهٔ حافظهٔ تکراری یا دنباله‌دار فقط تازه‌ها را می‌دهد`() {
    val first = listOf(MemoryItem("A"), MemoryItem("B"), MemoryItem("A"))
    val mark = first.size to MemoryImport.hash(first)
    assertEquals(first, MemoryImport.fresh(first, null))
    assertTrue(MemoryImport.fresh(first, mark).isEmpty())
    val longer = first + MemoryItem("C", 2)
    assertEquals(listOf(MemoryItem("C", 2)), MemoryImport.fresh(longer, mark))
    //  بارکدخوان حافظه را پاک کرد و بستهٔ تازه‌ای آمد
    val other = listOf(MemoryItem("Z"))
    assertEquals(other, MemoryImport.fresh(other, mark))
    //  اثر در دفتر می‌ماند
    val st = ScanJournal.replay(journal(ScanJournal.memory(dev, mark.first, mark.second)))
    assertEquals(mark, st.memory[Discovery.key(dev)])
  }

  private val key = "shop-1"
  private fun cmd(c: CommandCard.Command) = CommandCard.payload(c, key)
  private fun seq(vararg codes: String) = codes.mapIndexed { i, c -> s("$i", c, i * 1_000L) }
  private fun split(list: List<QueuedScan>) = CommandSequence.split(list) { CommandCard.parse(it, key) }

  @Test fun `بارکدِ فرمان فضای‌نامِ خودش و امضای دکان را دارد`() {
    val cash = cmd(CommandCard.Command.CASH)
    assertTrue(cash.startsWith(CommandCard.PREFIX))
    assertEquals(CommandCard.Command.CASH, CommandCard.parse(cash, key))
    assertEquals(CommandCard.Command.CASH, CommandCard.parse(cash.lowercase(), key))
    //  کارتِ دکانِ دیگر فرمان نیست، ولی کالا هم نیست
    assertNull(CommandCard.parse(cash, "shop-2"))
    assertTrue(CommandCard.isCommandLike(cash))
    assertNull(CommandCard.parse("4006381333931", key))
    assertFalse(CommandCard.isCommandLike("4006381333931"))
    assertEquals(3, CommandCard.Command.entries.map { cmd(it) }.distinct().size)
  }

  @Test fun `دنبالهٔ فرمان - تازه، حذفِ آخرین، نقد، نقدِ دوباره و نبودِ نقد`() {
    val r = split(seq(
      "A", "B", cmd(CommandCard.Command.UNDO), "C", cmd(CommandCard.Command.CASH), cmd(CommandCard.Command.CASH),
      "D", cmd(CommandCard.Command.NEW), cmd(CommandCard.Command.UNDO), "E", "E", CommandCard.payload(CommandCard.Command.CASH, "other"),
    ))
    assertEquals(listOf(listOf("A", "C"), listOf("D"), listOf("E", "E")), r.batches.map { b -> b.items.map { it.code } })
    assertEquals(listOf(true, false, false), r.batches.map { it.cashClosed })
    assertEquals(
      listOf(CommandSequence.Note.DOUBLE_CASH, CommandSequence.Note.EMPTY_UNDO, CommandSequence.Note.FOREIGN_CODE),
      r.notes.map { it.first },
    )
    //  بی کارت: کدِ فرمانی که شناخته نمی‌شود باز هم کالا نیست
    val plain = CommandSequence.split(seq("A", cmd(CommandCard.Command.CASH))) { null }
    assertEquals(listOf(listOf("A")), plain.batches.map { b -> b.items.map { it.code } })
    assertFalse(plain.batches.single().cashClosed)
  }

  @Test fun `هیچ فروشِ بی‌صدا - کارتِ خاموش همیشه نگه‌داشته`() {
    val paid = split(seq("A", cmd(CommandCard.Command.CASH))).batches.single()
    val open = split(seq("A", "B")).batches.single()
    val all = ScanSaleRule.Gate(cardOn = true, tillOpen = true, canSell = true)
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(paid, all.copy(cardOn = false), 0))
    assertEquals(ScanSaleRule.Action.RECORD_CASH, ScanSaleRule.decide(paid, all, 0))
    //  بی «نقد»، صندوقِ بسته، بی اجازه یا کدِ ناشناس ⇒ راهِ الف
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(open, all, 0))
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(paid, all.copy(tillOpen = false), 0))
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(paid, all.copy(canSell = false), 0))
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(paid, all, 1))
  }

  @Test fun `کلیدِ فاکتورِ اسکن با واردکردنِ دوباره همان است`() {
    val a = split(seq("A", "B", cmd(CommandCard.Command.CASH), "C")).batches
    val b = split(seq("A", "B", cmd(CommandCard.Command.CASH), "C")).batches
    assertEquals(a.map(ScanSaleRule::key), b.map(ScanSaleRule::key))
    assertEquals(2, a.map(ScanSaleRule::key).distinct().size)
  }
}
