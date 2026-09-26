package android.net

import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class Network

/**
 *  «شبکه هست؟» روی کامپیوتر.
 *
 *  اندروید این را از سیستم می‌پرسد. این‌جا: یک رابطِ شبکهٔ روشن که
 *  loopback نباشد. سرور رسیدنی هست یا نه را خودِ درخواست می‌فهمد؛ این
 *  فقط جلوی تلاش‌های بی‌فایده را وقتی کابل کشیده شده می‌گیرد.
 */
class ConnectivityManager {
  val activeNetwork: Network? get() = if (hasInterface()) Network() else null
  fun getNetworkCapabilities(n: Network?): NetworkCapabilities? = n?.let { NetworkCapabilities() }

  private fun hasInterface(): Boolean = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
  }.getOrDefault(true)
}

class NetworkCapabilities {
  fun hasCapability(c: Int): Boolean = true
  fun hasTransport(t: Int): Boolean = t == TRANSPORT_ETHERNET || t == TRANSPORT_WIFI
  companion object {
    const val NET_CAPABILITY_INTERNET = 12
    const val NET_CAPABILITY_VALIDATED = 16
    const val TRANSPORT_WIFI = 1
    const val TRANSPORT_CELLULAR = 0
    const val TRANSPORT_ETHERNET = 3
  }
}
