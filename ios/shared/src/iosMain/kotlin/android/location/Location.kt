package android.location

import android.os.CancellationSignal
import ir.vil3ntec.tohid.ios.IosLocation
import ir.vil3ntec.tohid.ios.IosLocation.latLng
import ir.vil3ntec.tohid.ios.IosUi
import ir.vil3ntec.tohid.ios.jvm.Runnable
import java.util.concurrent.Executor
import java.util.function.Consumer
import platform.CoreLocation.CLLocation
import platform.Foundation.timeIntervalSince1970

/*
 *  shimِ iOS ِ `android.location` (گامِ ۷) — فقط آنچه **خودِ** `data/DeviceLocation.kt` ِ اندروید می‌خواهد، روی
 *  CoreLocation (‎ir/…/ios/IosLocation.kt‎: «هنگامِ استفاده»، یک‌باره، روی رشتهٔ اصلی). با آن همان قاعده‌های اندروید
 *  (تازگیِ ده‌دقیقه‌ای، پروندهٔ `tohid-location`، مهلتِ ۱۲ ثانیه، دقیق‌ترینِ تازه‌ها) روی آیفون هم یک کد است و
 *  جایگزینِ پیشین (`DeviceLocationIos.kt`) حذف شد.
 *
 *   - فراهم‌کننده: CoreLocation خودش GPS و Wi-Fi و دکل را می‌آمیزد ⇐ یک فراهم‌کننده (`gps`)، همیشه روشن؛ اجازه را
 *     خودِ `DeviceLocation.granted` با `ContextCompat` می‌سنجد (‎ios/IosPermissions.kt‎).
 *   - برچسبِ هر نقطه (`Location.provider`) از دقتش: تا ۵۰ متر `gps`، بیشتر `network` — همان برچسبی که سرور از
 *     اندروید می‌گیرد (و همان قاعدهٔ جایگزینِ پیشین).
 *   - `getLastKnownLocation` ⇐ `CLLocationManager.location` (از هر رشته؛ خودِ مدیر فقط روی رشتهٔ اصلی).
 *   - `requestLocationUpdates` ⇐ یک `requestLocation()` ِ یک‌باره (تنها صدازنندهٔ کدِ مشترک یک نقطه می‌خواهد و
 *     با نخستین نقطه `removeUpdates` می‌کند). شکست ⇐ بی خبر، مثلِ فراهم‌کنندهٔ بی‌جوابِ اندروید؛ مهلت را کدِ مشترک دارد.
 *   - `getCurrentLocation` (اندرویدِ ۱۱+) با `SDK_INT = 0` صدا زده نمی‌شود؛ با این حال همان یک‌باره است (شکست ⇐ null).
 */
class Location internal constructor(
  val provider: String?,
  val latitude: Double,
  val longitude: Double,
  /** متر؛ منفی ⇐ «دقت معلوم نیست» (`hasAccuracy() = false`، مثلِ اندروید `accuracy = 0`) */
  private val horizontal: Float,
  /** میلی‌ثانیهٔ یونیکس */
  val time: Long,
) {
  fun hasAccuracy(): Boolean = horizontal >= 0f
  val accuracy: Float get() = if (horizontal >= 0f) horizontal else 0f

  internal companion object {
    /** نقطهٔ CoreLocation ⇐ همان شکلِ اندروید */
    fun of(loc: CLLocation): Location {
      val (lat, lng) = loc.latLng()
      val acc = loc.horizontalAccuracy
      return Location(
        provider = if (acc in 0.0..GPS_METERS) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER,
        latitude = lat,
        longitude = lng,
        horizontal = if (acc >= 0.0) acc.toFloat() else -1f,
        time = (loc.timestamp.timeIntervalSince1970 * 1000).toLong(),
      )
    }

    /** دقتی که تا آن «gps» شمرده می‌شود (CoreLocation منبع را نمی‌گوید) */
    const val GPS_METERS = 50.0
  }
}

interface LocationListener {
  fun onLocationChanged(location: Location)
  fun onProviderDisabled(provider: String) {}
  fun onProviderEnabled(provider: String) {}
  fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}

/** `IosContext.getSystemService(LOCATION_SERVICE)` همین را می‌دهد */
class LocationManager private constructor() {
  /** گیرنده‌های منتظر ⇐ کارِ پسِ هر کدام نزدِ `IosLocation`؛ فقط روی رشتهٔ اصلی */
  private val waiting = HashMap<LocationListener, (CLLocation?) -> Unit>()

  fun getProviders(enabledOnly: Boolean): List<String> = listOf(GPS_PROVIDER)

  fun isProviderEnabled(provider: String): Boolean = provider == GPS_PROVIDER

  fun getLastKnownLocation(provider: String): Location? = IosLocation.lastNow()?.let { Location.of(it) }

  fun requestLocationUpdates(
    provider: String,
    minTimeMs: Long,
    minDistanceM: Float,
    listener: LocationListener,
    looper: android.os.Looper?,
  ) = IosUi.onMain {
    waiting.remove(listener)?.let(IosLocation::dropFix)
    val take: (CLLocation?) -> Unit = { loc ->
      waiting.remove(listener)
      if (loc != null) listener.onLocationChanged(Location.of(loc))
    }
    waiting[listener] = take
    IosLocation.requestFix(take)
  }

  fun removeUpdates(listener: LocationListener) = IosUi.onMain {
    waiting.remove(listener)?.let(IosLocation::dropFix)
  }

  fun getCurrentLocation(provider: String, cancellationSignal: CancellationSignal?, executor: Executor, consumer: Consumer<Location?>) =
    IosUi.onMain {
      IosLocation.requestFix { loc ->
        if (cancellationSignal?.isCanceled != true) executor.execute(Runnable { consumer.accept(loc?.let { Location.of(it) }) })
      }
    }

  companion object {
    const val GPS_PROVIDER = "gps"
    const val NETWORK_PROVIDER = "network"
    const val PASSIVE_PROVIDER = "passive"
    const val FUSED_PROVIDER = "fused"

    internal val ios = LocationManager()
  }
}
