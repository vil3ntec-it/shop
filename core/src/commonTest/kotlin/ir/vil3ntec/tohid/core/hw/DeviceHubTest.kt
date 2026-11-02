package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «دستگاه‌ها»: بردارهای نوع‌شناسی (رده، سرویس، نام)، فهرستِ ثبت‌شده و وصلِ دوباره — بی دستگاهِ واقعی */
class DeviceHubTest {

  private fun v(name: String, cod: Int? = null, services: Set<String> = emptySet(), t: DeviceTransport = DeviceTransport.CLASSIC, desc: String = "") =
    DeviceClassifier.classify(DeviceSignal("bt:AA:BB:CC:DD:EE:FF", name, cod, services, t, description = desc))

  @Test fun `رده روشن بر نام برتری دارد`() {
    //  Imaging + چاپگر
    assertEquals(DeviceVerdict(DeviceKind.PRINTER, sure = true), v("Unnamed", 0x0680))
    //  Peripheral + اسکنرِ دستیِ بارکد، با بیتِ صفحه‌کلید
    val s = v("XYZ", 0x0560)
    assertEquals(DeviceKind.SCANNER, s.kind); assertTrue(s.keyboard); assertTrue(s.sure)
    //  Health + ترازوی وزن
    assertEquals(DeviceKind.SCALE, v("Body", 0x090C).kind)
    //  گوشی با نامِ چاپگرمانند هم «دیگر» است — رده روشن است
    assertEquals(DeviceKind.OTHER, v("Printer phone", 0x020C).kind)
    //  هدفون
    assertEquals(DeviceKind.OTHER, v("Buds", 0x0404).kind)
  }

  @Test fun `ردهٔ نامشخص با نام و سرویس شناخته می‌شود`() {
    assertEquals(DeviceKind.PRINTER, v("MTP-II", 0x1F00).kind)
    assertEquals(DeviceKind.PRINTER, v("XP-P323B", 0x0000).kind)
    assertEquals(DeviceKind.SCANNER, v("NETUM NT-W8", 0x1F00).kind)
    assertEquals(DeviceKind.SCANNER, v("BarCode POS Scanner", 0x1F00).kind)
    assertEquals(DeviceKind.SCALE, v("ACS-30 Scale", null).kind)
    assertEquals(DeviceKind.DISPLAY, v("VFD Customer Display", null).kind)
    assertEquals(DeviceKind.UNKNOWN, v("HC-05", 0x1F00).kind)
    assertFalse(v("HC-05", 0x1F00).sure)
    //  سرویس‌های BLE
    assertEquals(DeviceKind.SCALE, v("WS", null, setOf("0000181d-0000-1000-8000-00805f9b34fb"), DeviceTransport.BLE).kind)
    assertEquals(DeviceKind.PRINTER, v("BLE", null, setOf("000018F0-0000-1000-8000-00805F9B34FB"), DeviceTransport.BLE).kind)
    assertEquals(DeviceKind.PRINTER, v("BLE", null, setOf("E7810A71-73AE-499D-8C15-FAA9AEF0C3F2"), DeviceTransport.BLE).kind)
  }

  @Test fun `حالتِ صفحه‌کلید و سریال جدا گزارش می‌شوند`() {
    val hid = v("Inateck BCST-70", null, setOf("1812"), DeviceTransport.BLE)
    assertEquals(DeviceKind.SCANNER, hid.kind); assertTrue(hid.keyboard); assertFalse(hid.serial)
    val spp = v("Inateck BCST-70", 0x1F00, setOf("00001101-0000-1000-8000-00805f9b34fb"))
    assertTrue(spp.serial); assertFalse(spp.keyboard)
    //  صفحه‌کلیدِ معمولی بارکدخوان نیست و در فهرست نمی‌آید
    val kb = v("Logi K380", 0x0540)
    assertEquals(DeviceKind.OTHER, kb.kind); assertFalse(DeviceClassifier.shopDevice(kb))
    //  درگاهِ COM ِ کامپیوتر همیشه سریال است؛ نوع از شرحِ درگاه
    val com = DeviceClassifier.classify(DeviceSignal("bt:COM5", "COM5", transport = DeviceTransport.SERIAL, description = "Honeywell Imaging (COM5)"))
    assertEquals(DeviceKind.SCANNER, com.kind); assertTrue(com.serial)
    assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.classify(DeviceSignal("bt:COM3", "COM3", transport = DeviceTransport.SERIAL, description = "Standard Serial over Bluetooth link (COM3)")).kind)
  }

  @Test fun `فهرستِ ثبت‌شده رفت‌وبرگشت دارد و سطرِ خراب کنار می‌رود`() {
    val list = listOf(
      SavedDevice("bt:aa:bb:cc:dd:ee:ff", "Printer\t58", DeviceKind.PRINTER),
      SavedDevice("bt:11:22:33:44:55:66", "Scanner", DeviceKind.SCANNER, auto = false),
    )
    val back = DeviceRoster.decode(DeviceRoster.encode(list) + "\nخراب\nbt:X\tNOPE\t1\tn\nbt:AA:BB:CC:DD:EE:FF\tSCALE\t1\tdup")
    assertEquals(2, back.size)
    assertEquals("Printer 58", back[0].name)
    assertEquals(DeviceKind.PRINTER, back[0].kind)
    assertFalse(back[1].auto)
    assertTrue(DeviceRoster.backgroundScanners(back).isEmpty())
    val on = DeviceRoster.upsert(back, back[1].copy(auto = true))
    assertEquals(listOf("bt:11:22:33:44:55:66"), DeviceRoster.backgroundScanners(on).map { it.link })
    assertEquals(1, DeviceRoster.remove(on, "bt:AA:BB:CC:DD:EE:FF").size)
  }

  @Test fun `یافته‌های تازه بی ثبت‌شده‌ها و بی دستگاهِ غیرِ دکان`() {
    val saved = listOf(SavedDevice("bt:AA:AA:AA:AA:AA:AA", "P", DeviceKind.PRINTER))
    fun f(link: String, name: String, cod: Int?) = DeviceSignal(link, name, cod).let { it to DeviceClassifier.classify(it) }
    val merged = DeviceRoster.merge(
      saved,
      listOf(
        f("bt:aa:aa:aa:aa:aa:aa", "P", 0x0680),
        f("bt:BB:BB:BB:BB:BB:BB", "Phone", 0x020C),
        f("bt:CC:CC:CC:CC:CC:CC", "HC-05", 0x1F00),
        f("bt:DD:DD:DD:DD:DD:DD", "NETUM scanner", 0x1F00),
        f("bt:DD:DD:DD:DD:DD:DD", "NETUM scanner", 0x1F00),
      ),
    )
    assertEquals(listOf("NETUM scanner", "HC-05"), merged.map { it.first.name })
  }

  @Test fun `وصلِ دوباره پله‌پله و سقف‌دار است`() {
    assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L, 60_000L, 60_000L), (0..6).map(Reconnect::delayMs))
    assertEquals(0, Reconnect.nextAttempt(5, Reconnect.STABLE_MS))
    assertEquals(3, Reconnect.nextAttempt(2, 500))
  }
}
