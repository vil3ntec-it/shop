package android.location

/**
 *  کامپیوتر GPS ندارد. `getSystemService(LOCATION_SERVICE)` این‌جا `null`
 *  است، پس `DeviceLocation.current` همان «لوکیشن نبود» را می‌گوید — که
 *  طبقِ قراردادش خطا نیست و جلوی هیچ کاری را نمی‌گیرد.
 */
class Location(val provider: String?) {
  var latitude: Double = 0.0
  var longitude: Double = 0.0
  var accuracy: Float = 0f
  var time: Long = 0
  fun hasAccuracy(): Boolean = false
}

interface LocationListener {
  fun onLocationChanged(location: Location)
  fun onProviderDisabled(provider: String) {}
  fun onProviderEnabled(provider: String) {}
  fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}

class LocationManager {
  fun getProviders(enabledOnly: Boolean): List<String> = emptyList()
  fun getLastKnownLocation(provider: String): Location? = null
  fun isProviderEnabled(provider: String): Boolean = false
  fun getCurrentLocation(provider: String, signal: android.os.CancellationSignal?, executor: java.util.concurrent.Executor, consumer: java.util.function.Consumer<Location?>) { consumer.accept(null) }
  fun requestLocationUpdates(provider: String, minTime: Long, minDistance: Float, listener: LocationListener, looper: android.os.Looper?) {}
  fun removeUpdates(listener: LocationListener) {}
  companion object {
    const val GPS_PROVIDER = "gps"
    const val NETWORK_PROVIDER = "network"
  }
}
