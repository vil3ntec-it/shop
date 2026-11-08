package ir.vil3ntec.tohid.customer.ios

import ir.vil3ntec.tohid.core.shopper.AddressRules
import ir.vil3ntec.tohid.core.shopper.GeoPin
import kotlin.math.roundToInt
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 *  موقعیتِ گوشی برای پینِ نشانی (CHK-08) — همتای `LocationAndroid.kt` با CLLocationManager: اجازهٔ «هنگامِ استفاده» فقط
 *  با زدنِ «موقعیتِ فعلیِ من» (`NSLocationWhenInUseUsageDescription` ِ Info.plist)، یک موقعیت (`requestLocation`)،
 *  نه/خطا ⇒ null. فقط روی رشتهٔ اصلی (کلیکِ صفحه همان‌جاست).
 */
object LocationIos {
  private var manager: CLLocationManager? = null
  private var delegate: Delegate? = null

  fun current(done: (GeoPin?) -> Unit) {
    delegate?.finish(null)
    val d = Delegate { pin ->
      manager = null
      delegate = null
      done(pin)
    }
    val m = CLLocationManager()
    m.delegate = d
    m.desiredAccuracy = kCLLocationAccuracyBest
    manager = m
    delegate = d
    when {
      m.authorizationStatus == kCLAuthorizationStatusNotDetermined -> m.requestWhenInUseAuthorization()
      granted(m) -> m.requestLocation()
      else -> d.finish(null)
    }
  }

  private fun granted(m: CLLocationManager) =
    m.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse || m.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways

  private class Delegate(private val done: (GeoPin?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private var finished = false

    fun finish(pin: GeoPin?) {
      if (finished) return
      finished = true
      done(pin)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
      when {
        manager.authorizationStatus == kCLAuthorizationStatusNotDetermined -> Unit
        granted(manager) -> manager.requestLocation()
        else -> finish(null)
      }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
      val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return finish(null)
      val (lat, lng) = loc.coordinate.useContents { latitude to longitude }
      val acc = loc.horizontalAccuracy.takeIf { it >= 0 }?.roundToInt()
      finish(GeoPin(AddressRules.round6(lat), AddressRules.round6(lng), acc))
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) = finish(null)
  }
}
