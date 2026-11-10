package ir.vil3ntec.tohid.print

import ir.vil3ntec.tohid.core.hw.NetScan
import ir.vil3ntec.tohid.core.hw.ScannerSheet
import ir.vil3ntec.tohid.core.i18n.DesktopPhrases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 *  HW-12 — راه‌اندازیِ آسان: سیم‌کشیِ برنامه. منطقِ خالص در `core` (EasySetupTest)؛ این‌جا سوکتِ واقعیِ
 *  جست‌وجو، جفتِ پرونده‌های پلتفرم، مانیفست و «خاموش یعنی همان امروز» سنجیده می‌شود.
 */
class EasySetupWiringTest {

  private val repo = DesktopPhrases.repo()
  private val main = repo.resolve("android-native/app/src/main/java/ir/vil3ntec/tohid")

  @Test fun `جست‌وجوی شبکه درگاهِ باز را پیدا می‌کند و هیچ بایتی به چاپگر نمی‌فرستد`() {
    val loop = InetAddress.getLoopbackAddress().hostAddress
    ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
      val received = AtomicInteger(0)
      val reader = thread(isDaemon = true) {
        runCatching { server.accept().use { s -> s.soTimeout = 2_000; while (s.getInputStream().read() >= 0) received.incrementAndGet() } }
      }
      val progress = ArrayList<NetScan.Progress>()
      val found = runBlocking { NetPrinterScan.scan(listOf(loop), port = server.localPort) { p, _ -> progress += p } }
      reader.join(3_000)
      assertEquals(listOf(loop), found)
      assertEquals(0, received.get())
      assertTrue(progress.last().finished)
      assertEquals(1, progress.last().found)
    }
  }

  @Test fun `درگاهِ بسته پیدا نمی‌شود و فهرستِ خالی بی سوکت تمام می‌شود`() {
    val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    val loop = InetAddress.getLoopbackAddress().hostAddress
    assertEquals(emptyList<String>(), runBlocking { NetPrinterScan.scan(listOf(loop), port = port, timeoutMs = 200) })
    val progress = ArrayList<NetScan.Progress>()
    assertEquals(emptyList<String>(), runBlocking { NetPrinterScan.scan(emptyList()) { p, _ -> progress += p } })
    assertEquals(listOf(NetScan.Progress(0, 0, 0, finished = true)), progress)
  }

  @Test fun `کامپیوتر جایگزینِ همان API ِ پیدا کردنِ چاپگر را دارد و فایلِ اندرویدی کنار می‌رود`() {
    val gradle = repo.resolve("desktop/build.gradle.kts").readText()
    assertTrue(gradle.contains("\"hw/PrinterFinderAndroid.kt\""))
    val android = main.resolve("hw/PrinterFinderAndroid.kt").readText()
    val desk = repo.resolve("desktop/src/main/kotlin/ir/vil3ntec/tohid/hw/PrinterFinderDesktop.kt").readText()
    val api = Regex("""(?m)^\s*((?:suspend )?fun \w+\((?:[^()]|\([^()]*\))*\): [\w.<>?]+|const val \w+)""")
    fun sigs(s: String) = api.findAll(s).map { it.groupValues[1].trim() }.toSet()
    assertTrue(sigs(android).size >= 7)
    assertEquals(sigs(android), sigs(desk))
    //  روی کامپیوتر جست‌وجوی بلوتوث نیست و اجازه‌ای خواسته نمی‌شود — از متنِ پروندهٔ کامپیوتر، در هر دو ماژول
    assertTrue(desk.contains("fun bluetoothScan(): Boolean = false"))
    assertTrue(desk.contains("fun bluetoothPermissions(): List<String> = emptyList()"))
    assertTrue(android.contains("fun bluetoothScan(): Boolean = true"))
    //  و همان پیاده‌سازی‌ای که این ماژول کامپایل کرده (آزمون هم با `:app` ِ اندروید و هم با کامپیوتر اجرا می‌شود)
    if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
      assertFalse(ir.vil3ntec.tohid.hw.PrinterFinderPlatform.bluetoothScan())
      assertEquals(emptyList<String>(), ir.vil3ntec.tohid.hw.PrinterFinderPlatform.bluetoothPermissions())
    } else {
      assertTrue(ir.vil3ntec.tohid.hw.PrinterFinderPlatform.bluetoothScan())
    }
  }

  @Test fun `جست‌وجوی شبکه پرسشِ mDNS را هم می‌فرستد و اندروید پیش از سوکت اجازهٔ شبکهٔ محلی را می‌پرسد`() {
    val ui = main.resolve("ui/screens/EasySetupAndroid.kt").readText()
    val job = ui.substringAfter("netJob = scope.launch {").substringBefore("NetPrinterScan.scan(hosts)")
    assertTrue(job.contains("launch { add(PrinterFinderPlatform.mdnsPrinters(context, lans)) }"))
    val android = main.resolve("hw/PrinterFinderAndroid.kt").readText()
    val body = android.substringAfter("suspend fun mdnsPrinters(").substringBefore("\n  }\n")
    val ask = body.indexOf("MdnsProbe.ask(")
    assertTrue(ask > 0)
    assertTrue(body.indexOf("LocalNetworkAccess.blocked(context, Mdns.GROUP)") in 0 until ask)
    assertTrue(body.indexOf("LocalNetworkAccess.blocked(context, it.ip)") in 0 until ask)
    //  آیفون همان API را دارد و پرسش نمی‌فرستد (مجوزِ چندپخشِ اپل)
    val ios = repo.resolve("ios/shared/src/iosMain/kotlin/ir/vil3ntec/tohid/hw/PrinterFinderIos.kt").readText()
    assertTrue(ios.contains("suspend fun mdnsPrinters(context: Context, lans: List<Subnet.Lan>): List<FoundPrinter> = emptyList()"))
  }

  @Test fun `اجازهٔ جست‌وجوی بلوتوث در مانیفست با neverForLocation است و فقط هنگامِ زدنِ دکمه خواسته می‌شود`() {
    val manifest = repo.resolve("android-native/app/src/main/AndroidManifest.xml").readText()
    assertTrue(Regex("""android\.permission\.BLUETOOTH_SCAN"\s+android:usesPermissionFlags="neverForLocation"""").containsMatchIn(manifest))
    val ui = main.resolve("ui/screens/EasySetupAndroid.kt").readText()
    //  درخواستِ اجازه فقط درونِ onClick ِ دکمهٔ بلوتوث
    val launch = ui.lines().filter { it.contains("askBt.launch(") }
    assertEquals(1, launch.size)
    assertTrue(ui.substringBefore("askBt.launch(").substringAfterLast("OutlinedButton(").contains("onClick"))
  }

  @Test fun `تنظیمِ خاموش یعنی صفحهٔ چاپگر و بارکدخوان همان امروز`() {
    val pref = main.resolve("print/EasySetupAndroid.kt").readText()
    assertTrue(pref.contains("val on = mutableStateOf(false)"))
    assertTrue(pref.contains("getBoolean(KEY, false)"))
    assertEquals("easy_setup", EasySetupPref.KEY)
    assertFalse(EasySetupPref.on.value)
    val devices = main.resolve("ui/screens/Devices.kt").readText().lines()
    fun guarded(call: String, guard: String) {
      val at = devices.indexOfFirst { it.contains(call) }
      assertTrue("$call در Devices نیست", at >= 0)
      assertEquals("$call یک بار", 1, devices.count { it.contains(call) })
      assertTrue("$call پشتِ $guard نیست", devices.subList(maxOf(0, at - 3), at + 1).any { it.contains(guard) })
    }
    guarded("PrinterFinderDialog(", "if (finding && easy && canAdmin)")
    guarded("finding = true", "if (easy) {")
    guarded("ScannerSetupTools(", "EasySetupPref.on.value && canAdmin")
    assertTrue(devices.any { it.contains("val easy = ir.vil3ntec.tohid.print.EasySetupPref.on.value") })
    //  بارکدخوانِ صندوق دست نمی‌خورد: همان HwTools
    val scan = main.resolve("scan/ScanPrefs.kt").readText()
    assertFalse(scan.contains("EasySetup"))
    //  HW دورِ ۳۴ (#952): همان دروازهٔ HwTools، با «پایدار» ِ همیشگی برای صندوق — هنوز بی وابستگی به EasySetup
    assertTrue(scan.contains("fun current(): ScanConfig = if (HwTools.on.value) saved.value.copy(steady = true) else ScanConfig()"))
  }

  @Test fun `بارکدهای برگه در کاغذِ ۵۸ و ۸۰ میلی‌متری جا می‌شوند`() {
    for (paper in listOf(ThermalPrinter.WIDTH_58MM, ThermalPrinter.WIDTH_80MM)) {
      ScannerSheet.build().codes.forEach { c ->
        val m = c.modules()!!
        val img = ScannerSheetPrint.barcode(m, paper)
        assertTrue("${c.data} در $paper", img.width <= paper)
        assertTrue("ماژولِ ${c.data} دست‌کم دو نقطه", img.width >= (m.size + 20) * 2)
      }
    }
  }
}
