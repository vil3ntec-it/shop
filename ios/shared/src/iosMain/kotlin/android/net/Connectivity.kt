package android.net

/*
 *  shimِ iOS ِ `ConnectivityManager` برای `Backend.isOnline`: iOS همیشه «نت هست» می‌گوید و خودِ
 *  درخواست اگر نت نباشد زود خطا می‌دهد (همان مسیرِ «آفلاین» ِ برنامه). پایشِ واقعیِ مسیر با
 *  `NWPathMonitor` کارِ گامِ ۲ است. `IosContext.getSystemService(CONNECTIVITY_SERVICE)` همین را می‌دهد.
 */
class Network internal constructor()

class NetworkCapabilities internal constructor() {
  fun hasCapability(capability: Int): Boolean = capability == NET_CAPABILITY_INTERNET || capability == NET_CAPABILITY_VALIDATED

  companion object {
    const val NET_CAPABILITY_INTERNET = 12
    const val NET_CAPABILITY_VALIDATED = 16
    const val TRANSPORT_WIFI = 1
    const val TRANSPORT_CELLULAR = 0
  }
}

class ConnectivityManager internal constructor() {
  val activeNetwork: Network? get() = Network()
  fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = network?.let { NetworkCapabilities() }

  companion object {
    internal val ios = ConnectivityManager()
  }
}
