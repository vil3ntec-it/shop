package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.audit.HmacSha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  «کارتِ فرمان» (راهِ «ب»): امضای HMAC، نسلِ کارت، کدِ جعلی و کهنه، و لبه‌های دنبالهٔ فرمان — هیچ‌کدام هرگز کالا نیست
 *  و هیچ فاکتوری بی «نقد» یا با دروازهٔ بسته خودکار ثبت نمی‌شود.
 */
class CommandCardTest {

  private fun hex(b: ByteArray) = ir.vil3ntec.tohid.core.audit.Sha256.hex(b)

  @Test fun `HMAC-SHA256 با بردارهای RFC 4231`() {
    assertEquals(
      "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
      hex(HmacSha256.mac(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray())),
    )
    assertEquals(
      "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
      HmacSha256.hexOf("Jefe".encodeToByteArray(), "what do ya want for nothing?"),
    )
    //  کلیدِ بلندتر از بلوک اول درهم می‌شود
    assertEquals(
      "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
      HmacSha256.hexOf(ByteArray(131) { 0xaa.toByte() }, "Test Using Larger Than Block-Size Key - Hash Key First"),
    )
    assertNull(HmacSha256.bytesOfHex("abc"))
    assertNull(HmacSha256.bytesOfHex("zz"))
  }

  private val key = CommandCard.newKey(ByteArray(32) { it.toByte() }, 1)
  private fun cmd(c: CommandCard.Command, k: CommandCard.Key = key) = CommandCard.payload(c, k)

  @Test fun `هر فرمان سه بخش دارد و با همان راز و نسل خوانده می‌شود`() {
    CommandCard.Command.entries.forEach { c ->
      val p = cmd(c)
      assertTrue(Regex("""TOHID-CMD-(NEW|CASH|UNDO)-1-\d{12}""").matches(p), p)
      assertEquals(CommandCard.Read(CommandCard.Verdict.OK, c), CommandCard.read(p, key))
      //  بارکدخوان گاهی کوچک‌حرف یا با فاصله و Enter می‌فرستد
      assertEquals(c, CommandCard.parse(" " + p.lowercase() + "\r\n", key))
    }
    //  همان ورودی همان بارکد — چاپِ دوباره با همان راز همان کارت است
    assertEquals(cmd(CommandCard.Command.CASH), cmd(CommandCard.Command.CASH))
    assertEquals(3, CommandCard.Command.entries.map { cmd(it) }.distinct().size)
  }

  @Test fun `جعل - امضای نادرست، واژهٔ ناشناس، شکلِ بد و دستگاهِ بی راز هرگز فرمان یا کالا نیست`() {
    val cash = cmd(CommandCard.Command.CASH)
    val body = cash.substringBeforeLast('-')
    val mac = cash.substringAfterLast('-')
    val flipped = body + "-" + mac.dropLast(1) + ((mac.last() - '0' + 1) % 10)
    val forged = listOf(
      flipped,
      "TOHID-CMD-CASH-1-000000000000",
      "TOHID-CMD-PAY-1-$mac",
      "TOHID-CMD-CASH-1",
      "TOHID-CMD-CASH-x-$mac",
      "TOHID-CMD-CASH-1-${mac}9",
      "TOHID-CMD-",
      //  همان کد با نسلِ آینده
      "TOHID-CMD-CASH-2-$mac",
      //  «نقد» ِ کارتِ شبیه که روی کاغذ کپی شد ولی با رازِ دیگر
      cmd(CommandCard.Command.CASH, CommandCard.newKey(ByteArray(32) { 7 }, 1)),
    )
    forged.forEach { f ->
      assertEquals(CommandCard.Verdict.FORGED, CommandCard.read(f, key).verdict, f)
      assertNull(CommandCard.parse(f, key))
      assertTrue(CommandCard.isCommandLike(f), f)
    }
    //  دستگاهی که هنوز کارت ندارد هیچ فرمانی نمی‌پذیرد
    assertEquals(CommandCard.Verdict.FORGED, CommandCard.read(cash, null).verdict)
    assertEquals(CommandCard.Verdict.FORGED, CommandCard.read(cash, CommandCard.Key("abcd", 1)).verdict)
    //  کدِ کالا
    assertEquals(CommandCard.Verdict.NOT_COMMAND, CommandCard.read("4006381333931", key).verdict)
    assertFalse(CommandCard.isCommandLike("4006381333931"))
  }

  @Test fun `کارتِ تازه همهٔ کارت‌های پیشین را باطل می‌کند`() {
    val old = cmd(CommandCard.Command.CASH)
    val next = CommandCard.rotate(key, ByteArray(32) { (it * 3).toByte() })
    assertEquals(2, next.gen)
    assertEquals(CommandCard.Verdict.OLD_CARD, CommandCard.read(old, next).verdict)
    assertNull(CommandCard.parse(old, next))
    assertEquals(CommandCard.Command.CASH, CommandCard.parse(cmd(CommandCard.Command.CASH, next), next))
    //  کارتِ نخست از هیچ
    assertEquals(1, CommandCard.rotate(null, ByteArray(32)).gen)
  }

  private val dev = "bt:AA:BB:CC:DD:EE:02"
  private fun seq(vararg codes: String, step: Long = 1_000L) = codes.mapIndexed { i, c -> QueuedScan("$i", dev, i * step, c) }
  private fun split(list: List<QueuedScan>) = CommandSequence.split(list) { CommandCard.parse(it, key) }
  private val NEW get() = cmd(CommandCard.Command.NEW)
  private val CASH get() = cmd(CommandCard.Command.CASH)
  private val UNDO get() = cmd(CommandCard.Command.UNDO)

  @Test fun `بی «نقد» هیچ فاکتوری نقد بسته نمی‌شود و فاکتورِ باز باز می‌ماند`() {
    val r = split(seq("A", "B", NEW, "C"))
    assertEquals(listOf(ScanBatch.End.NEW, ScanBatch.End.OPEN), r.batches.map { it.end })
    assertTrue(r.batches.none { it.cashClosed })
    //  هر اسکن یک بار در یک فاکتور یا سرگردان
    assertEquals(listOf("0", "1", "2"), r.batches[0].span.map { it.id })
    assertEquals(listOf("3"), r.batches[1].span.map { it.id })
  }

  @Test fun `«نقد» ِ دوباره و «حذفِ آخرین» روی فاکتورِ خالی بی اثرند و سرگردان می‌مانند`() {
    val r = split(seq(CASH, UNDO, "A", CASH, CASH, UNDO, NEW, "B", UNDO, CASH))
    assertEquals(listOf(listOf("A")), r.batches.map { b -> b.items.map { it.code } })
    assertTrue(r.batches.single().cashClosed)
    assertEquals(
      listOf(CommandSequence.Note.DOUBLE_CASH, CommandSequence.Note.EMPTY_UNDO, CommandSequence.Note.DOUBLE_CASH, CommandSequence.Note.EMPTY_UNDO, CommandSequence.Note.DOUBLE_CASH),
      r.notes.map { it.first },
    )
    //  «حذفِ آخرین» B را برد ⇒ «نقد» ِ پایانی بی کالا ⇒ نادیده
    val used = r.batches.flatMap { it.span }.map { it.id } + r.stray.map { it.id }
    assertEquals((0..9).map { "$it" }, used.sortedBy { it.toInt() })
  }

  @Test fun `کدِ ناشناس یا جعلی درونِ فاکتور کالا نمی‌شود ولی در همان فاکتور برداشته می‌شود`() {
    val fake = "TOHID-CMD-CASH-1-123456789012"
    val r = split(seq("A", fake, "B", CASH))
    val b = r.batches.single()
    assertEquals(listOf("A", "B"), b.items.map { it.code })
    assertTrue(b.cashClosed)
    assertEquals(listOf("0", "1", "2", "3"), b.span.map { it.id })
    assertEquals(CommandSequence.Note.FOREIGN_CODE, r.notes.single().first)
  }

  @Test fun `اسکنِ کهنهٔ مانده به «نقد» ِ مشتریِ بعدی نمی‌چسبد`() {
    //  دو کالای انبارگردانی، ۳۰ دقیقه بعد مشتری: C و «نقد»
    val scans = listOf(
      QueuedScan("1", dev, 0, "A"), QueuedScan("2", dev, 1_000, "B"),
      QueuedScan("3", dev, 30 * 60_000L, "C"), QueuedScan("4", dev, 30 * 60_000L + 2_000, CASH),
    )
    val r = CommandSequence.split(scans, ScanSaleRule.MAX_GAP_MS) { CommandCard.parse(it, key) }
    assertEquals(listOf(ScanBatch.End.GAP, ScanBatch.End.CASH), r.batches.map { it.end })
    assertEquals(listOf(listOf("A", "B"), listOf("C")), r.batches.map { b -> b.items.map { it.code } })
    assertEquals(listOf("3", "4"), r.batches[1].span.map { it.id })
    //  بی مرزِ فاصله همه یک فاکتور است و دروازه نگهش می‌دارد
    val one = split(scans).batches.single()
    assertEquals(ScanSaleRule.Why.LONG_GAP, ScanSaleRule.why(one, open, 0))
  }

  private val open = ScanSaleRule.Gate(cardOn = true, tillOpen = true, canSell = true)

  @Test fun `دروازه - کارت، نقد، صندوق، اجازه، کدِ ناشناس و فاصلهٔ دراز`() {
    val paid = split(seq("A", "B", CASH)).batches.single()
    assertNull(ScanSaleRule.why(paid, open, 0))
    assertEquals(ScanSaleRule.Action.RECORD_CASH, ScanSaleRule.decide(paid, open, 0))
    assertEquals(ScanSaleRule.Why.CARD_OFF, ScanSaleRule.why(paid, open.copy(cardOn = false), 0))
    assertEquals(ScanSaleRule.Why.TILL_CLOSED, ScanSaleRule.why(paid, open.copy(tillOpen = false), 0))
    assertEquals(ScanSaleRule.Why.NO_PERMISSION, ScanSaleRule.why(paid, open.copy(canSell = false), 0))
    assertEquals(ScanSaleRule.Why.UNKNOWN_CODE, ScanSaleRule.why(paid, open, 1))
    assertEquals(ScanSaleRule.Why.NOT_PAID, ScanSaleRule.why(split(seq("A", NEW)).batches.single(), open, 0))
    //  کالاهای یک فاکتور ۱۱ دقیقه از هم دور ⇒ شاید اسکنِ انبارگردانیِ مانده ⇒ نگه‌داشته
    val far = split(seq("A", "B", CASH, step = 11 * 60_000L)).batches.single()
    assertEquals(ScanSaleRule.Why.LONG_GAP, ScanSaleRule.why(far, open, 0))
    assertEquals(ScanSaleRule.Action.HOLD, ScanSaleRule.decide(far, open, 0))
  }

  @Test fun `یادداشتِ فاکتور - زنده یا از حافظه`() {
    val live = split(seq("A", CASH)).batches.single()
    assertEquals(ScanSaleRule.NOTE_LIVE, ScanSaleRule.note(live))
    val mem = split(listOf(QueuedScan("m1", dev, 5, "A", ScanSource.MEMORY), QueuedScan("m2", dev, 6, CASH, ScanSource.MEMORY))).batches.single()
    assertEquals(ScanSaleRule.NOTE_MEMORY, ScanSaleRule.note(mem))
    assertTrue(ScanSaleRule.isScannerSale(ScanSaleRule.NOTE_MEMORY))
    assertTrue(ScanSaleRule.isScannerSale(ScanSaleRule.NOTE_LIVE))
    assertFalse(ScanSaleRule.isScannerSale("بستهٔ هدیه"))
    assertFalse(ScanSaleRule.isScannerSale(null))
  }
}
