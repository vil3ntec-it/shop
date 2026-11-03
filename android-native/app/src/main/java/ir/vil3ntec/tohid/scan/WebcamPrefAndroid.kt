package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 *  HW-08 — «وب‌کم به‌عنوانِ بارکدخوان» روی همین دستگاه (`tohid-ui`).
 *
 *  ⛔ پیش‌فرض **خاموش** (ردهٔ B ِ `SettingsTiers`: وابسته به دستگاهِ جانبی). خاموش ⇒ کادرِ اسکنِ کامپیوتر همان گیرندهٔ
 *  بارکدخوانِ USB/بلوتوث ِ امروز است و هیچ دوربینی باز نمی‌شود. روشن ⇒ همان کادر تصویرِ زندهٔ وب‌کم را هم نشان
 *  می‌دهد و بارکدِ جلوی دوربین را می‌خواند؛ بارکدخوانِ سخت‌افزاری کنارش کار می‌کند.
 *  `camera` شناسهٔ دوربینِ برگزیده است (تهی ⇒ نخستین دوربینِ وصل، `WebcamScan.pickCamera`).
 */
object WebcamPref {
  val on = mutableStateOf(false)
  val camera = mutableStateOf("")
  private var loaded = false
  private const val PREFS = "tohid-ui"
  private const val KEY_ON = "webcam_scan"
  private const val KEY_CAMERA = "webcam_id"

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    val p = context.getSharedPreferences(PREFS, 0)
    on.value = p.getBoolean(KEY_ON, false)
    camera.value = p.getString(KEY_CAMERA, "") ?: ""
  }

  fun set(context: Context, value: Boolean) {
    on.value = value
    context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_ON, value).apply()
  }

  fun choose(context: Context, id: String) {
    camera.value = id
    context.getSharedPreferences(PREFS, 0).edit().putString(KEY_CAMERA, id).apply()
  }
}
