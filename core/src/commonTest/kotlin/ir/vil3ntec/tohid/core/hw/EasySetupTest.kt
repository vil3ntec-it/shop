package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  HW-12 — راه‌اندازیِ آسان: زیرشبکه، یکی کردنِ یافته‌ها، چاپِ شناسایی، پیدا کردنِ پیشوند/پسوند و برگهٔ بارکدخوان.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class EasySetupTest {

  /* ============================ زیرشبکه ============================ */

  @Test
  fun slash24ListsAll254HostsWithoutSelfNetworkOrBroadcast() {
    val h = Subnet.hosts("192.168.1.23", 24)
    assertEquals(253, h.size)
    assertEquals("192.168.1.1", h.first())
    assertEquals("192.168.1.254", h.last())
    assertFalse("192.168.1.23" in h)
    assertFalse("192.168.1.0" in h)
    assertFalse("192.168.1.255" in h)
  }

  @Test
  fun widePrefixIsNarrowedToOwn24() {
    val h = Subnet.hosts("10.20.30.40", 8)
    assertTrue(h.size <= Subnet.MAX_HOSTS)
    assertTrue(h.all { it.startsWith("10.20.30.") })
    assertEquals(253, h.size)
    val b = Subnet.hosts("172.16.5.9", 16)
    assertTrue(b.all { it.startsWith("172.16.5.") })
  }

  @Test
  fun smallSubnetsAndMaxAreBounded() {
    assertEquals(listOf("192.168.1.9", "192.168.1.10"), Subnet.hosts("192.168.1.11", 29).take(2))
    assertEquals(5, Subnet.hosts("192.168.1.11", 29).size)
    assertEquals(listOf("192.168.1.10"), Subnet.hosts("192.168.1.11", 31))
    assertEquals(emptyList(), Subnet.hosts("192.168.1.11", 32))
    assertEquals(10, Subnet.hosts("192.168.1.11", 24, max = 10).size)
    assertEquals(Subnet.MAX_HOSTS, Subnet.hosts("10.0.0.1", 24, max = 10_000).size + 1)
  }

  @Test
  fun publicCgnatLinkLocalAndBadAddressesAreNeverScanned() {
    for (ip in listOf("8.8.8.8", "100.64.1.2", "169.254.10.10", "172.32.0.5", "11.0.0.1", "192.169.1.1", "", "300.1.1.1", "abc"))
      assertEquals(emptyList(), Subnet.hosts(ip, 24), ip)
    assertEquals(emptyList(), Subnet.hosts("192.168.1.5", 40))
    assertEquals(emptyList(), Subnet.hosts("192.168.1.5", -1))
    assertTrue(Subnet.isPrivate("172.31.255.1"))
    assertFalse(Subnet.isPrivate("172.15.0.1"))
  }

  @Test
  fun virtualAndDuplicateInterfacesAreSkipped() {
    val all = listOf(
      Subnet.Lan("wlan0", "192.168.1.23", 24),
      Subnet.Lan("docker0", "172.17.0.1", 16),
      Subnet.Lan("vEthernet (WSL)", "172.20.0.1", 20),
      Subnet.Lan("eth0", "192.168.1.40", 24),
      Subnet.Lan("rmnet_data0", "100.70.1.2", 30),
      Subnet.Lan("en0 Local Area Connection", "10.0.0.8", 24),
    )
    assertEquals(listOf("wlan0", "en0 Local Area Connection"), Subnet.usable(all).map { it.name })
  }

  @Test
  fun scanBatchesNeverExceedConcurrencyAndWorstCaseIsBounded() {
    val hosts = Subnet.hosts("192.168.0.77", 24)
    val b = NetScan.batches(hosts)
    assertTrue(b.all { it.size <= NetScan.CONCURRENCY })
    assertEquals(hosts, b.flatten())
    assertTrue(NetScan.batches(hosts, 1000).all { it.size <= NetScan.CONCURRENCY })
    assertEquals(8L * NetScan.CONNECT_TIMEOUT_MS, NetScan.worstCaseMs(253))
    assertTrue(NetScan.worstCaseMs(Subnet.MAX_HOSTS) <= 4_000L)
    assertEquals(9100, NetScan.PORT)
  }

  @Test
  fun progressMessagesArePersianAndHonest() {
    assertTrue(NetScan.Progress(0, 0, 0).message.contains("پیدا نشد"))
    assertTrue(NetScan.Progress(253, 40, 1).message.contains("40 از 253"))
    assertTrue(NetScan.Progress(253, 253, 0, finished = true).message.contains("چاپگری با درگاهِ ۹۱۰۰ پیدا نشد"))
    assertEquals("2 چاپگر در 253 نشانی پیدا شد", NetScan.Progress(253, 253, 2, finished = true).message)
    assertEquals(1f, NetScan.Progress(0, 0, 0).fraction)
  }

  /* ============================ یافته‌ها ============================ */

  @Test
  fun keyFollowsLinkParseRules() {
    assertEquals("bt:AA:BB:CC:DD:EE:FF", Discovery.key("aa:bb:cc:dd:ee:ff"))
    assertEquals("bt:AA:BB:CC:DD:EE:FF", Discovery.key("bt:AA:bb:CC:dd:EE:ff"))
    assertEquals("net:192.168.1.50:9100", Discovery.key("net:192.168.1.50"))
    assertEquals("net:printer.local:9100", Discovery.key("net:Printer.LOCAL:9100"))
    assertEquals("net:192.168.1.50:6101", Discovery.key("net:192.168.1.50:6101"))
  }

  @Test
  fun mergeDedupesAndKeepsBestNameAndPairedState() {
    val a = FoundPrinter("bt:AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF", FoundPrinter.Via.BT_NEARBY, printerLike = false)
    val b = FoundPrinter("aa:bb:cc:dd:ee:ff", "XP-58", FoundPrinter.Via.BT_PAIRED)
    val n1 = FoundPrinter.network("192.168.1.50")
    val n2 = FoundPrinter("net:192.168.1.50:9100", "192.168.1.50", FoundPrinter.Via.NETWORK)
    val other = FoundPrinter("bt:11:22:33:44:55:66", "Speaker", FoundPrinter.Via.BT_NEARBY, printerLike = false)
    val m = Discovery.merge(listOf(a, n1), listOf(b, n2, other))
    assertEquals(3, m.size)
    val bt = m.single { it.link.startsWith("bt:AA") || it.link.startsWith("aa") }
    assertEquals("XP-58", bt.name)
    assertEquals(FoundPrinter.Via.BT_PAIRED, bt.via)
    assertTrue(bt.printerLike)
    //  چاپگرها اول، بعد شبکه پیش از بلوتوث؛ «شاید چاپگر» آخر
    assertEquals(listOf("192.168.1.50", "XP-58", "Speaker"), m.map { it.name })
    //  یکی کردن دوباره چیزی را عوض نمی‌کند
    assertEquals(m, Discovery.merge(m, m))
  }

  @Test
  fun knownMatchesSavedLinksIncludingLegacyBareMac() {
    val f = FoundPrinter("bt:AA:BB:CC:DD:EE:FF", "XP", FoundPrinter.Via.BT_PAIRED)
    assertTrue(Discovery.known(f, listOf("aa:bb:cc:dd:ee:ff")))
    assertTrue(Discovery.known(FoundPrinter.network("192.168.1.50"), listOf("net:192.168.1.50")))
    assertFalse(Discovery.known(FoundPrinter.network("192.168.1.51"), listOf("net:192.168.1.50:9100")))
    assertFalse(Discovery.known(f, emptyList()))
  }

  @Test
  fun bluetoothClassRanksPrintersAndDropsClearNonPrinters() {
    assertEquals(true, BtClass.printer(0x000680, "BlueTooth Printer"))
    assertEquals(true, BtClass.printer(0x040680, ""))
    assertEquals(true, BtClass.printer(0x1F00, "MPT-II"))
    assertNull(BtClass.printer(0x1F00, "BT-01"))
    assertNull(BtClass.printer(0x0000, ""))
    assertNull(BtClass.printer(0x0620, "Camera"))
    assertEquals(false, BtClass.printer(0x5A020C, "Galaxy A14"))
    assertEquals(false, BtClass.printer(0x240404, "Buds"))
    assertEquals(false, BtClass.printer(0x0540, "Keyboard"))
  }

  /* ============================ چاپِ شناسایی ============================ */

  @Test
  fun testPrintPayloadIsAsciiEscPosWithInitFeedAndCut() {
    val b = EasyTestPrint.bytes("net:192.168.1.50:9100")
    assertContentEquals(EscPos.INIT, b.copyOfRange(0, 2))
    assertContentEquals(EscPos.cut(CutMode.PARTIAL), b.copyOfRange(b.size - 4, b.size))
    val text = b.decodeToString()
    assertTrue(text.contains("TOHID\n"))
    assertTrue(text.contains("net:192.168.1.50:9100\n"))
    assertTrue(b.all { it >= 0 }, "فقط بایتِ ASCII")
    //  بی ضربهٔ کشو و بی تصویر
    assertFalse((0 until b.size - 1).any { b[it] == 0x1B.toByte() && b[it + 1] == 0x70.toByte() })
    assertFalse((0 until b.size - 1).any { b[it] == 0x1D.toByte() && b[it + 1] == 0x76.toByte() })
    val none = EasyTestPrint.bytes("x", CutMode.NONE)
    assertEquals(0x0A, none.last().toInt())
    assertEquals("??-ok", EasyTestPrint.ascii("چا-ok"))
    assertEquals(40, EasyTestPrint.ascii("a".repeat(99)).length)
  }

  /* ============================ پیشوند و پسوند ============================ */

  @Test
  fun detectsPrefixSuffixAndEnterFromSampleScan() {
    val code = ScannerDetect.TEST_CODE
    val plain = ScannerDetect.detect(code, enter = true)
    assertTrue(plain.ok)
    assertEquals("", plain.prefix); assertEquals("", plain.suffix)
    assertTrue(plain.message.contains("پیش‌فرض"))

    val aim = ScannerDetect.detect("]C1$code", enter = true)
    assertTrue(aim.ok)
    assertEquals("]C1", aim.prefix)
    assertEquals("", aim.suffix)

    val noEnter = ScannerDetect.detect("#$code$$", enter = false)
    assertTrue(noEnter.ok)
    assertEquals("#", noEnter.prefix)
    assertEquals("$$", noEnter.suffix)
    assertFalse(noEnter.enter)
  }

  @Test
  fun detectExplainsFailures() {
    val code = ScannerDetect.TEST_CODE
    assertFalse(ScannerDetect.detect("", enter = true).ok)
    assertTrue(ScannerDetect.detect("فخاهی۲۴۶۸", enter = true).message.contains("انگلیسی"))
    assertTrue(ScannerDetect.detect(code.lowercase(), enter = true).message.contains("Caps Lock"))
    assertTrue(ScannerDetect.detect("1234567", enter = true).message.contains("نه کدِ آزمون"))
    assertTrue(ScannerDetect.detect(code, enter = false).message.contains("Enter"))
    assertFalse(ScannerDetect.detect(code, enter = false).ok)
    assertFalse(ScannerDetect.detect("ABCDEFGHIJ$code", enter = true).ok)
    assertFalse(ScannerDetect.detect(" $code", enter = true).ok)
    assertFalse(ScannerDetect.detect("$code\t", enter = true).ok)
  }

  @Test
  fun applyStoresOnlyPrefixAndSuffixAndFailureChangesNothing() {
    val base = ScanConfig(maxGapMs = 120, minLength = 6, prefix = "OLD", suffix = "")
    val r = ScannerDetect.detect("]C1${ScannerDetect.TEST_CODE}#", enter = true)
    val next = ScannerDetect.apply(base, r)
    assertEquals(ScanConfig(120, 6, "]C1", "#"), next)
    assertTrue(ScannerDetect.changes(base, r))
    assertFalse(ScannerDetect.changes(next, r))
    val bad = ScannerDetect.detect("nope", enter = true)
    assertEquals(base, ScannerDetect.apply(base, bad))
    assertFalse(ScannerDetect.changes(base, bad))
    //  پیدا شدنِ «بی پیشوند و پسوند» پیشوندِ کهنه را پاک می‌کند
    assertEquals("", ScannerDetect.apply(base, ScannerDetect.detect(ScannerDetect.TEST_CODE, true)).prefix)
    //  آنچه پیدا شد همان است که KeyScan می‌شناسد
    val k = KeyScan(next)
    var got: String? = null
    "]C1${ScannerDetect.TEST_CODE}#".forEachIndexed { i, c -> k.feed(c, i * 5L)?.let { got = it } }
    assertEquals(ScannerDetect.TEST_CODE, got ?: k.submit(60))
  }

  /* ============================ برگهٔ بارکدخوان ============================ */

  @Test
  fun sheetHasEncodableTestCodesAndNoUnsourcedVendorCodes() {
    val s = ScannerSheet.build()
    assertTrue(s.title.isNotBlank())
    assertEquals(3, s.steps.size)
    assertEquals(listOf(ScannerSheet.Kind.CODE128, ScannerSheet.Kind.EAN13), s.codes.map { it.kind })
    assertEquals(ScannerDetect.TEST_CODE, s.codes.first().data)
    s.codes.forEach { assertTrue((it.modules()?.size ?: 0) > 0, it.label) }
    assertTrue(Ean13.isValid(ScannerSheet.SAMPLE_EAN))
    assertTrue(ScannerSheet.SAMPLE_EAN.startsWith("200"))
    //  صادقانه: امروز هیچ کدِ سازنده‌ای سنجیده نشده
    assertEquals(emptyList(), ScannerSheet.VENDORS)
    assertEquals(emptyList(), s.vendors)
    assertTrue(s.note.contains("حدسی"))
  }

  @Test
  fun vendorRowsNeedSourceAndEncodableCodes() {
    val ok = ScannerSheet.Vendor("Brand", "Brand manual rev A, p. 12", listOf(ScannerSheet.Code("CR", ScannerSheet.Kind.CODE128, "SAMPLE")))
    val noSource = ok.copy(source = " ")
    val badCode = ok.copy(codes = listOf(ScannerSheet.Code("x", ScannerSheet.Kind.EAN13, "123")))
    val empty = ok.copy(codes = emptyList())
    assertEquals(listOf(ok), ScannerSheet.build(listOf(ok, noSource, badCode, empty)).vendors)
  }
}
