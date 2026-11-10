package ir.vil3ntec.tohid.hw

import android.content.Context
import ir.vil3ntec.tohid.core.hw.FoundPrinter
import ir.vil3ntec.tohid.core.hw.MdnsProbe
import ir.vil3ntec.tohid.core.hw.Subnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 *  HW-12 روی کامپیوتر: همان API ِ `hw/PrinterFinderAndroid.kt`. کارتِ شبکه از `NetworkInterface` (روشن، نه
 *  loopback، نه مجازی و نه نقطه‌به‌نقطه؛ ماشینِ مجازی و VPN را `Subnet.usable` کنار می‌گذارد). جست‌وجوی
 *  بلوتوث نیست: «بلوتوث» روی کامپیوتر درگاهِ COM ِ چاپگرِ جفت‌شده در تنظیماتِ سیستم است (`ThermalPrinter.paired`).
 */
object PrinterFinderPlatform {

  const val BLUETOOTH_SCAN_MS = 12_000L

  fun bluetoothScan(): Boolean = false

  fun lanInterfaces(context: Context): List<Subnet.Lan> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
      .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
      .flatMap { ni ->
        ni.interfaceAddresses.filter { it.address is Inet4Address }.map {
          Subnet.Lan("${ni.name} ${ni.displayName.orEmpty()}".trim(), it.address.hostAddress.orEmpty(), it.networkPrefixLength.toInt())
        }
      }
  }.getOrDefault(emptyList())

  /** HW-12 — همان `MdnsProbe` ِ اندروید (پرسشِ یک‌بارهٔ mDNS از هر کارت)؛ کامپیوتر اجازهٔ شبکهٔ محلی نمی‌خواهد */
  suspend fun mdnsPrinters(context: Context, lans: List<Subnet.Lan>): List<FoundPrinter> = withContext(Dispatchers.IO) {
    lans.flatMap { lan -> runCatching { MdnsProbe.ask(lan.ip) }.getOrDefault(emptyList()) }
  }

  fun bluetoothPermissions(): List<String> = emptyList()

  fun bluetoothGranted(context: Context): Boolean = true

  suspend fun bluetoothNearby(context: Context, ms: Long, onFound: (FoundPrinter) -> Unit): String? =
    "روی کامپیوتر چاپگرِ بلوتوثی را در تنظیماتِ بلوتوثِ سیستم جفت کنید؛ درگاهِ COM ِ آن در فهرست می‌آید"

  fun bond(context: Context, address: String): Boolean = false
}
