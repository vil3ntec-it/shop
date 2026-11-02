package ir.vil3ntec.tohid.ios

import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/*
 *  موقعیتِ آیفون (CoreLocation) پشتِ همان `data/DeviceLocation` ِ مشترک — فقط جایی که کدِ مشترک از پیش
 *  می‌خواهد (لوکیشنِ دکان F0-07 با رضایتِ صریح، پیک، ویترینِ نزدیک). فقط «هنگامِ استفاده» و یک‌باره
 *  (`requestLocation`)، نه دنبال‌کردنِ پیوسته و نه پس‌زمینه. همه روی رشتهٔ اصلی (قاعدهٔ CLLocationManager).
 */
internal object IosLocation {
  private var manager: CLLocationManager? = null
  private val delegate = Delegate()
  private val waitingAuth = ArrayList<(Boolean) -> Unit>()
  private val waitingFix = ArrayList<(CLLocation?) -> Unit>()

  /** فقط روی رشتهٔ اصلی */
  private fun manager(): CLLocationManager = manager ?: CLLocationManager().also {
    it.delegate = delegate
    it.desiredAccuracy = kCLLocationAccuracyHundredMeters
    manager = it
  }

  private fun status(): Int = manager?.authorizationStatus ?: CLLocationManager.authorizationStatus()

  fun granted(): Boolean = status().let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }

  fun request(done: (Boolean) -> Unit) = IosUi.onMain {
    val m = manager()
    if (m.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
      done(granted())
      return@onMain
    }
    waitingAuth += done
    m.requestWhenInUseAuthorization()
  }

  /** آخرین موقعیتِ دانسته (بی پرسیدن از سخت‌افزار) */
  suspend fun last(): CLLocation? = withContext(Dispatchers.Main) { manager().location }

  /** یک موقعیتِ تازه؛ نشد ⇐ null (مهلت را فراخواننده می‌گذارد) */
  suspend fun once(): CLLocation? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
      val m = manager()
      val take: (CLLocation?) -> Unit = { loc -> if (cont.isActive) cont.resume(loc) }
      waitingFix += take
      cont.invokeOnCancellation { IosUi.onMain { waitingFix -= take } }
      m.requestLocation()
    }
  }

  fun CLLocation.latLng(): Pair<Double, Double> = coordinate.useContents { latitude to longitude }

  private class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
      if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) return
      val ok = manager.authorizationStatus.let { it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways }
      val list = waitingAuth.toList()
      waitingAuth.clear()
      list.forEach { it(ok) }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
      flush(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
      flush(null)
    }

    private fun flush(loc: CLLocation?) {
      val list = waitingFix.toList()
      waitingFix.clear()
      list.forEach { it(loc) }
    }
  }
}
