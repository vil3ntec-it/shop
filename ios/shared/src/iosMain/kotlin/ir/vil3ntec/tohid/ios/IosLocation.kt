package ir.vil3ntec.tohid.ios

import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

/*
 *  موقعیتِ آیفون (CoreLocation) پشتِ همان `data/DeviceLocation` ِ اندروید (از گامِ ۷ خودِ فایلِ اندروید، با shim ِ
 *  `android.location` ‎android/location/Location.kt‎) — فقط جایی که کدِ مشترک از پیش
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

  /**
   *  آخرین موقعیتِ دانسته (بی پرسیدن از سخت‌افزار) — `LocationManager.getLastKnownLocation` ِ shim (‎android/location‎).
   *  از هر رشته: کدِ مشترک آن را بیرون از رشتهٔ اصلی هم می‌خواهد (`LocationPing` در کارِ پس‌زمینه)، ولی مدیر فقط
   *  روی رشتهٔ اصلی ساخته و خوانده می‌شود ⇐ گذرِ همگام به آن رشته (رشتهٔ اصلی هرگز منتظرِ این خواندن نمی‌ماند).
   */
  fun lastNow(): CLLocation? {
    if (NSThread.isMainThread) return manager().location
    var loc: CLLocation? = null
    dispatch_sync(dispatch_get_main_queue()) { loc = manager().location }
    return loc
  }

  /** یک موقعیتِ تازه برای `take` (شکست ⇐ `take(null)`)؛ فقط روی رشتهٔ اصلی. مهلت را صدازننده می‌گذارد */
  fun requestFix(take: (CLLocation?) -> Unit) {
    waitingFix += take
    manager().requestLocation()
  }

  /** همان `take` دیگر خبر نگیرد (لغو یا `removeUpdates`)؛ فقط روی رشتهٔ اصلی */
  fun dropFix(take: (CLLocation?) -> Unit) {
    waitingFix -= take
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
