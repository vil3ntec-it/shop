package ir.vil3ntec.tohid.data

import android.Manifest
import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/*
 *  جایگزینِ iOS ِ `data/DeviceLocation.kt` (همان API). موقعیتِ iOS (CoreLocation، اجازهٔ «هنگامِ استفاده»)
 *  گامِ ۳ است؛ تا آن روز «اجازه نیست» — دقیقاً همان مسیرِ اندرویدِ بی‌اجازه: `current` ⇐ null، و فقط
 *  موقعیتِ پیش‌تر ذخیره‌شده (که در آیفون هنوز نیست) خوانده می‌شود.
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

  val PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
  )

  fun granted(context: Context): Boolean = false

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

  suspend fun current(context: Context, force: Boolean = false, maxAgeMs: Long = 10 * 60 * 1000L): LocationFix? = null
}
