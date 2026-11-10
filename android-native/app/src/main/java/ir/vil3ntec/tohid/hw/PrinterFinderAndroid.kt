package ir.vil3ntec.tohid.hw

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.hw.BtClass
import ir.vil3ntec.tohid.core.hw.FoundPrinter
import ir.vil3ntec.tohid.core.hw.Mdns
import ir.vil3ntec.tohid.core.hw.MdnsProbe
import ir.vil3ntec.tohid.core.hw.Subnet
import ir.vil3ntec.tohid.print.ThermalPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address

/**
 *  HW-12 — مرزِ پلتفرمِ «پیدا کردنِ خودکارِ چاپگر» روی اندروید: کارتِ وای‌فای/اترنتِ فعال و جست‌وجوی
 *  بلوتوثِ نزدیک. منطق (زیرشبکه، رده، یکی کردن) در `core/hw/PrinterDiscovery.kt`؛ کامپیوتر جایگزینِ
 *  خودش را دارد (`desktop/…/hw/PrinterFinderDesktop.kt`، همان API، بی جست‌وجوی بلوتوث).
 *
 *  اجازه‌ها فقط هنگامِ زدنِ دکمه خواسته می‌شوند (همان `RequestMultiplePermissions` ِ برنامه):
 *  اندروید ۱۲+ «دستگاه‌های نزدیک» (`BLUETOOTH_SCAN` با `neverForLocation` و `BLUETOOTH_CONNECT`)؛ اندروید
 *  ۱۱ و پایین‌تر جست‌وجوی بلوتوث را فقط با `ACCESS_FINE_LOCATION` (و مکان‌یابِ روشن) می‌دهد.
 */
object PrinterFinderPlatform {

  /** درازای جست‌وجوی بلوتوث — خودِ اندروید هم حدودِ ۱۲ ثانیه می‌گردد */
  const val BLUETOOTH_SCAN_MS = 12_000L

  fun bluetoothScan(): Boolean = true

  /** کارت‌های وای‌فای و اترنت (نه داده‌ی همراه) با نشانیِ IPv4 و پیشوند */
  fun lanInterfaces(context: Context): List<Subnet.Lan> = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching emptyList<Subnet.Lan>()
    //  وای‌فایِ بی اینترنتِ چاپگر ممکن است شبکهٔ پیش‌فرض نباشد (داده‌ی همراه پیش‌فرض است)
    @Suppress("DEPRECATION")
    val nets = (listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()).distinct()
    nets.flatMap { n ->
      val caps = cm.getNetworkCapabilities(n)
      val lan = caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
      val lp = if (lan) cm.getLinkProperties(n) else null
      lp?.linkAddresses.orEmpty().filter { it.address is Inet4Address }.map {
        Subnet.Lan(lp?.interfaceName ?: "wlan", it.address.hostAddress.orEmpty(), it.prefixLength)
      }
    }
  }.getOrDefault(emptyList())

  /**
   *  HW-12 — چاپگرِ «خام» ِ شبکه با mDNS (`core/hw/Mdns.kt`): یک پرسشِ یک‌باره از هر کارتِ [lans] و پاسخِ تک‌پخش، بی
   *  قفلِ چندپخشِ وای‌فای. اندروید ۱۷ بی اجازهٔ شبکهٔ محلی (HW-13) ⇒ هیچ، پیش از هر سوکت. خطای سوکت ⇒ همان کارت هیچ.
   */
  suspend fun mdnsPrinters(context: Context, lans: List<Subnet.Lan>): List<FoundPrinter> = withContext(Dispatchers.IO) {
    if (LocalNetworkAccess.blocked(context, Mdns.GROUP)) return@withContext emptyList()
    lans.filterNot { LocalNetworkAccess.blocked(context, it.ip) }
      .flatMap { lan -> runCatching { MdnsProbe.ask(lan.ip) }.getOrDefault(emptyList()) }
  }

  fun bluetoothPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

  fun bluetoothGranted(context: Context): Boolean =
    bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

  /**
   *  جست‌وجوی بلوتوثِ کلاسیکِ نزدیک تا [ms]؛ هر یافته با [onFound] (رشتهٔ اصلی). دستگاهی که رده‌اش روشن
   *  می‌گوید چاپگر نیست (گوشی، هدفون…) نمی‌آید. نتیجه: پیامِ خطا، یا `null`.
   */
  suspend fun bluetoothNearby(context: Context, ms: Long, onFound: (FoundPrinter) -> Unit): String? {
    val adapter = ThermalPrinter.adapter(context) ?: return "این دستگاه بلوتوث ندارد"
    if (!bluetoothGranted(context)) return "اجازهٔ «دستگاه‌های نزدیک» داده نشده"
    if (!adapter.isEnabled) return "بلوتوث خاموش است — روشنش کنید و دوباره بزنید"
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_FOUND) return
        val device = deviceOf(intent) ?: return
        runCatching {
          val name = device.name.orEmpty()
          val verdict = BtClass.printer(device.bluetoothClass?.deviceClass ?: 0, name)
          if (verdict == false) return@runCatching
          val paired = device.bondState == BluetoothDevice.BOND_BONDED
          onFound(FoundPrinter(
            "bt:${device.address}", name.ifBlank { device.address },
            if (paired) FoundPrinter.Via.BT_PAIRED else FoundPrinter.Via.BT_NEARBY, printerLike = verdict == true,
          ))
        }
      }
    }
    return try {
      ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_NOT_EXPORTED)
      if (!adapter.startDiscovery()) {
        if (Build.VERSION.SDK_INT < 31) "جست‌وجوی بلوتوث آغاز نشد — مکان‌یابِ (GPS) دستگاه را روشن کنید" else "جست‌وجوی بلوتوث آغاز نشد"
      } else {
        delay(ms)
        null
      }
    } catch (e: SecurityException) {
      "اجازهٔ «دستگاه‌های نزدیک» داده نشده"
    } finally {
      runCatching { adapter.cancelDiscovery() }
      runCatching { context.unregisterReceiver(receiver) }
    }
  }

  @Suppress("DEPRECATION")
  private fun deviceOf(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

  /** جفت کردن (پنجرهٔ جفت‌سازیِ خودِ اندروید باز می‌شود)؛ از پیش جفت یا خطا ⇒ `false` */
  fun bond(context: Context, address: String): Boolean = runCatching {
    ThermalPrinter.adapter(context)?.getRemoteDevice(address)?.createBond() == true
  }.getOrDefault(false)
}
