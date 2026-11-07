package ir.vil3ntec.tohid.customer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import ir.vil3ntec.tohid.core.shopper.AddressRules
import ir.vil3ntec.tohid.core.shopper.GeoPin
import kotlin.math.roundToInt

/**
 *  موقعیتِ گوشی برای پینِ نشانی (CHK-08) — `LocationManager` ِ خودِ سیستم (بی Google Play Services، گوشیِ بی Play هم).
 *  اجازه را `MainActivity` فقط با زدنِ «موقعیتِ فعلیِ من» می‌پرسد. یک موقعیتِ تازه تا ۲۰ ثانیه؛ نیامد ⇒ آخرین موقعیتِ
 *  دانسته اگر کمتر از ۱۰ دقیقه کهنه است؛ وگرنه null (صفحه می‌گوید GPS/اجازه را ببینید یا پیوندِ نقشه بچسبانید).
 */
object LocationAndroid {
  private const val WAIT_MS = 20_000L
  private const val STALE_MS = 10 * 60 * 1000L

  @SuppressLint("MissingPermission")
  fun current(context: Context, done: (GeoPin?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return done(null)
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return done(null)
    val main = Handler(Looper.getMainLooper())
    var finished = false
    lateinit var listener: LocationListener
    fun finish(l: Location?) {
      if (finished) return
      finished = true
      runCatching { lm.removeUpdates(listener) }
      done(l?.let { GeoPin(AddressRules.round6(it.latitude), AddressRules.round6(it.longitude), if (it.hasAccuracy()) it.accuracy.roundToInt() else null) })
    }
    listener = object : LocationListener {
      override fun onLocationChanged(location: Location) = finish(location)
      //  اندروید ۷ تا ۱۰ این سه را بی پیاده‌سازیِ پیش‌فرض صدا می‌زند (AbstractMethodError بی آن‌ها)
      @Deprecated("API < 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
      override fun onProviderEnabled(provider: String) = Unit
      override fun onProviderDisabled(provider: String) = Unit
    }
    try {
      providers.forEach { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
    } catch (_: SecurityException) {
      return finish(null)
    }
    main.postDelayed({
      val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
      finish(last?.takeIf { System.currentTimeMillis() - it.time < STALE_MS })
    }, WAIT_MS)
  }
}
