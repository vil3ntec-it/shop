package ir.vil3ntec.tohid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.resume

/**
 *  یک نقطه روی نقشه — همان که به سرور می‌رود.
 *
 *  `source` می‌گوید از کجا آمده: `gps` دقیق ولی کند، `network` سریع ولی
 *  چند صد متر خطا. سرور هر دو را قبول می‌کند و همین برچسب است که بعداً
 *  می‌گوید به کدام‌شان می‌شود تکیه کرد.
 */
data class LocationFix(
  val lat: Double,
  val lng: Double,
  val accuracy: Float = -1f,
  val source: String = "",
  val at: Long = System.currentTimeMillis(),
) {
  fun json(): JsonObject = buildJsonObject {
    put("lat", JsonPrimitive(lat))
    put("lng", JsonPrimitive(lng))
    put("accuracy", JsonPrimitive(accuracy))
    put("source", JsonPrimitive(source))
  }
}

/**
 *  لوکیشنِ دستگاه.
 *
 *  ── قرارِ صاحب مخزن ────────────────────────────────────────────────
 *  «بدون اینکه برنامه برود ثبت‌نام کند هم لوکیشن باید روشن باشد و
 *  لوکیشنِ طرف ثبت بشود و بیاید به سرور» — پس گرفتنِ لوکیشن به حساب بند
 *  نیست و همان اولِ باز شدنِ برنامه یک بار انجام می‌شود.
 *
 *  ── چرا `LocationManager` و نه Play Services ───────────────────────
 *  `FusedLocationProvider` دقیق‌تر است ولی یک وابستگیِ گوگل می‌آورد و
 *  روی گوشی‌های بدونِ سرویسِ گوگل — که در بازارِ همین برنامه کم نیستند —
 *  اصلاً کار نمی‌کند. آنچه لازم داریم «کجای شهر» است، نه چند متر آن‌ور‌تر.
 *
 *  ── چرا سریع ───────────────────────────────────────────────────────
 *  اول آخرین نقطهٔ شناخته‌شده خوانده می‌شود؛ اگر تازه باشد، همان جواب
 *  است و انتظاری در کار نیست. تنها وقتی سراغِ گرفتنِ نقطهٔ تازه می‌رویم
 *  که چیزی در دست نباشد — و آن هم با مهلت، تا کاربر پشتِ یک چرخِ
 *  بی‌پایان نماند.
 */
object DeviceLocation {

  private const val PREFS = "tohid-location"
  private const val KEY_LAT = "lat"
  private const val KEY_LNG = "lng"
  private const val KEY_ACC = "acc"
  private const val KEY_AT = "at"
  private const val KEY_SRC = "src"

  /** نقطه‌ای که از این تازه‌تر باشد، به‌اندازهٔ کافی تازه است */
  private const val FRESH_MS = 10 * 60 * 1000L

  val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
  )

  fun granted(context: Context): Boolean = PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  /** آخرین نقطه‌ای که خودمان ذخیره کرده‌ایم — برای نشان دادنِ فوری */
  fun remembered(context: Context): LocationFix? {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!p.contains(KEY_LAT)) return null
    return LocationFix(
      lat = java.lang.Double.longBitsToDouble(p.getLong(KEY_LAT, 0)),
      lng = java.lang.Double.longBitsToDouble(p.getLong(KEY_LNG, 0)),
      accuracy = p.getFloat(KEY_ACC, -1f),
      source = p.getString(KEY_SRC, "").orEmpty(),
      at = p.getLong(KEY_AT, 0),
    )
  }

  private fun remember(context: Context, fix: LocationFix) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(fix.lat))
      .putLong(KEY_LNG, java.lang.Double.doubleToRawLongBits(fix.lng))
      .putFloat(KEY_ACC, fix.accuracy)
      .putString(KEY_SRC, fix.source)
      .putLong(KEY_AT, fix.at)
      .apply()
  }

  /**
   *  گرفتنِ لوکیشن.
   *
   *  @param force نقطهٔ ذخیره‌شده نادیده گرفته شود و از دستگاه پرسیده شود
   *  @return `null` یعنی اجازه نبود، لوکیشن خاموش بود یا در مهلت جواب نداد
   *    — هیچ‌کدام خطا نیست و هیچ‌کدام نباید جلوی کارِ کاربر را بگیرد.
   */
  suspend fun current(context: Context, force: Boolean = false): LocationFix? {
    if (!granted(context)) return null
    if (!force) {
      remembered(context)?.let { if (System.currentTimeMillis() - it.at < FRESH_MS) return it }
    }

    val manager = runCatching {
      context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }.getOrNull() ?: return null

    lastKnown(manager)?.let { fix -> remember(context, fix); return fix }

    //  چیزی در دست نیست: یک نقطهٔ تازه می‌خواهیم، ولی نه بیشتر از ۱۲ ثانیه
    val fresh = withTimeoutOrNull(12_000) { requestOnce(context, manager) }
    fresh?.let { remember(context, it) }
    return fresh
  }

  /** آخرین نقطهٔ هر فراهم‌کننده — دقیق‌ترینِ تازه‌ها */
  private fun lastKnown(manager: LocationManager): LocationFix? = runCatching {
    val now = System.currentTimeMillis()
    manager.getProviders(true)
      .mapNotNull { p -> @Suppress("MissingPermission") manager.getLastKnownLocation(p) }
      .filter { now - it.time < FRESH_MS }
      .minByOrNull { it.accuracy.takeIf { a -> a > 0 } ?: Float.MAX_VALUE }
      ?.let { it.toFix() }
  }.getOrNull()

  /** یک بار پرسیدن از دستگاه — بدون نگه داشتنِ گیرنده روی برنامه */
  private suspend fun requestOnce(context: Context, manager: LocationManager): LocationFix? =
    suspendCancellableCoroutine { cont ->
      val provider = when {
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> null
      }
      if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }

      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val signal = android.os.CancellationSignal()
          cont.invokeOnCancellation { runCatching { signal.cancel() } }
          @Suppress("MissingPermission")
          manager.getCurrentLocation(
            provider, signal, ContextCompat.getMainExecutor(context)
          ) { location -> if (cont.isActive) cont.resume(location?.toFix()) }
        } else {
          val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
              runCatching { manager.removeUpdates(this) }
              if (cont.isActive) cont.resume(location.toFix())
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("لازمهٔ نسخه‌های قدیمی")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
          }
          cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
          @Suppress("MissingPermission")
          manager.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
        }
      }.onFailure { if (cont.isActive) cont.resume(null) }
    }

  private fun Location.toFix() = LocationFix(
    lat = latitude,
    lng = longitude,
    accuracy = if (hasAccuracy()) accuracy else -1f,
    source = if (provider == LocationManager.GPS_PROVIDER) "gps" else "network",
    at = if (time > 0) time else System.currentTimeMillis(),
  )
}
