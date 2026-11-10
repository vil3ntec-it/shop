package ir.vil3ntec.tohid.hw

import android.content.Context
import ir.vil3ntec.tohid.core.hw.FoundPrinter
import ir.vil3ntec.tohid.core.hw.Subnet
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_POINTOPOINT
import platform.posix.IFF_UP
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.sockaddr_in

/*
 *  جایگزینِ iOS ِ `hw/PrinterFinderAndroid.kt` (همان API ِ ‎PrinterFinderDesktop‎). کارتِ شبکهٔ روشن از `getifaddrs`
 *  (IPv4، نه loopback، نه نقطه‌به‌نقطه) ⇐ جست‌وجوی چاپگرِ شبکه (درگاهِ ۹۱۰۰) همان کدِ مشترک است. جست‌وجوی بلوتوث
 *  در iOS نیست (SPP؛ §۵.۱).
 */
object PrinterFinderPlatform {
  const val BLUETOOTH_SCAN_MS = 12_000L

  fun bluetoothScan(): Boolean = false

  fun lanInterfaces(context: Context): List<Subnet.Lan> = runCatching {
    memScoped {
      val head = alloc<CPointerVar<ifaddrs>>()
      if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
      val out = ArrayList<Subnet.Lan>()
      try {
        var p = head.value
        while (p != null) {
          val a = p.pointed
          val flags = a.ifa_flags.toInt()
          val addr = a.ifa_addr
          val mask = a.ifa_netmask
          if (addr != null && mask != null && addr.pointed.sa_family.toInt() == AF_INET &&
            flags and IFF_UP != 0 && flags and IFF_LOOPBACK == 0 && flags and IFF_POINTOPOINT == 0
          ) {
            val ip = ipv4(addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr)
            val prefix = mask.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr.countOneBits()
            out += Subnet.Lan(a.ifa_name?.toKString().orEmpty(), ip, prefix)
          }
          p = a.ifa_next
        }
      } finally {
        freeifaddrs(head.value)
      }
      out
    }
  }.getOrDefault(emptyList())

  /** `in_addr` به ترتیبِ شبکه (big-endian) در حافظه؛ روی arm64 ِ little-endian بایتِ اول کم‌ارزش‌ترین است */
  private fun ipv4(raw: UInt): String = (0 until 4).joinToString(".") { ((raw shr (8 * it)) and 0xFFu).toString() }

  /**
   *  HW-12: پرسشِ mDNS ِ اندروید/کامپیوتر (`MdnsProbe`، هدفِ jvm) این‌جا نیست — فرستادنِ چندپخش در iOS مجوزِ
   *  `com.apple.developer.networking.multicast` ِ اپل را می‌خواهد. جست‌وجوی ۹۱۰۰ همچنان هست.
   */
  suspend fun mdnsPrinters(context: Context, lans: List<Subnet.Lan>): List<FoundPrinter> = emptyList()

  fun bluetoothPermissions(): List<String> = emptyList()

  fun bluetoothGranted(context: Context): Boolean = false

  suspend fun bluetoothNearby(context: Context, ms: Long, onFound: (FoundPrinter) -> Unit): String? =
    "آیفون چاپگرِ بلوتوثیِ کلاسیک را نمی‌گیرد؛ چاپگرِ شبکه (Wi-Fi) را بیفزایید"

  fun bond(context: Context, address: String): Boolean = false
}
