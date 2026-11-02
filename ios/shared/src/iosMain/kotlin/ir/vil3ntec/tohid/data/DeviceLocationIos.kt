package ir.vil3ntec.tohid.data

import android.Manifest
import android.content.Context
import ir.vil3ntec.tohid.ios.IosLocation
import ir.vil3ntec.tohid.ios.IosLocation.latLng
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.CoreLocation.CLLocation
import platform.Foundation.timeIntervalSince1970

/*
 *  جایگزینِ iOS ِ `data/DeviceLocation.kt` (همان API و همان قاعده‌ها، روی CoreLocation ‎ios/IosLocation.kt‎):
 *  بی اجازه ⇐ null؛ موقعیتِ ذخیره‌شدهٔ تازه‌تر از ده دقیقه ⇐ همان؛ سپس آخرین موقعیتِ دانستهٔ گوشی اگر به اندازهٔ
 *  `maxAgeMs` تازه است؛ وگرنه یک موقعیتِ تازه با مهلتِ ۱۲ ثانیه. هر موقعیتِ گرفته‌شده در همان پروندهٔ تنظیمِ اندروید
 *  (`tohid-location`) می‌ماند. اجازه را کدِ مشترک با `PERMISSIONS` می‌پرسد (‎ios/IosPermissions.kt‎).
 */
data class LocationFix(
  val lat: Double,
  val lng: Double,
  val accuracy: Float = -1f,
  val source: String = "",
  val at: Long = ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis(),
) {
  fun json(): JsonObject = buildJsonObject {
    put("lat", JsonPrimitive(lat))
    put("lng", JsonPrimitive(lng))
    put("accuracy", JsonPrimitive(accuracy))
    put("source", JsonPrimitive(source))
  }
}

object DeviceLocation {
  private const val PREFS = "tohid-location"
  private const val FRESH_MS = 10 * 60 * 1000L

  val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
  )

  fun granted(context: Context): Boolean = IosLocation.granted()

  fun remembered(context: Context): LocationFix? {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!p.contains("lat")) return null
    return LocationFix(
      lat = Double.fromBits(p.getLong("lat", 0)),
      lng = Double.fromBits(p.getLong("lng", 0)),
      accuracy = p.getFloat("acc", -1f),
      source = p.getString("src", "").orEmpty(),
      at = p.getLong("at", 0),
    )
  }

  private fun remember(context: Context, fix: LocationFix) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putLong("lat", fix.lat.toRawBits())
      .putLong("lng", fix.lng.toRawBits())
      .putFloat("acc", fix.accuracy)
      .putString("src", fix.source)
      .putLong("at", fix.at)
      .apply()
  }

  suspend fun current(context: Context, force: Boolean = false, maxAgeMs: Long = FRESH_MS): LocationFix? {
    if (!granted(context)) return null
    if (!force) {
      remembered(context)?.let { if (ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis() - it.at < FRESH_MS) return it }
    }
    IosLocation.last()?.toFix()?.takeIf { ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis() - it.at < maxAgeMs }?.let { fix ->
      remember(context, fix)
      return fix
    }
    val fresh = withTimeoutOrNull(12_000) { IosLocation.once() }?.toFix()
    fresh?.let { remember(context, it) }
    return fresh
  }

  private fun CLLocation.toFix(): LocationFix {
    val (lat, lng) = latLng()
    return LocationFix(
      lat = lat,
      lng = lng,
      accuracy = if (horizontalAccuracy >= 0) horizontalAccuracy.toFloat() else -1f,
      //  اندروید: «gps» یا «network»؛ CoreLocation منبع را نمی‌گوید ⇐ از دقت: زیرِ ۵۰ متر همان GPS است
      source = if (horizontalAccuracy in 0.0..50.0) "gps" else "network",
      at = (timestamp.timeIntervalSince1970 * 1000).toLong(),
    )
  }
}
